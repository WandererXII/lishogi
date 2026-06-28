package views.html
package study

import controllers.routes
import play.api.mvc.Call

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.study.StudyPager.Order

object bits {

  def orderSelect(langCode: String, order: Order, active: String, url: (String, String) => Call)(
      implicit ctx: Context,
  ) = {
    val orders =
      if (active == "all") Order.allButOldest
      else if (active startsWith "topic") Order.allWithMine
      else Order.all
    views.html.base.bits.mselect(
      "orders",
      span(order.name()),
      orders map { o =>
        a(href := url(langCode, o.key), cls := (order == o).option("current"))(o.name())
      },
    )
  }

  def langSelect(langCode: String, order: Order, url: (String, String) => Call)(implicit
      ctx: Context,
  ) = {
    views.html.base.bits.mselect(
      "langs",
      if (langCode == "all") trans.allLanguages.txt()
      else span(lila.i18n.LangList.nameByStr(langCode)),
      (("all", trans.allLanguages.txt()) :: lila.i18n.LangList.allByLangCodesSorted.toList) map {
        case (code, name) =>
          a(href := url(code, order.key), cls := (langCode == code).option("current"))(
            name,
          )
      },
    )
  }

  def newForm()(implicit ctx: Context) =
    postForm(cls := "new-study", action := routes.Study.create)(
      submitButton(
        cls      := "button button-green",
        dataIcon := Icons.createNew,
        title    := trans.study.createStudy.txt(),
      ),
    )

  def authLinks(active: String, langCode: String, order: Order)(implicit ctx: Context) = {
    def activeCls(c: String) = cls := (c == active).option("active")
    frag(
      a(activeCls("mine"), href := routes.Study.mine(langCode, order.key))(trans.study.myStudies()),
      a(activeCls("mineMember"), href := routes.Study.mineMember(langCode, order.key))(
        trans.study.studiesIContributeTo(),
      ),
      a(activeCls("minePublic"), href := routes.Study.minePublic(langCode, order.key))(
        trans.study.myPublicStudies(),
      ),
      a(activeCls("minePrivate"), href := routes.Study.minePrivate(langCode, order.key))(
        trans.study.myPrivateStudies(),
      ),
      a(activeCls("mineLikes"), href := routes.Study.mineLikes(langCode, order.key))(
        trans.study.myFavoriteStudies(),
      ),
      a(
        activeCls("postGameStudies"),
        href := routes.Study.minePostGameStudies(langCode, order.key),
      )(
        trans.postGameStudies(),
      ),
    )
  }

  def widget(s: lila.study.Study.WithChaptersAndLiked, tag: Tag = h2)(implicit ctx: Context) =
    frag(
      a(cls := "overlay", href := routes.Study.show(s.study.id.value), title := s.study.name.value),
      div(
        cls := "top",
      )(
        div(
          cls := "study__icon",
        )(
          spriteSvg("study", s.study.icon.getOrElse("study")),
        ),
        div(
          tag(cls := "study-name")(s.study.name.value),
          span(
            !s.study.isPublic option frag(
              iconTag(Icons.lock)(cls := "private", ariaTitle(trans.study.`private`.txt())),
              " ",
            ),
            iconTag(if (s.liked) Icons.heartFull else Icons.heartOutline),
            " ",
            s.study.likes.value,
            " - ",
            usernameOrId(s.study.ownerId),
            " - ",
            s.study.lang.map(l => s"${lila.i18n.LangList.nameByStr(l)} - "),
            momentFromNow(s.study.createdAt),
          ),
        ),
      ),
      div(cls := "body")(
        ol(cls := "chapters")(
          s.chapters.map { name =>
            li(cls := "text", dataIcon := Icons.circleOutline)(name.value)
          },
        ),
        ol(cls := "members")(
          s.study.members.members.values
            .take(4)
            .map { m =>
              li(
                cls      := "text",
                dataIcon := (if (m.canContribute) Icons.person else Icons.view),
              )(usernameOrId(m.id))
            }
            .toList,
        ),
      ),
    )

  def home(studies: List[lila.study.Study.MiniStudy]) =
    table(cls := "studies")(
      studies map { s =>
        tr(
          td(cls := "name")(
            a(cls := "text", href := routes.Study.show(s.id.value))(
              s.name,
            ),
          ),
          td(momentFromNow(s.createdAt)),
          td(dataIcon := Icons.heartFull, cls := "text")(s.likes.value),
        )
      },
    )
}
