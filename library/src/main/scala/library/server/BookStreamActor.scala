package library.server

import java.io.FileNotFoundException

import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import library.io.FileReader
import library.message.BookStreamRequest

import scala.concurrent.duration._

object BookStreamActor {

  def props(request: BookStreamRequest, client: ActorRef): Props =
    Props(new BookStreamActor(request, client))
}

/**
  * Streams requested book line by line to the client.
  */
class BookStreamActor(request: BookStreamRequest, client: ActorRef) extends Actor {

  try {
    val filename: String = request.title
    val reader = new FileReader(filename)

    implicit val materializer = ActorMaterializer.create(context.system)

    Source.fromIterator(() => reader.lines.map(request.toChunk))
      .throttle(1, 1 second)
      .runWith(Sink.actorRef(client, {request.toCompleted}))

  } catch {
    case _: FileNotFoundException => client ! request.toNotFound
  }

  self ! PoisonPill

  override def receive: Receive = {
    case m => println("unknown message: " + m.toString)
  }
}