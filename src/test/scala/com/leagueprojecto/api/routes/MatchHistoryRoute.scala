package com.leagueprojecto.api.routes

import com.analyzedgg.api.domain.riot.Player
import com.analyzedgg.api.services.riot.SummonerService.SummonerNotFound
import com.leagueprojecto.api.routes.RoutesTest

// Do not remove the following import. IntelliJ might say it's not used, but it is for converting json to case classes.
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.StatusCodes._
import com.analyzedgg.api.domain._
import com.analyzedgg.api.services.MatchHistoryManager

class MatchHistoryRoute extends RoutesTest {
  val endpoint = "/api/euw/matchhistory"

  val validSummonerId = 123456789
  val invalidSummonerId = 987654321

  val validPlayerStats = PlayerStats(10, 5, 3, 2)
  val validTeamRed = Team(List(Player(validSummonerId, "Minikoen")))
  val validTeamBlue = Team(List(Player(validSummonerId, "Waggles")))
  val validTeams = Teams(validTeamRed, validTeamBlue)
  val validMatchVersion = "6.3.0.240"
  val validHistory =
    MatchDetail(12312312L, "RANKED_SOLO_5x5", 1600, 1432328493438L, validSummonerId, 100, "DUO_SUPPORT", "BOTTOM", winner = true,
      validMatchVersion, validPlayerStats, validTeams)
  val validHistoryList = List(validHistory)

  class MatchHistoryManagerMock extends MatchHistoryManager {
    override def getMatchHistory(region: String, summonerId: Long, queueParam: String, championParam: String): Seq[MatchDetail] = {
      summonerId match {
        case `validSummonerId` => validHistoryList
        case `invalidSummonerId` => throw SummonerNotFound
        case _ => throw new Exception("Internal server error")
      }
    }
  }

  override def getMatchHistoryManager: MatchHistoryManager = {
    new MatchHistoryManagerMock()
  }

  "MatchHistory path" should "return a json response with a Match History in it" in {
    Get(s"$endpoint/$validSummonerId") ~> routes ~> check {
      status shouldBe OK
      contentType shouldBe `application/json`

      responseAs[Seq[MatchDetail]] shouldBe validHistoryList
    }
  }

  it should "return a 404 not found when the summoner id does not exist" in {
    Get(s"$endpoint/$invalidSummonerId") ~> routes ~> check {
      status shouldBe NotFound
      responseAs[String] shouldBe ""
    }
  }

  it should "return a 500 internal server error when an unknown exception is thrown" in {
    Get(s"$endpoint/0") ~> routes ~> check {
      status shouldBe InternalServerError
      responseAs[String] shouldBe ""
    }
  }

  it should "always send Options back on requests" in {
    Options(s"$endpoint/$validSummonerId") ~> routes ~> check {
      status shouldBe OK
      responseAs[String] shouldBe ""
    }
  }
}
