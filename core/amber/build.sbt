name := "texera"
organization := "edu.uci.ics"
version := "0.1-SNAPSHOT"

scalaVersion := "2.12.15"

// to turn on, use: INFO
// to turn off, use: WARNING
scalacOptions ++= Seq("-Xelide-below", "WARNING")

// to check feature warnings
scalacOptions += "-feature"
// to check deprecation warnings
scalacOptions += "-deprecation"

// ensuring no parallel execution of multiple tasks
concurrentRestrictions in Global += Tags.limit(Tags.Test, 1)

// temp fix for the netty dependency issue
// https://github.com/coursier/coursier/issues/2016
ThisBuild / useCoursier := false

// add python as an additional source
Compile / unmanagedSourceDirectories += baseDirectory.value / "src" / "main" / "python"

// Excluding some proto files:
PB.generate / excludeFilter := "scalapb.proto"

/////////////////////////////////////////////////////////////////////////////
// Akka related
val akkaVersion = "2.6.12"
val akkaDependencies = Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-remote" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-metrics" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
  "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
  "io.kamon" % "sigar-loader" % "1.6.6-rev002",
  "com.softwaremill.macwire" %% "macros" % "2.3.6" % Provided,
  "com.softwaremill.macwire" %% "macrosakka" % "2.3.6" % Provided,
  "com.softwaremill.macwire" %% "util" % "2.3.6",
  "com.softwaremill.macwire" %% "proxy" % "2.3.6",
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.3"
)

// dropwizard web framework

/////////////////////////////////////////////////////////////////////////////
// DropWizard server related
val dropwizardVersion = "1.3.23"
// jersey version should be the same as jersey-server that is contained in dropwizard
val jerseyMultipartVersion = "2.25.1"
val jacksonVersion = "2.13.2"
val dropwizardDependencies = Seq(
  "io.dropwizard" % "dropwizard-core" % dropwizardVersion,
  "io.dropwizard" % "dropwizard-client" % dropwizardVersion,
  "io.dropwizard" % "dropwizard-auth" % dropwizardVersion,
  // https://mvnrepository.com/artifact/com.github.toastshaman/dropwizard-auth-jwt
  "com.github.toastshaman" % "dropwizard-auth-jwt" % "1.1.2-0",
  "com.github.dirkraft.dropwizard" % "dropwizard-file-assets" % "0.0.2",
  "io.dropwizard-bundles" % "dropwizard-redirect-bundle" % "1.0.5",
  "com.liveperson" % "dropwizard-websockets" % "1.3.14",
  "org.glassfish.jersey.media" % "jersey-media-multipart" % jerseyMultipartVersion,
  "com.fasterxml.jackson.module" % "jackson-module-jsonSchema" % jacksonVersion,
  "com.fasterxml.jackson.module" % "jackson-module-scala_2.12" % jacksonVersion,
  // https://mvnrepository.com/artifact/commons-io/commons-io
  "commons-io" % "commons-io" % "2.11.0"
)

// deps from library
//"com.kjetland" % "mbknor-jackson-jsonschema_2.12" % "1.0.39"

val slf4jVersion = "1.7.26"
val mbknorJacksonJsonSchemaDependencies = Seq(
  "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
  "javax.validation" % "validation-api" % "2.0.1.Final",
  "org.slf4j" % "slf4j-api" % slf4jVersion,
  "io.github.classgraph" % "classgraph" % "4.8.21",
  "ch.qos.logback" % "logback-classic" % "1.2.3" % "test",
  "com.github.java-json-tools" % "json-schema-validator" % "2.2.11" % "test",
  "com.fasterxml.jackson.module" % "jackson-module-kotlin" % jacksonVersion % "test",
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8" % jacksonVersion % "test",
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % jacksonVersion % "test",
  "joda-time" % "joda-time" % "2.10.1" % "test",
  "com.fasterxml.jackson.datatype" % "jackson-datatype-joda" % jacksonVersion % "test"
)

/////////////////////////////////////////////////////////////////////////////
// Lucene related
val luceneVersion = "8.7.0"
val luceneDependencies = Seq(
  "org.apache.lucene" % "lucene-core" % luceneVersion,
  "org.apache.lucene" % "lucene-analyzers-common" % luceneVersion,
  "org.apache.lucene" % "lucene-queryparser" % luceneVersion,
  "org.apache.lucene" % "lucene-queries" % luceneVersion,
  "org.apache.lucene" % "lucene-memory" % luceneVersion
)

/////////////////////////////////////////////////////////////////////////////

/////////////////////////////////////////////////////////////////////////////
// Google Service related
val googleServiceDependencies = Seq(
  "com.google.oauth-client" % "google-oauth-client" % "1.31.4" exclude ("com.google.guava", "guava"),
  "com.google.oauth-client" % "google-oauth-client-jetty" % "1.31.4" exclude ("com.google.guava", "guava"),
  "com.google.api-client" % "google-api-client" % "1.31.1" exclude ("com.google.guava", "guava"),
  "com.google.apis" % "google-api-services-sheets" % "v4-rev612-1.25.0" exclude ("com.google.guava", "guava"),
  "com.google.apis" % "google-api-services-drive" % "v3-rev197-1.25.0" exclude ("com.google.guava", "guava")
)

/////////////////////////////////////////////////////////////////////////////
// Arrow related
val arrowVersion = "8.0.0"
val arrowDependencies = Seq(
  // https://mvnrepository.com/artifact/org.apache.arrow/flight-grpc
  "org.apache.arrow" % "flight-grpc" % arrowVersion,
  // https://mvnrepository.com/artifact/org.apache.arrow/flight-core
  "org.apache.arrow" % "flight-core" % arrowVersion
)

