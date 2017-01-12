package com.analyzedgg.api.services.riot

import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes.{NotFound, OK}
import com.analyzedgg.api.domain.riot._
import com.analyzedgg.api.domain.{MatchDetail, PlayerStats, Team, Teams}
import com.analyzedgg.api.services.riot.MatchService.{FailedRetrievingMatchDetails, FailedRetrievingRecentMatches}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future

object MatchService {

  case object FailedRetrievingRecentMatches extends Exception

  case object FailedRetrievingMatchDetails extends Exception

}

case class MatchService() extends RiotService with LazyLogging {
  def getRecentMatchIds(region: String, summonerId: Long, amount: Int): Future[Seq[Long]] = {
    val queryParams: Map[String, String] = Map("beginIndex" -> 0.toString, "endIndex" -> amount.toString)
    riotGetRequest(region, matchListBySummonerId + summonerId, queryParams).mapTo[HttpResponse].map(httpResponse =>
      httpResponse.status match {
        case OK => mapRiotTo(httpResponse.entity, classOf[RiotRecentMatches])
        case NotFound => throw FailedRetrievingRecentMatches
        case _ => throw new RuntimeException(s"An unknown error occurred. riot API response:\n$httpResponse")
      }
    ).flatMap(futureMatches => futureMatches.map(recentMatchList => recentMatchList.matches.map(_.matchId)))
  }


  def getMatchDetails(region: String, summonerId: Long, matchId: Long): Future[MatchDetail] = {
    riotGetRequest(region, matchById + matchId).mapTo[HttpResponse].map(httpResponse => {
      httpResponse.status match {
        case OK => mapRiotTo(httpResponse.entity, classOf[RiotMatch]).mapTo[RiotMatch]
        case _ =>
          logger.error(s"Failed retrieving match details.\nReason: $httpResponse")
          throw FailedRetrievingMatchDetails
      }
    }).flatMap(futureRiotMatch => {
      futureRiotMatch.map(toMatchDetail(_, summonerId))
    })
  }

  private[this] def toMatchDetail(riotMatch: RiotMatch, summonerId: Long): MatchDetail = {
    val participantIdentity = riotMatch.participantIdentities.filter(_.player.summonerId == summonerId).head
    val participant = riotMatch.participants.filter(_.participantId == participantIdentity.participantId).head
    val blueId = 100
    val redId = 200
    val blue: Seq[Player] = getPlayersFromTeam(riotMatch.participants, riotMatch.participantIdentities, blueId)
    val red: Seq[Player] = getPlayersFromTeam(riotMatch.participants, riotMatch.participantIdentities, redId)

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
