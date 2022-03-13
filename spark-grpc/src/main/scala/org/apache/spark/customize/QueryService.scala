package org.apache.spark.customize


import org.apache.spark.scheduler.{SparkListener, SparkListenerJobEnd, SparkListenerJobStart}
import org.apache.spark.sql.SparkSession
import org.apache.spark.status.AppStatusStore
import org.apache.spark.status.api.v1.{ApplicationInfo, ExecutorSummary, StageData}
import org.apache.spark.{SparkContext, SparkExecutorInfo, SparkStatusTracker}
import org.slf4j.LoggerFactory
import scala.jdk.CollectionConverters._
import java.util.UUID
import java.util.concurrent.{ScheduledExecutorService, TimeUnit}
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future, blocking}

/*
  Entry to spark
 */

class QueryService(spark: SparkSession, ex: ExecutionContext) {
  self =>
  val queries: mutable.Map[String, SQuery] = mutable.Map()
  val handlers: mutable.ListBuffer[QueryHandler] = mutable.ListBuffer()
  private val sc: SparkContext = spark.sparkContext
  private val lg = LoggerFactory.getLogger(this.getClass)
  private val sstore: AppStatusStore = sc.statusStore
  private val stracker: SparkStatusTracker = sc.statusTracker

  def getSparkStatus: SparkStatus = {
    new SparkStatus(
      stracker.getExecutorInfos,
      sstore.executorList(false),
      sstore.applicationInfo(),
    )
  }

  /*
  Return related status of a submitted request
   */
  def getQuery(requestId: String): Option[SQuery] = {
    //    require(queries.contains(requestId), "RequestId is not found")
    val squery = queries.get(requestId)
    squery
  }

  /*
  Execute an action and return an object to track the status of execution
   */
  def execute(requestId: String)(op: SparkSession => Unit): SQuery = {
    require(!queries.contains(requestId), "RequestId must be unique")
    val group = UUID.randomUUID().toString
    lg.info("serving requestId {}", requestId)
    var started: Box[Boolean] = new Box(false)

    val fut = Future {
      sc.setJobGroup(group, s"request: $requestId")
      blocking {
        started.value = true
        op(spark)
        sc.clearJobGroup()
      }
    }(ex)
    val squery = new SQuery(spark, requestId, group, fut, started)
    queries(requestId) = squery
    fut.onComplete { it =>
      this.handlers.foreach {
        _.handle(squery)
      }
    }(ex)
    fut.recover { it =>
      lg.error("request {} failed", requestId, it)
    }(ex)
    squery
  }

  def listenForQuery(handler: QueryHandler): Unit = {
    this.handlers.append(handler)
  }

  def getStateData(id: Int): Seq[StageData] = {
    sstore.stageData(id, true, List().asJava, true)
  }

  def runningSQueries: List[SQuery] = queries.values.filter(_.isStarted).toList

  def initialize(): Unit = sc.addSparkListener(new SimpleListener)

  def debug(executor: ScheduledExecutorService): Unit = {
    executor.scheduleAtFixedRate({ () =>
      queries.foreach(println(_))
    }, 1, 5, TimeUnit.SECONDS)
  }

  class SparkStatus(val executorInfos: Array[SparkExecutorInfo],
                    val executors: Seq[ExecutorSummary],
                    val applicationInfo: ApplicationInfo)

  private class SimpleListener extends SparkListener {
    override def onJobStart(jobStart: SparkListenerJobStart): Unit = {
      val jobId = jobStart.jobId
      //       not yet available when job starts
      //      val job = sstore.job(jobId)
      //      println("onJobStart", job)
      //      val requestId = job.jobGroup.get
      //      handlers.foreach (_.handle(queries(requestId)))
    }

    override def onJobEnd(jobEnd: SparkListenerJobEnd): Unit = {
      val jobId = jobEnd.jobId
      val job = sstore.job(jobId)
      //      println("onJobEnd", job)
      //      val requestId = job.jobGroup.get
      //      handlers.foreach (_.handle(queries(requestId)))
    }
  }

}
