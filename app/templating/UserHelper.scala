package lila.app
package templating

import controllers.routes
import play.api.i18n.Lang

import lila.app.mashup._
import lila.app.ui.ScalatagsTemplate._
import lila.common.LightUser
import lila.i18n.{ I18nKeys => trans }
import lila.rating.Perf
import lila.rating.PerfType
import lila.rating.Rank
import lila.user.User

trait UserHelper {
  self: AssetHelper with I18nHelper with StringHelper with NumberHelper with DateHelper =>

  def showRatingProgress(progress: Int): Option[Frag] =
    if (progress > 0) goodTag(cls := "rp")(progress).some
    else if (progress < 0) badTag(cls := "rp")(math.abs(progress)).some
    else none

  def showPerfRating(rating: Int, name: String, nb: Int, provisional: Boolean, icon: String)(
      implicit lang: Lang,
  ): Frag =
    span(
      title    := s"$name rating over ${nb.localize} games",
      dataIcon := icon,
      cls      := "text",
    )(
      if (nb > 0) frag(rating, provisional option "?")
      else frag(nbsp, nbsp, nbsp, "-"),
    )

  def showPerfRating(perfType: PerfType, perf: Perf)(implicit lang: Lang): Frag =
    showPerfRating(perf.intRating, perfType.trans, perf.nb, perf.provisional, perfType.icon)

  def showPerfRating(u: User, perfType: PerfType)(implicit lang: Lang): Frag =
    showPerfRating(perfType, u perfs perfType)

  def showPerfRating(u: User, perfKey: String)(implicit lang: Lang): Option[Frag] =
    PerfType.byKey(perfKey) map { showPerfRating(u, _) }

  def showBestPerf(u: User)(implicit lang: Lang): Option[Frag] =
    u.perfs.bestPerf map { case (pt, perf) =>
      showPerfRating(pt, perf)
    }
  def showBestPerfs(u: User, nb: Int)(implicit lang: Lang): List[Frag] =
    u.perfs.bestPerfs(nb) map { case (pt, perf) =>
      showPerfRating(pt, perf)
    }

  def showRatingDiff(diff: Int): Frag =
    diff match {
      case 0          => span("±0")
      case d if d > 0 => goodTag(s"+$d")
      case d          => badTag(s"−${-d}")
    }

  def botTag = span(cls := "bot-tag")(lila.user.Title.BOT.value, nbsp)

  def rankTag(perf: Perf, withUnknown: Boolean = false)(implicit lang: Lang): Option[Frag] =
    Rank
      .fromPerf(perf)
      .map(rankTag)
      .orElse(
        withUnknown option span(cls := "rank-tag unknown")("?", nbsp),
      )
  def rankTag(rating: Int)(implicit lang: Lang): Frag =
    rankTag(Rank.fromRating(rating))
  def rankTag(rank: Rank)(implicit lang: Lang): Frag =
    span(cls := s"rank-tag r-${rank.enName}")(rank.trans, nbsp)

  def lightUser                   = env.user.lightUserSync
  def isOnline(userId: String)    = env.socket isOnline userId
  def isStreaming(userId: String) = env.streamer.liveStreamApi isStreaming userId

  def anonSpan(implicit lang: Lang): Frag =
    span(cls := "anon")(trans.anonymousUser())

  def usernameOrId(userId: String) = lightUser(userId).fold(userId)(_.name)

  def showUsernameById(
      userIdOption: Option[String],
      rating: Option[Int] = none,
      withOnline: Boolean = true,
      withPowerTip: Boolean = true,
      withFlag: Boolean = true,
      withLink: Boolean = true,
      withModLink: Boolean = false,
  )(implicit lang: Lang): Frag =
    userIdOption.flatMap(lightUser).fold[Frag](anonSpan) { user =>
      showUsernameLight(
        user,
        rating = rating,
        withOnline = withOnline,
        withPowerTip = withPowerTip,
        withFlag = withFlag,
        withLink = withLink,
        withModLink = withModLink,
      )
    }

  def showUsernameLight(
      user: LightUser,
      rating: Option[Int] = none,
      withOnline: Boolean = true,
      withPowerTip: Boolean = true,
      withFlag: Boolean = true,
      withLink: Boolean = true,
      withModLink: Boolean = false,
  )(implicit lang: Lang): Tag =
    renderUsername(
      username = user.name,
      isPatron = user.isPatron,
      title = user.title,
      rating = rating,
      flag = user.countryCode,
      withOnline = withOnline,
      withPowerTip = withPowerTip,
      withFlag = withFlag,
      withLink = withLink,
      withModLink = withModLink,
    )

