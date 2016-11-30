package com.analyzedgg.api.services.riot

import com.analyzedgg.api.domain.riot.RiotSummoner
import com.analyzedgg.api.services.riot.SummonerService._

import scala.concurrent.Await
import scala.concurrent.duration._

object SummonerService {
  case object SummonerNotFound extends Exception
}

case class SummonerService() extends RiotService {
  def getByName(region: String, name: String): RiotSummoner = {
    val response = Await.result(riotGetRequest(region, summonerByName + name), 5.seconds)
    response.status.intValue() match {
      case 200 => Await.result(mapRiotTo(response.entity, classOf[RiotSummoner]), 5.seconds)
      case 404 => throw SummonerNotFound
      case _ => throw new RuntimeException(s"An unknown error occurred. Riot API response:\n$response")
    }
  }
}
