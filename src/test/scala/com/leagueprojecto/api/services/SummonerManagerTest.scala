package com.analyzedgg.api.services

import akka.actor.ActorSystem
import akka.pattern.CircuitBreaker
import akka.testkit.TestProbe
import com.analyzedgg.api.domain.Summoner
import com.analyzedgg.api.domain.riot.RiotSummoner
import com.analyzedgg.api.services.riot.SummonerService
import com.analyzedgg.api.services.riot.SummonerService.SummonerNotFound
import org.scalatest.{FlatSpec, GivenWhenThen, Matchers}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

class SummonerManagerTest extends FlatSpec with Matchers with GivenWhenThen {
  val system = ActorSystem.create()
  val executor: ExecutionContextExecutor = system.dispatcher
  val dbProbe = new TestProbe(system)
  val riotProbe = new TestProbe(system)

  lazy val couchDbCircuitBreaker =
    new CircuitBreaker(system.scheduler, maxFailures = 5, callTimeout = 10.seconds, resetTimeout = 1.minute)(executor)
  val testRegion = "EUW"
  val testName = "Wagglez"
  val testNameNoDb = "No db"
  val testNameNoDbNorRiot = "No db nor riot"
  val testSummoner = Summoner(123123123, testName, 100, 1434315156000L, 30)
  val testRiotSummoner = RiotSummoner(testSummoner)

  class SummonerManagerTest extends SummonerManager {
    override def createSummonerService(): SummonerService = new SummonerServiceMock()
  }

  class SummonerServiceMock extends SummonerService {
    override def getByName(region: String, name: String): RiotSummoner = {
      name match {
        case "exists" => testRiotSummoner
        case "noExists" => throw SummonerNotFound
        case _ => throw new RuntimeException
      }
    }
  }

  "SummonerManager" should "get a Summoner from Riot" in {
    Given("an instance of te SummonerManager")
    val manager = new SummonerManagerTest()

    When("an existing Summoner is requested from riot")
    val response = manager.getSummoner("euw", "exists")

    Then("the Summoner should match the returned Summoner")
    response.shouldEqual(testSummoner)
  }

  it should "throw a SummonetNotFound exception if no Summoner exist" in {
    Given("an instance of the SummonerManager")
    val manager = new SummonerManagerTest()

    Then("a non existing Summoner is requested from riot")
    try {
      manager.getSummoner("euw", "noExists")
      fail("SummerNotFound exception was not thrown")
    }
    catch {
      case SummonerNotFound => succeed
      case _ => fail("SummonerNotFound exception was not thrown")
    }
  }
}
