package com.analyzedgg.api.services.riot

import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Created by RemcoW on 12-12-2016.
  */
case class TempMatchService() extends RiotService with LazyLogging {
  def getRecentMatchIds(regionParam: String, summonerId: Long, queueType: String, championList: String, amount: Int): Unit = {
    val queryParams: Map[String, String] = Map("beginIndex" -> 0.toString, "endIndex" -> amount.toString)
    val response = Await.result(riotGetRequest(regionParam, matchListBySummonerId + summonerId, queryParams), 5.seconds)
    response.status.intValue() match {
      case 200 => data.summonerPromise.success(Await.result(mapRiotTo(response.entity, classOf[RiotSummoner]), 5.seconds).summoner)
      case 404 => data.summonerPromise.failure(SummonerNotFound)
      case _ => data.summonerPromise.failure(new RuntimeException(s"An unknown error occurred. Riot API response:\n$response"))
    }
  }
}
