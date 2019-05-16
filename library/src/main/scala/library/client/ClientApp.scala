package library.client

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory

import scala.io.StdIn.readLine

object ClientApp extends App {

  val host = "127.0.0.1"
  val port = 0

  val config = ConfigFactory.parseString(
    s"""
       |akka {
       |  loglevel = "ERROR"
       |
       |  actor {
       |    provider = remote
       |  }
       |  remote {
       |    enabled-transports = ["akka.remote.netty.tcp"]
       |    netty.tcp {
       |      hostname = "$host"
       |      port = $port
       |    }
       |  }
       |}
    """.stripMargin)

  val system = ActorSystem.create("client", config)

  val server = system.actorSelection("akka.tcp://library@127.0.0.1:9990/user/server-actor")
  val clientActor = system.actorOf(ClientActor.props(server), name = "client-actor")

  while (true) {
    val input = readLine()
    clientActor ! input
  }
}
