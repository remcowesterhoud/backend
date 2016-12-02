package com.analyzedgg.api.services.riot

import java.util.concurrent.{ExecutorService, Executors}

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, _}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, Materializer}
import com.analyzedgg.api.domain.riot.{RiotSummoner, RiotSummonerDeserializer}
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService, Future}

object RiotService {

  class ServiceNotAvailable(message: String) extends Exception

  class TooManyRequests(message: String) extends Exception

}

trait RiotService extends LazyLogging {
  val objectMapper = new ObjectMapper with ScalaObjectMapper
  objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
  objectMapper.registerModule(DefaultScalaModule)

  val riotChampionModule: SimpleModule = new SimpleModule()
  riotChampionModule.addDeserializer(classOf[RiotSummoner], new RiotSummonerDeserializer())
  objectMapper.registerModule(riotChampionModule)

  private val config = ConfigFactory.load()

  val executorService: ExecutorService = Executors.newCachedThreadPool()
  implicit val executionContext: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(executorService)
  implicit val system = ActorSystem("service")
  implicit val materializer: Materializer = ActorMaterializer()

  private val port: Int = config.getInt("riot.api.port")
  private val api_key: String = config.getString("riot.api.key").trim

  protected def riotConnectionFlow(region: String, service: String, hostType: String): Flow[HttpRequest, HttpResponse, Any] = {
    val hostname: String = config.getString(s"riot.api.hostname.$hostType")
    val host = hostname.replace(":region", region)

    logger.debug(s"endpoint host: $host")


    if (config.getBoolean("riot.api.tls")) {
      Http(system).outgoingConnectionHttps(host, port)
    } else {
      Http(system).outgoingConnection(host, port)
    }
  }

  protected def riotGetRequest(regionParam: String, serviceParam: String, queryParams: Map[String, String] = Map.empty,
                               prefix: String = "api/lol", hostType: String = "api"): Future[HttpResponse] = {
    val queryString = (queryParams + ("api_key" -> api_key)).collect { case x => x._1 + "=" + x._2 }.mkString("&")
    val URL = s"/$prefix/$regionParam/$serviceParam?$queryString"
    logger.debug(s"endpoint: $URL")
    Source.single(RequestBuilding.Get(URL)).via(riotConnectionFlow(regionParam, serviceParam, hostType)).runWith(Sink.head)
  }

  protected def mapRiotTo[R](response: ResponseEntity, responseClass: Class[R]): Future[R] = {
    Unmarshal(response).to[String].map { mappedResult =>
      logger.debug(s"Got json string $mappedResult")
      objectMapper.readValue[R](mappedResult, responseClass)
    }
  }

  // Services
  val championByTags: String = config.getString("riot.services.championByTags.endpoint")
  val summonerByName: String = config.getString("riot.services.summonerbyname.endpoint")
  val matchById: String = config.getString("riot.services.match.endpoint")
  val matchListBySummonerId: String = config.getString("riot.services.matchlist.endpoint")
}