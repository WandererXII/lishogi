package views.html.game

import controllers.routes
import play.api.i18n.Lang

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.game.Game
import lila.game.Player
import lila.game.Pov

object bits {

  def miniWrap(pov: Pov, gameFrag: Frag)(implicit lang: Lang): Frag =
    frag(
      miniPlayer(pov.opponent),
      gameFrag,
      miniPlayer(pov.player),
    )

  def mini(pov: Pov)(implicit ctx: Context): Frag =
    a(href := gameLink(pov, ctx.me))(
      miniWrap(pov, gameSfen(pov, ctx.me, withLink = false)),
    )

  def miniBoard(
      sfen: shogi.format.forsyth.Sfen,
      color: shogi.Color = shogi.Sente,
      variant: shogi.variant.Variant = shogi.variant.Standard,
  ): Frag =
    div(
      cls         := s"mini-board parse-sfen ${variantClass(variant)}",
      dataColor   := color.name,
      dataSfen    := sfen.value,
      dataVariant := variant.key,
    )(shogigroundEmpty(variant, color))

  def gameIcon(game: Game): String =
    if (game.initialSfen.isDefined) Icons.position
    else if (game.imported) Icons.uploadCloud
    else if (game.hasAi) Icons.cogs
    else game.perfType.icon

  def sides(
      pov: Pov,
      tour: Option[lila.tournament.TourAndTeamVs],
      cross: Option[lila.game.Crosstable.WithMatchup],
      simul: Option[lila.simul.Simul],
      userTv: Option[lila.user.User] = None,
      bookmarked: Boolean,
  )(implicit ctx: Context) =
    div(
      side.meta(pov, tour, simul, userTv, bookmarked = bookmarked),
      cross.map { c =>
        div(cls := "crosstable")(crosstable(ctx.userId.fold(c)(c.fromPov), pov.gameId.some))
      },
    )

  def variantLink(
      variant: shogi.variant.Variant,
      perfType: Option[lila.rating.PerfType],
  )(implicit lang: Lang): Frag = {
    def link(
        href: String,
        title: String,
        name: String,
    ) = a(
      cls      := "variant-link",
      st.href  := href,
      rel      := "nofollow",
      target   := "_blank",
      st.title := title,
    )(name)

    if (!variant.standard)
      link(
        href = routes.Prismic.variant(variant.key).url,
        title = variantDescription(variant),
        name = variantName(variant),
      )
    else
      perfType match {
        case Some(pt) => span(pt.trans)
        case _        => variantName(variant)
      }
  }

  private def miniPlayer(player: Player)(implicit lang: Lang): Frag =
    div(cls := "mini-player user-link")(
      showPlayer(
        player,
        withRank = true,
        withOnline = false,
        withLink = false,
        withPowerTip = false,
        withRating = false,
        withDiff = false,
      ),
    )

}
