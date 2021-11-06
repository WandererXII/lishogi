package views.html.simul

import lishogi.api.Context
import lishogi.app.templating.Environment._
import play.api.i18n.Lang
import lishogi.app.ui.ScalatagsTemplate._

import controllers.routes

object homeInner {

  def apply(
      pendings: List[lishogi.simul.Simul],
      createds: List[lishogi.simul.Simul],
      starteds: List[lishogi.simul.Simul],
      finisheds: List[lishogi.simul.Simul]
  )(implicit ctx: Context) =
    div(cls := "box")(
      h1(trans.simultaneousExhibitions()),
      table(cls := "slist slist-pad")(
        pendings.nonEmpty option frag(
          thead(
            tr(
              th("Your pending simuls"),
              th(cls := "host")(trans.host()),
              th(cls := "players")(trans.players())
            )
          ),
          tbody(
            pendings.map { sim =>
              tr(cls := "scheduled")(
                simTd(sim),
                simHost(sim),
                td(cls := "players text", dataIcon := "r")(sim.applicants.size)
              )
            }
          )
        ),
        thead(
          tr(
            th(trans.createdSimuls()),
            th(cls := "host")(trans.host()),
            th(cls := "players")(trans.players())
          )
        ),
        tbody(
          createds.map { sim =>
            tr(cls := "scheduled")(
              simTd(sim),
              simHost(sim),
              td(cls := "players text", dataIcon := "r")(sim.applicants.size)
            )
          },
          ctx.isAuth option tr(cls := "create")(
            td(colspan := "4")(
              a(href := routes.Simul.form(), cls := "action button text")(trans.hostANewSimul())
            )
          )
        ),
        starteds.nonEmpty option (
          frag(
            thead(
              tr(
                th(trans.eventInProgress()),
                th(cls := "host")(trans.host()),
                th(cls := "players")(trans.players())
              )
            ),
            starteds.map { sim =>
              tr(
                simTd(sim),
                simHost(sim),
                td(cls := "players text", dataIcon := "r")(sim.pairings.size)
              )
            }
          )
        ),
        thead(
          tr(
            th(trans.finished()),
            th(cls := "host")(trans.host()),
            th(cls := "players")(trans.players())
          )
        ),
        tbody(
          finisheds.map { sim =>
            tr(
              simTd(sim),
              simHost(sim),
              td(cls := "players text", dataIcon := "r")(sim.pairings.size)
            )
          }
        )
      )
    )

  private def simTd(sim: lishogi.simul.Simul) =
    td(cls := "header")(
      a(href := routes.Simul.show(sim.id))(
        span(cls := "name")(sim.fullName),
        bits.setup(sim)
      )
    )

  private def simHost(sim: lishogi.simul.Simul)(implicit lang: Lang) =
    td(cls := "host")(
      userIdLink(sim.hostId.some, withOnline = false),
      br,
      strong(sim.hostRating)
    )
}
