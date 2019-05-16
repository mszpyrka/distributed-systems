package library.server
import java.io.FileNotFoundException
import java.util.UUID
import akka.actor.SupervisorStrategy.{Escalate, Restart}
import akka.actor.{Actor, ActorRef, OneForOneStrategy, PoisonPill, Props}
import akka.pattern.{BackoffOpts, BackoffSupervisor}
import library.io.DbAccess
import library.message.{BookPriceRequest, Cancel, NotFound, Price}

import scala.concurrent.duration._

object PriceCheckActor {

  def props(request: BookPriceRequest, databases: Array[DbAccess], client: ActorRef): Props =
    Props(new PriceCheckActor(request, databases, client))
}

class PriceCheckActor(request: BookPriceRequest, databases: Array[DbAccess], client: ActorRef) extends Actor {

  /**
    * Creates new child for each database that needs to be checked.
    * Each child has its own supervisor, that restarts the child with
    * exponential backoff strategy every time there is a failure
    * in access to the database.
    */
  private val children = for (db <- databases) yield {
    val actorName = UUID.randomUUID().toString + ":db-search"
    val childProps = DbSearchActor.props(request.title, db, self)

    val supervisor = BackoffSupervisor.props(
      BackoffOpts.onFailure(
        childProps,
        childName = actorName,
        minBackoff = 5.seconds,
        maxBackoff = 30.seconds,
        randomFactor = 0.2
      )
        .withAutoReset(10.seconds)
        .withSupervisorStrategy(OneForOneStrategy() {
        case _: FileNotFoundException => Restart
        case _: Exception => Escalate
      }))

    val supervisorName = s"$actorName:backoffSupervisor"
    context.system.actorOf(supervisor, supervisorName)
  }

  private var bookFound = false
  private var notFoundCounter = 0

  /**
    * If the book is found in any database, cancel message is broadcasted
    * to all children.
    */
  private def handleBookFound(price: Price): Unit = {

    if (bookFound) return

    bookFound = true
    for (child <- children)
      child ! PoisonPill

    client ! request.toResponse(price.price)
    self ! PoisonPill
  }

  /**
    * If the child signals that book was not found in it's database, the counter
    * is increased. After all children confirm that there is no book, proper message
    * is sent to the client.
    */
  private def handleBookNotFound(): Unit = {
    notFoundCounter += 1

    if (notFoundCounter == children.length) {
      client ! request.toNotFound
      self ! PoisonPill
    }
  }

  override def receive: Receive = {
    case p: Price => handleBookFound(p)
    case NotFound => handleBookNotFound()
    case m => println("unknown message: " + m.toString)
  }
}
