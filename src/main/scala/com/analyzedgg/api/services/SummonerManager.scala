package com.analyzedgg.api.services

import java.util.NoSuchElementException
import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.pattern.CircuitBreaker
import akka.stream._
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, RunnableGraph, Sink, Source, SourceQueueWithComplete}
import com.analyzedgg.api.domain.Summoner
import com.analyzedgg.api.services.SummonerManager.GetSummoner
import com.analyzedgg.api.services.couchdb.SummonerRepository
import com.analyzedgg.api.services.couchdb.SummonerRepository.SummonerNotFound
import com.analyzedgg.api.services.riot.SummonerService
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent._
import scala.concurrent.duration._


/**
  * Created by RemcoW on 16-1-2017.
  */
object SummonerManager {
  private val manager: SummonerManager = new SummonerManager()

  def apply(): SummonerManager = manager

  case class GetSummoner(region: String, name: String) {
    var isCached: Boolean = false
    val result: Promise[Summoner] = Promise()
  }

}

class SummonerManager extends LazyLogging {
  private val maxFailures = 5
  lazy val couchDbCircuitBreaker =
    new CircuitBreaker(system.scheduler, maxFailures, callTimeout = 5.seconds, resetTimeout = 1.minute)
  implicit val executionContext: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())
  val system = ActorSystem("summoner-system")
  val decider: Supervision.Decider = { e =>
    logger.warn(e.getCause.toString)
    e.printStackTrace()
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
    getSummoner.result.future
  }

  private def createGraph(bufferSize: Int = 100): RunnableGraph[SourceQueueWithComplete[GetSummoner]] = {
    // Sink which will set the summoner to the result variable of the request
    val resultSink = Sink.foreach(setResult)
    // start of the Stream, GetSummoner requests can dynamically be passed to this Source
    val source = Source.queue[GetSummoner](bufferSize, OverflowStrategy.backpressure)

    // A custom Flow is created which will collect the Summoner information from either the cache or the Riot API
    val getSummonerFlow = Flow.fromGraph(GraphDSL.create() { implicit builder =>
      // Import the implicits so the ~> syntax can be used
      import GraphDSL.Implicits._

      // Flow that tries to get the Summoner from the cache
      val fromCacheFlow = builder.add(Flow[GetSummoner].map(retrieveFromCache).async)
      // Flow that tries to get the Summoner from the Riot api
      val fromRiotFlow = Flow[(GetSummoner, Future[Option[Summoner]])].map(retrieveFromRiot).async
      // Broadcast that sends the summoner to the cacheSink and the resultSink
      val summonerBroadcast = builder.add(Broadcast[(GetSummoner, Future[Summoner])](2))
      // Sink that caches the summoner in the database
      val cacheSink = Sink.foreach(cacheSummoner).async

      // Chain the different components of the flow together
      fromCacheFlow ~> fromRiotFlow ~> summonerBroadcast ~> cacheSink

      // The shape of this Graph is a Flow, meaning it has a single input and a single output.
      FlowShape(fromCacheFlow.in, summonerBroadcast.out(1))
    })
    // Connect the Source to the Flow to the Sink
    source.via(getSummonerFlow).to(resultSink)
  }

  private def retrieveFromCache(data: GetSummoner): (GetSummoner, Future[Option[Summoner]]) = {
    // Get the Summoner from the cache if possible
    (data, Future(repository.getByName(data.region, data.name)).map({
      data.isCached = true
      Some(_)
    })
      // If the Summoner is not in the cache a SummonerNotFound exception is thrown which is then handled here
      .recover({ case SummonerNotFound =>
      data.isCached = false
      None
    }))
  }

  private def retrieveFromRiot(data: (GetSummoner, Future[Option[Summoner]])): (GetSummoner, Future[Summoner]) = {
    // Try to get the summoner from Riot, only if it didn't exist in the cache
    (data._1, data._2.map(optionalSummoner => optionalSummoner.get)
      // If the Summoner did not exist in the cache a NoSuchElementException is thrown here. The Summoner is then requested from the Riot api
      .recoverWith({ case _: NoSuchElementException => service.getByName(data._1.region, data._1.name) }))
  }

  private def cacheSummoner(data: (GetSummoner, Future[Summoner])): Unit = {
    // If this Stream resulted in a Summoner and it did not come from our cache we store the Summoner in our cache
    data._2.onSuccess({ case summoner if !data._1.isCached => repository.save(summoner, data._1.region) })
  }

  private def setResult(data: (GetSummoner, Future[Summoner])): Unit = {
    // Set the result variable of the GetSummoner request to contain the requests Summoner
    data._1.result.completeWith(data._2)
  }
}
