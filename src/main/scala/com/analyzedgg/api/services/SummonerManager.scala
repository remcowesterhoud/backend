package com.analyzedgg.api.services

import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, GraphDSL, RunnableGraph, Sink, Source}
import akka.stream.{ActorMaterializer, ClosedShape}
import com.analyzedgg.api.domain.Summoner
import com.analyzedgg.api.services.riot.SummonerService

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

object SummonerManager {
  private val service = SummonerService()
  implicit val executionContext = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())
  implicit val materializer = ActorMaterializer()(ActorSystem("summoner"))

  case class GetSummoner(region: String, name: String)

  def getSummoner(region: String, name: String): Summoner = {
    val graph = createGraph(GetSummoner(region, name))
    Await.result(graph.run(), 5.seconds)
  }

  private def retrieveFromRiot(data: GetSummoner): Summoner = {
    service.getByName(data.region, data.name).summoner
  }

  private def createGraph(summoner: GetSummoner) = {
    val out = Sink.head[Summoner]
    RunnableGraph.fromGraph(GraphDSL.create(out) { implicit builder => out =>
      // Import the implicits so the ~> syntax can be used
      import GraphDSL.Implicits._
      val in = Source.single[GetSummoner](summoner)
      val flow = Flow[GetSummoner].map(retrieveFromRiot)

      // Chain the Stream components together
      in ~> flow ~> out

      ClosedShape
    })
  }
}


