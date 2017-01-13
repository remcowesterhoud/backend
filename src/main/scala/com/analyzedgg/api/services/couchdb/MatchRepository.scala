package com.analyzedgg.api.services.couchdb

import akka.pattern.CircuitBreaker
import com.analyzedgg.api.domain.MatchDetail
import com.ibm.couchdb.Res.Error
import com.ibm.couchdb.{CouchDbApi, CouchException, TypeMapping}
import org.http4s.Status.NotFound

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

  def getDetailsAllowEmpty(ids: Seq[Long], region: String, summonerId: Long): Seq[MatchDetail] = {
    val id = generateId(region, summonerId)
    val decoratedIds = ids.map(key => generateId(id, key))
    val matches = tryWithCircuitBreaker(db.docs.getMany.queryIncludeDocsAllowMissing[MatchDetail](decoratedIds).attemptRun)
    matches match {
      case \/-(matchesDocs) =>
        logger.info(s"Yay, got ${matchesDocs.getDocsData.size} matches from Db")
        matchesDocs.getDocsData
      case -\/(CouchException(e: Error)) if e.status == NotFound =>
        logger.info(s"No matches found ($ids) from Db")
        Seq()
      case -\/(exception) =>
        logger.error(s"Error retrieving matchDetails ($ids) from Db with reason: $exception")
        Seq()
    }
  }
}
