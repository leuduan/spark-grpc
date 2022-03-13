import com.google.protobuf.gradle.*

plugins {
    application
    scala
    id("com.google.protobuf").version("0.8.18")
}

group = "io.flexy"
version = "1.0"

repositories {
    mavenCentral()
}

val sparkVersion = "3.2.1"
val grpcVer = "1.44.1"
val protobufVer = "3.19.4"

dependencies {
    implementation("org.scala-lang:scala-library:2.13.8")

    implementation("org.apache.spark:spark-core_2.13:$sparkVersion")
    implementation("org.apache.spark:spark-sql_2.13:$sparkVersion")
    implementation("org.apache.spark:spark-kubernetes_2.13:$sparkVersion")
    // grpc
    // https://mvnrepository.com/artifact/javax.annotation/javax.annotation-api
    implementation("javax.annotation:javax.annotation-api:1.3.2")
    implementation("io.grpc:grpc-protobuf:$grpcVer")
    implementation("io.grpc:grpc-stub:$grpcVer")
    implementation("com.google.protobuf:protobuf-java-util:$protobufVer")
    implementation("io.grpc:grpc-services:${grpcVer}")
    // https://mvnrepository.com/artifact/io.grpc/grpc-netty
    runtimeOnly("io.grpc:grpc-netty:$grpcVer")


    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
//    testImplementation("org.scalactic:scalatic_2.13:3.2.1")
    testImplementation("org.scalatest:scalatest_2.13:3.2.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

configurations.all {
    exclude("org.eclipse.jetty")
    exclude("org.apache.hbase")
    exclude("com.github.joshelser")
    exclude("org.apache.hadoop", "hadoop-yarn-server-resourcemanager")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

application {
    this.mainClass.set("sc.duanlv.Main")
}

val classPath = project.tasks[JavaPlugin.JAR_TASK_NAME].outputs.files + project.configurations.runtimeClasspath

tasks.create("runMain", JavaExec::class) {
    this.mainClass.set("sc.duanlv.Main")
    this.args = listOf("one", "two")
    this.classpath = classPath
}

sourceSets {
    main {
        java {
            srcDirs("build/generated/source/proto/main/grpc")
            srcDirs("build/generated/source/proto/main/java")
        }
    }
}


// protobuf build task
protobuf {
    protoc {
        // The artifact spec for the Protobuf Compiler
        artifact = "com.google.protobuf:protoc:$protobufVer"
    }
    plugins {
        // Optional: an artifact spec for a protoc plugin, with "grpc" as
        // the identifier, which can be referred to in the "plugins"
        // container of the "generateProtoTasks" closure.
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpcVer"
        }
    }
    generateProtoTasks {
        ofSourceSet("main").forEach {
            it.plugins {
                // Apply the "grpc" plugin whose spec is defined above, without options.
                id("grpc")
            }
        }
    }
}

tasks.create("generateGrpcPythonClient", Exec::class) {
    this.commandLine = listOf("bash", "-c", "/home/kakarot/miniconda3/envs/dx/bin/python -m grpc_tools.protoc -Isrc/main/proto --python_out=. --grpc_python_out=. src/main/proto/spark_qs/*")
}
