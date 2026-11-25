package views.html
package tournament

import controllers.routes
import play.api.data.Field
import play.api.data.Form

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.tournament.Condition
import lila.tournament.TournamentForm
import lila.tournament.Format
import lila.tournament.TimeControl
import lila.tournament.Tournament

object form {

  def create(form: Form[_], myTeams: List[lila.hub.LightTeam])(implicit ctx: Context) =
    views.html.base.layout(
      title = trans.newTournament.txt(),
      moreCss = cssTag("tournament.form"),
      moreJs = frag(
        flatpickrTag,
        jsTag("tournament.form"),
      ),
    ) {
      val fields = new TourFields(form, none)
      main(cls := "page-small")(
        div(cls := "tour__form box box-pad")(
          h1(
            if (fields.isTeamBattle) "New Team Battle"
            else trans.createANewTournament(),
          ),
          postForm(cls := "form3 f-rt f-robin", action := routes.Tournament.create)(
            form3.globalError(form),
            allFieldsets(form, fields, teams = myTeams, tour = none),
            form3.actions(
              a(href := routes.Tournament.homeDefault(1))(trans.cancel()),
              form3.submit(trans.createANewTournament(), icon = Icons.trophy.some),
            ),
          ),
        ),
        a(href := routes.Tournament.help),
      )
    }

  def edit(tour: Tournament, form: Form[_], myTeams: List[lila.hub.LightTeam])(implicit
      ctx: Context,
  ) = {
    val title = s"${trans.edit.txt()} - ${tour.trans}"
    views.html.base.layout(
      title = title,
      moreCss = cssTag("tournament.form"),
      moreJs = frag(
        flatpickrTag,
        jsTag("tournament.form"),
      ),
    ) {
      val fields = new TourFields(form, tour.some)
      main(cls := "page-small")(
        div(cls := s"tour__form box box-pad${tour.isFinished ?? " tour-finished"}")(
          h1(title),
          postForm(cls := "form3", action := routes.Tournament.update(tour.id))(
            form3.globalError(form),
            allFieldsets(form, fields, teams = myTeams, tour = tour.some),
            form3.actions(
              a(href := routes.Tournament.show(tour.id))(trans.back()),
              form3.submit(trans.save(), icon = Icons.trophy.some),
            ),
          ),
          postForm(cls := "terminate", action := routes.Tournament.terminate(tour.id))(
            submitButton(
              dataIcon := Icons.warning,
              cls      := s"text button button-red confirm${tour.isFinished ?? " disabled"}",
            )(s"${trans.delete.txt()} - ${trans.notReversible.txt()}"),
          ),
        ),
      )
    }
  }

  def allFieldsets(
      form: Form[_],
      fields: TourFields,
      teams: List[lila.hub.LightTeam],
      tour: Option[Tournament],
  )(implicit ctx: Context) =
    frag(
      tourFields(fields),
      gameFields(fields),
      clocksFields(form, fields),
      conditionFields(form, fields, teams = teams, tour = tour),
      featuresFields(form),
      fields.isTeamBattle option form3.hidden(form("teamBattleByTeam")),
    )

  def tourFields(
      fields: TourFields,
  )(implicit ctx: Context) =
    form3.fieldset(trans.tournament.txt(), toggle = true.some)(
      form3.split(cls := "icon-name-format")(
        form3.split(cls := "icon-name form-half")(fields.icon, fields.name),
        fields.format,
      ),
      form3.split(fields.startDate, fields.finishDate),
      form3.split(fields.description),
    )

  def gameFields(
      fields: TourFields,
  )(implicit ctx: Context) =
    form3.fieldset(trans.games.txt(), toggle = true.some)(
      form3.split(fields.variant, fields.startPosition),
    )

  def clocksFields(
      form: Form[_],
      fields: TourFields,
  )(implicit ctx: Context) =
    form3.fieldset(trans.clock.txt(), toggle = true.some)(
      errMsg(form("timeControlSetup")),
      fields.clocks,
    )

