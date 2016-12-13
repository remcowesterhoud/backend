name := "api"

organization := "com.analyzedgg"

version := "0.0.1"

scalaVersion := "2.11.6"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint")

initialize := {
  val _ = initialize.value
  if (sys.props("java.specification.version") != "1.8") {
    sys.error("Java 8 is required for this project.")
  }
}

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
resolvers += "Mvn repository" at "http://mvnrepository.com/artifact/"

libraryDependencies ++= {
  val akkaVersion = "2.4.14"
  val scalaTestVersion = "3.0.1"
  val scalaMockVersion = "3.3.0"
  val logbackVersion = "1.1.2"
  val jacksonVersion: String = "2.7.4"
  val couchDbScalaVersion: String = "0.7.0"
  val scalaLoggingVersion: String = "3.5.0"

  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-remote" % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    "com.typesafe.akka" %% "akka-http-spray-json-experimental" % akkaVersion,
    "ch.qos.logback" % "logback-classic" % logbackVersion % "runtime",
    "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingVersion,
    "com.typesafe.akka" % "akka-testkit_2.11" % akkaVersion % "test,it",
    "com.typesafe.akka" % "akka-http-testkit_2.11" % akkaVersion,
    "org.scalatest" %% "scalatest" % scalaTestVersion % "test,it",
    "org.scalamock" %% "scalamock-scalatest-support" % scalaMockVersion % "test",
    "com.fasterxml.jackson.core" % "jackson-core" % jacksonVersion,
    "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
    "com.fasterxml.jackson.module" % "jackson-module-scala_2.11" % jacksonVersion,
    "com.ibm" %% "couchdb-scala" % couchDbScalaVersion
  )
}

val deployTask = TaskKey[Unit]("deploy", "Copies assembly jar to remote location")

deployTask <<= assembly map { (asm) =>
  val account = "pi@192.168.178.12"
  val relativeLocal = new File("build.sbt").getAbsoluteFile.getParentFile
  val local = asm.getAbsoluteFile.relativeTo(relativeLocal).get.toString
  val remote = account + ":" + "league/" + asm.getName
  println(s"Copying: $local -> $remote")
  s"scp $local $remote".!

  println("Run deployed app")
  s"ssh $account cd league && ./run.sh".!
}

mainClass in(Compile, run) := Some("com.analyzedgg.api.Startup")

fork in run := true
