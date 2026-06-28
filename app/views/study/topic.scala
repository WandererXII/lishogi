package views.html.study

import controllers.routes
import play.api.data.Form

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.common.paginator.Paginator
import lila.study.Study.WithChaptersAndLiked
import lila.study.StudyPager.Order
import lila.study.StudyTopic
import lila.study.StudyTopics

object topic {

  def index(
      popular: StudyTopics,
      mine: Option[StudyTopics],
      myForm: Option[Form[_]],
      langCode: String,
  )(implicit
      ctx: Context,
  ) =
    views.html.base.layout(
      title = trans.study.studyTopics.txt(),
      moreCss = frag(cssTag("analyse.study.index"), cssTag("misc.form3"), cssTag("misc.tagify")),
      moreJs = frag(tagifyTag, jsTag("analyse.study-topic-form")),
      wrapClass = "full-screen-force",
    ) {
      main(cls := "page-menu")(
        views.html.study.list.menu("topic", langCode, Order.Mine, mine.??(_.value)),
        main(cls := "page-menu__content study-topics box box-pad")(
          h1(trans.study.studyTopics()),
          myForm.map { form =>
            postForm(cls := "form3", action := routes.Study.topics(langCode))(
              form3.group(form("topics"), trans.study.topicsDescription())(
                form3.textarea(_)(rows := 10),
              ),
              form3.submit(trans.save()),
            )
          },
          mine.filter(_.value.nonEmpty) map { topics =>
            frag(
              h2(trans.study.myTopics()),
              topicsList(topics, langCode, Order.Mine),
            )
          },
          h2(trans.study.popularTopics()),
          topicsList(popular, langCode),
        ),
      )
    }

  def show(
      topic: StudyTopic,
      pag: Paginator[WithChaptersAndLiked],
      langCode: String,
      order: Order,
      myTopics: Option[StudyTopics],
  )(implicit ctx: Context) =
    views.html.base.layout(
      title = topic.value,
      moreCss = cssTag("analyse.study.index"),
      wrapClass = "full-screen-force",
      moreJs = infiniteScrollTag,
    ) {
      val active = s"topic:$topic"
      val url    = (l: String, o: String) => routes.Study.byTopic(topic.value, l, o)
      main(cls := "page-menu")(
        views.html.study.list.menu(active, langCode, order, myTopics.??(_.value)),
        main(cls := "page-menu__content study-index box")(
          div(cls := "box__top")(
            h1(topic.value),
            bits.langSelect(langCode, order, url),
            bits.orderSelect(langCode, order, active, url),
            bits.newForm(),
          ),
          myTopics.ifTrue(order == Order.Mine) map { ts =>
            div(cls := "box__pad")(
              topicsList(ts, langCode, Order.Mine),
            )
          },
          views.html.study.list.paginate(pag, url(langCode, order.key)),
        ),
      )
    }

  def topicsList(topics: StudyTopics, langCode: String, order: Order = Order.default) =
    div(cls := "chip-list")(
      topics.value.map { t =>
        a(cls := "chip", href := routes.Study.byTopic(t.value, langCode, order.key))(t.value)
      },
    )
}
