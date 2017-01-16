package com.leagueprojecto.api.services

import com.analyzedgg.api.services.SummonerManager
import com.analyzedgg.api.services.SummonerManager.GetSummoner
import com.analyzedgg.api.services.couchdb.SummonerRepository
import com.analyzedgg.api.services.riot.SummonerService
import com.leagueprojecto.api.testHelpers.SummonerMockData._
import com.leagueprojecto.api.testHelpers.TestClass

import scala.concurrent.Await
import scala.concurrent.duration._

class SummonerManagerTest extends TestClass {

  class MockableSummonerRepository extends SummonerRepository(null)

  class TestSummonerManager extends SummonerManager {
    override val service: SummonerService = stub[SummonerService]
    override val repository: SummonerRepository = stub[MockableSummonerRepository]
  }

  "SummonerManager" should "retrieve a summoner from the cache if it already exists in the cache" in {
    // Setup
    val manager = new TestSummonerManager()

    Given("a summoner exists in the cache")
    manager.repository.getByName _ when(testRegion, testSummoner.name) returns testSummoner
    When("a summoner is being retrieved")
    val response = manager.getSummoner(testRegion, testSummoner.name)
    Then("the expected summoner from the cache should be returned")
    val summoner = Await.result(response, 5.seconds)
    summoner shouldEqual testSummoner
    And("no call to the Riot api should be made")
    manager.service.getByName _ verify * never()
  }

  it should "retrieve a summoner from riot and cache it if it doesn't exist" in {
    // Setup
    val manager = new TestSummonerManager()

    Given("the summoner doesn't exist in the cache")
    manager.repository.getByName _ when(testRegion, testSummoner.name) throws SummonerRepository.SummonerNotFound
    And("the requests summoner is a valid summoner")
    manager.service.getByName _ when * onCall { x: GetSummoner =>
      x.summonerPromise.success(testSummoner)
      x
    }
    When("a summoner is being retrieved")
    val response = manager.getSummoner(testRegion, testSummoner.name)
    Then("the expected summoner should be returned")
    val summoner = Await.result(response, 5.seconds)
    summoner shouldEqual testSummoner
    And("the summoner should be cached")
    // 500ms sleep required as the db save call is made asynchronously. This means this test will check if the save
    // call was made before this actually happened.
    Thread.sleep(500)
    manager.repository.save _ verify(testSummoner, testRegion) once()
    And("the cache should have been checked for the summoner")
    manager.repository.getByName _ verify(testRegion, testSummoner.name) once()
  }

  it should "should throw a SummonerNotFound exception if the summoner is invalid" in {
    // Setup
    val manager = new TestSummonerManager()

    Given("the requested summoner is invalid")
    manager.repository.getByName _ when(testRegion, testSummoner.name) throws SummonerRepository.SummonerNotFound
    manager.service.getByName _ when * onCall { x: GetSummoner =>
      x.summonerPromise.failure(SummonerService.SummonerNotFound)
      x
    }
    When("a summoner is being retrieved")
    val response = manager.getSummoner(testRegion, testSummoner.name)
    val exception = the[SummonerService.SummonerNotFound.type] thrownBy Await.result(response, 5.seconds)
    Then("the cache should be checked for the summoner")
    manager.repository.getByName _ verify(testRegion, testSummoner.name) once()
    And("a SummonerNotFound exception should be thrown")
    exception shouldEqual SummonerService.SummonerNotFound
  }

  it should "throw an exception when the riot api is offline" in {
    // Setup
    val manager = new TestSummonerManager()

    Given("the riot api is offline")
    manager.service.getByName _ when * onCall { x: GetSummoner =>
      x.summonerPromise.failure(new RuntimeException)
      x
    }
    And("the summoner does not exist in the cache")
    manager.repository.getByName _ when(testRegion, testSummoner.name) throws SummonerRepository.SummonerNotFound
    When("a summoner is being retrieved")
    val response = manager.getSummoner(testRegion, testSummoner.name)
    val exception = the[RuntimeException] thrownBy Await.result(response, 5.seconds)
    Then("the cache should be checked fro the summoner")
    manager.repository.getByName _ verify(testRegion, testSummoner.name) once()
    And("a RunTimeException should be thrown")
    exception.isInstanceOf[RuntimeException] shouldBe true
  }
}
