package library.io

import scala.io.Source

class FileReader(filename: String) {
  private val source = Source.fromFile(filename)
  def lines: Iterator[String] = source.getLines()
  def close(): Unit = source.close()
}
