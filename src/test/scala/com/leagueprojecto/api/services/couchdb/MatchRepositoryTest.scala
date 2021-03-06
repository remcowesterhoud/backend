package com.leagueprojecto.api.services.couchdb

import akka.actor.ActorSystem
import akka.pattern.CircuitBreaker
import com.analyzedgg.api.services.couchdb.MatchRepository
import com.ibm.couchdb.{CouchException, Res}
import com.leagueprojecto.api.testHelpers.MatchMockData._
import com.leagueprojecto.api.testHelpers.SummonerMockData._
import com.leagueprojecto.api.testHelpers.TestClass

import scala.concurrent.duration._
import scalaz.{-\/, \/-}

/**
  * Created by RemcoW on 5-1-2017.
  */
class MatchRepositoryTest extends TestClass {
  val system = ActorSystem("testsystem")

  class MockCB extends CircuitBreaker(system.scheduler, 10, 5.seconds, 1.minute)

  val cbMock: MockCB = stub[MockCB]

  "MatchRepository" should "save a single match detail in the database" in {
    Given("a single match detail can be succesfully saved in the database")
    val repo = new MatchRepository(cbMock)
    val response = \/-(Res.DocOk(ok = true, "euw:12345:54321", "rev"))
    When("the match detail gets saved")
    cbMock.withSyncCircuitBreaker[\/-[Res.DocOk]] _ when * returns response
    Then("no exception should be thrown")
    noException should be thrownBy repo.save(Seq(createMockDetails(1L)), testRegion, testSummoner.id)
  }

  it should "save multiple match details in the database" in {
    Given("a multiple match details can be succesfully saved in the database")
    val repo = new MatchRepository(cbMock)
    val response = \/-(Res.DocOk(ok = true, "", ""))
    When("the match details get saved")
    cbMock.withSyncCircuitBreaker[\/-[Res.DocOk]] _ when * returns response
    Then("no exception should be thrown")
    val matches = Seq(
      createMockDetails(1L),
      createMockDetails(2L),
      createMockDetails(3L),
      createMockDetails(4L),
      createMockDetails(5L)
    )
    noException should be thrownBy repo.save(matches, testRegion, testSummoner.id)
  }

  it should "throw a CouchException when saving a single match detail in the database" in {
    Given("a single match detail can't be saved in the database")
    val repo = new MatchRepository(cbMock)
    val expectedException = CouchException(Res.Error("error", "test"))
    val response = -\/(expectedException)
    When("the match detail gets saved")
    cbMock.withSyncCircuitBreaker[-\/[CouchException[Res.Error]]] _ when * returns response
    val exception = the[CouchException[Res.Error]] thrownBy repo.save(Seq(createMockDetails(1L)), testRegion, testSummoner.id)
    Then("a CouchException should be thrown")
    exception shouldEqual expectedException
  }

  it should "throw a CouchException when saving multiple match details in the database" in {
    Given("a multiple match details can be succesfully saved in the database")
    val repo = new MatchRepository(cbMock)
    val expectedException = CouchException(Res.Error("error", "test"))
    val response = -\/(expectedException)
    When("the match details get saved")
    cbMock.withSyncCircuitBreaker[-\/[CouchException[Res.Error]]] _ when * returns response
    val matches = Seq(
      createMockDetails(1L),
      createMockDetails(2L),
      createMockDetails(3L),
      createMockDetails(4L),
      createMockDetails(5L)
    )
    val exception = the[CouchException[Res.Error]] thrownBy repo.save(matches, testRegion, testSummoner.id)
    Then("a CouchException should be thrown")
    exception shouldEqual expectedException
  }
}
