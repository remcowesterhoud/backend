package com.leagueprojecto.api.services.riot

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.scaladsl.Flow
import com.analyzedgg.api.JsonProtocols
import com.analyzedgg.api.services.riot.SummonerService
import com.analyzedgg.api.services.riot.SummonerService.SummonerNotFound
import org.scalatest.{FlatSpec, GivenWhenThen, Matchers}

class SummonerServiceTest extends FlatSpec with Matchers with GivenWhenThen with JsonProtocols {

  class TestSummonerService(httpResponse: HttpResponse) extends SummonerService {
    override def riotConnectionFlow(region: String, service: String, hostType: String): Flow[HttpRequest, HttpResponse, Any] = {
      Flow[HttpRequest].map { _ =>
        httpResponse
      }
    }
  }

  "SummonerService" should "return a RiotSummoner" in {
    Given("an instance of the SummonerService")
    val summoner =
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
    val response = HttpResponse(status = OK, entity = summoner)
    val service = new TestSummonerService(response)

    When("a valid Summoner is requested")
    val result = service.getByName("euw", "Wagglez")

    Then("the passed RiotSummoner should be returned")
    result.summoner.id.shouldBe(52477463)
    result.summoner.name.shouldBe("Wagglez")
    result.summoner.profileIconId.shouldBe(785)
    result.summoner.summonerLevel.shouldBe(30)
    result.summoner.revisionDate.shouldBe(1434315156000L)
  }

  it should "throw a SummonerNotFound exception" in {
    Given("an instance of the SummonerService")
    val response = HttpResponse(status = NotFound)
    val service = new TestSummonerService(response)

    When("a non existing Summoner is requested")
    //val result = service.getByName("euw", "noExists")
    val exception = the[SummonerNotFound.type] thrownBy service.getByName("euw", "noExists")

    Then("a SummonerNotFound exception must be thrown")
    exception.shouldEqual(SummonerNotFound)
  }

  it should "throw a RunTimeException" in {
    Given("an instance of the SummonerService")
    val response = HttpResponse(status = InternalServerError)
    val service = new TestSummonerService(response)

    When("the Riot api cannot be reached")
    //val result = service.getByName("euw", "noExists")
    val exception = the[RuntimeException].thrownBy(service.getByName("euw", "noExists"))

    Then("a RunTimeException must be thrown")
    exception.isInstanceOf[RuntimeException].shouldBe(true)
    exception.getMessage.contains("An unknown error occurred").shouldBe(true)
  }
}
