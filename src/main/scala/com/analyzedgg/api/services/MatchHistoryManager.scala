package com.analyzedgg.api.services

import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.pattern.CircuitBreaker
import akka.stream._
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, RunnableGraph, Sink, Source, SourceQueueWithComplete}
import com.analyzedgg.api.domain.MatchDetail
import com.analyzedgg.api.services.MatchHistoryManager.GetMatches
import com.analyzedgg.api.services.couchdb.MatchRepository
import com.analyzedgg.api.services.riot.MatchService
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object MatchHistoryManager {
  private val manager: MatchHistoryManager = new MatchHistoryManager()

  def apply(): MatchHistoryManager = manager

  case class GetMatches(region: String, summonerId: Long, queueType: String, championList: String) {
    val result: Promise[Seq[MatchDetail]] = Promise()
  }

}

class MatchHistoryManager extends LazyLogging {
  private val maxFailures = 5
  lazy val couchDbCircuitBreaker =
    new CircuitBreaker(system.scheduler, maxFailures, callTimeout = 5.seconds, resetTimeout = 1.minute)
  implicit val executionContext: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())
  val system = ActorSystem("match-system")
  val decider: Supervision.Decider = { e =>
    logger.warn(e.getCause.toString)
    Supervision.Resume
  }
  val materializerSettings: ActorMaterializerSettings = ActorMaterializerSettings(system).withSupervisionStrategy(decider)
  implicit val materializer: ActorMaterializer = ActorMaterializer(materializerSettings)(system)
  private final val matchAmount: Int = 10

  protected val service = new MatchService()
  protected val repository = new MatchRepository(couchDbCircuitBreaker)
  private val graph = createGraph().run()

  def getMatchHistory(region: String, summonerId: Long, queueParam: String, championParam: String): Future[Seq[MatchDetail]] = {
    val getMatches = GetMatches(region, summonerId, queueParam, championParam)
    graph.offer(getMatches)
    getMatches.result.future
  }

  private def createGraph(bufferSize: Int = 100): RunnableGraph[SourceQueueWithComplete[GetMatches]] = {
    // This Sink doesn't need to do anything with the elements as the reference to the objects is being tracked till they complete the Stream
    val returnSink = Sink.foreach(setResult)
    val source = Source.queue[GetMatches](bufferSize, OverflowStrategy.backpressure)

    val lastIdsFlow = Flow.fromGraph(GraphDSL.create() { implicit builder =>
      // Import the implicits so the ~> syntax can be used
      import GraphDSL.Implicits._

      val lastIdsFlow = builder.add(Flow[GetMatches].map(getLastIds).async)
      val detailsFromCacheFlow = builder.add(Flow[(GetMatches, Future[Seq[Long]])].map(getDetailsFromCache).async)
      val detailsFromRiotFlow = builder.add(Flow[(GetMatches, Future[Map[Long, Option[MatchDetail]]])].map(getDetailsFromRiot).async)
      val detailsBroadcast = builder.add(Broadcast[(GetMatches, Future[Seq[MatchDetail]])](2))
      val cacheDetailsSink = Sink.foreach(cacheDetails).async

      lastIdsFlow ~> detailsFromCacheFlow ~> detailsFromRiotFlow ~> detailsBroadcast ~> cacheDetailsSink

      FlowShape(lastIdsFlow.in, detailsBroadcast.out(1))
    })
    source.via(lastIdsFlow).to(returnSink)
  }

  def getLastIds(data: GetMatches): (GetMatches, Future[Seq[Long]]) = {
    logger.info(s"Requesting last $matchAmount match ids from riot")
    (data, service.getRecentMatchIds(data.region, data.summonerId, matchAmount))
  }

  def getDetailsFromCache(data: (GetMatches, Future[Seq[Long]])): (GetMatches, Future[Map[Long, Option[MatchDetail]]]) = {
    (data._1, data._2.map(ids => ids.map(id => id -> None).toMap[Long, Option[MatchDetail]])
      .map(matchesMap => matchesMap ++ repository.getDetailsAllowEmpty(matchesMap.keys.toSeq, data._1.region, data._1.summonerId)
          .map(detail => detail.matchId -> Some(detail))
      ))
  }

  def getDetailsFromRiot(data: (GetMatches, Future[Map[Long, Option[MatchDetail]]])): (GetMatches, Future[Seq[MatchDetail]]) = {
    (data._1, data._2.flatMap(matchesMap =>
      Future.sequence(matchesMap.filter(_._2.isEmpty).keys.toSeq.map(id =>
        service.getMatchDetails(data._1.region, data._1.summonerId, id))
        .map(_.map(Success(_)).recover({ case exception => Failure(exception) })))
        .map(_.collect({ case Success(succeededDetail) => succeededDetail }))
        .map(details => details.map(detail => detail.matchId -> Some(detail)).toMap)
        .map(riotDetailMap => matchesMap ++ riotDetailMap)
    ).map(detailsMap => detailsMap.values.toSeq.flatten))
  }

  def cacheDetails(data: (GetMatches, Future[Seq[MatchDetail]])): Unit = {
    data._2.onSuccess({ case matchDetails => repository.save(matchDetails, data._1.region, data._1.summonerId) })
  }

  def setResult(data: (GetMatches, Future[Seq[MatchDetail]])): Unit = {
    data._1.result.completeWith(data._2)
  }
}
