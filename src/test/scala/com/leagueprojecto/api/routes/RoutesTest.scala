package com.leagueprojecto.api.routes

import akka.event.LoggingAdapter
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.analyzedgg.api.Routes
import com.typesafe.config.Config
import org.scalamock.scalatest.MockFactory
import org.scalatest.{FlatSpec, Matchers}

abstract class RoutesTest extends FlatSpec with Matchers with ScalatestRouteTest with Routes with MockFactory {
  override def config: Config = testConfig

  override val logger: LoggingAdapter = system.log
}
