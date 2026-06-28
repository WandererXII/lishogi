package views.html

import controllers.routes

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.hub.actorApi.timeline._

object timeline {

  def entries(entries: Vector[lila.timeline.Entry])(implicit ctx: Context) =
    div(cls := "entries")(
      filterEntries(entries) map { entry =>
        div(cls := "entry")(timeline.entry(entry))
      },
    )

  def more(entries: Vector[lila.timeline.Entry])(implicit ctx: Context) =
    views.html.base.layout(
      title = trans.timeline.txt(),
      moreCss = cssTag("misc.slist"),
    )(
      main(cls := "timeline page-small box")(
        h1(trans.timeline()),
        table(cls := "slist slist-pad")(
          tbody(
            filterEntries(entries) map { e =>
              tr(td(entry(e)))
            },
          ),
        ),
      ),
    )

  private def filterEntries(entries: Vector[lila.timeline.Entry])(implicit ctx: Context) =
    if (ctx.noKid) entries
    else entries.filter(e => e.okForKid)

  private def entry(e: lila.timeline.Entry)(implicit ctx: Context) =
    frag(
      e.decode.map[Frag] {
        case Follow(u1, u2) =>
          trans.xStartedFollowingY(
            showUsernameById(u1.some, withOnline = false, withFlag = false),
            showUsernameById(u2.some, withOnline = false, withFlag = false),
          )
        case TeamJoin(userId, teamId) =>
          trans.xJoinedTeamY(
            showUsernameById(userId.some, withOnline = false, withFlag = false),
            teamLink(teamId, withIcon = false),
          )
        case TeamCreate(userId, teamId) =>
          trans.xCreatedTeamY(
            showUsernameById(userId.some, withOnline = false, withFlag = false),
            teamLink(teamId, withIcon = false),
          )
        case ForumPost(userId, _, topicName, postId) =>
          trans.xPostedInForumY(
            showUsernameById(userId.some, withOnline = false, withFlag = false),
            a(
              href  := routes.ForumPost.redirect(postId),
              title := topicName,
            )(shorten(topicName, 30)),
          )
        case TourJoin(userId, tourId, tourName) =>
          trans.xCompetesInY(
            showUsernameById(userId.some, withOnline = false, withFlag = false),
            a(href := routes.Tournament.show(tourId))(tourName),
          )
        case SimulCreate(userId, simulId, simulName) =>
          trans.xHostsY(
            showUsernameById(userId.some, withOnline = false, withFlag = false),
            a(href := routes.Simul.show(simulId))(simulName),
          )
        case SimulJoin(userId, simulId, simulName) =>
          trans.xJoinsY(
            showUsernameById(userId.some, withOnline = false, withFlag = false),
            a(href := routes.Simul.show(simulId))(simulName),
          )
        case GameEnd(playerId, opponent, win, perfKey) =>
          lila.rating.PerfType.byKey(perfKey) map { perf =>
            (win match {
              case Some(true)  => trans.victoryVsYInZ
              case Some(false) => trans.defeatVsYInZ
              case None        => trans.drawVsYInZ
            })(
              a(
                href := routes.Round.gameOrChallengeDefault(
                  playerId.take(lila.game.Game.gameIdSize),
                ),
                dataIcon := perf.icon,
                cls      := "text glpt",
              )(win match {
                case Some(true)  => trans.victory()
                case Some(false) => trans.defeat()
                case None        => trans.draw()
              }),
              showUsernameById(opponent, withOnline = false, withFlag = false),
              perf.trans,
            )
          }
        case StudyCreate(userId, studyId, studyName) =>
          trans.xCreatesStudyY(
            showUsernameById(userId.some, withOnline = false, withFlag = false),
            a(href := routes.Study.show(studyId))(studyName),
          )
        case StudyLike(userId, studyId, studyName) =>
          trans.xLikesY(
            showUsernameById(userId.some, withOnline = false, withFlag = false),
            a(href := routes.Study.show(studyId))(studyName),
          )
        case PlanStart(userId) =>
          a(href := routes.Plan.index)(
            trans.patron.xBecamePatron(
              showUsernameById(userId.some, withOnline = true, withFlag = false),
            ),
          )
        case StreamStart(id, name) =>
          views.html.streamer.bits
            .redirectLink(id)(cls := "text", dataIcon := Icons.mic)(trans.xStartedStreaming(name))
        case SystemNotification(text) =>
          div(cls := "text system-notification", dataIcon := Icons.cogs)(text)
        case ArticlePublished(author, id) =>
          a(cls := "text", href := routes.Article.showRedirect(id), dataIcon := Icons.article)(
            trans.article.newArticleByX(
              showUsernameById(author.some, withOnline = false, withFlag = false),
            ),
          )
      },
      " ",
      momentFromNow(e.date),
    )
}
