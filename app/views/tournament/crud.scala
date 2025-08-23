package views.html
package tournament

import controllers.routes
import play.api.data.Form

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.common.paginator.Paginator
import lila.tournament.TimeControl
import lila.tournament.Tournament
import lila.tournament.crud.CrudForm

object crud {

  private def layout(
      title: String,
      evenMoreJs: Frag = emptyFrag,
      css: String = "user.mod.misc",
  )(
      body: Frag,
  )(implicit ctx: Context) =
    views.html.base.layout(
      title = title,
      moreCss = cssTag(css),
      moreJs = frag(
        flatpickrTag,
        evenMoreJs,
      ),
    ) {
      main(cls := "page-menu")(
        views.html.mod.menu("tour"),
        body,
      )
    }

  def create(form: Form[_])(implicit ctx: Context) =
    layout(
      title = "New tournament",
      css = "user.mod.form",
    ) {
      div(cls := "crud page-menu__content box box-pad")(
        h1("New tournament"),
        postForm(cls := "form3", action := routes.TournamentCrud.create)(inForm(form, none)),
      )
    }

  def edit(tour: Tournament, form: Form[_])(implicit ctx: Context) =
    layout(
      title = tour.trans,
      css = "user.mod.form",
    ) {
      div(cls := "crud edit page-menu__content box box-pad")(
        div(cls := "box__top")(
          h1(
            a(href := routes.Tournament.show(tour.id))(tour.trans),
            " ",
            span("Created by ", usernameOrId(tour.createdBy), " on ", showDate(tour.createdAt)),
          ),
          st.form(
            cls    := "box__top__actions",
            action := routes.TournamentCrud.cloneT(tour.id),
            method := "get",
          )(
            form3.submit("Clone", Icons.trophy.some, klass = "button-green"),
          ),
        ),
        standardFlash(),
        postForm(cls := "form3", action := routes.TournamentCrud.update(tour.id))(
          inForm(form, tour.some),
        ),
      )
    }

  private def inForm(form: Form[_], tour: Option[Tournament])(implicit ctx: Context) =
    frag(
      form3.split(
        form3.group(form("date"), frag("Start date ", strong(utcLink)), half = true)(
          form3.flatpickr(_, init = true),
        ),
        form3.group(
          form("name"),
          raw("Name"),
          help = raw("Keep it VERY short, so it fits on homepage").some,
          half = true,
        )(form3.input(_)),
      ),
      form3.split(
        form3.group(
          form("homepageHours"),
          raw(s"Hours on homepage (0 to ${CrudForm.maxHomepageHours})"),
          half = true,
          help = raw("Ask first").some,
        )(form3.input(_, typ = "number")),
        form3.group(form("image"), raw("Custom icon"), half = true)(
          form3.select(_, CrudForm.imageChoices),
        ),
      ),
      form3.group(
        form("headline"),
        raw("Homepage headline"),
        help = raw("Keep it VERY short, so it fits on homepage").some,
      )(form3.input(_)),
      form3
        .group(form("description"), raw("Full description"), help = raw("Link: [text](url)").some)(
          form3.textarea(_)(rows := 6),
        ),
      form3.split(
        form3.group(form("variant"), raw("Variant"), half = true) { f =>
          form3.select(f, translatedVariantChoices.map(x => x._1 -> x._2))
        },
        form3.group(form("minutes"), raw("Duration in minutes"), half = true)(
          form3.input(_, typ = "number"),
        ),
      ),
      form3.split(
        form3.group(form("clockTime"), raw("Clock time"), half = true)(
          form3.select(_, TimeControl.DataForm.clockTimes.map(ct => (ct, s"$ct minutes"))),
        ),
        form3.group(form("clockByoyomi"), raw("Clock Byoyomi"), half = true)(
          form3.select(_, TimeControl.DataForm.clockByoyomi.map(b => (b, s"$b seconds"))),
        ),
      ),
      form3.split(
        form3.group(form("clockIncrement"), raw("Clock increment"), half = true)(
          form3.select(_, TimeControl.DataForm.clockIncrements.map(i => (i, s"$i seconds"))),
        ),
        form3.group(form("periods"), raw("Number of byoyomi periods"), half = true)(
          form3.select(_, TimeControl.DataForm.periods.map(p => (p, s"$p periods"))),
        ),
      ),
      form3.split(
        form3.group(form("position"), trans.startPosition(), half = true)(
          tournament.form.startingPosition(_, tour),
        ),
        form3.checkbox(
          form("teamBattle"),
          raw("Team battle"),
          half = true,
        ),
      ),
      h2("Entry requirements"),
      tournament.form.conditionFields(form, new TourFields(form, tour), Nil, tour),
      form3.action(form3.submit(trans.apply())),
    )

  def index(tours: Paginator[Tournament])(implicit ctx: Context) =
    layout(
      title = "Tournament manager",
      evenMoreJs = infiniteScrollTag,
    ) {
      div(cls := "crud page-menu__content box")(
        div(cls := "box__top")(
          h1("Tournament manager"),
          div(cls := "box__top__actions")(
            a(
              cls      := "button button-green",
              href     := routes.TournamentCrud.form,
              dataIcon := Icons.createNew,
            ),
          ),
        ),
        table(cls := "slist slist-pad")(
          thead(
            tr(
              th(),
              th("Variant"),
              th("Clock"),
              th("Duration"),
              th(utcLink, " Date"),
              th(),
            ),
          ),
          tbody(cls := "infinitescroll")(
            tours.nextPage.map { n =>
              frag(
                tr(
                  th(cls := "pager none")(
                    a(rel := "next", href := routes.TournamentCrud.index(n))("Next"),
                  ),
                ),
                tr(),
              )
            },
            tours.currentPageResults.map { tour =>
              tr(cls := "paginated")(
                td(
                  a(href := routes.TournamentCrud.edit(tour.id))(
                    strong(tour.trans),
                    " ",
                    em(tour.spotlight.map(_.headline)),
                  ),
                ),
                td(tour.variant.name),
                td(tour.timeControl.show),
                td(tour.minutes, "m"),
                td(showDateTimeUTC(tour.startsAt), " ", momentFromNow(tour.startsAt)),
                td(
                  a(
                    href     := routes.Tournament.show(tour.id),
                    dataIcon := Icons.view,
                    title    := "View on site",
                  ),
                ),
              )
            },
          ),
        ),
      )
    }
}
