package library.server

import java.util.UUID

import akka.actor.{Actor, Props}
import library.io.{DbAccess, OrdersManager}
import library.message.{BookPriceRequest, BookStreamRequest, OrderRequest}

object ServerActor {
  def props(dbFiles: Array[String], ordersFile: String, rootDir: String): Props =
    Props(new ServerActor(dbFiles, ordersFile, rootDir))
}

/**
  * Library server that handles incoming client requests.
  * @param dbFiles - paths to files with books prices
  * @param ordersFile - path to the file in which orders are stored
  */
class ServerActor(dbFiles: Array[String], ordersFile: String, rootDir: String) extends Actor {

  private def withRootDir(r: BookStreamRequest) = BookStreamRequest(rootDir + "/" + r.title, r.id)

  println(s"server actor started at: ${self.path}")

  private val ordersManager = OrdersManager(rootDir + "/" + ordersFile)
  private val databases = for (file <- dbFiles) yield new DbAccess(rootDir + "/" + file)

  private def createPriceCheckActor(request: BookPriceRequest) = {
    val name = UUID.randomUUID().toString + ":price-check"
    context.actorOf(PriceCheckActor.props(request, databases, context.sender()), name)
  }

  private def createOrderProcessActor(request: OrderRequest) = {
    val name = UUID.randomUUID().toString + ":order"
    context.actorOf(OrderProcessActor.props(request, ordersManager, context.sender()), name)
  }

  private def createBookStreamActor(request: BookStreamRequest) = {
    val name = UUID.randomUUID().toString + ":stream"
    context.actorOf(BookStreamActor.props(withRootDir(request), context.sender()), name)
  }

  override def receive: Receive = {
    case request: BookPriceRequest => println(request); createPriceCheckActor(request)
    case request: OrderRequest => println(request); createOrderProcessActor(request)
    case request: BookStreamRequest => println(request); createBookStreamActor(request)
    case m => println("unknown message: " + m.toString)
  }
}
