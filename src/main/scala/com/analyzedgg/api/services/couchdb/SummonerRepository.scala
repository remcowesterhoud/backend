package com.analyzedgg.api.services.couchdb

import com.analyzedgg.api.domain.Summoner
import com.ibm.couchdb.TypeMapping

import scalaz.{-\/, \/-}

/**
  * Created by RemcoW on 5-12-2016.
  */
class SummonerRepository extends AbstractRepository[Summoner] {
  val mapping = TypeMapping(classOf[Summoner] -> "Summoner")
  val db = couch.db("summoner-db", mapping)

  def save(summoner: Summoner, region: String): Unit = {
    val id = s"$region:${summoner.name}"
    val response = db.docs.create(summoner, id).attemptRun
    response match {
      case \/-(_) =>
        logger.info("Yay, summoner saved!")
      case -\/(e) =>
        logger.error(s"Error saving summoner ($id) in Db with reason: $e")
    }
  }
}
