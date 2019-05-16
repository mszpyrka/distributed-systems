package library.io

import java.io.FileWriter
import java.util
import java.util.concurrent.locks.{Lock, ReentrantLock}

object OrdersManager {
  private val ordersLocks = new util.HashMap[String, Lock]
  private val mapLock = new ReentrantLock()

  private def getOrdersLock(filename: String) = {
    mapLock.lock()
    val lock = ordersLocks.get(filename)
    mapLock.unlock()

    lock
  }

  def apply(filename: String): OrdersManager = {
    mapLock.lock()
    ordersLocks.put(filename, new ReentrantLock())
    mapLock.unlock()
    new OrdersManager(filename)
  }
}

class OrdersManager private (val filename: String) {

  private def ordersLock = OrdersManager.getOrdersLock(this.filename)

  def makeOrder(title: String): Unit = {
    ordersLock.lock()

    val fw = new FileWriter(this.filename, true)
    try fw.write(s"$title\n")
    finally fw.close()

    ordersLock.unlock()
  }
}
