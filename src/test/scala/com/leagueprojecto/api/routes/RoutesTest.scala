package com.leagueprojecto.api.routes

import akka.event.LoggingAdapter
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.analyzedgg.api.Routes
import com.analyzedgg.api.domain.Summoner
import com.analyzedgg.api.services.SummonerManager
import com.analyzedgg.api.services.riot.SummonerService.SummonerNotFound
import com.typesafe.config.Config
import org.scalatest.{FlatSpec, Matchers}

abstract class RoutesTest extends FlatSpec with Matchers with ScalatestRouteTest with Routes {
  override def config: Config = testConfig
  override val logger: LoggingAdapter = system.log
  
//    override def createSummonerActor = {
//      val summonerProbe = new TestProbe(system)
//      setSummonerAutoPilot(summonerProbe)
//      summonerProbe.ref
//    }
//
//    override def createMatchHistoryActor = {
//      val matchHistoryProbe = new TestProbe(system)
//      setMatchHistoryAutoPilot(matchHistoryProbe)
//      matchHistoryProbe.ref
//    }
//
//    def setSummonerAutoPilot(probe: TestProbe) = ()
//    def setMatchHistoryAutoPilot(probe: TestProbe) = ()
}
