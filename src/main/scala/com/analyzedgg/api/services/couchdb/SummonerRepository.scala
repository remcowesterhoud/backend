package com.analyzedgg.api.services.couchdb

import com.analyzedgg.api.domain.Summoner
import com.ibm.couchdb.Res.Error
import com.ibm.couchdb.{CouchException, TypeMapping}
import org.http4s.Status.NotFound

import scalaz.{-\/, \/-}

/**
  * Created by RemcoW on 5-12-2016.
  */
class SummonerRepository extends AbstractRepository[Summoner] {
  val mapping = TypeMapping(classOf[Summoner] -> "Summoner")
  val db = couch.db("summoner-db", mapping)

  def save(summoner: Summoner, region: String): Unit = {
    val id = generateId(region, summoner.name.toLowerCase)
    val response = db.docs.create(summoner, id).attemptRun
    response match {
      case \/-(_) =>
        logger.info("Yay, summoner saved!")
      case -\/(e) =>
        logger.error(s"Error saving summoner ($id) in Db with reason: $e")
    }
  }

  def getByName(region: String, name: String): Option[Summoner] ={
    val id = generateId(region, name)
    val summoners = db.docs.get[Summoner](id).attemptRun
    summoners match {
      case \/-(summonerDoc) =>
        logger.info(s"Yay got summoner from Db: $summonerDoc")
        Some(summonerDoc.doc)
      case -\/(CouchException(e: Error)) if e.status == NotFound =>
        logger.info(s"No summoner found ($id) from Db")
        None
      case -\/(e) =>
        logger.error(s"Error retrieving summoner ($id) from Db with reason: $e")
        None
    }
  }

  private def generateId(region: String, name: String): String ={
    s"$region:$name"
  }
}
