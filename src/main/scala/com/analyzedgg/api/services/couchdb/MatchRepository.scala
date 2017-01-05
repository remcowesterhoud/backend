package com.analyzedgg.api.services.couchdb

import akka.pattern.CircuitBreaker
import com.analyzedgg.api.domain.MatchDetail
import com.ibm.couchdb.{CouchDbApi, TypeMapping}

import scalaz.{-\/, \/-}

/**
  * Created by RemcoW on 5-1-2017.
  */
class MatchRepository(override val couchDbCircuitBreaker: CircuitBreaker) extends AbstractRepository {
  override val mapping: TypeMapping = TypeMapping(classOf[MatchDetail] -> "MatchDetail")
  override val db: CouchDbApi = couch.db("matches-db", mapping)

  def save(matches: Seq[MatchDetail], region: String, summonerId: Long): Unit = {
    val id = generateId(region, summonerId)
    val matchesById = matches.map(m => generateId(id, m.matchId.toString) -> m).toMap
    val saveMatches = tryWithCircuitBreaker(db.docs.createMany(matchesById).attemptRun)
    saveMatches match {
      case \/-(_) =>
        logger.info("Yay, matches are saved!")
      case -\/(exception) =>
        logger.error(s"Error saving matches ($id) in Db with reason: $exception")
        throw exception
    }
  }
}
