package library.message

import java.util.UUID.randomUUID

// ======================================================================================
// External messages (exchanged between client and server)
// ======================================================================================

case class BookPriceRequest(title: String, id: String = randomUUID().toString) {
  def toResponse(price: Double) = BookPriceResponse(price, this.id)
  def toNotFound = BookNotFound(this.id)
}

case class BookPriceResponse(price: Double, id: String)
case class BookNotFound(id: String)

case class OrderRequest(title: String, id: String = randomUUID().toString) {
  def toConfirmation = OrderConfirmation(this.id)
}

case class OrderConfirmation(id: String)

case class BookStreamRequest(title: String, id: String = randomUUID().toString) {
  def toChunk(chunk: String) = BookStreamChunk(chunk, this.id)
  def toCompleted = BookStreamCompleted(this.id)
  def toNotFound = BookNotFound(this.id)
}

case class BookStreamChunk(chunk: String, id: String)
case class BookStreamCompleted(id: String)

// ======================================================================================
// Internal messages (exchanged between server actors)
// ======================================================================================

case class Price(price: Double)
case object NotFound