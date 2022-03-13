package org.apache.spark.customize


trait QueryHandler {
  def handle(squery: SQuery): Unit
}
