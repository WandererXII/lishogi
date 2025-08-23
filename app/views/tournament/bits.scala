package views.html.tournament

import controllers.routes
import play.api.mvc.Call

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.tournament.Tournament
import lila.tournament.TournamentPager.Order

object bits {

  def orderSelect(order: Order, active: String, url: String => Call)(implicit ctx: Context) = {
    val all = if (active == "finished") Order.allButHot else Order.all
    views.html.base.bits.mselect(
      "orders",
      span(order.name()),
      all map { o =>
        a(href := url(o.key), cls := (order == o).option("current"))(o.name())
      },
    )
  }

  def notFound()(implicit ctx: Context) =
    views.html.base.layout(
      title = trans.tournamentNotFound.txt(),
    ) {
      main(cls := "page-small box box-pad")(
        h1(trans.tournamentNotFound()),
        p(trans.tournamentDoesNotExist()),
        p(trans.tournamentMayHaveBeenCanceled()),
        br,
        br,
        a(href := routes.Tournament.homeDefault(1))(trans.returnToTournamentsHomepage()),
      )
    }

  def enterable(tours: List[Tournament])(implicit ctx: Context) =
    table(cls := "tournaments")(
      tours map { tour =>
        tr(
          td(cls := "name")(
            a(
              cls      := s"text ${tour.format}",
              dataIcon := tournamentIcon(tour),
              href     := routes.Tournament.show(tour.id),
            )(
              tour.trans,
            ),
          ),
          td(momentFromNow(tour.startsAt)),
          td(dataIcon := Icons.person, cls := "text")(tour.nbPlayers),
        )
      },
    )

  def userPrizeDisclaimer =
    div(cls := "tour__prize")(
      "This tournament is NOT organized by Lishogi.",
      br,
      "If it has prizes, Lishogi is NOT responsible for paying them.",
    )

}
