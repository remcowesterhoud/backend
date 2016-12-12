package com.analyzedgg.api.services.couchdb

import com.ibm.couchdb.{CouchDb, CouchDbApi, TypeMapping}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

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
}
