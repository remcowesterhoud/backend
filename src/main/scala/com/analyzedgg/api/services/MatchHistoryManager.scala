package com.analyzedgg.api.services

import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.pattern.CircuitBreaker
import akka.stream.scaladsl.{Flow, GraphDSL, RunnableGraph, Sink, Source, SourceQueueWithComplete}
import akka.stream._
import com.analyzedgg.api.domain.MatchDetail
import com.analyzedgg.api.services.MatchHistoryManager.GetMatches
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutorService, Promise}

object MatchHistoryManager {
  private val manager: MatchHistoryManager = new MatchHistoryManager()

  def apply(): MatchHistoryManager = manager

  case class GetMatches(region: String, summonerId: Long, queueType: String, championList: String, var matchesPromise: Promise[Seq[MatchDetail]] = Promise()) {
    def result: Seq[MatchDetail] = Await.result(matchesPromise.future, 5.seconds)
  }

}

class MatchHistoryManager extends LazyLogging {
  lazy val couchDbCircuitBreaker =
    new CircuitBreaker(system.scheduler, maxFailures = 5, callTimeout = 5.seconds, resetTimeout = 1.minute)
  implicit val executionContext: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())
  val system = ActorSystem("system")
  val decider: Supervision.Decider = { e =>
    logger.warn(e.getMessage)
    Supervision.Resume
  }
  val materializerSettings: ActorMaterializerSettings = ActorMaterializerSettings(system).withSupervisionStrategy(decider)
  implicit val materializer: ActorMaterializer = ActorMaterializer(materializerSettings)(system)
  private final val matchAmount: Int = 10

  protected val service = null
  protected val repository = null
  private val graph = createGraph.run()

  def getMatchHistory(region: String, summonerId: Long, queueParam: String, championParam: String): Seq[MatchDetail] = {
    val getMatches = GetMatches(region, summonerId, queueParam, championParam)
    graph.offer(getMatches)
    getMatches.result
  }

  private def createGraph: RunnableGraph[SourceQueueWithComplete[GetMatches]] = {
    // This Sink doesn't need to do anything with the elements as the reference to the objects is being tracked till they complete the Stream
    val returnSink = Sink.ignore
    val source = Source.queue[GetMatches](100, OverflowStrategy.backpressure)

    val flow = Flow.fromGraph(GraphDSL.create() { implicit builder =>
      // Import the implicits so the ~> syntax can be used
      import GraphDSL.Implicits._

      val lastIdsFlow = builder.add(Flow[GetMatches].map(getLastIds))

      FlowShape(null, null)
    })
    source.via(flow).to(returnSink)
  }

  private def getLastIds(data: GetMatches): Seq[Long] ={
    logger.info(s"Requesting last $matchAmount match ids from riot")
    null
  }

  private def hasEmptyValues(mergedMatches: Map[Long, Option[MatchDetail]]): Boolean =
    mergedMatches.values.exists(_.isEmpty)

  private def getValues(mergedMatches: Map[Long, Option[MatchDetail]]): Seq[MatchDetail] =
    mergedMatches.values.map(_.get).toSeq
}