  def conditionFields(
      form: Form[_],
      fields: TourFields,
      teams: List[lila.hub.LightTeam],
      tour: Option[Tournament],
  )(implicit ctx: Context) =
    form3.fieldset(
      "Entry conditions",
      toggle = tour
        .exists(t =>
          t.conditions.list.nonEmpty || t.candidatesOnly || t.maxPlayers.isDefined || t.isPrivate,
        )
        .some,
    )(
      errMsg(form("conditions")),
      form3.split(
        form3.checkbox(
          form("candidatesOnly"),
          trans.askToJoin(),
          help = frag("Players can only join after you approve their request").some,
          half = true,
          disabled = fields.isTeamBattle,
        ),
        fields.password,
      ),
      form3.split(
        form3.group(
          form("maxPlayers"),
          frag("Max players allowed to join"),
          help = frag("Cannot be higher than format default").some,
          half = true,
        )(form3.input(_)(autocomplete := "off")),
        form3.group(form("conditions.nbRatedGame.nb"), frag("Minimum rated games"), half = true)(
          form3.select(
            _,
            Condition.DataForm.nbRatedGames map {
              case 0 => (0, trans.noRestriction.txt())
              case x => (x, trans.moreThanNbRatedGames.txt(x))
            },
          ),
        ),
      ),
      form3.split(
        form3.group(form("conditions.minRating.rating"), frag("Minimum rating"), half = true)(
          form3.select(
            _,
            ("" :: Condition.DataForm.minRatings.map(_.toString)) map {
              case "" => ("", trans.noRestriction.txt())
              case x  => (x, x)
            },
          ),
        ),
        form3
          .group(form("conditions.maxRating.rating"), frag("Maximum weekly rating"), half = true)(
            form3.select(
              _,
              ("" :: Condition.DataForm.maxRatings.map(_.toString)) map {
                case "" => ("", trans.noRestriction.txt())
                case x  => (x, x)
              },
            ),
          ),
      ),
      (tour.isEmpty && teams.nonEmpty) option {
        val baseField = form("conditions.teamMember.teamId")
        val field = ctx.req.queryString get "team" flatMap (_.headOption) match {
          case None       => baseField
          case Some(team) => baseField.copy(value = team.some)
        }
        form3.group(field, frag("Only members of team"), half = true)(
          form3.select(_, List(("", "No Restriction")) ::: teams.map(_.pair)),
        )
      },
    )

  def featuresFields(form: Form[_])(implicit ctx: Context) =
    form3.fieldset(trans.features.txt(), toggle = false.some)(
      form3.split(
        form3.checkbox(
          form("proMode"),
          trans.proMode(),
          help = trans.proModeDescription().some,
          half = true,
        ),
        form3.checkbox(
          form("rated"),
          trans.rated(),
          help = raw("Games are rated<br>and impact players ratings").some,
        ),
        form3.hiddenFalse(form("rated")),
      ),
      form3.split(
        div(cls := "f-arena")(
          form3.checkbox(
            form("berserkable"),
            trans.arena.berserk(),
            help = frag("Let players halve their clock time to gain an extra point").some,
            half = true,
          ),
          form3.hiddenFalse(form("berserkable")),
          form3.checkbox(
            form("streakable"),
            "Streaks", // trans.arena.arenaStreaks(),
            help = frag("After 2 wins, consecutive wins grant 4 points instead of 2.").some,
            half = true,
          ),
          form3.hiddenFalse(form("streakable")),
        ),
      ),
      form3.split(
        form3.checkbox(
          form("hasChat"),
          trans.chatRoom(),
          help = frag("Let players discuss in a chat room").some,
          half = true,
        ),
        form3.hiddenFalse(form("hasChat")),
      ),
    )

  def startingPosition(field: Field, tour: Option[Tournament]) =
    form3.input(field)(
      tour.exists(t => !t.isCreated && t.position.isEmpty).option(disabled := true),
    )

  val positionInputHelp = frag(
    "Paste a valid SFEN to start every game from a given position.",
    br,
    "You can use the ",
    a(href := routes.Editor.index, target := "_blank")("board editor"),
    " to generate a SFEN position, then paste it here.",
    br,
    "Leave empty to start games from the normal initial position.",
  )
}

final private class TourFields(form: Form[_], tour: Option[Tournament])(implicit ctx: Context) {

  def isTeamBattle = tour.exists(_.isTeamBattle) || form("teamBattleByTeam").value.nonEmpty

  private def disabledAfterFinish = tour.exists(_.isFinished)
  private def disabledAfterStart  = tour.exists(!_.isCreated)
  private def disabledAfterCreate = tour.isDefined

  def format =
    form3.group(
      form("format"),
      trans.tourFormat(),
      half = true,
      help = frag(
        a(href := routes.Tournament.help)(
          s"${trans.tourFormat.txt()} - ${trans.faq.faqAbbreviation.txt()}",
        ),
      ).some,
    )(
      form3.select(
        _,
        Format.all.map(f => (f.key, f.trans)),
        disabled = disabledAfterCreate || isTeamBattle,
      ),
    )

