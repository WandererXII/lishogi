package lila.blog

import scala.concurrent.duration._
import scala.util.matching.Regex

import com.github.blemale.scaffeine.LoadingCache

object BlogTransform {

  private val RemoveRegex          = """http://(\w{2}\.)?+lishogi\.org""".r
  def removeProtocol(html: String) = RemoveRegex.replaceAllIn(html, _ => "//lishogi.org")

  private val AddRegex          = """(https?+:)?+(//)?+(\w{2}\.)?+lishogi\.org""".r
  def addProtocol(html: String) = AddRegex.replaceAllIn(html, _ => "https://lishogi.org")

  object markdown {
    private type Text = String
    private type Html = String

    private val renderer = new lila.common.Markdown

    private val cache: LoadingCache[Text, Html] = lila.memo.CacheApi.scaffeineNoScheduler
      .expireAfterWrite(15 minutes)
      .maximumSize(32)
      .build((text: Text) => renderer(text.replace("<br>", "\n")))

    private val PreRegex = """<pre>markdown(.+)</pre>""".r

    def apply(html: Html): Html =
      PreRegex.replaceAllIn(html, m => Regex.quoteReplacement(cache get m.group(1)))
  }
}
