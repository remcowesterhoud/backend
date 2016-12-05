package com.analyzedgg.api.services

import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, RunnableGraph, Sink, Source}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, ClosedShape, Supervision}
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
    val x = graph.run()
    val result = Await.result(graph.run(), 5.seconds)
    result.option.get
  }

  private def retrieveFromRiot(data: GetSummoner): GetSummoner = {
    val summoner = service.getByName(data.region, data.name).summoner
    data.option = Some(summoner)
    data
  }

  private def cacheSummoner(data: GetSummoner) = {
    repository.save(data.option.get, data.region)
  }

  private def createGraph(summoner: GetSummoner) = {
    val returnSink = Sink.head[GetSummoner]
    RunnableGraph.fromGraph(GraphDSL.create(returnSink) { implicit builder => returnSink =>
      // Import the implicits so the ~> syntax can be used
      import GraphDSL.Implicits._
      // Source
      val source = Source.single[GetSummoner](summoner)

      // Flows
      val fromRiotFlow = Flow[GetSummoner].map(retrieveFromRiot)
      val cacheBroadcast = builder.add(Broadcast[GetSummoner](2))

      // Sinks
      val cacheSink = Sink.foreach(cacheSummoner)

      // Chain the Stream components together
      // source: Start of stream, sends the passed GetSummoner object downstream
      // fromRiotFlow: Uses the GetSummoner object to retrieve the summoner from the Riot API
      // cacheBroadcast: Send the element to the returnSink and the cacheSink
      // returnSink: End of stream, returns the summoner
      // cacheSink: Caches the summoner in the database
      source ~> fromRiotFlow ~> cacheBroadcast ~> returnSink
      cacheBroadcast ~> cacheSink

      ClosedShape
    })
  }
}


