package com.leagueprojecto.api.services.riot

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.scaladsl.Flow
import com.analyzedgg.api.JsonProtocols
import com.analyzedgg.api.domain.riot._
import com.analyzedgg.api.domain.{MatchDetail, PlayerStats, Team, Teams}
import com.analyzedgg.api.services.MatchHistoryManager.GetMatches
import com.analyzedgg.api.services.riot.TempMatchService
import com.leagueprojecto.api.testHelpers.TestClass
import spray.json._


class MatchServiceTest extends TestClass with JsonProtocols {

  class TestMatchService(httpResponse: HttpResponse) extends TempMatchService {
    override def riotConnectionFlow(region: String, service: String, hostType: String): Flow[HttpRequest, HttpResponse, Any] = {
      Flow[HttpRequest].map { _ => httpResponse }
    }
  }

  "MatchService" should "return MatchDetails" in {
    // Setup
    val matchId = 1000L
    val matchDetails = mockRiotMatch.toJson.prettyPrint

    Given("an instance of the MatchService")
    val response = HttpResponse(status = OK, entity = matchDetails)
    val service = new TestMatchService(response)
    When("valid MatchDetails are requested")
    val result = service.getMatchDetails(region, summonerId, matchId)
    Then("the expected MatchDetail should be returned")
    result shouldEqual mockMatchDetail
  }

  it should "return null" in {
    // Setup
    val matchId = 1000L

    Given("an instance of the MatchService")
    val response = HttpResponse(status = NotFound)
    val service = new TestMatchService(response)
    When("invalid MatchDetails are requested")
    val result = service.getMatchDetails("invalid", summonerId, matchId)
    Then("an null should be returned")
    result shouldBe null
  }

  val region = "euw"
  val summonerId = 210L
  val mockParticipant1 = Participant(
    201L,
    301L,
    100L,
    ParticipantTimeline("tank", "top"),
    ParticipantStats(1L, 11L, 111L, 1111L, winner = true)
  )
  val mockParticipant2 = Participant(
    202L,
    302L,
    100L,
    ParticipantTimeline("support", "mid"),
    ParticipantStats(2L, 22L, 222L, 2222L, winner = true)
  )
  val mockParticipant3 = Participant(
    203L,
    303L,
    200L,
    ParticipantTimeline("assault", "bot"),
    ParticipantStats(3L, 33L, 333L, 3333L, winner = false)
  )
  val mockParticipantIdentity1 = ParticipantIdentity(
    mockParticipant1.participantId,
    Player(210L, "Summoner210")
  )
  val mockParticipantIdentity2 = ParticipantIdentity(
    mockParticipant2.participantId,
    Player(220L, "Summoner220")
  )
  val mockParticipantIdentity3 = ParticipantIdentity(
    mockParticipant3.participantId,
    Player(230L, "Summoner230")
  )
  val mockRiotMatch = RiotMatch(
    100L,
    "queueType",
    12345L,
    54321L,
    Seq(mockParticipantIdentity1, mockParticipantIdentity2, mockParticipantIdentity3),
    Seq(mockParticipant1, mockParticipant2, mockParticipant3),
    "matchVersion"
  )
  val mockPlayerStats = PlayerStats(
    mockParticipant1.stats.minionsKilled,
    mockParticipant1.stats.kills,
    mockParticipant1.stats.deaths,
    mockParticipant1.stats.assists
  )
  val mockTeams = Teams(
    Team(Seq(
      Player(mockParticipantIdentity1.player.summonerId, mockParticipantIdentity1.player.summonerName),
      Player(mockParticipantIdentity2.player.summonerId, mockParticipantIdentity2.player.summonerName)
    )),
    Team(Seq(
      Player(mockParticipantIdentity3.player.summonerId, mockParticipantIdentity3.player.summonerName)
    ))
  )
  val mockMatchDetail = MatchDetail(
    mockRiotMatch.matchId,
    mockRiotMatch.queueType,
    mockRiotMatch.matchDuration,
    mockRiotMatch.matchCreation,
    mockParticipantIdentity1.player.summonerId,
    mockParticipant1.championId,
    mockParticipant1.timeline.role,
    mockParticipant1.timeline.lane,
    mockParticipant1.stats.winner,
    mockRiotMatch.matchVersion,
    mockPlayerStats,
    mockTeams
  )

}
