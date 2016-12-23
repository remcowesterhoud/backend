package com.analyzedgg.api.services.riot

import akka.http.scaladsl.model.StatusCodes.OK
import akka.http.scaladsl.model.StatusCodes.NotFound
import com.analyzedgg.api.domain.riot.RiotSummoner
import com.analyzedgg.api.services.SummonerManager.GetSummoner
import com.analyzedgg.api.services.riot.SummonerService._

import scala.concurrent.Await
import scala.concurrent.duration._

object SummonerService {

  case object SummonerNotFound extends Exception

}

case class SummonerService() extends RiotService {
  def getByName(data: GetSummoner): GetSummoner = {
    val response = Await.result(riotGetRequest(data.region, summonerByName + data.name), 5.seconds)
    response.status match {
      case OK => data.summonerPromise.success(Await.result(mapRiotTo(response.entity, classOf[RiotSummoner]), 5.seconds).summoner)
      case NotFound => data.summonerPromise.failure(SummonerNotFound)
      case _ => data.summonerPromise.failure(new RuntimeException(s"An unknown error occurred. Riot API response:\n$response"))
    }
    data
  }
}
