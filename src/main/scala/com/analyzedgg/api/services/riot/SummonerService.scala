package com.analyzedgg.api.services.riot

import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes.{NotFound, OK}
import com.analyzedgg.api.domain.riot.RiotSummoner
import com.analyzedgg.api.services.SummonerManager.GetSummoner
import com.analyzedgg.api.services.riot.SummonerService._

object SummonerService {

  case object SummonerNotFound extends Exception

}

case class SummonerService() extends RiotService {
  def getByName(data: GetSummoner): GetSummoner = {
    riotGetRequest(data.region, summonerByName + data.name).mapTo[HttpResponse].map(httpResponse =>
      httpResponse.status match {
        case OK =>
          mapRiotTo(httpResponse.entity, classOf[RiotSummoner]).mapTo[RiotSummoner].onSuccess {
            case riotSummoner => data.summonerPromise.success(riotSummoner.summoner)
          }
        case NotFound => data.summonerPromise.failure(SummonerNotFound)
        case _ => data.summonerPromise.failure(new RuntimeException(s"An unknown error occurred. Riot API response:\n$httpResponse"))
      })
    data
  }
}
