package com.leagueprojecto.api.routes

import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.StatusCodes._
import com.analyzedgg.api.domain.Summoner
import com.analyzedgg.api.services.SummonerManager
import com.analyzedgg.api.services.riot.SummonerService.SummonerNotFound

import scala.concurrent.Future

// Do not remove the following import! IntelliJ might say it's not used, but it is for converting json to case classes.
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

class SummonerRoute extends RoutesTest {
  val endpoint = "/api/euw/summoner"
  val validSummoner = Summoner(123, "Wagglez", 1, 1372782894000L, 30)

  class SummonerManagerMock extends SummonerManager {
    override def getSummoner(region: String, name: String): Future[Summoner] = {
      name match {
        case "existing" => Future(validSummoner)
        case "nonExisting" => throw SummonerNotFound
        case _ => throw new Exception("Internal server error")
      }
    }
  }

  override def getSummonerManager: SummonerManagerMock = {
    new SummonerManagerMock()
  }

  "Summoner path" should "return a json response with a Summoner in it" in {
    Get(s"$endpoint/existing") ~> routes ~> check {
      status shouldBe OK
      contentType shouldBe `application/json`

      responseAs[Summoner] shouldBe validSummoner
    }
  }

  it should "return a 404 not found when the Summoner does not exist" in {
    Get(s"$endpoint/nonExisting") ~> routes ~> check {
      status shouldBe NotFound
      responseAs[String] shouldBe ""
    }
  }

  it should "return a 500 internal server error when an unknown exception is thrown" in {
    Get(s"$endpoint/unknown") ~> routes ~> check {
      status shouldBe InternalServerError
      responseAs[String] shouldBe ""
    }
  }

  it should "always send Options back on requests" in {
    Options("/api/euw/summoner/Wagglez") ~> routes ~> check {
      status shouldBe OK
      responseAs[String] shouldBe ""
    }
  }
}