  def showUsername(
      user: User,
      rating: Option[Int] = none,
      withOnline: Boolean = true,
      withPowerTip: Boolean = true,
      withFlag: Boolean = true,
      withLink: Boolean = true,
      withModLink: Boolean = false,
  )(implicit lang: Lang): Tag =
    renderUsername(
      username = user.username,
      isPatron = user.isPatron,
      title = user.title.map(_.value),
      rating = rating,
      flag = user.countryCode,
      withOnline = withOnline,
      withPowerTip = withPowerTip,
      withFlag = withFlag,
      withLink = withLink,
      withModLink = withModLink,
    )

  private def renderUsername(
      username: String,
      isPatron: Boolean,
      title: Option[String],
      rating: Option[Int],
      flag: Option[String],
      withOnline: Boolean,
      withPowerTip: Boolean,
      withFlag: Boolean,
      withLink: Boolean,
      withModLink: Boolean,
  )(implicit lang: Lang): Tag = {
    val userCls = userClass(username, withOnline, withPowerTip)
    val isBot   = title has lila.user.Title.BOT.value
    val tag =
      if (withLink)
        a(cls := userCls, dataUserTitle := title, href := userUrl(username, mod = withModLink))
      else
        span(
          cls           := userCls,
          dataUserTitle := title,
          dataHref      := userUrl(username, mod = withModLink),
        )
    tag(
      withOnline ?? lineIcon(isPatron),
      if (isBot) botTag.some
      else rating.map(rankTag),
      username,
      flag.ifTrue(withFlag).map(flagImage),
    )
  }

  def userUrl(username: String, mod: Boolean) =
    s"""${routes.User.show(username)}${mod ?? "?mod"}"""

  protected def userClass(
      username: String,
      withOnline: Boolean,
      withPowerTip: Boolean = true,
  ): List[(String, Boolean)] =
    (withOnline ?? List(
      (if (isOnline(User.normalize(username))) "online" else "offline") -> true,
    )) ::: List(
      "user-link" -> true,
      "ulpt"      -> withPowerTip,
    )

  def userGameFilterTitle(u: User, nbs: UserInfo.NbGames, filter: GameFilter)(implicit
      lang: Lang,
  ): Frag =
    if (filter == GameFilter.Search) frag(br, trans.search.advancedSearch())
    else splitNumber(userGameFilterTitleNoTag(u, nbs, filter))

  def userGameFilterTitleNoTag(u: User, nbs: UserInfo.NbGames, filter: GameFilter)(implicit
      lang: Lang,
  ): String =
    filter match {
      case GameFilter.All      => trans.nbGames.pluralSameTxt(u.count.game)
      case GameFilter.Me       => nbs.withMe ?? trans.nbGamesWithYou.pluralSameTxt
      case GameFilter.Rated    => trans.nbRated.pluralSameTxt(u.count.rated)
      case GameFilter.Win      => trans.nbWins.pluralSameTxt(u.count.win)
      case GameFilter.Loss     => trans.nbLosses.pluralSameTxt(u.count.loss)
      case GameFilter.Draw     => trans.nbDraws.pluralSameTxt(u.count.draw)
      case GameFilter.Playing  => trans.nbPlaying.pluralSameTxt(nbs.playing)
      case GameFilter.Paused   => trans.nbAdjourned.pluralSameTxt(nbs.paused)
      case GameFilter.Bookmark => trans.nbBookmarks.pluralSameTxt(nbs.bookmark)
      case GameFilter.Imported => trans.nbImportedGames.pluralSameTxt(nbs.imported)
      case GameFilter.Search   => trans.search.advancedSearch.txt()
    }

  def describeUser(user: User)(implicit lang: Lang) = {
    val name      = user.username
    val nbGames   = user.count.game
    val createdAt = showEnglishDate(user.createdAt)
    val currentRating = user.perfs.bestPerf ?? { case (pt, perf) =>
      s" Current ${pt.trans} rating: ${perf.intRating}."
    }
    s"$name played $nbGames games since $createdAt.$currentRating"
  }

  val lineIcon: Frag = i(cls := "line")
  private def patronIcon(implicit lang: Lang): Frag =
    i(cls := "line patron", title := trans.patron.lishogiPatron.txt())
  private def lineIcon(patron: Boolean)(implicit lang: Lang): Frag =
    if (patron) patronIcon else lineIcon
  def lineIcon(user: LightUser)(implicit lang: Lang): Frag = lineIcon(user.isPatron)
  def lineIcon(user: User)(implicit lang: Lang): Frag      = lineIcon(user.isPatron)
}
