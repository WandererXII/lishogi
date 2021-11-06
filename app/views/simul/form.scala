package views.html.simul

import play.api.data.Form

import lishogi.api.Context
import lishogi.app.templating.Environment._
import lishogi.app.ui.ScalatagsTemplate._

import controllers.routes

object form {

  def apply(form: Form[lishogi.simul.SimulForm.Setup], teams: List[lishogi.hub.LightTeam])(implicit
      ctx: Context
  ) = {

    import lishogi.simul.SimulForm._

    views.html.base.layout(
      title = trans.hostANewSimul.txt(),
      moreCss = cssTag("simul.form")
    ) {
      main(cls := "box box-pad page-small simul-form")(
        h1(trans.hostANewSimul()),
        postForm(cls := "form3", action := routes.Simul.create())(
          br,
          p(trans.whenCreateSimul()),
          br,
          br,
          globalError(form),
          form3.group(form("name"), trans.name()) { f =>
            div(
              form3.input(f),
              " Simul",
              br,
              small(cls := "form-help")(trans.inappropriateNameWarning())
            )
          },
          form3.group(form("variant"), trans.simulVariantsHint()) { f =>
            frag(
              div(cls := "variants")(
                views.html.setup.filter.renderCheckboxes(
                  form,
                  "variants",
                  translatedVariantChoicesWithVariants,
                  checks = form.value.??(_.variants.map(_.toString).toSet)
                )
              ),
              errMsg(f)
            )
          },
          form3.split(
            form3.group(
              form("clockTime"),
              trans.clockInitialTime(),
              //help = trans.simulClockHint().some,
              half = true
            )(form3.select(_, clockTimeChoices)),
            form3.group(
              form("clockByoyomi"),
              trans.clockByoyomi(),
              half = true
            )(form3.select(_, clockByoyomiChoices))
          ),
          form3.split(
            form3.group(
              form("clockIncrement"),
              trans.clockIncrement(),
              half = true
            )(form3.select(_, clockIncrementChoices)),
            form3.group(
              form("periods"),
              trans.numberOfByoyomiPeriods(),
              half = true
            )(form3.select(_, periodsChoices))
          ),
          form3.split(
            form3.group(
              form("clockExtra"),
              trans.simulHostExtraTime(),
              help = trans.simulAddExtraTime().some,
              half = true
            )(
              form3.select(_, clockExtraChoices)
            ),
            form3.group(form("color"), trans.hostColorForEachGame(), half = true)(
              form3.select(_, colorChoices)
            )
          ),
          form3.split(
            (teams.size > 0) ?? {
              form3.group(form("team"), trans.onlyMembersOfTeam(), half = true)(
                form3.select(_, List(("", "No Restriction")) ::: teams.map(_.pair))
              )
            },
            form3.group(
              form("position"),
              trans.startPosition(),
              klass = "position",
              half = true,
              help = frag("Custom starting position only works with the standard variant.").some
            ) { field =>
              st.select(
                id := form3.id(field),
                st.name := field.name,
                cls := "form-control"
              )(
                option(
                  value := shogi.StartingPosition.initial.fen,
                  field.value.has(shogi.StartingPosition.initial.fen) option selected
                )(shogi.StartingPosition.initial.name),
                shogi.StartingPosition.categories.map { categ =>
                  optgroup(attr("label") := categ.name)(
                    categ.positions.map { v =>
                      option(value := v.fen, field.value.has(v.fen) option selected)(v.fullName)
                    }
                  )
                }
              )
            }
          ),
          form3.group(
            form("text"),
            trans.simulDescription(),
            help = trans.anythingTellParticipants().some
          )(form3.textarea(_)(rows := 10)),
          form3.actions(
            a(href := routes.Simul.home())(trans.cancel()),
            form3.submit(trans.hostANewSimul(), icon = "g".some)
          )
        )
      )
    }
  }
}
