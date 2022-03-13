name := "dev-only"
version := "0.1"

scalaVersion := "2.13.8"
val grpcVer = "1.44.1"
val protobufVer = "3.19.4"

libraryDependencies += "org.apache.spark" %% "spark-sql" % "3.2.1"
libraryDependencies += "org.apache.spark" %% "spark-core" % "3.2.1"
// libraryDependencies += "org.yaml" % "snakeyaml" % "1.27"
// libraryDependencies += "com.google.cloud.spark" %% "spark-bigquery" % "0.18.1"
// https://mvnrepository.com/artifact/org.jetbrains.kotlin/kotlin-stdlib
// libraryDependencies += "org.jetbrains.kotlin" % "kotlin-stdlib" % "1.4.10"

libraryDependencies += "javax.annotation" % "javax.annotation-api" % "1.3.2"
libraryDependencies += "io.grpc" % "grpc-protobuf" % s"$grpcVer"
libraryDependencies += "io.grpc" % "grpc-stub" % s"$grpcVer"
libraryDependencies += "com.google.protobuf" % "protobuf-java-util" % s"$protobufVer"
libraryDependencies += "io.grpc" % "grpc-netty" % s"$grpcVer"
libraryDependencies += "io.grpc" % "grpc-services" % s"$grpcVer"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.11" % "test"

//https://www.scala-sbt.org/0.13/docs/Howto-Customizing-Paths.html
Compile / unmanagedSourceDirectories += baseDirectory.value / "build" / "generated" / "source" / "proto" / "main" / "grpc"
Compile / unmanagedSourceDirectories += baseDirectory.value / "build" / "generated" / "source" / "proto" / "main" / "java"

// https://github.com/sbt/sbt/issues/2274
cancelable in Global := true
fork in Global := true
//javaOptions := Seq("-Dspark.metrics.conf=metrics.properties")

scalacOptions ++= Seq(
//  "-Xfatal-warnings",
//  "-deprecation",
  "-feature"
)
