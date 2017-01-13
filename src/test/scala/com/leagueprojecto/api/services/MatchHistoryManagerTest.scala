package com.leagueprojecto.api.services

import com.analyzedgg.api.domain._
import com.analyzedgg.api.services.MatchHistoryManager
import com.analyzedgg.api.services.couchdb.MatchRepository
import com.analyzedgg.api.services.riot.MatchService
import com.analyzedgg.api.services.riot.MatchService.{FailedRetrievingMatchDetails, FailedRetrievingRecentMatches, NoRecentMatchesPlayed}
import com.leagueprojecto.api.testHelpers.MatchMockData._
import com.leagueprojecto.api.testHelpers.SummonerMockData._
import com.leagueprojecto.api.testHelpers.TestClass

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/**
  * Created by RemcoW on 14-12-2016.
  */
class MatchHistoryManagerTest extends TestClass {

  class MockableMatchRepository extends MatchRepository(null)

  class TestMatchHistoryManager extends MatchHistoryManager {
    override val service: MatchService = stub[MatchService]
    override val repository: MatchRepository = stub[MockableMatchRepository]
  }

  "MatchHistoryManager" should "retrieve the 10 latest match details and cache them" in {
    // Setup
    val manager = new TestMatchHistoryManager()

    Given("10 matches have been played recently")
    val expectedMatches = ArrayBuffer[MatchDetail]()
    manager.service.getRecentMatchIds _ when(testRegion, testSummoner.id, 10) returns Future(Seq(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L))
    for (i <- 1L to 10L) {
      val detail = createMockDetails(i)
      expectedMatches.append(detail)
      manager.service.getMatchDetails _ when(testRegion, testSummoner.id, i) returns Future(detail)
    }
    And("the matches don't exist in the cache")
    manager.repository.getDetailsAllowEmpty _ when(*, testRegion, testSummoner.id) returns Seq()
    When("the match details are being retrieved")
    val actualMatches = Await.result(manager.getMatchHistory(testRegion, testSummoner.id, "", ""), 10.seconds)
    Then("the 10 latest match details should be returned")
    actualMatches.length shouldEqual 10
    for (actualDetails <- actualMatches) {
      expectedMatches contains actualDetails shouldBe true
    }
    And("the match details should be cached")
    // 500ms sleep required as the db save call is made asynchronously. This means this test will check if the save
    // call was made before this actually happened.
    Thread.sleep(500)
    manager.repository.save _ verify(actualMatches, testRegion, testSummoner.id)
  }

  it should "retrieve less then 10 matches if the summoner did not play 10 matches yet" in {
    // Setup
    val manager = new TestMatchHistoryManager()

    Given("3 matches have been played recently")
    val expectedMatches = ArrayBuffer[MatchDetail]()
    manager.service.getRecentMatchIds _ when(testRegion, testSummoner.id, 10) returns Future(Seq(1L, 2L, 3L))
    for (i <- 1L to 3L) {
      val detail = createMockDetails(i)
      expectedMatches.append(detail)
      manager.service.getMatchDetails _ when(testRegion, testSummoner.id, i) returns Future(detail)
    }
    And("the matches don't exist in the cache")
    manager.repository.getDetailsAllowEmpty _ when(*, testRegion, testSummoner.id) returns Seq()
    When("the match details are being retrieved")
    val actualMatches = Await.result(manager.getMatchHistory(testRegion, testSummoner.id, "", ""), 5.seconds)
    And("the 3 latest match details should be returned")
    actualMatches.length shouldEqual 3
    for (actualDetails <- actualMatches) {
      expectedMatches contains actualDetails shouldBe true
    }
    And("the match details should be cached")
    // 500ms sleep required as the db save call is made asynchronously. This means this test will check if the save
    // call was made before this actually happened.
    Thread.sleep(500)
    manager.repository.save _ verify(expectedMatches, testRegion, testSummoner.id)
  }

  it should "throw a NoRecentMatchesPlayed exception if the summoner has played no matches" in {
    // Setup
    val manager = new TestMatchHistoryManager()

    Given("no matches have been played recently")
    manager.service.getRecentMatchIds _ when(testRegion, testSummoner.id, 10) returns Future.failed(NoRecentMatchesPlayed)
    When("the match details are being retrieved")
    val exception = the[NoRecentMatchesPlayed.type] thrownBy Await.result(manager.getMatchHistory(testRegion, testSummoner.id, "", ""), 5.seconds)
    Then("the thrown exception should be a NoRecentMatchesPlayed exception")
    exception shouldBe NoRecentMatchesPlayed
  }

  it should "remove matches that could not be retrieved from the recent matches list" in {
    // Setup
    val manager = new TestMatchHistoryManager()
    val id = 1L

    Given("a match has been played recently")
    manager.service.getRecentMatchIds _ when(testRegion, testSummoner.id, 10) returns Future(Seq(id))
    And("the match doesn't exist in the cache")
    manager.repository.getDetailsAllowEmpty _ when(*, testRegion, testSummoner.id) returns Seq()
    And("the match details can not be retrieved from the Riot API")
    manager.service.getMatchDetails _ when(testRegion, testSummoner.id, id) returns Future.failed(FailedRetrievingMatchDetails)
    When("the match details are being retrieved")
    val actualMatches = Await.result(manager.getMatchHistory(testRegion, testSummoner.id, "", ""), 5.seconds)
    Then("an empty list should be returned")
    actualMatches.isEmpty shouldBe true
  }

  it should "throw a FailedRetrievingRecentMatches exception if the latest match ids can't be retrieved from the Riot API" in {
    // Setup
    val manager = new TestMatchHistoryManager()

    Given("the summoner's last match ids can't be retrieved")
    manager.service.getRecentMatchIds _ when(testRegion, testSummoner.id, 10) returns Future.failed(FailedRetrievingRecentMatches)
    When("the match details are being retrieved")
    val exception = the[FailedRetrievingRecentMatches.type] thrownBy Await.result(manager.getMatchHistory(testRegion, testSummoner.id, "", ""), 5.seconds)
    Then("a FailedRetrievingRecentMatches exception should be thrown")
    exception shouldEqual FailedRetrievingRecentMatches
  }

  it should "throw an exception if the Riot API is offline" in {
    // Setup
    val manager = new TestMatchHistoryManager()

    Given("the summoner's last match ids can't be retrieved")
    manager.service.getRecentMatchIds _ when(testRegion, testSummoner.id, 10) returns Future.failed(new RuntimeException)
    When("the match details are being retrieved")
    val exception = the[RuntimeException] thrownBy Await.result(manager.getMatchHistory(testRegion, testSummoner.id, "", ""), 5.seconds)
    Then("an exception should be thrown")
    exception.isInstanceOf[RuntimeException] shouldBe true
  }
}
