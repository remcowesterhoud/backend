package com.analyzedgg.api.services

import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.pattern.CircuitBreaker
import akka.stream._
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, Sink, Source}
import com.analyzedgg.api.domain.Summoner
import com.analyzedgg.api.services.SummonerManager.GetSummoner
import com.analyzedgg.api.services.couchdb.SummonerRepository
import com.analyzedgg.api.services.riot.SummonerService
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent._
import scala.concurrent.duration._

object SummonerManager {

  case class GetSummoner(region: String, name: String, var summonerPromise: Promise[Summoner] = Promise[Summoner]()) {
    def result: Summoner = Await.result(summonerPromise.future, 5.seconds)
  }

  private val manager: SummonerManager = new SummonerManager()

  def apply(): SummonerManager = manager
}

class SummonerManager extends LazyLogging {
  protected val service = new SummonerService()
  implicit val executionContext: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())
  val system = ActorSystem("system")
  val decider: Supervision.Decider = { e =>
    logger.warn(e.getMessage)
    Supervision.Resume
  }
  lazy val couchDbCircuitBreaker =
    new CircuitBreaker(system.scheduler, maxFailures = 5, callTimeout = 5.seconds, resetTimeout = 1.minute)
  protected val repository = new SummonerRepository(couchDbCircuitBreaker)
  val materializerSettings: ActorMaterializerSettings = ActorMaterializerSettings(system).withSupervisionStrategy(decider)
  implicit val materializer: ActorMaterializer = ActorMaterializer(materializerSettings)(system)

  private val graph = createGraph().run()

  def getSummoner(region: String, name: String): Summoner = {
    val getSummoner = GetSummoner(region, name)
    graph.offer(getSummoner)
    getSummoner.result
  }

  private def retrieveFromCache(data: GetSummoner): GetSummoner = {
    val result = repository.getByName(data.region, data.name)
    if (result != null) {
      data.summonerPromise.success(result)
    }
    data
  }

  private def retrieveFromRiot(data: GetSummoner): GetSummoner = {
    service.getByName(data)
  }

  private def cacheSummoner(data: GetSummoner) = {
    data.summonerPromise.future.onSuccess { case summoner => repository.save(summoner, data.region)}
  }

  private def createGraph() = {
    val returnSink = Sink.ignore
    val source = Source.queue[GetSummoner](100, OverflowStrategy.backpressure)

    val getSummonerFlow = Flow.fromGraph(GraphDSL.create() { implicit builder =>
      // Import the implicits so the ~> syntax can be used
      import GraphDSL.Implicits._

      // Flows
      // Tries to retrieve the Summoner from the cache
      val fromCache = builder.add(Flow[GetSummoner].map(retrieveFromCache).async)
      // Tries to retrieve the Summoner from the Riot api
      val fromRiotFlow = Flow[GetSummoner].filter(data => !data.summonerPromise.isCompleted).map(retrieveFromRiot).async
      // Broadcasts the result from the cache so it can be returned if it existed, or retrieved from Riot if it didn't
      val cacheBroadcast1 = builder.add(Broadcast[GetSummoner](2))
      // Broadcasts the result from riot so it can be returned and cached
      val cacheBroadcast2 = builder.add(Broadcast[GetSummoner](2))
      // Merges the different flows that should output to returnSink into a single flow
      val merge = builder.add(Merge[GetSummoner](2))
      // Filters out elements where the summoner doesn't exist in the cache so it doesn't get returned yet
      val filterFlow = Flow[GetSummoner].filter(data => data.summonerPromise.isCompleted).async

      // Sinks
      // Caches the summoner in the database
      val cacheSink = Sink.foreach(cacheSummoner).async

      fromCache ~> cacheBroadcast1 ~> fromRiotFlow ~> cacheBroadcast2 ~> cacheSink
      cacheBroadcast1 ~> filterFlow ~> merge
      cacheBroadcast2 ~> merge

      FlowShape(fromCache.in, merge.out)
    })

    source.via(getSummonerFlow).to(returnSink)
  }
}


