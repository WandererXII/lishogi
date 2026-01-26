package views.html.coach

import controllers.routes
import play.api.data.Form
import play.api.libs.json.Json

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.common.String.html.safeJsonValue
import lila.i18n.LangList

object edit {

  private val dataTab = attr("data-tab")

  private lazy val jsonLanguages = safeJsonValue {
    Json toJson LangList.allWithMissing.map { case (lang, langName) =>
      Json.obj(
        "code"  -> lang.code,
        "value" -> langName,
        "searchBy" -> List(
          lang.toLocale.getDisplayLanguage,
          lang.toLocale.getDisplayCountry,
        ).mkString(","),
      )
    }
  }

  def apply(c: lila.coach.Coach.WithUser, form: Form[_])(implicit
      ctx: Context,
  ) = {
    views.html.account.layout(
      title = s"${c.user.titleUsername} coach page",
      evenMoreCss = frag(cssTag("misc.coach.editor"), cssTag("misc.tagify")),
      evenMoreJs = frag(
        tagifyTag,
        jsTag("misc.coach-form"),
      ),
      active = "coach",
    )(
      div(cls := "account coach-edit box")(
        div(cls := "top")(
          div(cls := "picture_wrap")(
            a(
              cls  := "upload_picture",
              href := routes.Coach.picture,
              title := (if (c.coach.hasPicture)
                          trans.streamer.changePicture.txt()
                        else trans.streamer.uploadPicture.txt()),
            )(
              widget.pic(c, 250),
            ),
          ),
          div(cls := "overview")(
            h1(widget.titleName(c)),
            div(
              a(
                href     := routes.Coach.show(c.user.username),
                cls      := "button button-empty text",
                dataIcon := Icons.view,
              )(trans.preview()),
            ),
          ),
        ),
        postForm(cls := "box__pad form3 async", action := routes.Coach.edit)(
          div(cls := "tabs")(
            div(dataTab := "basics", cls := "active")(trans.coach.basicsTab()),
            div(dataTab := "texts")(trans.coach.textsTab()),
            div(dataTab := "contents")(trans.coach.contentsTab()),
          ),
          div(cls := "panels")(
            div(cls := "panel basics active")(
              form3.split(
                form3.checkbox(
                  form("listed"),
                  trans.coach.publishOnList(),
                  help = trans.coach.publishOnListHelp().some,
                  half = true,
                ),
                form3.checkbox(
                  form("available"),
                  trans.coach.availableForLessons(),
                  help = trans.coach.availableForLessonsHelp().some,
                  half = true,
                ),
              ),
              form3.group(
                form("profile.headline"),
                trans.coach.headline(),
                help =
                  raw("Just one sentence to make students want to choose you (3 to 170 chars)").some,
              )(form3.input(_)),
              form3.split(
                form3.group(
                  form("languages"),
                  trans.coach.languagesSpoken(),
                  help = trans.coach.languagesSpokenHelp().some,
                  half = true,
                )(
                  form3.input(_)(
                    data("all")   := jsonLanguages,
                    data("value") := c.coach.languages.mkString(","),
                  ),
                ),
                form3.group(
                  form("profile.hourlyRate"),
                  trans.coach.hourlyRate(),
                  help = trans.coach.hourlyRateHelp().some,
                  half = true,
                )(form3.input(_)),
              ),
            ),
            div(cls := "panel texts")(
              form3.group(
                form("profile.description"),
                trans.coach.whoAreYou(),
                help = trans.coach.whoAreYouHelp().some,
              )(form3.textarea(_)(rows := 8)),
              form3.group(
                form("profile.playingExperience"),
                trans.coach.playingExperience(),
                help = trans.coach.playingExperienceHelp().some,
              )(form3.textarea(_)(rows := 8)),
              form3.group(
                form("profile.teachingExperience"),
                trans.coach.teachingExperience(),
                help = trans.coach.teachingExperienceHelp().some,
              )(form3.textarea(_)(rows := 8)),
              form3.group(
                form("profile.otherExperience"),
                trans.coach.otherExperiences(),
                help = trans.coach.otherExperiencesHelp().some,
              )(form3.textarea(_)(rows := 8)),
              form3.group(
                form("profile.skills"),
                trans.coach.bestSkills(),
                help = trans.coach.bestSkillsHelp().some,
              )(form3.textarea(_)(rows := 8)),
              form3.group(
                form("profile.methodology"),
                trans.coach.teachingMethod(),
                help = trans.coach.teachingMethodHelp().some,
              )(form3.textarea(_)(rows := 8)),
            ),
            div(cls := "panel contents")(
              form3.group(
                form("profile.publicStudies"),
                trans.coach.publicStudies(),
                help = trans.coach.publicStudiesHelp().some,
              )(form3.textarea(_)()),
              form3.group(
                form("profile.youtubeChannel"),
                trans.coach.youtubeChannelUrl(),
              )(form3.input(_)),
              form3.group(
                form("profile.youtubeVideos"),
                trans.coach.youtubeVideos(),
                help = trans.coach.youtubeVideosHelp().some,
              )(form3.textarea(_)(rows := 6)),
            ),
          ),
          div(cls := "status text", dataIcon := Icons.correct)(trans.coach.profileSaved()),
        ),
      ),
    )
  }
}
