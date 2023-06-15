package views.html.base

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._

import controllers.routes

object topnav {

  private def linkTitle(url: String, name: Frag)(implicit ctx: Context) =
    if (ctx.blind) h3(name) else a(href := url)(name)

  private def canSeeClasMenu(implicit ctx: Context) =
    ctx.hasClas || ctx.me.exists(u => u.hasTitle || u.roles.contains("ROLE_COACH"))

  def apply()(implicit ctx: Context) =
    st.nav(id := "topnav", cls := "hover")(
      st.section(
        linkTitle(
          langHref("/"),
          frag(
            span(cls := "play")(trans.play()),
            span(cls := "home")("lishogi.org beta")
          )
        ),
        div(role := "group")(
          if (ctx.noBot) a(href := s"${langHref("/?any")}#hook")(trans.createAGame())
          else a(href := s"${langHref("/?any")}#friend")(trans.playWithAFriend()),
          ctx.noBot option frag(
            a(href := langHref(routes.Tournament.home))(trans.arena.arenaTournaments()),
            a(href := langHref(routes.Simul.home))(trans.simultaneousExhibitions())
          )
        )
      ),
      ctx.noBot option st.section {
        val puzzleUrl = langHref(routes.Puzzle.home)
        frag(
          linkTitle(puzzleUrl, trans.puzzles()),
          div(role := "group")(
            a(href := puzzleUrl)(trans.puzzles()),
            a(href := langHref(routes.Puzzle.dashboard(30, "home")))(trans.puzzle.puzzleDashboard()),
            a(href := langHref(routes.Puzzle.show("tsume")))(trans.puzzleTheme.tsume())
            // a(cls := "new-feature")(href := langHref(routes.Storm.home))("Tsume Storm")
          )
        )
      },
      st.section {
        val learnUrl = langHref(routes.Learn.index)
        frag(
          linkTitle(learnUrl, trans.learnMenu()),
          div(role := "group")(
            ctx.noBot option frag(
              a(href := learnUrl)(trans.shogiBasics()),
              // a(href := langHref(routes.Practice.index))(trans.practice()),
              a(href := langHref(routes.Coordinate.home))(trans.coordinates.coordinates())
            ),
            a(href := langHref(routes.Study.allDefault(1)))(trans.studyMenu()),
            // ctx.noKid option a(href := routes.Coach.all(1))(trans.coaches()),
            canSeeClasMenu option a(href := langHref(routes.Clas.index))(trans.clas.lishogiClasses()),
            a(href := routes.Page.variantHome)(trans.variants())
          )
        )
      },
      st.section {
        val tvUrl = langHref(routes.Tv.index)
        frag(
          linkTitle(tvUrl, trans.watch()),
          div(role := "group")(
            a(href := tvUrl)("Lishogi TV"),
            a(href := langHref(routes.Tv.games))(trans.currentGames()),
            ctx.noKid option a(href := langHref(routes.Streamer.index()))(trans.streamersMenu()),
            // a(href := langHref(routes.Relay.index))(trans.broadcast.broadcasts()),
            ctx.noBot option a(href := langHref(routes.Video.index))(trans.videoLibrary())
          )
        )
      },
      st.section {
        val userUrl = langHref(routes.User.list)
        frag(
          linkTitle(userUrl, trans.community()),
          div(role := "group")(
            a(href := userUrl)(trans.players()),
            a(href := langHref(routes.PlayApi.botOnline))(trans.bots()),
            a(href := langHref(routes.Team.home()))(trans.team.teams()),
            ctx.noKid option a(href := routes.ForumCateg.index)(trans.forum()),
            ctx.me.exists(!_.kid) option a(href := langHref(routes.Plan.index))(trans.patron.donate())
          )
        )
      },
      st.section {
        val analysisUrl = langHref(routes.UserAnalysis.index)
        frag(
          linkTitle(analysisUrl, trans.tools()),
          div(role := "group")(
            a(href := analysisUrl)(trans.analysis()),
            // a(href := s"${routes.UserAnalysis.index}#explorer")(trans.openingExplorer()),
            a(href := langHref(routes.Editor.index))(trans.boardEditor()),
            a(href := langHref(routes.Importer.importGame))(trans.importGame()),
            a(href := langHref(routes.Search.index()))(trans.search.advancedSearch())
          )
        )
      }
    )
}
