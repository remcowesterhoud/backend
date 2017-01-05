package com.leagueprojecto.api.services.couchdb

import akka.actor.ActorSystem
import akka.pattern.CircuitBreaker
import com.analyzedgg.api.domain.Summoner
import com.analyzedgg.api.services.couchdb.SummonerRepository
import com.analyzedgg.api.services.couchdb.SummonerRepository.SummonerNotFound
import com.ibm.couchdb.{CouchDoc, CouchException, Res}
import com.leagueprojecto.api.testHelpers.SummonerMockData._
import com.leagueprojecto.api.testHelpers.TestClass

import scala.concurrent.duration._
import scalaz.{-\/, \/-}

/**
  * Created by RemcoW on 3-1-2017.
  */
class SummonerRepositoryTest extends TestClass {
  val system = ActorSystem("testsystem")

  class MockCB extends CircuitBreaker(system.scheduler, 10, 5.seconds, 1.minute)

  val cbMock: MockCB = stub[MockCB]

  "SummonerRepository" should "save a summoner in the database" in {
    Given("the summoner can be succesfully saved in the database")
    val repo = new SummonerRepository(cbMock)
    val response = \/-(Res.DocOk(ok = true, id = "euw:wagglez", rev = "rev"))
    When("the summoner gets saved")
    cbMock.withSyncCircuitBreaker[\/-[Res.DocOk]] _ when * returns response
    Then("no exception should be thrown")
    noException should be thrownBy repo.save(testSummoner, testRegion)
  }

  it should "throw a CouchException when saving a summoner in the database" in {
    Given("the summoner already exists in the database")
    val repo = new SummonerRepository(cbMock)
    val expectedException = CouchException(Res.Error("error", "test"))
    val response = -\/(expectedException)
    When("the summoner gets saved")
    cbMock.withSyncCircuitBreaker[-\/[CouchException[Res.Error]]] _ when * returns response
    val exception = the[CouchException[Res.Error]] thrownBy repo.save(testSummoner, testRegion)
    Then("a CouchException should be thrown")
    exception shouldEqual expectedException
  }

  it should "throw a RuntimeException when saving the summoner in the database" in {
    Given("an unkown error occurred")
    val repo = new SummonerRepository(cbMock)
    cbMock.withSyncCircuitBreaker[String] _ when * returns null
    When("the summoner gets saved")
    val exception = the[RuntimeException] thrownBy repo.save(testSummoner, testRegion)
    Then("a RuntimeException should be thrown")
    exception.isInstanceOf[RuntimeException] shouldBe true
  }

  it should "get a Summoner from the database by it's name" in {
    Given("the summoner exists in the database")
    val repo = new SummonerRepository(cbMock)
    val response = \/-(CouchDoc(testSummoner, "Summoner"))
    cbMock.withSyncCircuitBreaker[\/-[CouchDoc[Summoner]]] _ when * returns response
    When("a summoner is retrieved")
    val summoner = repo.getByName(testRegion, testSummoner.name)
    Then("the expected Summoner should be returned")
    summoner shouldEqual testSummoner
  }

  it should "throw a SummonerNotFound exception when getting a non-existing summoner from the database" in {
    Given("the summoner doesn't exist in the database")
    val repo = new SummonerRepository(cbMock)
    val response = -\/(CouchException(Res.Error("not_found", "deleted")))
    cbMock.withSyncCircuitBreaker[-\/[CouchException[Res.Error]]] _ when * returns response
    When("a summoner is retrieved")
    val exception = the[SummonerNotFound.type] thrownBy repo.getByName(testRegion, testSummoner.name)
    Then("a SummonerNotfound exception should be thrown")
    exception shouldEqual SummonerNotFound
  }

  it should "throw a SummonerNotFoundException if an unknown error occurred when getting a summoner from the database" in {
    Given("an unknown error occurred")
    val repo = new SummonerRepository(cbMock)
    cbMock.withSyncCircuitBreaker[String] _ when * returns null
    When("a summoner is retrieved")
    val exception = the[SummonerNotFound.type] thrownBy repo.getByName(testRegion, testSummoner.name)
    Then("a SummonerNotFound exceptions should be thrown")
    exception shouldEqual SummonerNotFound
  }
}
