package library.server

import java.util.concurrent.CancellationException

import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import com.nthportal.concurrent.CancellableTask
import library.io.{BookNotFoundException, DbAccess}
import library.message.{NotFound, Price}

import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global

object DbSearchActor {

  def props(title: String, dbAccess: DbAccess, reportTo: ActorRef): Props =
    Props(new DbSearchActor(title, dbAccess, reportTo))
}

/**
  * Responsible for finding given title in the database.
  */
class DbSearchActor(title: String, dbAccess: DbAccess, reportTo: ActorRef) extends Actor {

  private val task = CancellableTask { dbAccess.getPrice(title) }

  task.future onComplete {

    case Success(priceValue) => complete(priceValue)

    case Failure(exception) => exception match {
        case _: CancellationException => println("task cancelled successfully")
        case _: BookNotFoundException => reportTo ! NotFound
        case otherException => self ! otherException
      }
  }

  private def complete(priceValue: Double): Unit = {
    reportTo ! Price(priceValue)
    self ! PoisonPill
  }

  override def postStop(): Unit = {
    task.cancel(true)
  }

  override def receive: Receive = {
    case e: Exception => throw e
    case m => println("unknown message: " + m.toString)
  }
}
