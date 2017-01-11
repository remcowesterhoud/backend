package com.analyzedgg.api.services

import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.pattern.CircuitBreaker
import akka.stream._
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source, SourceQueueWithComplete}
import com.analyzedgg.api.domain.Summoner
import com.analyzedgg.api.services.SummonerManager.GetSummoner
import com.analyzedgg.api.services.couchdb.SummonerRepository
import com.analyzedgg.api.services.couchdb.SummonerRepository.SummonerNotFound
import com.analyzedgg.api.services.riot.SummonerService
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent._
import scala.concurrent.duration._

object SummonerManager {

  private val manager: SummonerManager = new SummonerManager()

  def apply(): SummonerManager = manager

  case class GetSummoner(region: String, name: String, var summonerPromise: Promise[Summoner] = Promise[Summoner]()) {
    def result: Future[Summoner] = summonerPromise.future
  }

}

class SummonerManager extends LazyLogging {
  private val maxFailures = 5
  lazy val couchDbCircuitBreaker =
    new CircuitBreaker(system.scheduler, maxFailures, callTimeout = 5.seconds, resetTimeout = 1.minute)
  implicit val executionContext: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())
  val system = ActorSystem("summoner-system")
  val decider: Supervision.Decider = { e =>
    logger.warn(e.getMessage)
    Supervision.Resume
  }
  val materializerSettings: ActorMaterializerSettings = ActorMaterializerSettings(system).withSupervisionStrategy(decider)
  implicit val materializer: ActorMaterializer = ActorMaterializer(materializerSettings)(system)
  protected val service = new SummonerService()
  protected val repository = new SummonerRepository(couchDbCircuitBreaker)
  private val graph = createGraph().run()

  def getSummoner(region: String, name: String): Future[Summoner] = {
    val getSummoner = GetSummoner(region, name)
    graph.offer(getSummoner)
    getSummoner.result
  }

  private def createGraph(bufferSize: Int = 100): RunnableGraph[SourceQueueWithComplete[GetSummoner]] = {
    // This Sink doesn't need to do anything with the elements as the reference to the objects is being tracked till they complete the Stream
    val returnSink = Sink.ignore
    // A Queue source allows for dynamically sending elements through the Stream
    val source = Source.queue[GetSummoner](bufferSize, OverflowStrategy.backpressure)
    // A custom Flow is created which will collect the Summoner information from either the cache or the Riot API
    val getSummonerFlow = Flow.fromGraph(GraphDSL.create() { implicit builder =>
      // Import the implicits so the ~> syntax can be used
      import GraphDSL.Implicits._
      // Flows
      // Tries to retrieve the Summoner from the cache
      val fromCacheFlow = builder.add(Flow[GetSummoner].map(retrieveFromCache).async)
      // Tries to retrieve the Summoner from the Riot api
      val fromRiotFlow = Flow[GetSummoner].filter(data => !data.summonerPromise.isCompleted).map(retrieveFromRiot).async
      // Broadcasts the result from the cache so it can be returned if it existed, or retrieved from Riot if it didn't
      val cacheResultBroadcast = builder.add(Broadcast[GetSummoner](2))
      // Broadcasts the result from riot so it can be returned and cached
      val riotResultBroadcast = builder.add(Broadcast[GetSummoner](2))
      // Merges the different flows that should output to returnSink into a single flow
      val merge = builder.add(Merge[GetSummoner](2))
      // Filters out elements where the summoner doesn't exist in the cache so it doesn't get returned yet
      val notCachedFilter = Flow[GetSummoner].filter(data => data.summonerPromise.isCompleted).async
      // Sinks
      // Caches the summoner in the database
      val cacheSink = Sink.foreach(cacheSummoner).async

      // String the Flow together
      fromCacheFlow ~> cacheResultBroadcast ~> fromRiotFlow ~> riotResultBroadcast ~> cacheSink
                       cacheResultBroadcast ~> notCachedFilter                     ~> merge
                                                               riotResultBroadcast ~> merge

      // The shape of this Graph is a Flow, meaning it has a single input and a single output.
      FlowShape(fromCacheFlow.in, merge.out)
    })
    // Connect the Source to the Flow to the Sink
    source.via(getSummonerFlow).to(returnSink)
  }

  private def retrieveFromCache(data: GetSummoner): GetSummoner = {
    try {
      data.summonerPromise.success(repository.getByName(data.region, data.name))
      data
    }
    catch { case SummonerNotFound => data }
  }

  private def retrieveFromRiot(data: GetSummoner): GetSummoner = {
    service.getByName(data)
  }

  private def cacheSummoner(data: GetSummoner) = {
    data.summonerPromise.future.onSuccess { case summoner => repository.save(summoner, data.region) }
  }
}


