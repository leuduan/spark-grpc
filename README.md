# Spark-gRPC

An attempt to expose apache spark driver info and submit sql queries via gRPC

```protobuf
service SparkQueryService {
  rpc executeQuery (ExecuteQueryRequest) returns (ExecuteQueryResponse) {}
  rpc getQuery (GetQueryRequest) returns (GetQueryResponse) {}
  rpc getSparkStatus(GetSparkStatusRequest) returns (GetSparkStatusResponse) {}
  rpc getStage(GetStageRequest) returns (GetStageResponse){}
}
```

See: [query.proto](spark-grpc/src/main/proto/spark_qs/query.proto)
