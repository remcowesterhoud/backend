package com.analyzedgg.api.services

import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.pattern.CircuitBreaker
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import com.analyzedgg.api.domain.MatchDetail
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}

object MatchHistoryManager {
  private val manager: MatchHistoryManager = new MatchHistoryManager()

  def apply(): MatchHistoryManager = manager
}

class MatchHistoryManager extends LazyLogging {
  lazy val couchDbCircuitBreaker =
    new CircuitBreaker(system.scheduler, maxFailures = 5, callTimeout = 5.seconds, resetTimeout = 1.minute)
  implicit val executionContext: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())
  val system = ActorSystem("system")
  val decider: Supervision.Decider = { e =>
    logger.warn(e.getMessage)
    Supervision.Resume
  }
  val materializerSettings: ActorMaterializerSettings = ActorMaterializerSettings(system).withSupervisionStrategy(decider)
  implicit val materializer: ActorMaterializer = ActorMaterializer(materializerSettings)(system)

  protected val service = null
  protected val repository = null
  private val graph = null

  def getMatchHistory(region: String, summonerId: Long, queueParam: String, championParam: String): Seq[MatchDetail] = {
    null
  }

  private def hasEmptyValues(mergedMatches: Map[Long, Option[MatchDetail]]): Boolean =
    mergedMatches.values.exists(_.isEmpty)

  private def getValues(mergedMatches: Map[Long, Option[MatchDetail]]): Seq[MatchDetail] =
    mergedMatches.values.map(_.get).toSeq
}
