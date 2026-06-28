package lila.article

import play.api.i18n.Lang

import org.joda.time.DateTime

import lila.i18n.I18nKeys
import lila.user.User

case class Article(
    id: Article.ID,
    author: User.ID,
    translations: Map[Article.Lang.Code, Article.Content],
    categories: List[Article.Category],
    likes: Int,
    state: Article.State,
    approvedBy: Option[User.ID],
    rankBoost: Int,
    createdAt: DateTime,
    updatedAt: DateTime,
    publishedAt: Option[DateTime],
    systemKey: Option[String], // for system pages
) {

  def langCodes = translations.keySet

  def title(lang: Article.Lang.Code): String =
    ~translations.get(lang).map(_.title)

  def slug(lang: Article.Lang.Code): String =
    ~translations.get(lang).map(_.slug)

  def image(lang: Article.Lang.Code): Option[String] =
    translations.get(lang).map(_.image).filter(_.nonEmpty)

  def intro(lang: Article.Lang.Code): String =
    ~translations.get(lang).map(_.intro)

  def body(lang: Article.Lang.Code): String =
    ~translations.get(lang).map(_.body)

  def translator(lang: Article.Lang.Code): Option[String] =
    translations.get(lang).flatMap(_.translator).filter(t => t.nonEmpty && t != author)

  def isSystemPage: Boolean = systemKey.isDefined

  def editable = isSystemPage || publishedAt.fold(true)(d => d isAfter DateTime.now.minusDays(14))

  def translatable = isSystemPage || langCodes.size < Article.Lang.Max

  def published = state == Article.State.Published

}

object Article {
  type ID = String

  object Lang {
    type Code = String

    val allCode     = "all"
    val defaultLang = lila.i18n.defaultLang
    val defaultCode = lila.i18n.languageCode(defaultLang)

    def toLangCode(lang: Lang): Code =
      lila.i18n.languageCode(lang)

    def resolve(available: Set[Code], desired: Lang): Code =
      resolve(available, toLangCode(desired))

    def resolve(available: Set[Code], desiredCode: Code): Code =
      if (available.contains(desiredCode)) desiredCode
      else if (available.contains(defaultCode)) defaultCode
      else available.headOption | defaultCode

    val Max = 3

  }

  case class Content(
      title: String,
      slug: String,
      image: String,
      intro: String,
      body: String,
      translator: Option[String],
  )

  object Content {

    case class Meta(
        title: String,
        slug: String,
        image: String,
    )
  }

  trait Category {
    def key = toString.toLowerCase
  }
  object Category {
    case object Official extends Category
    case object Analysis extends Category // games, openings, castles, positions
    case object Culture  extends Category // players, history
    case object Events   extends Category // tournaments, meetups, etc.
    case object Guides   extends Category // how to play/improve
    case object Lishogi  extends Category // about lishogi
    case object Software extends Category // engines, tools, etc.
    case object Variants extends Category // other shogi variants

    val all = List[Category](
      Official,
      Analysis,
      Culture,
      Events,
      Guides,
      Lishogi,
      Software,
      Variants,
    )

    val MaxPerArticle = 3

    def byKey(key: String): Option[Category] =
      all.find(_.key == key)

    def trans(c: Category)(implicit lang: Lang): String =
      c match {
        case Official => I18nKeys.article.categoryOfficial.txt()
        case Analysis => I18nKeys.article.categoryAnalysis.txt()
        case Culture  => I18nKeys.article.categoryCulture.txt()
        case Events   => I18nKeys.article.categoryEvents.txt()
        case Guides   => I18nKeys.article.categoryGuides.txt()
        case Lishogi  => I18nKeys.article.categoryLishogi.txt()
        case Software => I18nKeys.article.categorySoftware.txt()
        case Variants => I18nKeys.article.categoryVariants.txt()
      }
  }

  trait State {
    def key = toString.toLowerCase
  }
  object State {
    case object Draft     extends State
    case object ToPublish extends State
    case object Published extends State

    val all = List(
      Draft,
      ToPublish,
      Published,
    )

    def byKey(key: String): Option[State] =
      all.find(_.key == key)

    def maxDraftsPerUser = 3

  }

  type Rank = DateTime
  def calculateRank(likes: Int, publishedAt: DateTime, boost: Int): Rank =
    publishedAt.plusDays(likes + boost)

  case class Preview(
      id: ID,
      author: User.ID,
      translations: Map[Article.Lang.Code, Content.Meta],
      categories: List[Article.Category],
      state: Article.State,
      publishedAt: Option[DateTime],
      updatedAt: DateTime,
      systemKey: Option[String],
  ) {

    def langCodes = translations.keySet

    def title(lang: Article.Lang.Code): String =
      ~translations.get(lang).map(_.title)

    def slug(lang: Article.Lang.Code): String =
      ~translations.get(lang).map(_.slug)

    def image(lang: Article.Lang.Code): Option[String] =
      translations.get(lang).map(_.image).filter(_.nonEmpty)

    def isSystemPage: Boolean = systemKey.isDefined
  }

  val maxDrafts = 5

  object BSONFields {
    val id          = "_id"
    val author      = "a"
    val categories  = "c"
    val likes       = "l"
    val state       = "s"
    val approvedBy  = "ab"
    val rank        = "r"
    val rankBoost   = "rb"
    val createdAt   = "ca"
    val updatedAt   = "ua"
    val publishedAt = "pa"
    val systemKey   = "sk"
    val blogBcKey   = "bb"

    // translations separated for preview
    val title      = "t"
    val slug       = "u"
    val translator = "tr"
    val image      = "m"
    val intro      = "i"
    val body       = "b"
    val langs      = "ls"
    val likers     = "lk"
  }
}
