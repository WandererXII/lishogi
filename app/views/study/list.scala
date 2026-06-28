package views.html
package study

import controllers.routes
import play.api.mvc.Call

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.common.paginator.Paginator
import lila.study.Study.WithChaptersAndLiked
import lila.study.StudyPager.Lang
import lila.study.StudyPager.Order
import lila.study.StudyTopic
import lila.study.StudyTopics
import lila.user.User

object list {

  def all(pag: Paginator[WithChaptersAndLiked], langCode: String, order: Order)(implicit
      ctx: Context,
  ) =
    layout(
      title = trans.study.allStudies.txt(),
      active = "all",
      langCode = langCode,
      order = order,
      pag = pag,
      url = (l, o) => routes.Study.all(l, o),
      canonicalPath = lila.common.CanonicalPath(routes.Study.allDefault(1)).some,
    )

  def byOwner(pag: Paginator[WithChaptersAndLiked], langCode: String, order: Order, owner: User)(
      implicit ctx: Context,
  ) =
    layout(
      title = trans.study.studiesCreatedByX.txt(owner.titleUsername),
      active = "owner",
      langCode = langCode,
      order = order,
      pag = pag,
      searchFilter = s"owner:${owner.username}".some,
      url = (l, o) => routes.Study.byOwner(owner.username, l, o),
      canonicalPath = lila.common.CanonicalPath(routes.Study.byOwnerDefault(owner.username)).some,
    )

  def mine(
      pag: Paginator[WithChaptersAndLiked],
      langCode: String,
      order: Order,
      me: User,
      topics: StudyTopics,
  )(implicit
      ctx: Context,
  ) =
    layout(
      title = trans.study.myStudies.txt(),
      active = "mine",
      langCode = langCode,
      order = order,
      pag = pag,
      searchFilter = s"owner:${me.username}".some,
      url = (l, o) => routes.Study.mine(l, o),
      topics = topics.some,
    )

  def mineLikes(
      pag: Paginator[WithChaptersAndLiked],
      langCode: String,
      order: Order,
  )(implicit ctx: Context) =
    layout(
      title = trans.study.myFavoriteStudies.txt(),
      active = "mineLikes",
      langCode = langCode,
      order = order,
      pag = pag,
      url = (l, o) => routes.Study.mineLikes(l, o),
    )

  def minePostGameStudies(
      pag: Paginator[WithChaptersAndLiked],
      langCode: String,
      order: Order,
  )(implicit ctx: Context) =
    layout(
      title = trans.postGameStudies.txt(),
      active = "postGameStudies",
      langCode = langCode,
      order = order,
      pag = pag,
      url = (l, o) => routes.Study.minePostGameStudies(l, o),
    )

  def postGameStudiesOf(
      gameId: String,
      pag: Paginator[WithChaptersAndLiked],
      langCode: String,
      order: Order,
  )(implicit ctx: Context) =
    layout(
      title = trans.postGameStudies.txt(),
      active = "postGameStudies",
      langCode = langCode,
      order = order,
      pag = pag,
      url = (l, o) => routes.Study.postGameStudiesOf(gameId, l, o),
      canonicalPath = lila.common.CanonicalPath(routes.Study.postGameStudiesOfDefault(gameId)).some,
    )

  def mineMember(
      pag: Paginator[WithChaptersAndLiked],
      langCode: String,
      order: Order,
      me: User,
      topics: StudyTopics,
  )(implicit
      ctx: Context,
  ) =
    layout(
      title = trans.study.studiesIContributeTo.txt(),
      active = "mineMember",
      langCode = langCode,
      order = order,
      pag = pag,
      searchFilter = s"member:${me.username}".some,
      url = (l, o) => routes.Study.mineMember(l, o),
      topics = topics.some,
    )

  def minePublic(pag: Paginator[WithChaptersAndLiked], langCode: String, order: Order, me: User)(
      implicit ctx: Context,
  ) =
    layout(
      title = trans.study.myPublicStudies.txt(),
      active = "minePublic",
      langCode = langCode,
      order = order,
      pag = pag,
      searchFilter = s"owner:${me.username}".some,
      url = (l, o) => routes.Study.minePublic(l, o),
    )

