package io.flex

import com.google.protobuf.util.JsonFormat
import io.flex.SparkQueryServiceGrpc.SparkQueryServiceImplBase
import io.flex.{Query => Q}
import io.flex.SparkQueryServiceImpl.{constructReadPath, lg}
import io.grpc.Status
import io.grpc.stub.StreamObserver
import org.apache.spark.customize.QueryService
import org.apache.spark.sql.SparkSession
import org.slf4j.{Logger, LoggerFactory}
import scala.jdk.CollectionConverters._
import scala.concurrent.{ExecutionContextExecutor, Future, Promise}
import scala.util.{Failure, Success}

object SparkQueryServiceImpl {
  val lg: Logger = LoggerFactory.getLogger(this.getClass)

  /*
  Construct read path that spark can use
   */
  def constructReadPath(s: Q.DataSource): String = {
    val (name, bucket, path, format) = (s.getName, s.getBucket, s.getPath, s.getFormat)
    s.getService match {
      case "gs" => s"gs://$bucket/${path.stripPrefix("/")}"
      case "local" => s"${path}"
      case _ => {
        val json = JsonFormat.printer().print(s)
        throw new RuntimeException(s"datasource service not valid in ${s}")
      }
    }
  }
}

class SparkQueryServiceImpl(val spark: SparkSession, val queryService: QueryService) extends SparkQueryServiceImplBase() {
  private val converter = new ProtoBufConverter

  converter.registerMessage(classOf[Q.JobData])
  converter.registerEnum(classOf[Q.JobStatus])
  converter.registerMessage(classOf[Q.GetQueryResponse])
  converter.registerMessage(classOf[Q.GetSparkStatusResponse])
  converter.registerMessage(classOf[Q.SparkExecutorInfo])
  converter.registerMessage(classOf[Q.ExecutorSummary])
  converter.registerMessage(classOf[Q.ApplicationAttemptInfo])
  converter.registerMessage(classOf[Q.ApplicationInfo])
  converter.registerMessage(classOf[Q.ResourceInformation])
  converter.registerMessage(classOf[Q.MemoryMetrics])
  converter.registerMessage(classOf[Q.AccumulableInfo])
  converter.registerAny("io.flex.JobStatus",
    (in: Any) => {
      import io.flex.Query.JobStatus._
      in.toString match {
        case "SUCCEEDED" => JOB_SUCCEEDED
        case "RUNNING" => JOB_RUNNING
        case "FAILED" => JOB_FAILED
        case "UNKNOWN" => JOB_UNKNOWN
      }
    }
  )
  converter.registerAny("io.flex.StageStatus",
    (in: Any) => {
      import io.flex.Query.StageStatus._
      in.toString match {
        case "ACTIVE" => STAGE_ACTIVE
        case "COMPLETE" => STAGE_COMPLETE
        case "FAILED" => STAGE_FAILED
        case "PENDING" => STAGE_PENDING
        case "SKIPPED" => STAGE_SKIPPED
      }
    }
  )
  converter.registerMessage(classOf[Q.StageData])
  converter.registerMessage(classOf[Q.ExecutorStageSummary])
  converter.registerMessage(classOf[Q.AccumulableInfo])
  converter.registerMessage(classOf[Q.TaskData])
  converter.registerMessage(classOf[Q.TaskMetrics])
  converter.registerMessage(classOf[Q.InputMetrics])
  converter.registerMessage(classOf[Q.OutputMetrics])
  converter.registerMessage(classOf[Q.ShuffleReadMetrics])
  converter.registerMessage(classOf[Q.ShuffleWriteMetrics])
  converter.registerAny("io.flex.TaskStatus",
    (in: Any) => {
      import io.flex.Query.TaskStatus._
      in.toString match {
        case "UNKNOWN" => TASK_UNKNOWN
        case "RUNNING" => TASK_RUNNING
        case "KILLED" => TASK_KILLED
        case "FAILED" => TASK_FAILED
        case "SUCCESS" => TASK_SUCCESS
      }
    }
  )

  implicit val ec: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global

  override def executeQuery(request: Q.ExecuteQueryRequest, responseObserver: StreamObserver[Q.ExecuteQueryResponse]): Unit = {
    val request_id = request.getRequestId

    val fut = _executeQuery(request)
    fut.onComplete { result =>
      lg.info("request {} is completed", request_id)

      result match {
        case Success(r) => {
          println(r)
          responseObserver.onNext(r)
          responseObserver.onCompleted()
        }
        case Failure(t) => {
          lg.error("failed with \n{}", t.toString)
          val status = Status.INTERNAL.withCause(t).asRuntimeException()
          responseObserver.onError(status)
        }
      }
    }
  }

  private def _executeQuery(req: Q.ExecuteQueryRequest): Future[Q.ExecuteQueryResponse] = {
    val promise = Promise[Q.ExecuteQueryResponse]()

    val f = Future {
      val res = Q.ExecuteQueryResponse.newBuilder().setRequestId(req.getRequestId).build()
      lg.info("serving request {} in thread {}", req.getRequestId, Thread.currentThread().getId)
      // do sth here

      val squery = queryService.execute(req.getRequestId) { spark =>
        val query = req.getQuery
        query.getDataSourcesMap.forEach((k, v) => {
          val (name, bucket, path, format) = (v.getName, v.getBucket, v.getPath, v.getFormat)
          val fullPath = constructReadPath(v)
          lg.info("loading {} to dataframe {}", fullPath, name)
          val df = spark.read.format(format.name()).load(fullPath)
          df.createOrReplaceTempView(name)
        })
        val df = spark.sql(query.getQuery)
        val savePath = s"gs://victini-prd-asia-southeast1-01/${req.getRequestId}"
        lg.info("writing to query result to {}", savePath)
        df.show()
      }
      res
    }
    promise.completeWith(f)
    promise.future
  }

  override def getQuery(request: Q.GetQueryRequest, responseObserver: StreamObserver[Q.GetQueryResponse]): Unit = {
    val requestId = request.getRequestId
    val srequest = queryService.getQuery(requestId)
    srequest match {
      case None => responseObserver.onError(Status.NOT_FOUND.asRuntimeException())
      case Some(s) => {
        val builder = Q.GetQueryResponse.newBuilder()
        builder.setRequestId(requestId)
        builder.setStatus(s.status)
        s.getSparkJobs.map { j =>
          converter.convert("io.flex.JobData", j).asInstanceOf[Q.JobData]
        }.foreach(builder.addSparkJobs)
        val res = builder.build()
        responseObserver.onNext(res)
        responseObserver.onCompleted()
      }
    }
  }

  override def getSparkStatus(request: Query.GetSparkStatusRequest, responseObserver: StreamObserver[Query.GetSparkStatusResponse]): Unit = {
    val res = converter.convert("io.flex.GetSparkStatusResponse", queryService.getSparkStatus).asInstanceOf[Query.GetSparkStatusResponse]
    responseObserver.onNext(res)
    responseObserver.onCompleted()
  }

  override def getStage(request: Query.GetStageRequest, responseObserver: StreamObserver[Query.GetStageResponse]): Unit = {
    val data = queryService.getStateData(request.getId)
    val rdata = data.map(converter.convert("io.flex.StageData", _).asInstanceOf[Q.StageData])
    val res = Q.GetStageResponse.newBuilder().addAllData(rdata.asJava).setId(request.getId).build()
    responseObserver.onNext(res)
    responseObserver.onCompleted()
  }
}

