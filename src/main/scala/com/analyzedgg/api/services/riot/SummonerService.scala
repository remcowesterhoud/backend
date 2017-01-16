package com.analyzedgg.api.services.riot

import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes.{NotFound, OK}
import com.analyzedgg.api.domain.Summoner
import com.analyzedgg.api.domain.riot.RiotSummoner
import com.analyzedgg.api.services.riot.SummonerService._

import scala.concurrent.Future

object SummonerService {

  case object SummonerNotFound extends Exception

}

case class SummonerService() extends RiotService {
  def getByName(region: String, name: String): Future[Summoner] = {
    riotGetRequest(region, summonerByName + name).mapTo[HttpResponse].map(httpResponse =>
      httpResponse.status match {
        case OK => mapRiotTo(httpResponse.entity, classOf[RiotSummoner]).mapTo[RiotSummoner]
        case NotFound => throw SummonerNotFound
        case _ => throw new RuntimeException(s"An unknown error occurred. Riot API response:\n$httpResponse")
      })
      .flatMap(futureRiotSummoner => futureRiotSummoner.map(riotSummoner => riotSummoner.summoner))
  }
}
