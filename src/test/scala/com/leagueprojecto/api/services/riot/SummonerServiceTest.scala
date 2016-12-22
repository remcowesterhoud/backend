package com.leagueprojecto.api.services.riot

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.scaladsl.Flow
import com.analyzedgg.api.JsonProtocols
import com.analyzedgg.api.services.SummonerManager.GetSummoner
import com.analyzedgg.api.services.riot.SummonerService
import com.analyzedgg.api.services.riot.SummonerService.SummonerNotFound
import com.leagueprojecto.api.testHelpers.TestClass

import scala.concurrent.Await
import scala.concurrent.duration._

class SummonerServiceTest extends TestClass with JsonProtocols {

  class TestSummonerService(httpResponse: HttpResponse) extends SummonerService {
    override def riotConnectionFlow(region: String, service: String, hostType: String): Flow[HttpRequest, HttpResponse, Any] = {
      Flow[HttpRequest].map { _ =>
        httpResponse
      }
    }
  }

  "SummonerService" should "return a RiotSummoner" in {
    // Setup
    val region = "euw"
    val name = "Wagglez"
    val getSummonerRequest = GetSummoner(region, name)

    Given("an instance of the SummonerService")
    val mockSummoner =
      """
      {
      "wagglez":{
          "id":52477463,
          "name":"Wagglez",
          "profileIconId":785,
          "summonerLevel":30,
          "revisionDate":1434315156000
        }
      }
      """
    val response = HttpResponse(status = OK, entity = mockSummoner)
    val service = new TestSummonerService(response)
    When("a valid Summoner is requested")
    val result = service.getByName(getSummonerRequest)
    Then("the request promise should contain a valid summoner")
    val summoner = Await.result(result.summonerPromise.future, 3.seconds)
    summoner.id shouldBe 52477463
    summoner.name shouldBe "Wagglez"
    summoner.profileIconId shouldBe 785
    summoner.summonerLevel shouldBe 30
    summoner.revisionDate shouldBe 1434315156000L
  }

  it should "throw a SummonerNotFound exception" in {
    // Setup
    val region = "euw"
    val name = "Wagglez"
    val getSummonerRequest = GetSummoner(region, name)

    Given("an instance of the SummonerService")
    val response = HttpResponse(status = NotFound)
    val service = new TestSummonerService(response)
    When("a non existing Summoner is requested")
    val result = service.getByName(getSummonerRequest)
    Then("the request promise should contain a SummonerNotFound exception")
    val exception = the[SummonerNotFound.type] thrownBy Await.result(result.summonerPromise.future, 3.seconds)
    exception shouldEqual SummonerNotFound
  }

  it should "throw a RunTimeException" in {
    // Setup
    val region = "euw"
    val name = "Wagglez"
    val getSummonerRequest = GetSummoner(region, name)

    Given("an instance of the SummonerService")
    val response = HttpResponse(status = InternalServerError)
    val service = new TestSummonerService(response)
    When("the Riot api cannot be reached")
    val result = service.getByName(getSummonerRequest)
    Then("the request promise should contain a RuntimeException")
    val exception = the[RuntimeException] thrownBy Await.result(result.summonerPromise.future, 3.seconds)
    exception.isInstanceOf[RuntimeException] shouldBe true
    exception.getMessage.contains("An unknown error occurred").shouldBe(true)
  }
}
