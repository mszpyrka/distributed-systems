package library.server

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object ServerApp extends App {

  val host = "127.0.0.1"
  val port = 9990

  val config = ConfigFactory.parseString(
    s"""
      |akka {
      |
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

  val system = ActorSystem.create("library", config)

  val rootDir = "/home/mateusz/Programowanie/distributed-systems/library/db/"
  val dbs = Array("books1.txt", "books2.txt")
  val orders = "orders.txt"

  val clientActor = system.actorOf(ServerActor.props(dbs, orders, rootDir), name = "server-actor")

  Await.result(system.whenTerminated, Duration.Inf)
}
