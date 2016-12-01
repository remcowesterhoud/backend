package com.analyzedgg.api.services

import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, GraphDSL, RunnableGraph, Sink, Source}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, ClosedShape, Supervision}
import com.analyzedgg.api.domain.Summoner
import com.analyzedgg.api.services.SummonerManager.GetSummoner
import com.analyzedgg.api.services.riot.SummonerService
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

object SummonerManager extends LazyLogging {
  case class GetSummoner(region: String, name: String)
}

class SummonerManager {
  private val service = createSummonerService()
  implicit val executionContext = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())
  val system = ActorSystem("system")
  val decider: Supervision.Decider = { e =>
    Supervision.Stop
    throw e
  }
  val materializerSettings = ActorMaterializerSettings(system).withSupervisionStrategy(decider)
  implicit val materializer = ActorMaterializer(materializerSettings)(system)

  def getSummoner(region: String, name: String): Summoner = {
    val graph = createGraph(GetSummoner(region, name))
    Await.result(graph.run(), 5.seconds)
  }

  private def retrieveFromRiot(data: GetSummoner): Summoner = {
    service.getByName(data.region, data.name).summoner
  }

  private def createGraph(summoner: GetSummoner) = {
    val sink = Sink.head[Summoner]
    RunnableGraph.fromGraph(GraphDSL.create(sink) { implicit builder => sink =>
      // Import the implicits so the ~> syntax can be used
      import GraphDSL.Implicits._
      val source = Source.single[GetSummoner](summoner)
      val fromRiotFlow = Flow[GetSummoner].map(retrieveFromRiot)

      // Chain the Stream components together
      // source: Start of stream, sends the passed GetSummoner object downstream
      // fromRiotFlow: Uses the GetSummoner object to retrieve the summoner from the Riot API
      // sink: End fo stream, returns the summoner
      source ~> fromRiotFlow ~> sink

      ClosedShape
    })
  }

  protected def createSummonerService(): SummonerService = SummonerService()
}