/////////////////////////////////////////////////////////////////////////////
// MongoDB related
val mongoDbDependencies = Seq(
  // https://mvnrepository.com/artifact/org.mongodb/mongo-java-driver
  "org.mongodb" % "mongo-java-driver" % "3.12.10",
  // https://mvnrepository.com/artifact/org.apache.commons/commons-jcs3-core/3.0
  "org.apache.commons" % "commons-jcs3-core" % "3.0"
)

libraryDependencies ++= akkaDependencies
libraryDependencies ++= luceneDependencies
libraryDependencies ++= dropwizardDependencies
libraryDependencies ++= mbknorJacksonJsonSchemaDependencies
libraryDependencies ++= arrowDependencies
libraryDependencies ++= googleServiceDependencies
libraryDependencies ++= mongoDbDependencies

/////////////////////////////////////////////////////////////////////////////
// protobuf related
// run the following with sbt to have protobuf codegen

PB.protocVersion := "3.19.4"

Compile / PB.targets := Seq(
  scalapb.gen(
    singleLineToProtoString = true
  ) -> (Compile / sourceDirectory).value / "scalapb"
)

libraryDependencies ++= Seq(
  "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf"
)
// For ScalaPB 0.11.x:
libraryDependencies += "com.thesamet.scalapb" %% "scalapb-json4s" % "0.11.0"

// enable protobuf compilation in Test
Test / PB.protoSources += PB.externalSourcePath.value

/////////////////////////////////////////////////////////////////////////////
// Test related
// https://mvnrepository.com/artifact/org.scalamock/scalamock
libraryDependencies += "org.scalamock" %% "scalamock" % "4.4.0" % Test
// https://mvnrepository.com/artifact/ch.vorburger.mariaDB4j/mariaDB4j
libraryDependencies += "ch.vorburger.mariaDB4j" % "mariaDB4j" % "2.4.0" % Test
// https://www.scalatest.org/getting_started_with_fun_suite
libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.9" % Test

/////////////////////////////////////////////////////////////////////////////
// Workflow version control related
// https://mvnrepository.com/artifact/com.flipkart.zjsonpatch/zjsonpatch
libraryDependencies += "com.flipkart.zjsonpatch" % "zjsonpatch" % "0.2.1"

/////////////////////////////////////////////////////////////////////////////
// Uncategorized

// https://mvnrepository.com/artifact/io.reactivex.rxjava3/rxjava
libraryDependencies += "io.reactivex.rxjava3" % "rxjava" % "3.1.3"

// https://mvnrepository.com/artifact/org.postgresql/postgresql
libraryDependencies += "org.postgresql" % "postgresql" % "42.2.18"

// https://mvnrepository.com/artifact/com.typesafe.scala-logging/scala-logging
libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2"

// https://mvnrepository.com/artifact/org.scalactic/scalactic
libraryDependencies += "org.scalactic" %% "scalactic" % "3.2.2"

// https://mvnrepository.com/artifact/com.github.tototoshi/scala-csv
libraryDependencies += "com.github.tototoshi" %% "scala-csv" % "1.3.6"

// https://mvnrepository.com/artifact/com.univocity/univocity-parsers
libraryDependencies += "com.univocity" % "univocity-parsers" % "2.9.1"

// https://mvnrepository.com/artifact/com.konghq/unirest-java
libraryDependencies += "com.konghq" % "unirest-java" % "3.11.11"

// https://mvnrepository.com/artifact/com.github.marianobarrios/lbmq
libraryDependencies += "com.github.marianobarrios" % "lbmq" % "0.5.0"

// https://mvnrepository.com/artifact/io.github.redouane59.twitter/twittered
libraryDependencies += "io.github.redouane59.twitter" % "twittered" % "2.16"

// https://mvnrepository.com/artifact/org.jooq/jooq
libraryDependencies += "org.jooq" % "jooq" % "3.14.4"

// https://mvnrepository.com/artifact/mysql/mysql-connector-java
libraryDependencies += "mysql" % "mysql-connector-java" % "8.0.23"

// https://mvnrepository.com/artifact/org.jgrapht/jgrapht-core
libraryDependencies += "org.jgrapht" % "jgrapht-core" % "1.4.0"

// https://mvnrepository.com/artifact/edu.stanford.nlp/stanford-corenlp
libraryDependencies += "edu.stanford.nlp" % "stanford-corenlp" % "3.9.2"
libraryDependencies += "edu.stanford.nlp" % "stanford-corenlp" % "3.9.2" classifier "models"

// https://mvnrepository.com/artifact/com.twitter/chill-akka
libraryDependencies += "com.twitter" %% "chill-akka" % "0.9.3"

// https://mvnrepository.com/artifact/com.twitter/util-core
libraryDependencies += "com.twitter" %% "util-core" % "20.9.0"

// https://mvnrepository.com/artifact/com.typesafe.play/play-json
libraryDependencies += "com.typesafe.play" %% "play-json" % "2.7.3"

// https://mvnrepository.com/artifact/org.fusesource.leveldbjni/leveldbjni-all
libraryDependencies += "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8"

// https://mvnrepository.com/artifact/com.github.nscala-time/nscala-time
libraryDependencies += "com.github.nscala-time" %% "nscala-time" % "2.22.0"

// https://mvnrepository.com/artifact/com.google.guava/guava
libraryDependencies += "com.google.guava" % "guava" % "29.0-jre"

// https://mvnrepository.com/artifact/org.tukaani/xz
libraryDependencies += "org.tukaani" % "xz" % "1.5"

// https://mvnrepository.com/artifact/org.jasypt/jasypt
libraryDependencies += "org.jasypt" % "jasypt" % "1.9.3"
