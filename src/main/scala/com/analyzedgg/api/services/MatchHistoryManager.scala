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
    // Sink wich will set the match details to the result variable of the request
    val returnSink = Sink.foreach(setResult)
    // Start of the Stream, GetMatches requests can dynamically be passed to this Source
    val source = Source.queue[GetMatches](bufferSize, OverflowStrategy.backpressure)

    val lastIdsFlow = Flow.fromGraph(GraphDSL.create() { implicit builder =>
      // Import the implicits so the ~> syntax can be used
      import GraphDSL.Implicits._

      // Flow that gets the latest match ids from the riot api
      val lastIdsFlow = builder.add(Flow[GetMatches].map(getLastIds).async)
      // Flow that uses the match ids to try and get the details from the cache
      val detailsFromCacheFlow = builder.add(Flow[(GetMatches, Future[Seq[Long]])].map(getDetailsFromCache).async)
      // Flow that uses the match ids to try and get the details from the riot api
      val detailsFromRiotFlow = builder.add(Flow[(GetMatches, Future[Map[Long, Option[MatchDetail]]])].map(getDetailsFromRiot).async)
      // Broadcast that sends the match details to the cacheSink and the returnSink
      val detailsBroadcast = builder.add(Broadcast[(GetMatches, Future[Seq[MatchDetail]])](2))
      // Sink that caches the details in the database
      val cacheDetailsSink = Sink.foreach(cacheDetails).async

      // Chain the different components of the flow together
      lastIdsFlow ~> detailsFromCacheFlow ~> detailsFromRiotFlow ~> detailsBroadcast ~> cacheDetailsSink

      FlowShape(lastIdsFlow.in, detailsBroadcast.out(1))
    })
    source.via(lastIdsFlow).to(returnSink)
  }

  def getLastIds(data: GetMatches): (GetMatches, Future[Seq[Long]]) = {
    logger.info(s"Requesting last $matchAmount match ids from riot")
    // Request the recent match ids from the riot api
    (data, service.getRecentMatchIds(data.region, data.summonerId, matchAmount))
  }

  def getDetailsFromCache(data: (GetMatches, Future[Seq[Long]])): (GetMatches, Future[Map[Long, Option[MatchDetail]]]) = {
    (data._1, data._2.map(ids =>
      // Parse the sequence of ids to a map of ids and optional MatchDetails
      ids.map(id => id -> None).toMap[Long, Option[MatchDetail]])
      .map(matchesMap =>
        // Get the details that exist in the cache from there and put them in the map
        matchesMap ++ repository.getDetailsAllowEmpty(matchesMap.keys.toSeq, data._1.region, data._1.summonerId)
          .map(detail => detail.matchId -> Some(detail))
      ))
  }

  def getDetailsFromRiot(data: (GetMatches, Future[Map[Long, Option[MatchDetail]]])): (GetMatches, Future[Seq[MatchDetail]]) = {
    (data._1, data._2.flatMap(matchesMap =>
      // Filter the ids of which the match details were not available in the cache
      Future.sequence(matchesMap.filter(_._2.isEmpty).keys.toSeq.map(id =>
        // Get the remaining match details
        service.getMatchDetails(data._1.region, data._1.summonerId, id))
        // If the match details could be retrieved from the riot api pass them, else ignore them
        .map(_.map(Success(_)).recover({ case exception => Failure(exception) })))
        .map(_.collect({ case Success(succeededDetail) => succeededDetail }))
        // Map the details that could be retrieved from riot to their corresponding ids in the map
        .map(details => details.map(detail => detail.matchId -> Some(detail)).toMap)
        .map(riotDetailMap => matchesMap ++ riotDetailMap)
      // Remove the details that could not be retrieved from riot or the cache from the map
    ).map(detailsMap => detailsMap.values.toSeq.flatten))
  }

  def cacheDetails(data: (GetMatches, Future[Seq[MatchDetail]])): Unit = {
    // Save the retrieved match details in the cache
    data._2.onSuccess({ case matchDetails => repository.save(matchDetails, data._1.region, data._1.summonerId) })
  }

  def setResult(data: (GetMatches, Future[Seq[MatchDetail]])): Unit = {
    // Set the result variable of the request to contain a list of the most recent match details of the summoner
    data._1.result.completeWith(data._2)
  }
}
