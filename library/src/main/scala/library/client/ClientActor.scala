package library.client

import akka.actor.{Actor, ActorSelection, Props}
import library.message._

object ClientActor {
  def props(server: ActorSelection) = Props(new ClientActor(server))
}

class ClientActor(val server: ActorSelection) extends Actor {

  println("client actor starting...")

  override def receive: Receive = {

    case input: String => processUserInput(input)

    case response: BookPriceResponse => println(s"price: ${response.price} (request id = ${response.id})")
    case response: OrderConfirmation => println(s"order confirmed (request id = ${response.id})")
    case response: BookStreamChunk => println(s"book chunk: '${response.chunk}' (request id = ${response.id})")
    case response: BookStreamCompleted => println(s"book streaming completed (request id = ${response.id})")
    case response: BookNotFound => println(s"book not found (request id = ${response.id})")

    case m => println(m)
  }

  private def processUserInput(input: String): Unit = {
    try {
      val parts = input.split(" ", 2)
      val command = parts(0)
      val content = parts(1)

      if (command.equalsIgnoreCase("find")) {
        val request = BookPriceRequest(content)
        server ! request
      }
      else if (command.equalsIgnoreCase("order")) {
        val request = OrderRequest(content)
        server ! request
      }
      else if (command.equalsIgnoreCase("stream")) {
        val request = BookStreamRequest(content)
        server ! request
      }
      else {
        println(s"unknown command: $command")
      }
    } catch {
      case _: IndexOutOfBoundsException =>
        println("command syntax: [find | order | stream] book_title")
    }
  }
}
