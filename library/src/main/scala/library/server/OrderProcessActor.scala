package library.server

import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import library.io.OrdersManager
import library.message.OrderRequest

object OrderProcessActor {

  def props(request: OrderRequest, ordersManager: OrdersManager, client: ActorRef): Props =
    Props(new OrderProcessActor(request, ordersManager, client))
}

/**
  * Saves new order in orders database file.
  */
class OrderProcessActor(request: OrderRequest, ordersManager: OrdersManager, client: ActorRef) extends Actor {

  ordersManager.makeOrder(request.title)
  client ! request.toConfirmation
  self ! PoisonPill

  override def receive: Receive = {
    case m => println("unknown message: " + m.toString)
  }
}
