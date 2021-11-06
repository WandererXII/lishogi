package views.html.game

import lishogi.api.Context
import lishogi.app.templating.Environment._
import lishogi.app.ui.ScalatagsTemplate._
import lishogi.game.{ Game, Player, Pov }
import lishogi.user.Title

import controllers.routes

object bits {

  private val dataLastmove = attr("data-lastmove")

  def featuredJs(pov: Pov): Frag =
    frag(
      gameFenNoCtx(pov, tv = true),
      vstext(pov)(none)
    )

  def mini(pov: Pov)(implicit ctx: Context): Frag =
    a(href := gameLink(pov))(
      gameFen(pov, withLink = false),
      vstext(pov)(ctx.some)
    )

  def miniBoard(fen: shogi.format.FEN, color: shogi.Color = shogi.Sente): Frag =
    div(
      cls := "mini-board parse-fen cg-wrap is2d",
      dataColor := color.name,
      dataFen := fen.value
    )(cgWrapContent)

  def miniTag(fen: shogi.format.FEN, color: shogi.Color = shogi.Sente, lastMove: String = "")(tag: Tag): Tag =
    tag(
      cls := "mini-board parse-fen cg-wrap is2d",
      dataColor := color.name,
      dataFen := fen.value,
      dataLastmove := lastMove
    )(cgWrapContent)

  def gameIcon(game: Game): Char =
    game.perfType match {
      case _ if game.fromPosition         => '*'
      case _ if game.imported             => '/'
      case Some(p) if game.variant.exotic => p.iconChar
      case _ if game.hasAi                => 'n'
      case Some(p)                        => p.iconChar
      case _                              => '9'
    }

  def sides(
      pov: Pov,
      initialFen: Option[shogi.format.FEN],
      tour: Option[lishogi.tournament.TourAndTeamVs],
      cross: Option[lishogi.game.Crosstable.WithMatchup],
      simul: Option[lishogi.simul.Simul],
      userTv: Option[lishogi.user.User] = None,
      bookmarked: Boolean
  )(implicit ctx: Context) =
    div(
      side.meta(pov, initialFen, tour, simul, userTv, bookmarked = bookmarked),
      cross.map { c =>
        div(cls := "crosstable")(crosstable(ctx.userId.fold(c)(c.fromPov), pov.gameId.some))
      }
    )

  def variantLink(
      variant: shogi.variant.Variant,
      name: String,
      initialFen: Option[shogi.format.FEN] = None
  ) =
    a(
      cls := "variant-link",
      href := (variant match {
        case shogi.variant.Standard => "https://en.wikipedia.org/wiki/Shogi"
        case shogi.variant.FromPosition =>
          s"""${routes.Editor.index()}?fen=${initialFen.??(_.value.replace(' ', '_'))}"""
        case v => routes.Page.variant(v.key).url
      }),
      rel := "nofollow",
      target := "_blank",
      title := variant.title
    )(name)

  private def playerTitle(player: Player) =
    player.userId.flatMap(lightUser).flatMap(_.title) map Title.apply map { t =>
      span(cls := "title", dataBot(t), title := Title titleName t)(t.value)
    }

  def vstext(pov: Pov)(ctxOption: Option[Context]): Frag =
    span(cls := "vstext")(
      span(cls := "vstext__pl user-link")(
        playerUsername(pov.player, withRating = false, withTitle = false),
        br,
        playerTitle(pov.player) map { t =>
          frag(t, " ")
        },
        pov.player.rating,
        pov.player.provisional option "?"
      ),
      pov.game.clock map { c =>
        span(cls := "vstext__clock")(shortClockName(c.config))
      } orElse {
        ctxOption flatMap { implicit ctx =>
          pov.game.daysPerTurn map { days =>
            span(cls := "vstext__clock")(
              if (days == 1) trans.oneDay() else trans.nbDays.pluralSame(days)
            )
          }
        }
      },
      span(cls := "vstext__op user-link")(
        playerUsername(pov.opponent, withRating = false, withTitle = false),
        br,
        pov.opponent.rating,
        pov.opponent.provisional option "?",
        playerTitle(pov.opponent) map { t =>
          frag(" ", t)
        }
      )
    )
}
