package views.html
package coach

import controllers.routes

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.i18n.LangList

object widget {

  import trans.coach._

  def titleName(c: lila.coach.Coach.WithUser) =
    frag(
      c.user.title.map { t =>
        s"$t "
      },
      c.user.realNameOrUsername,
    )

  def pic(c: lila.coach.Coach.WithUser, size: Int) =
    c.coach.picturePath
      .map { path =>
        img(
          width  := size,
          height := size,
          cls    := "picture",
          src    := dbImageUrl(path.value),
          alt    := s"${c.user.titleUsername} Lishogi coach picture",
        )
      }
      .getOrElse {
        img(
          width  := size,
          height := size,
          cls    := "default picture",
          src    := staticUrl("images/placeholder.png"),
          alt    := "Default Lishogi coach picture",
        )
      }

  def apply(c: lila.coach.Coach.WithUser, link: Boolean)(implicit ctx: Context) = {
    val profile = c.user.profileOrDefault
    frag(
      link option a(cls := "overlay", href := routes.Coach.show(c.user.username)),
      pic(c, if (link) 300 else 350),
      div(cls := "overview")(
        (if (link) h2 else h1) (cls := "coach-name")(titleName(c)),
        c.coach.profile.headline
          .map { h =>
            p(
              cls := s"headline ${if (h.sizeIs < 60) "small"
                else if (h.sizeIs < 120) "medium"
                else "large"}",
            )(h)
          },
        table(
          tbody(
            tr(
              th(location()),
              td(
                profile.nonEmptyLocation.map { l =>
                  span(cls := "location")(l)
                },
                profile.countryInfo.map { country =>
                  frag(
                    span(cls := "country")(
                      img(cls := "flag", src := staticUrl(s"images/flags/${country.code}.png")),
                      " ",
                      country.name,
                    ),
                  )
                },
              ),
            ),
            tr(cls := "languages")(
              th(languages()),
              td(c.coach.languages.map(LangList.name) mkString ", "),
            ),
            tr(cls := "rating")(
              th(rating()),
              td(
                a(href := routes.User.show(c.user.username))(
                  c.user.best8Perfs.take(6).filter(c.user.hasEstablishedRating).map {
                    showPerfRating(c.user, _)
                  },
                ),
              ),
            ),
            c.coach.profile.hourlyRate.map { r =>
              tr(cls := "rate")(
                th(hourlyRate()),
                td(r),
              )
            },
            tr(cls := "available")(
              th(availability()),
              td(
                if (c.coach.available.value) span(cls := "text", dataIcon := "E")(accepting())
                else span(cls := "text", dataIcon := "L")(notAccepting()),
              ),
            ),
            c.user.seenAt.map { seen =>
              tr(cls := "seen")(
                th,
                td(trans.lastSeenActive(momentFromNow(seen))),
              )
            },
          ),
        ),
      ),
    )
  }
}
