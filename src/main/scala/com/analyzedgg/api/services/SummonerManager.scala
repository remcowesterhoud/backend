package com.analyzedgg.api.services

import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source}
import com.analyzedgg.api.domain.Summoner
import com.analyzedgg.api.services.SummonerManager.GetSummoner
import com.analyzedgg.api.services.couchdb.SummonerRepository
import com.analyzedgg.api.services.riot.SummonerService
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutorService}

object SummonerManager extends LazyLogging {

  case class GetSummoner(region: String, name: String, var option: Option[Summoner] = None)

}

class SummonerManager {
  protected val service = new SummonerService()
  protected val repository = new SummonerRepository()
  implicit val executionContext: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())
  val system = ActorSystem("system")
  val decider: Supervision.Decider = { e =>
    Supervision.Stop
    throw e
  }
  val materializerSettings: ActorMaterializerSettings = ActorMaterializerSettings(system).withSupervisionStrategy(decider)
  implicit val materializer: ActorMaterializer = ActorMaterializer(materializerSettings)(system)

  def getSummoner(region: String, name: String): Summoner = {
    val graph = createGraph(GetSummoner(region, name))
    val result = Await.result(graph.run(), 5.seconds)
    result.option.get
  }

  private def retrieveFromCache(data: GetSummoner): GetSummoner = {
    println("Retrieved from cache")
    data.option = repository.getByName(data.region, data.name)
    data
  }

  private def retrieveFromRiot(data: GetSummoner): GetSummoner = {
    println("Retrieved from riot")
    val summoner = service.getByName(data.region, data.name).summoner
    data.option = Some(summoner)
    data
  }

  private def cacheSummoner(data: GetSummoner) = {
    println("Cached stuff")
    repository.save(data.option.get, data.region)
  }

  private def createGraph(summoner: GetSummoner) = {
    val returnSink = Sink.head[GetSummoner].async
    RunnableGraph.fromGraph(GraphDSL.create(returnSink) { implicit builder => returnSink =>
      // Import the implicits so the ~> syntax can be used
      import GraphDSL.Implicits._
      // Source
      val source = Source.single[GetSummoner](summoner)

      // Flows
      // Tries to retrieve the Summoner from the cache
      val fromCache = Flow[GetSummoner].map(retrieveFromCache).async
      // Tries to retrieve the Summoner from the Riot api
      val fromRiotFlow = Flow[GetSummoner].filter(data => data.option.isEmpty).map(retrieveFromRiot).async
      // Broadcasts the result from the cache so it can be returned if it existed, or retrieved from Riot if it didn't
      val cacheBroadcast1 = builder.add(Broadcast[GetSummoner](2))
      // Broadcasts the result from riot so it can be returned and cached
      val cacheBroadcast2 = builder.add(Broadcast[GetSummoner](2))
      // Merges the different flows that should output to returnSink into a single flow
      val merge = builder.add(Merge[GetSummoner](2))
      // Filters out elements where the summoner doesn't exist in the cache so it doesn't get returned yet
      val filterFlow = Flow[GetSummoner].filter(data => data.option.isDefined).async

      // Sinks
      // Caches the summoner in the database
      val cacheSink = Sink.foreach(cacheSummoner).async

      source ~> fromCache ~> cacheBroadcast1 ~> fromRiotFlow ~> cacheBroadcast2 ~> cacheSink
      cacheBroadcast1 ~> filterFlow ~> merge
      cacheBroadcast2 ~> merge
      merge ~> returnSink

      ClosedShape
    })
  }
}


