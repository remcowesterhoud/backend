package com.analyzedgg.api.services.riot

import com.analyzedgg.api.domain.riot.RiotRecentMatches
import com.analyzedgg.api.services.MatchHistoryManager.GetMatches
import com.analyzedgg.api.services.riot.TempMatchService.FailedRetrievingRecentMatches
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Await
import scala.concurrent.duration._

object TempMatchService {

  case object FailedRetrievingRecentMatches extends Exception

}

case class TempMatchService() extends RiotService with LazyLogging {
  def getRecentMatchIds(data: GetMatches, amount: Int): GetMatches = {
    val queryParams: Map[String, String] = Map("beginIndex" -> 0.toString, "endIndex" -> amount.toString)
    val response = Await.result(riotGetRequest(data.region, matchListBySummonerId + data.summonerId, queryParams), 5.seconds)
    response.status.intValue() match {
      case 200 =>
        val recentMatchList = Await.result(mapRiotTo(response.entity, classOf[RiotRecentMatches]), 5.seconds)
        data.lastIdsPromise.success(recentMatchList.matches.map(_.matchId))
      case 404 => data.lastIdsPromise.failure(FailedRetrievingRecentMatches)
      case _ => data.lastIdsPromise.failure(new RuntimeException(s"An unknown error occurred. Riot API response:\n$response"))
    }
    data
  }
}
