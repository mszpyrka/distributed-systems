package library.io

class BookNotFoundException(s:String) extends Exception(s)

object DbAccess {

  private val nonAlphaNum = """[^A-Za-z0-9]+"""
  private def cleanTitle(title: String) = title.replaceAll(nonAlphaNum, " ").trim
}


class DbAccess(val filename: String) {

  def getPrice(title: String): Double = {

    val cleaned = DbAccess.cleanTitle(title)
    val reader = new FileReader(this.filename)

    for (line <- reader.lines) {
      val details = line.split(":")
      val bookTitle = DbAccess.cleanTitle(details(0))
      val bookPrice = details(1).toDouble

      if (bookTitle.equalsIgnoreCase(cleaned)) {
        reader.close()
        return bookPrice
      }
    }

    reader.close()
    throw new BookNotFoundException(s"'$title' was not found in the database")
  }
}