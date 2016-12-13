package com.analyzedgg.api.services.riot

import akka.NotUsed
import akka.stream.scaladsl.{Flow, RunnableGraph, Sink, Source}
import com.analyzedgg.api.domain.{MatchDetail, PlayerStats, Team, Teams}
import com.analyzedgg.api.domain.riot._
import com.analyzedgg.api.services.MatchHistoryManager.GetMatches
import com.analyzedgg.api.services.riot.TempMatchService.FailedRetrievingRecentMatches
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{Await, Future}
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


  def getMatchDetails(region: String, summonerId: Long, matchId: Long): MatchDetail = {
    val response = Await.result(riotGetRequest(region, matchById + matchId), 5.seconds)
    response.status.intValue() match {
      case 200 => toMatchDetail(Await.result(mapRiotTo(response.entity, classOf[RiotMatch]), 5.seconds), summonerId)
      case _ => null
    }
  }

  private[this] def toMatchDetail(riotMatch: RiotMatch, summonerId: Long): MatchDetail = {
    val participantIdentity = riotMatch.participantIdentities.filter(_.player.summonerId == summonerId).head
    val participant = riotMatch.participants.filter(_.participantId == participantIdentity.participantId).head

    val blue: Seq[Player] = getPlayersFromTeam(riotMatch.participants, riotMatch.participantIdentities, 100)
    val red: Seq[Player] = getPlayersFromTeam(riotMatch.participants, riotMatch.participantIdentities, 200)

    MatchDetail(
      riotMatch.matchId,
      riotMatch.queueType,
      riotMatch.matchDuration,
      riotMatch.matchCreation,
      participantIdentity.player.summonerId,
      participant.championId,
      participant.timeline.role,
      participant.timeline.lane,
      participant.stats.winner,
      riotMatch.matchVersion,
      toPlayerStats(participant.stats),
      Teams(Team(blue), Team(red))
    )
  }

  private[this] def getPlayersFromTeam(participants: Seq[Participant], participantIdentities: Seq[ParticipantIdentity], teamId: Int): Seq[Player] = {
    // Create a list of all participants in `teamId`
    val participantIdsInTeam: Seq[Long] = participants.map(p =>
      p.participantId -> p.teamId
    ).filter(_._2 == teamId).map(_._1)

    // Get the id and name of all above summoners
    participantIdentities.flatMap(pId => {
      val participantId = pId.participantId

      if (participantIdsInTeam.contains(participantId)) {
        Some(
          Player(
            pId.player.summonerId,
            pId.player.summonerName
          )
        )
      } else {
        None
      }
    })
  }

  private[this] def toPlayerStats(stats: ParticipantStats): PlayerStats = {
    PlayerStats(stats.minionsKilled, stats.kills, stats.deaths, stats.assists)
  }
}
