package com.analyzedgg.api.services

import java.util.concurrent.{ConcurrentHashMap, Executors}

import akka.actor.ActorSystem
import akka.pattern.CircuitBreaker
import akka.stream._
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source, SourceQueueWithComplete}
import com.analyzedgg.api.domain.MatchDetail
import com.analyzedgg.api.services.MatchHistoryManager.GetMatches
import com.analyzedgg.api.services.couchdb.MatchRepository
import com.analyzedgg.api.services.riot.MatchService
import com.typesafe.scalalogging.LazyLogging

import scala.collection.concurrent.Map
import scala.collection.convert.decorateAsScala._
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

object MatchHistoryManager {
  private val manager: MatchHistoryManager = new MatchHistoryManager()

  def apply(): MatchHistoryManager = manager

  case class GetMatches(region: String, summonerId: Long, queueType: String, championList: String) {
    val detailsPromise: Promise[Seq[MatchDetail]] = Promise()
    val lastIdsPromise: Promise[Seq[Long]] = Promise()
    var matchesMap: Map[Long, Option[MatchDetail]] = new ConcurrentHashMap[Long, Option[MatchDetail]]().asScala

    def result: Future[Seq[MatchDetail]] = detailsPromise.future
  }

}

class MatchHistoryManager extends LazyLogging {
  private val maxFailures = 5
  lazy val couchDbCircuitBreaker =
    new CircuitBreaker(system.scheduler, maxFailures, callTimeout = 5.seconds, resetTimeout = 1.minute)
  implicit val executionContext: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())
  val system = ActorSystem("match-system")
  val decider: Supervision.Decider = { e =>
    logger.warn(e.getMessage)
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
    getMatches.result
  }

  private def createGraph(bufferSize: Int = 100): RunnableGraph[SourceQueueWithComplete[GetMatches]] = {
    // This Sink doesn't need to do anything with the elements as the reference to the objects is being tracked till they complete the Stream
    val returnSink = Sink.ignore
    val source = Source.queue[GetMatches](bufferSize, OverflowStrategy.backpressure)

    val lastIdsFlow = Flow.fromGraph(GraphDSL.create() { implicit builder =>
      // Import the implicits so the ~> syntax can be used
      import GraphDSL.Implicits._

      val lastIdsFlow = builder.add(Flow[GetMatches].map(getLastIds).async)
      val lastIdsBroadcast = builder.add(Broadcast[GetMatches](2))
      val lastIdsFailedFilter = Flow[GetMatches].filter(data => !foundLastIds(data)).async
      val lastIdsSuccessFilter = Flow[GetMatches].filter(data => foundLastIds(data)).async
      val merge = builder.add(Merge[GetMatches](2))
      val detailsFromRiotFlow = createMatchDetailsFlow.async
      val parseResultsFlow = builder.add(Flow[GetMatches].map(parseResults).async)

      lastIdsFlow ~> lastIdsBroadcast ~> lastIdsSuccessFilter ~> detailsFromRiotFlow ~> merge
      lastIdsBroadcast ~> lastIdsFailedFilter ~> merge
      merge ~> parseResultsFlow

      FlowShape(lastIdsFlow.in, parseResultsFlow.out)
    })
    source.via(lastIdsFlow).to(returnSink)
  }

  private def createMatchDetailsFlow = Flow.fromGraph(GraphDSL.create() { implicit builder =>
    // Import the implicits so the ~> syntax can be used
    import GraphDSL.Implicits._

    val mapIdsFlow = builder.add(Flow[GetMatches].map(mapMatchIds).async)
    val detailsFromRiotFlow = builder.add(Flow[GetMatches].map(getDetailsFromRiot).async)
    val detailsBroadcast = builder.add(Broadcast[GetMatches](2))
    val cacheDetailsSink = Sink.foreach(cacheDetails).async

    mapIdsFlow ~> detailsFromRiotFlow ~> detailsBroadcast ~> cacheDetailsSink

    FlowShape(mapIdsFlow.in, detailsBroadcast.out(1))
  })

  private def getLastIds(data: GetMatches): GetMatches = {
    logger.info(s"Requesting last $matchAmount match ids from riot")
    service.getRecentMatchIds(data, matchAmount)
  }

  private def foundLastIds(data: GetMatches): Boolean = {
    // I can't think of any way to do this without a blocking Await.
    Try(Await.result(data.lastIdsPromise.future, 5.seconds)) match {
      case Success(ids) => ids.nonEmpty
      case v => false
    }
  }

  private def mapMatchIds(data: GetMatches) = {
    val ids: Seq[Long] = Await.result(data.lastIdsPromise.future, 5.seconds)
    ids.map(data.matchesMap.put(_, None))
    data
  }

  private def getDetailsFromRiot(data: GetMatches): GetMatches = {
    val matchesToRetrieve = data.matchesMap.filter(m => m._2.isEmpty).keys
    logger.info(s"going to get matches: [$matchesToRetrieve] from riot")
    matchesToRetrieve.foreach(matchId => {
      Try(service.getMatchDetails(data.region, data.summonerId, matchId)) match {
        case Success(v) => v.onComplete({
          case Success(details) => data.matchesMap.update(matchId, Some(details))
          case Failure(exception) =>
            logger.error(s"Parsing match details with id $matchId failed. Removing from details list.")
            data.matchesMap -= matchId
        })
        case Failure(exception) =>
          logger.error(s"Retrieving match details with id $matchId failed. Removing from details list.")
          data.matchesMap -= matchId
      }
    })
    while (data.matchesMap.values.exists(_.isEmpty)) {}
    data
  }

  private def cacheDetails(data: GetMatches) = {
    data.detailsPromise.future.onSuccess { case matchDetails => repository.save(matchDetails, data.region, data.summonerId) }
  }

  private def parseResults(data: GetMatches): GetMatches = {
    data.lastIdsPromise.future.value match {
      case Some(Success(v)) =>
        data.detailsPromise.success(data.matchesMap.values.flatten.toSeq.sortBy(_.matchId))
      case Some(Failure(e)) =>
        data.detailsPromise.failure(e)
      case _ =>
        val msg = "Could not determine status of last ID promise"
        logger.error(msg)
        data.detailsPromise.failure(new RuntimeException(msg))
    }
    data
  }
}
