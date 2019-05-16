name := "library"

version := "0.1"

scalaVersion := "2.12.8"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.5.22",
  "com.typesafe.akka" %% "akka-remote" % "2.5.22",
  "com.typesafe.akka" %% "akka-stream" % "2.5.22",
  "com.nthportal" %% "cancellable-task" % "1.0.0"

)