  def icon =
    form3.group(
      form("icon"),
      trans.icon(),
    ) { f =>
      frag(
        form3.input(f, klass = "none"),
        div(id := "icon-selector", cls := "icon-select"),
      )
    }

  def name =
    form3.group(
      form("name"),
      trans.name(),
      help = frag(
        trans.safeTournamentName(),
        br,
        trans.inappropriateNameWarning(),
        br,
        trans.emptyTournamentAnimalName(),
      ).some,
    ) { f =>
      form3.input(f)
    }

  def variant =
    form3.group(form("variant"), trans.variant(), half = true)(
      form3.select(
        _,
        TournamentForm.validVariants.map(v => (v.id.toString, transKeyTxt(v.key))),
        disabled = disabledAfterCreate,
      ),
    )

  def startPosition =
    form3.group(
      form("position"),
      trans.startPosition(),
      klass = "position",
      half = true,
      help = tournament.form.positionInputHelp.some,
    )(
      views.html.tournament.form.startingPosition(_, tour),
    )

  def clocks =
    frag(
      form3.split(
        form3.group(form("timeControlSetup.timeControl"), trans.timeControl(), half = true)(
          form3.select(
            _,
            TimeControl.DataForm.timeControls.map(tc =>
              (
                tc,
                if (tc == TimeControl.RealTime.id) trans.realTime.txt()
                else trans.correspondence.txt(),
              ),
            ),
            disabled = disabledAfterCreate,
          ),
        ),
        form3
          .group(
            form("timeControlSetup.daysPerTurn"),
            trans.daysPerTurn(),
            klass = "f-corres",
            half = true,
          )(
            form3.select(
              _,
              TimeControl.DataForm.daysPerTurn.map(d => (d, trans.nbDays.pluralSameTxt(d))),
              disabled = disabledAfterStart,
            ),
          ),
        form3.group(
          form("timeControlSetup.clockTime"),
          trans.clockInitialTime(),
          klass = "f-rt",
          half = true,
        )(
          form3.select(
            _,
            TimeControl.DataForm.clockTimes.map { ct =>
              if (ct < 1) (ct, trans.nbSeconds.pluralSameTxt(ct * 60 toInt))
              else (ct, trans.nbMinutes.pluralSameTxt(ct.toInt))
            },
            disabled = disabledAfterStart,
          ),
        ),
      ),
      form3.split(
        form3.group(
          form("timeControlSetup.clockByoyomi"),
          trans.clockByoyomi(),
          klass = "f-rt",
          half = true,
        )(
          form3.select(
            _,
            TimeControl.DataForm.clockByoyomi.map(b => (b, trans.nbSeconds.pluralSameTxt(b))),
            disabled = disabledAfterStart,
          ),
        ),
        form3.group(
          form("timeControlSetup.periods"),
          trans.numberOfPeriods(),
          klass = "f-rt",
          half = true,
        )(
          form3.select(
            _,
            TimeControl.DataForm.periods.map(p => (p, p.toString)),
            disabled = disabledAfterStart,
          ),
        ),
      ),
      form3.split(
        form3.group(
          form("timeControlSetup.clockIncrement"),
          trans.clockIncrement(),
          klass = "f-rt",
          half = true,
        )(
          form3.select(
            _,
            TimeControl.DataForm.clockIncrements.map(i => (i, trans.nbSeconds.pluralSameTxt(i))),
            disabled = disabledAfterStart,
          ),
        ),
      ),
    )

  def startDate =
    form3.group(
      form("startDate"),
      trans.startDate(),
      half = true,
      help = !disabledAfterCreate option frag("Leave empty to start now"),
    )(form3.flatpickr(_, disabled = disabledAfterStart))

  def finishDate =
    frag(
      form3.group(form("minutes"), trans.duration(), klass = "f-arena", half = true)(
        form3.select(
          _,
          TournamentForm.minutes.map(m => (m, trans.nbMinutes.pluralSameTxt(m))),
          disabled = disabledAfterFinish || tour.exists(_.hasArrangements),
        ),
      ),
      form3.group(
        form("finishDate"),
        trans.endDate(),
        klass = "f-robin f-organized",
        half = true,
      )(form3.flatpickr(_, disabled = disabledAfterFinish || tour.exists(!_.hasArrangements))),
    )

  def description =
    form3.group(
      form("description"),
      trans.description(),
      help = frag(
        "Anything special you want to tell the participants? Try to keep it short.",
      ).some,
    )(form3.textarea(_)(rows := 2))

  def password =
    form3.group(
      form("password"),
      trans.password(),
      help = trans.makePrivateTournament().some,
      half = true,
    )(form3.input(_)(autocomplete := "off"))

}
