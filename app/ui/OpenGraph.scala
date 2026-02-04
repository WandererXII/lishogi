package lila.app
package ui

import play.api.i18n.Lang

import lila.app.ui.ScalatagsTemplate._

case class OpenGraph(
    title: String,
    description: String,
    url: String,
    `type`: String = "website",
    image: Option[String] = None,
    twitterImage: Option[String] = None,
    more: List[(String, String)] = Nil,
) {

  def frags(staticUrl: String => String)(implicit lang: Lang): List[Frag] =
    og.frags(staticUrl) ::: twitter.frags(staticUrl)

  object og {

    private val property = attr("property")

    private def tag(name: String, value: String) =
      meta(
        property := s"og:$name",
        content  := value,
      )

    private val tupledTag = (tag _).tupled

    private def defaultImage(staticUrl: String => String) = staticUrl("logo/lishogi-tile-wide.png")

    def frags(staticUrl: String => String)(implicit lang: Lang): List[Frag] =
      List(
        "title"       -> title,
        "description" -> description,
        "url"         -> url,
        "type"        -> `type`,
        "locale"      -> lang.language,
        "site_name"   -> "lishogi.org",
        "image"       -> image.getOrElse(defaultImage(staticUrl)),
      ).map(tupledTag) :::
        more.map(tupledTag)
  }

  object twitter {

    private def tag(name: String, value: String) =
      meta(
        st.name := s"twitter:$name",
        content := value,
      )

    private val tupledTag = (tag _).tupled

    private def defaultImage(staticUrl: String => String) = staticUrl("logo/lishogi-tile.png")

    def frags(staticUrl: String => String): List[Frag] =
      List(
        "card"        -> "summary",
        "title"       -> title,
        "description" -> description,
        "image"       -> twitterImage.orElse(image).getOrElse(defaultImage(staticUrl)),
      ).map(tupledTag) :::
        more.map(tupledTag)
  }
}
