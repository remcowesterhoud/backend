package com.leagueprojecto.api.testHelpers

import java.util.concurrent.Executors

import org.scalamock.scalatest.MockFactory
import org.scalatest.{FlatSpec, GivenWhenThen, Matchers}

import scala.concurrent.ExecutionContext

/**
  * Created by RemcoW on 14-12-2016.
  */
class TestClass extends FlatSpec with Matchers with GivenWhenThen with MockFactory {
  implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())
}
