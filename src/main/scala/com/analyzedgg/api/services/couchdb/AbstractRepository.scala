package com.analyzedgg.api.services.couchdb

import akka.pattern.{CircuitBreaker, CircuitBreakerOpenException}
import com.ibm.couchdb.{CouchDb, CouchDbApi, TypeMapping}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

import scala.util.{Failure, Success, Try}
import scalaz.{-\/, \/}

/**
  * Created by RemcoW on 5-12-2016.
  */
trait AbstractRepository extends LazyLogging {
  private val config = ConfigFactory.load()
  private val hostname: String = config.getString("couchdb.hostname")
  private val port: Int = config.getInt("couchdb.port")

  val couch = CouchDb(hostname, port)
  val mapping: TypeMapping
  val db: CouchDbApi
  val couchDbCircuitBreaker: CircuitBreaker

  protected def generateId(part1: String, part2: String): String = s"$part1:$part2"

  protected def generateId(part1: String, part2: Long): String = generateId(part1, part2.toString)

  protected def generateId(part1: Long, part2: Long): String = generateId(part1.toString, part2.toString)

  protected[this] def tryWithCircuitBreaker[A](query: => Throwable \/ A): Throwable \/ A = {
    Try(couchDbCircuitBreaker.withSyncCircuitBreaker(query)) match {
      case Success(validResponse: (Throwable \/ A)) => validResponse
      case Failure(exception: CircuitBreakerOpenException) => -\/(exception)
      case _ => -\/(new RuntimeException("An unknown error has occurred."))
    }
  }
}
