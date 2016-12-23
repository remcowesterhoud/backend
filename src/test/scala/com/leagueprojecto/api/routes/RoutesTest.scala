package com.leagueprojecto.api.routes

import akka.event.LoggingAdapter
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.analyzedgg.api.Routes
import com.leagueprojecto.api.testHelpers.TestClass
import com.typesafe.config.Config

abstract class RoutesTest extends TestClass with ScalatestRouteTest with Routes {
  override def config: Config = testConfig

  override val logger: LoggingAdapter = system.log
}
