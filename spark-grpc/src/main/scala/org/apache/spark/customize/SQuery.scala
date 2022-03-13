package org.apache.spark.customize

import io.flex.Query.JobStatus
import org.apache.spark.SparkStatusTracker
import org.apache.spark.sql.SparkSession
import org.apache.spark.status.AppStatusStore
import org.apache.spark.status.api.v1.JobData

import scala.concurrent.Future

class SQuery(spark: SparkSession, requestId: String, groupId: String, fut: Future[Unit], started: Box[Boolean]) {
  val sstore: AppStatusStore = spark.sparkContext.statusStore
  val stracker: SparkStatusTracker = spark.sparkContext.statusTracker

  override def toString: String = "SQuery: " + Map(
    "isStarted" -> isStarted, "jobIds" -> jobIds,
    "sparkJobs" -> getSparkJobs, "isFinished" -> isFinished, "isSucceeded" -> isSucceeded
  ).toString()

  def getSparkJobs: Array[JobData] = jobIds.map(spark.sparkContext.statusStore.job(_))

  private def jobIds: Array[Int] = {
    val jobs = stracker.getJobIdsForGroup(groupId)
    jobs
  }

  def status: JobStatus = {
    if (!isStarted) JobStatus.JOB_UNKNOWN
    else if (!isFinished) JobStatus.JOB_RUNNING
    else if (!isSucceeded) JobStatus.JOB_FAILED else JobStatus.JOB_SUCCEEDED
  }

  def isSucceeded: Boolean = isFinished && fut.value.get.isSuccess

  def isFinished: Boolean = fut.isCompleted

  def isStarted: Boolean = started.value
}
