package com.analyzedgg.api

import com.analyzedgg.api.domain.riot._
import com.analyzedgg.api.domain._
import com.analyzedgg.api.services.riot.ChampionService.ChampionsResponse
import spray.json._

trait JsonProtocols extends DefaultJsonProtocol {
  //shared
  implicit val playerFormat: RootJsonFormat[Player] = jsonFormat2(Player.apply)

  // Domain
  implicit val playerStatsFormat: RootJsonFormat[PlayerStats] = jsonFormat4(PlayerStats.apply)
  implicit val teamFormat: RootJsonFormat[Team] = jsonFormat1(Team.apply)
  implicit val teamsFormat: RootJsonFormat[Teams] = jsonFormat2(Teams.apply)
  implicit val matchDetailFormat: RootJsonFormat[MatchDetail] = jsonFormat12(MatchDetail.apply)
  implicit val matchFormat: RootJsonFormat[Match] = jsonFormat(Match, "timestamp", "champion", "region", "queue", "season", "matchId", "role", "platformId", "lane")
  implicit val championFormat: RootJsonFormat[Champion] = jsonFormat5(Champion.apply)
  implicit val championListFormat: RootJsonFormat[ChampionList] = jsonFormat3(ChampionList.apply)
  implicit val championsResponseFormat: RootJsonFormat[ChampionsResponse] = jsonFormat1(ChampionsResponse.apply)

  implicit val summonerFormat: RootJsonFormat[Summoner] = jsonFormat5(Summoner.apply)

  // Riot Match related
  implicit val timeLineFormat: RootJsonFormat[ParticipantTimeline] = jsonFormat2(ParticipantTimeline.apply)
  implicit val participantStatsFormat: RootJsonFormat[ParticipantStats] = jsonFormat5(ParticipantStats.apply)
  implicit val participantIdentityFormat: RootJsonFormat[ParticipantIdentity] = jsonFormat2(ParticipantIdentity.apply)
  implicit val participantFormat: RootJsonFormat[Participant] = jsonFormat5(Participant.apply)
  implicit val riotMatchFormat: RootJsonFormat[RiotMatch] = jsonFormat7(RiotMatch.apply)
}
