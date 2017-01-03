package com.analyzedgg.api

import akka.actor.{ActorRef, ActorSystem}
import akka.event.LoggingAdapter
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.pattern.{CircuitBreaker, ask}
import akka.util.Timeout
import com.analyzedgg.api.domain.Summoner
import com.analyzedgg.api.services.riot.ChampionService.GetChampions
import com.analyzedgg.api.services.riot.{ChampionService, RiotService, SummonerService, MatchService}
import com.analyzedgg.api.services.{MatchHistoryManager, SummonerManager}
import com.typesafe.config.Config

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.util.matching.Regex

trait Routes extends JsonProtocols {
  implicit val system: ActorSystem

  implicit def executor: ExecutionContextExecutor

  implicit val timeout: Timeout = Timeout(1.minute)

  private val maxFailures = 5
  lazy val couchDbCircuitBreaker =
    new CircuitBreaker(system.scheduler, maxFailures, callTimeout = 5.seconds, resetTimeout = 1.minute)(executor)

  def config: Config

  val logger: LoggingAdapter

  def regionMatcher: Regex = config.getString("riot.regions").r

  def queueMatcher: String = config.getString("riot.queueTypes")

  val optionsSupport: Route = {
    options {
      complete("")
    }
  }

  protected implicit def myExceptionHandler = ExceptionHandler {
    case e: RiotService.TooManyRequests => complete(HttpResponse(TooManyRequests))
    case SummonerService.SummonerNotFound => complete(HttpResponse(NotFound))
    case MatchService.FailedRetrievingRecentMatches => complete(HttpResponse(ServiceUnavailable))
    //         ChampionService.FailedRetrievingChampions |
    //         SummonerService.FailedRetrievingSummoner => complete(HttpResponse(ServiceUnavailable))
    case _ => complete(HttpResponse(InternalServerError))
  }

  val corsHeaders = List(RawHeader("Access-Control-Allow-Origin", "*"),
    RawHeader("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS, DELETE"),
    RawHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization"))

  def championsRoute(implicit region: String): Route = {
    pathPrefix("champions") {
      pathEndOrSingleSlash {
        get {
          complete {
            val championManager = createChampionActor
            val future = championManager ? GetChampions(region)
            future.mapTo[ChampionService.ChampionsResponse].map(_.championList)
          }
        } ~ optionsSupport
      }
    }
  }

  def summonerRoute(implicit region: String): Route = {
    pathPrefix("summoner" / Segment) { name =>
      pathEndOrSingleSlash {
        get {
          complete {
            val future = getSummonerManager.getSummoner(region, name)
            future.mapTo[Summoner]
          }
        } ~ optionsSupport
      }
    }
  }

  def matchHistoryRoute(implicit region: String): Route = {
    pathPrefix("matchhistory" / LongNumber) { summonerId =>
      parameters("queue" ? "", "champions" ? "") { (queueParam: String, championParam: String) =>
        pathEndOrSingleSlash {
          get {
            complete {
              getMatchHistoryManager.getMatchHistory(region, summonerId, queueParam, championParam)
            }
          } ~ optionsSupport
        }
      }
    }
  }


  val routes: Route = {
    //  logRequestResult("API-service") {
    respondWithHeaders(corsHeaders) {
      pathPrefix("api" / regionMatcher) { regionSegment =>
        implicit val region = regionSegment.toLowerCase

        championsRoute ~ summonerRoute ~ matchHistoryRoute
      }
    }
  }

  protected[Routes] def createChampionActor: ActorRef = system.actorOf(ChampionService.props)

  protected[Routes] def getSummonerManager: SummonerManager = SummonerManager()

  protected[Routes] def getMatchHistoryManager: MatchHistoryManager = MatchHistoryManager()
}
