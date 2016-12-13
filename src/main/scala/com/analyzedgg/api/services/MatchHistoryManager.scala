package com.analyzedgg.api.services

import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.pattern.CircuitBreaker
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, RunnableGraph, Sink, Source, SourceQueueWithComplete}
import akka.stream._
import com.analyzedgg.api.domain.MatchDetail
import com.analyzedgg.api.domain.riot.RecentMatch
import com.analyzedgg.api.services.MatchHistoryManager.GetMatches
import com.analyzedgg.api.services.riot.TempMatchService
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutorService, Promise}
import scala.util.{Failure, Success}

object MatchHistoryManager {
  private val manager: MatchHistoryManager = new MatchHistoryManager()

  def apply(): MatchHistoryManager = manager

  case class GetMatches(region: String, summonerId: Long, queueType: String, championList: String, var detailsPromise: Promise[Seq[MatchDetail]] = Promise(), var lastIdsPromise: Promise[Seq[Long]] = Promise()) {
    def result: Seq[MatchDetail] = Await.result(detailsPromise.future, 5.seconds)
  }

}

class MatchHistoryManager extends LazyLogging {
  lazy val couchDbCircuitBreaker =
    new CircuitBreaker(system.scheduler, maxFailures = 5, callTimeout = 5.seconds, resetTimeout = 1.minute)
  implicit val executionContext: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())
  val system = ActorSystem("match-system")
  val decider: Supervision.Decider = { e =>
    logger.warn(e.getMessage)
    Supervision.Resume
  }
  val materializerSettings: ActorMaterializerSettings = ActorMaterializerSettings(system).withSupervisionStrategy(decider)
  implicit val materializer: ActorMaterializer = ActorMaterializer(materializerSettings)(system)
  private final val matchAmount: Int = 10

  protected val service = new TempMatchService()
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

      val lastIdsFlow = builder.add(Flow[GetMatches].map(getLastIds).async)
      val lastIdsMerge = builder.add(Merge[GetMatches](1))
      val lastIdsFailedFilter = Flow[GetMatches].filter(filterLastIds).async

      lastIdsFlow ~> lastIdsFailedFilter ~> lastIdsMerge

      FlowShape(lastIdsFlow.in, lastIdsMerge.out)
    })
    source.via(flow).to(returnSink)
  }

  private def getLastIds(data: GetMatches): GetMatches = {
    logger.info(s"Requesting last $matchAmount match ids from riot")
    service.getRecentMatchIds(data, matchAmount)
  }

  private def filterLastIds(data: GetMatches): Boolean = {
    data.lastIdsPromise.future.value match {
      case Some(Success(v)) => false
      case Some(Failure(e)) =>
        data.detailsPromise.failure(e)
        true
      case _ =>
        val msg = "Could not determine status of last ID promise"
        logger.error(msg)
        data.detailsPromise.failure(new RuntimeException(msg))
        true
    }
  }

  private def hasEmptyValues(mergedMatches: Map[Long, Option[MatchDetail]]): Boolean =
    mergedMatches.values.exists(_.isEmpty)

  private def getValues(mergedMatches: Map[Long, Option[MatchDetail]]): Seq[MatchDetail] =
    mergedMatches.values.map(_.get).toSeq
}