  def minePrivate(pag: Paginator[WithChaptersAndLiked], langCode: String, order: Order, me: User)(
      implicit ctx: Context,
  ) =
    layout(
      title = trans.study.myPrivateStudies.txt(),
      active = "minePrivate",
      langCode = langCode,
      order = order,
      pag = pag,
      searchFilter = s"owner:${me.username}".some,
      url = (l, o) => routes.Study.minePrivate(l, o),
    )

  def search(pag: Paginator[WithChaptersAndLiked], text: String)(implicit ctx: Context) =
    views.html.base.layout(
      title = text,
      moreCss = cssTag("analyse.study.index"),
      wrapClass = "full-screen-force",
      moreJs = infiniteScrollTag,
    ) {
      main(cls := "page-menu")(
        menu("search", Lang.default, Order.default),
        main(cls := "page-menu__content study-index box")(
          div(cls := "box__top")(
            searchForm(trans.search.search.txt(), text),
            bits.newForm(),
          ),
          paginate(pag, routes.Study.search(text)),
        ),
      )
    }

  private[study] def paginate(pager: Paginator[WithChaptersAndLiked], url: Call)(implicit
      ctx: Context,
  ) =
    if (pager.currentPageResults.isEmpty)
      div(cls := "nostudies")(
        iconTag(Icons.study),
        p(trans.study.noneYet()),
      )
    else
      div(cls := "studies list infinitescroll")(
        pager.currentPageResults.map { s =>
          div(cls := "study paginated")(bits.widget(s))
        },
        pagerNext(pager, np => addQueryParameter(url.url, "page", np)),
      )

  private[study] def menu(
      active: String,
      langCode: String,
      order: Order,
      topics: List[StudyTopic] = Nil,
  )(implicit
      ctx: Context,
  ) = {
    val nonMineOrder = if (order == Order.Mine) Order.Hot else order
    st.aside(cls := "page-menu__menu subnav")(
      a(cls := active.active("all"), href := routes.Study.all(langCode, nonMineOrder.key))(
        trans.study.allStudies(),
      ),
      ctx.isAuth option bits.authLinks(active, langCode, nonMineOrder),
      div(cls := "sep"),
      a(
        cls  := List("active" -> active.startsWith("topic"), "topic-menu" -> true),
        href := routes.Study.topics(langCode),
      )(
        trans.topics(),
      ),
      topics.map { topic =>
        a(
          cls  := active.active(s"topic:$topic"),
          href := routes.Study.byTopic(topic.value, langCode, order.key),
        )(
          topic.value,
        )
      },
    )
  }

  private[study] def searchForm(placeholder: String, value: String) =
    form(cls := "search", action := routes.Study.search(), method := "get")(
      input(name       := "q", st.placeholder := placeholder, st.value := value),
      submitButton(cls := "button", dataIcon  := Icons.search),
    )

  private def layout(
      title: String,
      active: String,
      langCode: String,
      order: Order,
      pag: Paginator[WithChaptersAndLiked],
      url: (String, String) => Call,
      searchFilter: Option[String] = None,
      topics: Option[StudyTopics] = None,
      canonicalPath: Option[lila.common.CanonicalPath] = None,
  )(implicit ctx: Context) =
    views.html.base.layout(
      title = title,
      moreCss = cssTag("analyse.study.index"),
      wrapClass = "full-screen-force",
      moreJs = infiniteScrollTag,
      canonicalPath = canonicalPath,
    ) {
      main(cls := "page-menu")(
        menu(active, langCode, order, topics.??(_.value)),
        main(cls := "page-menu__content study-index box")(
          div(cls := "box__top")(
            searchForm(searchFilter.isDefined ?? title, searchFilter.fold("") { sf => s"$sf " }),
            bits.orderSelect(langCode, order, active, url),
            bits.langSelect(langCode, order, url),
            bits.newForm(),
          ),
          topics map { ts =>
            div(cls := "box__pad")(
              views.html.study.topic.topicsList(ts, langCode, Order.Mine),
            )
          },
          paginate(pag, url(langCode, order.key)),
        ),
      )
    }
}
