package views.html.search

import scala.util.chaining._

import play.api.data.Form

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.gameSearch.Query
import lila.gameSearch.Sorting

private object bits {

  import trans.search._

  class SearchBits(form: Form[_])(implicit ctx: Context) {

    def dataReqs =
      List("winner", "loser", "sente", "gote").map { f =>
        data(s"req-$f") := ~form("players")(f).value
      }

    def colors(hide: Boolean) =
      shogi.Color.all.map { color =>
        tr(cls := List(s"${color.name}User user-row" -> true, "none" -> hide))(
          th(label(`for` := form3.id(form("players")(color.name)))(standardColorName(color))),
          td(cls := "single")(
            st.select(
              id   := form3.id(form("players")(color.name)),
              name := form("players")(color.name).name,
            )(
              option(cls := "blank", value := ""),
            ),
          ),
        )
      }

    def winner(hide: Boolean) =
      form("players")("winner") pipe { field =>
        tr(cls := List("winner user-row" -> true, "none" -> hide))(
          th(label(`for` := form3.id(field))(trans.winner())),
          td(cls := "single")(
            st.select(id := form3.id(field), name := field.name)(
              option(cls := "blank", value := ""),
            ),
          ),
        )
      }

    def loser(hide: Boolean) =
      form("players")("loser") pipe { field =>
        tr(cls := List("loser user-row" -> true, "none" -> hide))(
          th(label(`for` := form3.id(field))(trans.search.loser())),
          td(cls := "single")(
            st.select(id := form3.id(field), name := field.name)(
              option(cls := "blank", value := ""),
            ),
          ),
        )
      }

    def rating = {
      val options = Query.averageRatings.map(v => (v, trans.averageRatingX.txt(v)))
      tr(
        th(
          label(
            trans.rating(),
            " ",
            span(cls := "help", title := "The average rating of both players")("(?)"),
          ),
        ),
        td(
          div(cls := "half")(
            from(),
            " ",
            form3.select(form("ratingMin"), options, "".some),
          ),
          div(cls := "half")(
            to(),
            " ",
            form3.select(form("ratingMax"), options, "".some),
          ),
        ),
      )
    }

    def hasAi =
      tr(
        th(
          label(`for` := form3.id(form("hasAi")))(
            trans.opponent(),
            " ",
            span(cls := "help", title := humanOrComputer.txt())("(?)"),
          ),
        ),
        td(cls := "single opponent")(
          form3.select(
            form("hasAi"),
            Query.hasAis.map(v => (v, if (v != 0) trans.computer.txt() else trans.human.txt())),
            "".some,
          ),
        ),
      )

    def aiLevel = {
      val options = Query.aiLevels.map(v => (v, trans.levelX.txt(v)))
      tr(cls := "aiLevel none")(
        th(label(trans.search.aiLevel())),
        td(
          div(cls := "half")(
            from(),
            " ",
            form3.select(form("aiLevelMin"), options, "".some),
          ),
          div(cls := "half")(to(), " ", form3.select(form("aiLevelMax"), options, "".some)),
        ),
      )
    }

    def source =
      tr(
        th(label(`for` := form3.id(form("source")))(trans.search.source())),
        td(cls := "single")(
          form3.select(
            form("source"),
            Query.sources.map(v =>
              (v, sourceName(lila.game.Source(v).getOrElse(lila.game.Source.Lobby))),
            ),
            "".some,
          ),
        ),
      )

    def perf =
      tr(
        th(label(`for` := form3.id(form("perf")))(trans.variant())),
        td(cls := "single")(
          form3.select(
            form("perf"),
            lila.rating.PerfType.nonPuzzle map { pt =>
              pt.id -> pt.trans
            },
            "".some,
          ),
        ),
      )

    def mode =
      tr(
        th(label(`for` := form3.id(form("mode")))(trans.mode())),
        td(cls := "single")(
          form3.select(
            form("mode"),
            Query.modes.map(v => (v, modeName(shogi.Mode.orDefault(v)))),
            "".some,
          ),
        ),
      )

    def turns = {
      val options = Query.plies.map(v => (v, trans.nbMoves.txt(v)))
      tr(
        th(label(nbTurns())),
        td(
          div(cls := "half")(from(), " ", form3.select(form("pliesMin"), options, "".some)),
          div(cls := "half")(to(), " ", form3.select(form("pliesMax"), options, "".some)),
        ),
      )
    }

