package com.leagueprojecto.api.testHelpers

import org.scalatest.{FlatSpec, GivenWhenThen, Matchers}

import scala.io.Source

abstract class BaseServiceHelper extends FlatSpec with Matchers with GivenWhenThen {
  def readFileFromClasspath(file: String): String = Source.fromInputStream(getClass.getResourceAsStream(file)).mkString
}
