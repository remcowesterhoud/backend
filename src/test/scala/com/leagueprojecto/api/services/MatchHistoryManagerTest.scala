package com.leagueprojecto.api.services

import com.analyzedgg.api.domain._
import com.analyzedgg.api.domain.riot.Player
import com.analyzedgg.api.services.MatchHistoryManager
import com.analyzedgg.api.services.MatchHistoryManager.GetMatches
import com.analyzedgg.api.services.riot.TempMatchService
import com.leagueprojecto.api.testHelpers.TestClass

import scala.collection.mutable.ArrayBuffer

/**
  * Created by RemcoW on 14-12-2016.
  */
class MatchHistoryManagerTest extends TestClass {

  class TestMatchHistoryManager extends MatchHistoryManager {
    override val service: TempMatchService = stub[TempMatchService]
  }

  val testSummoner = Summoner(123123123, "Wagglez", 100, 1434315156000L, 30)
  val testRegion = "euw"

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

  "MatchHistoryManager" should "retrieve the 10 latest match details" in {
    // Setup
    val manager = new TestMatchHistoryManager()

    Given("10 matches have been played recently")
    val expectedMatches = ArrayBuffer[MatchDetail]()
    manager.service.getRecentMatchIds _ when(*, *) onCall { (data: GetMatches, amount: Int) =>
      data.lastIdsPromise.success(Seq(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L))
      data
    }
    for (i <- 1L to 10L) {
      val detail = createMockDetails(i)
      expectedMatches.append(detail)
      manager.service.getMatchDetails _ when(testRegion, testSummoner.id, i) returns detail
    }
    When("the match details are being retrieved")
    val actualMatches = manager.getMatchHistory(testRegion, testSummoner.id, "", "")
    Then("the 10 latests match details should be returned")
    for (actualDetails <- actualMatches) {
      expectedMatches contains actualDetails shouldBe true
    }
  }
}
