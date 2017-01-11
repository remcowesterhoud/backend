package com.leagueprojecto.api.testHelpers

import com.analyzedgg.api.domain.riot.Player
import com.analyzedgg.api.domain.{MatchDetail, PlayerStats, Team, Teams}
import com.leagueprojecto.api.testHelpers.SummonerMockData._

/**
  * Created by RemcoW on 5-1-2017.
  */
object MatchMockData {
  def createMockDetails(matchId: Long): MatchDetail = {
    val playerStats = PlayerStats(
      101L,
      202L,
      303L,
      404L
    )
    val teams = Teams(
      Team(Seq(
        Player(testSummoner.id, testSummoner.name),
        Player(111L, "ally1"),
        Player(121L, "ally2")
      )),
      Team(Seq(
        Player(131L, "enemy1"),
        Player(141L, "enemy2"),
        Player(151L, "enemy3")
      ))
    )
    MatchDetail(
      matchId,
      "queueType",
      100L,
      200L,
      testSummoner.id,
      300L,
      "role",
      "lane",
      winner = true,
      "matchVersion",
      playerStats,
      teams
    )
  }

}
