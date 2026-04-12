package views.html.game

import controllers.routes

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.game.Crosstable

object crosstable {

  def apply(ct: Crosstable.WithMatchup, currentId: Option[String])(implicit ctx: Context): Frag =
    apply(ct.crosstable, ct.matchup, currentId)(ctx)

  def apply(ct: Crosstable, trueMatchup: Option[Crosstable.Matchup], currentId: Option[String])(
      implicit ctx: Context,
  ): Frag = {
    val matchup = trueMatchup.filter(_.users != ct.users)
    val matchupSepAt: Option[Int] = matchup map { m =>
      (ct.nbGames min Crosstable.maxGames) - m.users.nbGames
    }
    div(cls := "crosstable")(
      ct.fillSize > 0 option raw { s"""<fill style="flex:${ct.fillSize * 0.75} 1 auto"></fill>""" },
      ct.results.zipWithIndex.map { case (r, i) =>
        tag("povs")(
          cls := List(
            "sep"     -> matchupSepAt.has(i),
            "current" -> currentId.has(r.gameId),
          ),
        )(ct.users.toList.map { u =>
          val (linkClass, text) = r.winnerId match {
            case Some(w) if w == u.id => "glpt win"  -> iconTag(Icons.circleFull)
            case None                 => "glpt"      -> iconTag(Icons.triangle)
            case _                    => "glpt loss" -> iconTag(Icons.circleFull)
          }
          a(
            href := routes.Round.gameOrChallengeDefault(r.gameId),
            cls  := linkClass,
          )(text)
        })
      },
      matchup map { m =>
        div(cls := "crosstable__matchup", title := trans.currentMatchScore.txt())(
          ct.users.toList.map { u =>
            span(cls := m.users.winnerId.map(w => if (w == u.id) "win" else "loss"))(
              m.users.showScore(u.id),
            )
          },
        )
      },
      div(cls := "crosstable__users")(ct.users.toList.map { u =>
        showUsernameById(u.id.some, withOnline = false, withFlag = false)
      }),
      div(cls := "crosstable__score", title := trans.lifetimeScore.txt())(ct.users.toList.map { u =>
        span(cls := ct.users.winnerId.map(w => if (w == u.id) "win" else "loss"))(
          ct.showScore(u.id),
        )
      }),
    )
  }
}
