package views.html
package study

import controllers.routes
import play.api.mvc.Call

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.common.paginator.Paginator
import lila.study.Order
import lila.study.Study.WithChaptersAndLiked
import lila.study.StudyTopic
import lila.study.StudyTopics
import lila.user.User

object list {

  def all(pag: Paginator[WithChaptersAndLiked], order: Order)(implicit ctx: Context) =
    layout(
      title = trans.study.allStudies.txt(),
      active = "all",
      order = order,
      pag = pag,
      url = o => routes.Study.all(o),
      canonicalPath = lila.common.CanonicalPath(routes.Study.allDefault(1)).some,
    )

  def byOwner(pag: Paginator[WithChaptersAndLiked], order: Order, owner: User)(implicit
      ctx: Context,
  ) =
    layout(
      title = trans.study.studiesCreatedByX.txt(owner.titleUsername),
      active = "owner",
      order = order,
      pag = pag,
      searchFilter = s"owner:${owner.username}".some,
      url = o => routes.Study.byOwner(owner.username, o),
      canonicalPath = lila.common.CanonicalPath(routes.Study.byOwnerDefault(owner.username)).some,
    )

  def mine(pag: Paginator[WithChaptersAndLiked], order: Order, me: User, topics: StudyTopics)(
      implicit ctx: Context,
  ) =
    layout(
      title = trans.study.myStudies.txt(),
      active = "mine",
      order = order,
      pag = pag,
      searchFilter = s"owner:${me.username}".some,
      url = o => routes.Study.mine(o),
      topics = topics.some,
    )

  def mineLikes(
      pag: Paginator[WithChaptersAndLiked],
      order: Order,
  )(implicit ctx: Context) =
    layout(
      title = trans.study.myFavoriteStudies.txt(),
      active = "mineLikes",
      order = order,
      pag = pag,
      url = o => routes.Study.mineLikes(o),
    )

  def minePostGameStudies(
      pag: Paginator[WithChaptersAndLiked],
      order: Order,
  )(implicit ctx: Context) =
    layout(
      title = trans.postGameStudies.txt(),
      active = "postGameStudies",
      order = order,
      pag = pag,
      url = o => routes.Study.minePostGameStudies(o),
    )

  def postGameStudiesOf(
      gameId: String,
      pag: Paginator[WithChaptersAndLiked],
      order: Order,
  )(implicit ctx: Context) =
    layout(
      title = trans.postGameStudies.txt(),
      active = "postGameStudies",
      order = order,
      pag = pag,
      url = o => routes.Study.postGameStudiesOf(gameId, o),
      canonicalPath = lila.common.CanonicalPath(routes.Study.postGameStudiesOfDefault(gameId)).some,
    )

  def mineMember(pag: Paginator[WithChaptersAndLiked], order: Order, me: User, topics: StudyTopics)(
      implicit ctx: Context,
  ) =
    layout(
      title = trans.study.studiesIContributeTo.txt(),
      active = "mineMember",
      order = order,
      pag = pag,
      searchFilter = s"member:${me.username}".some,
      url = o => routes.Study.mineMember(o),
      topics = topics.some,
    )

  def minePublic(pag: Paginator[WithChaptersAndLiked], order: Order, me: User)(implicit
      ctx: Context,
  ) =
    layout(
      title = trans.study.myPublicStudies.txt(),
      active = "minePublic",
      order = order,
      pag = pag,
      searchFilter = s"owner:${me.username}".some,
      url = o => routes.Study.minePublic(o),
    )

  def minePrivate(pag: Paginator[WithChaptersAndLiked], order: Order, me: User)(implicit
      ctx: Context,
  ) =
    layout(
      title = trans.study.myPrivateStudies.txt(),
      active = "minePrivate",
      order = order,
      pag = pag,
      searchFilter = s"owner:${me.username}".some,
      url = o => routes.Study.minePrivate(o),
    )

  def search(pag: Paginator[WithChaptersAndLiked], text: String)(implicit ctx: Context) =
    views.html.base.layout(
      title = text,
      moreCss = cssTag("analyse.study.index"),
      wrapClass = "full-screen-force",
      moreJs = infiniteScrollTag,
    ) {
      main(cls := "page-menu")(
        menu("search", Order.default),
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
        iconTag("4"),
        p(trans.study.noneYet()),
      )
    else
      div(cls := "studies list infinitescroll")(
        pager.currentPageResults.map { s =>
          div(cls := "study paginated")(bits.widget(s))
        },
        pagerNext(pager, np => addQueryParameter(url.url, "page", np)),
      )

  private[study] def menu(active: String, order: Order, topics: List[StudyTopic] = Nil)(implicit
      ctx: Context,
  ) = {
    val nonMineOrder = if (order == Order.Mine) Order.Hot else order
    st.aside(cls := "page-menu__menu subnav")(
      a(cls := active.active("all"), href := routes.Study.all(nonMineOrder.key))(
        trans.study.allStudies(),
      ),
      ctx.isAuth option bits.authLinks(active, nonMineOrder),
      a(cls := List("active" -> active.startsWith("topic")), href := routes.Study.topics)("Topics"),
      topics.map { topic =>
        a(
          cls  := active.active(s"topic:$topic"),
          href := routes.Study.byTopic(topic.value, order.key),
        )(
          topic.value,
        )
      },
    )
  }

  private[study] def searchForm(placeholder: String, value: String) =
    form(cls := "search", action := routes.Study.search(), method := "get")(
      input(name       := "q", st.placeholder := placeholder, st.value := value),
      submitButton(cls := "button", dataIcon  := "y"),
    )

  private def layout(
      title: String,
      active: String,
      order: Order,
      pag: Paginator[WithChaptersAndLiked],
      url: String => Call,
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
        menu(active, order, topics.??(_.value)),
        main(cls := "page-menu__content study-index box")(
          div(cls := "box__top")(
            searchForm(searchFilter.isDefined ?? title, searchFilter.fold("") { sf => s"$sf " }),
            bits.orderSelect(order, active, url),
            bits.newForm(),
          ),
          topics map { ts =>
            div(cls := "box__pad")(
              views.html.study.topic.topicsList(ts, Order.Mine),
            )
          },
          paginate(pag, url(order.key)),
        ),
      )
    }
}
