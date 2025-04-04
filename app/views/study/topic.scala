package views.html.study

import controllers.routes
import play.api.data.Form

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.common.paginator.Paginator
import lila.study.Order
import lila.study.Study.WithChaptersAndLiked
import lila.study.StudyTopic
import lila.study.StudyTopics

object topic {

  def index(popular: StudyTopics, mine: Option[StudyTopics], myForm: Option[Form[_]])(implicit
      ctx: Context,
  ) =
    views.html.base.layout(
      title = trans.study.studyTopics.txt(),
      moreCss = frag(cssTag("analyse.study.index"), cssTag("misc.form3"), cssTag("misc.tagify")),
      moreJs = frag(tagifyTag, jsTag("analyse.study-topic-form")),
      wrapClass = "full-screen-force",
    ) {
      main(cls := "page-menu")(
        views.html.study.list.menu("topic", Order.Mine, mine.??(_.value)),
        main(cls := "page-menu__content study-topics box box-pad")(
          h1(trans.study.studyTopics()),
          myForm.map { form =>
            postForm(cls := "form3", action := routes.Study.topics)(
              form3.group(form("topics"), trans.study.topicsDescription())(
                form3.textarea(_)(rows := 10),
              ),
              form3.submit(trans.save()),
            )
          },
          mine.filter(_.value.nonEmpty) map { topics =>
            frag(
              h2(trans.study.myTopics()),
              topicsList(topics, Order.Mine),
            )
          },
          h2(trans.study.popularTopics()),
          topicsList(popular),
        ),
      )
    }

  def show(
      topic: StudyTopic,
      pag: Paginator[WithChaptersAndLiked],
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
      val url    = (o: String) => routes.Study.byTopic(topic.value, o)
      main(cls := "page-menu")(
        views.html.study.list.menu(active, order, myTopics.??(_.value)),
        main(cls := "page-menu__content study-index box")(
          div(cls := "box__top")(
            h1(topic.value),
            bits.orderSelect(order, active, url),
            bits.newForm(),
          ),
          myTopics.ifTrue(order == Order.Mine) map { ts =>
            div(cls := "box__pad")(
              topicsList(ts, Order.Mine),
            )
          },
          views.html.study.list.paginate(pag, url(order.key)),
        ),
      )
    }

  def topicsList(topics: StudyTopics, order: Order = Order.default) =
    div(cls := "topic-list")(
      topics.value.map { t =>
        a(href := routes.Study.byTopic(t.value, order.key))(t.value)
      },
    )
}
