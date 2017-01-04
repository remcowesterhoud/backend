package com.analyzedgg.api.services.couchdb

import akka.pattern.{CircuitBreaker, CircuitBreakerOpenException}
import com.analyzedgg.api.domain.Summoner
import com.analyzedgg.api.services.couchdb.SummonerRepository.SummonerNotFound
import com.ibm.couchdb.Res.Error
import com.ibm.couchdb.{CouchDbApi, CouchException, TypeMapping}
import org.http4s.Status.NotFound

import scala.util.{Failure, Success, Try}
import scalaz.{-\/, \/, \/-}

/**
  * Created by RemcoW on 5-12-2016.
  */
object SummonerRepository {

  case object SummonerNotFound extends Exception

}

class SummonerRepository(couchDbCircuitBreaker: CircuitBreaker) extends AbstractRepository {
  val mapping = TypeMapping(classOf[Summoner] -> "Summoner")
  val db: CouchDbApi = couch.db("summoner-db", mapping)

  def save(summoner: Summoner, region: String): Unit = {
    val id = generateId(region, summoner.name.toLowerCase)
    val response = tryWithCircuitBreaker(db.docs.create(summoner, id).attemptRun)
    response match {
      case \/-(_) =>
        logger.info("Yay, summoner saved!")
      case -\/(exception) =>
        logger.error(s"Error saving summoner ($id) in Db with reason: $exception")
        throw exception
    }
  }

  def getByName(region: String, name: String): Summoner = {
    val id = generateId(region, name)
    val summoners = tryWithCircuitBreaker(db.docs.get[Summoner](id).attemptRun)
    summoners match {
      case \/-(summonerDoc) =>
        logger.info(s"Yay got summoner from Db: $summonerDoc")
        summonerDoc.doc
      case -\/(CouchException(e: Error)) if e.status == NotFound =>
        logger.info(s"No summoner found ($id) from Db")
        throw SummonerNotFound
      case -\/(exception) =>
        logger.error(s"Error retrieving summoner ($id) from Db with reason: $exception")
        throw SummonerNotFound
    }
  }

  private def generateId(region: String, name: String): String = {
    s"$region:$name"
  }

  private[this] def tryWithCircuitBreaker[A](query: => Throwable \/ A): Throwable \/ A = {
    Try(couchDbCircuitBreaker.withSyncCircuitBreaker(query)) match {
      case Success(validResponse: (Throwable \/ A)) =>
        validResponse
      case Failure(exception: CircuitBreakerOpenException) => -\/(exception)
      case _ => -\/(new RuntimeException("An unknown error has occurred."))
    }
  }
}
