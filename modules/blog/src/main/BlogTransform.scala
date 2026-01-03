package lila.blog

object BlogTransform {

  private val RemoveRegex          = """http://(\w{2}\.)?+lishogi\.org""".r
  def removeProtocol(html: String) = RemoveRegex.replaceAllIn(html, _ => "//lishogi.org")

  private val AddRegex          = """(https?+:)?+(//)?+(\w{2}\.)?+lishogi\.org""".r
  def addProtocol(html: String) = AddRegex.replaceAllIn(html, _ => "https://lishogi.org")

}
