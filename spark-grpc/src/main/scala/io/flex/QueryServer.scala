package io.flex

import io.grpc.protobuf.services.ProtoReflectionService
import io.grpc.{Server, ServerBuilder}
import org.apache.spark.customize.{QueryService, SQuery}
import org.apache.spark.sql.SparkSession
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.ExecutionContext

object QueryServer {
  val lg: Logger = LoggerFactory.getLogger(this.getClass)
  var server: Server = null

  def main(args: Array[String]): Unit = {
    val builder = ServerBuilder.forPort(50051)
    val spark = constructSparkSession
    val ec = ExecutionContext.global
    val queryService = new QueryService(spark, ec)
    queryService.initialize()
    queryService.listenForQuery((squery: SQuery) => {
      println(squery.toString)
    })
    builder.addService(new SparkQueryServiceImpl(spark, queryService))
    builder.addService(ProtoReflectionService.newInstance())
    server = builder.build()
    server.start()
    lg.info("server started")

    Runtime.getRuntime.addShutdownHook(new Thread {
      override def run(): Unit = {
        // Use stderr here since the logger may have been reset by its JVM shutdown hook.
        System.err.println("*** shutting down gRPC server since JVM is shutting down")
        try {
          server.shutdown()
          spark.stop()
        } catch {
          case e: InterruptedException => e.printStackTrace(System.err)
          case _: Throwable =>
        }
        System.err.println("*** server shut down")
      }
    })
    server.awaitTermination()
  }

  def constructSparkSession: SparkSession = {
    SparkSession.builder().master("local")
      .config("spark.ui.enabled", "false")
      .getOrCreate()
  }
}
