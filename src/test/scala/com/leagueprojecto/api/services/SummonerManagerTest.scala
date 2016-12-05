package com.leagueprojecto.api.services

import com.analyzedgg.api.domain.Summoner
import com.analyzedgg.api.domain.riot.RiotSummoner
import com.analyzedgg.api.services.SummonerManager
import com.analyzedgg.api.services.couchdb.SummonerRepository
import com.analyzedgg.api.services.riot.SummonerService
import com.analyzedgg.api.services.riot.SummonerService.SummonerNotFound
import org.scalamock.scalatest.MockFactory
import org.scalatest.{FlatSpec, Matchers}

class SummonerManagerTest extends FlatSpec with Matchers with MockFactory {
  val testSummoner = Summoner(123123123, "Wagglez", 100, 1434315156000L, 30)
  val testRiotSummoner = RiotSummoner(testSummoner)

  class TestSummonerManager extends SummonerManager {
    override val service: SummonerService = stub[SummonerService]
    override val repository: SummonerRepository = mock[SummonerRepository]
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

  "SummonerManager" should "get a Summoner from Riot and save it in the database" in {
    val region = "euw"
    val name = "exists"
    val manager = new TestSummonerManager()
    manager.service.getByName _ when(region, name) returns testRiotSummoner
    manager.repository.save _ expects(testSummoner, region)

    val response = manager.getSummoner("euw", "exists")

    response.shouldEqual(testSummoner)
  }

  it should "throw a SummonerNotFound exception if no Summoner exists" in {
    val region = "euw"
    val name = "noExists"
    val manager = new TestSummonerManager()
    manager.service.getByName _ when(region, name) throwing {
      SummonerNotFound
    }

    val exception = the[SummonerNotFound.type].thrownBy(manager.getSummoner(region, name))

    exception.shouldEqual(SummonerNotFound)
  }
}