    def duration = {
      val options = translatedSeconds(Query.clockInits)
      tr(
        tr(
          th(label(trans.duration())),
          td(
            div(cls := "half")(
              from(),
              " ",
              form3.select(form("durationMin"), options, "".some),
            ),
            div(cls := "half")(
              to(),
              " ",
              form3.select(form("durationMax"), options, "".some),
            ),
          ),
        ),
      )
    }

    def clockTime = {
      val options = translatedSeconds(Query.clockInits)
      tr(
        th(label(trans.clockInitialTime())),
        td(
          div(cls := "half")(
            from(),
            " ",
            form3.select(form("clock")("initMin"), options, "".some),
          ),
          div(cls := "half")(
            to(),
            " ",
            form3.select(form("clock")("initMax"), options, "".some),
          ),
        ),
      )
    }

    def clockIncrement = {
      val options = translatedSeconds(Query.clockIncs, forceSeconds = true)
      tr(
        th(label(trans.clockIncrement())),
        td(
          div(cls := "half")(
            from(),
            " ",
            form3.select(form("clock")("incMin"), options, "".some),
          ),
          div(cls := "half")(
            to(),
            " ",
            form3.select(form("clock")("incMax"), options, "".some),
          ),
        ),
      )
    }

    def clockByoyomi = {
      val options = translatedSeconds(Query.clockByos, forceSeconds = true)
      tr(
        th(label(trans.clockByoyomi())),
        td(
          div(cls := "half")(
            from(),
            " ",
            form3.select(form("clock")("byoMin"), options, "".some),
          ),
          div(cls := "half")(
            to(),
            " ",
            form3.select(form("clock")("byoMax"), options, "".some),
          ),
        ),
      )
    }

    def status =
      tr(
        th(label(`for` := form3.id(form("status")))(result())),
        td(cls := "single")(
          form3.select(
            form("status"),
            Query.statuses.map(v =>
              (v, statusName(shogi.Status(v).getOrElse(shogi.Status.UnknownFinish))),
            ),
            "".some,
          ),
        ),
      )

    def winnerColor =
      tr(
        th(label(`for` := form3.id(form("winnerColor")))(trans.search.winnerColor())),
        td(cls := "single")(
          form3.select(
            form("winnerColor"),
            Query.winnerColors.map(v =>
              (v, Query.winnerColorsToColor(v).map(standardColorName).getOrElse("-")),
            ),
          ),
        ),
      )

    def date =
      tr(cls := "date")(
        th(label(trans.search.date())),
        td(
          div(cls := "half")(
            from(),
            " ",
            form3.flatpickr(form("dateMin"), init = true, noTime = true),
          ),
          div(cls := "half")(
            to(),
            " ",
            form3.flatpickr(form("dateMax"), init = true, noTime = true),
          ),
        ),
      )

    def sort =
      tr(
        th(label(trans.search.sortBy())),
        td(
          div(cls := "half")(
            form3.select(
              form("sort")("field"),
              Sorting.fields.map(v =>
                (
                  v,
                  v match {
                    case lila.gameSearch.Fields.date          => trans.search.date.txt()
                    case lila.gameSearch.Fields.plies         => trans.search.moves.txt()
                    case lila.gameSearch.Fields.averageRating => trans.rating.txt()
                  },
                ),
              ),
            ),
          ),
          div(cls := "half")(
            form3.select(
              form("sort")("order"),
              Sorting.orders.map(v =>
                (
                  v,
                  if (v == "desc") trans.search.descending.txt() else trans.search.ascending.txt(),
                ),
              ),
            ),
          ),
        ),
      )

    def analysed = {
      val field = form("analysed")
      tr(
        th(
          label(`for` := form3.id(field))(
            trans.search.analysis(),
            " ",
            span(cls := "help", title := onlyAnalysed.txt())("(?)"),
          ),
        ),
        td(cls := "single")(
          form3.cmnToggle(
            form3.id(field),
            field.name,
            checked = field.value.has("1"),
            value = "1",
          ),
        ),
      )
    }
  }

  def of(form: Form[_])(implicit ctx: Context) = new SearchBits(form)
}
