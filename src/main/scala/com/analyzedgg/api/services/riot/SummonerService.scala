package com.analyzedgg.api.services.riot

import com.analyzedgg.api.domain.riot.RiotSummoner

import scala.concurrent.Await
import scala.concurrent.duration._

case class SummonerService() extends RiotService {
  def getByName(region: String, name: String): RiotSummoner = {
    val response = riotGetRequest(region, summonerByName + name)
    Await.result(mapRiotTo(Await.result(response, 5.seconds).entity, classOf[RiotSummoner]), 5.seconds)
  }
}
