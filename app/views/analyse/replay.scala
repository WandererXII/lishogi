package views.html.analyse

import controllers.routes
import play.api.i18n.Lang
import play.api.libs.json.Json
import views.html.analyse.bits.dataPanel

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.game.Game
import lila.game.Pov
import lila.socket.Socket.SocketVersion

object replay {

  private[analyse] def titleOf(pov: Pov)(implicit lang: Lang) =
    s"${playerText(pov.game.sentePlayer)} vs ${playerText(pov.game.gotePlayer)}: ${trans.analysis.txt()}"

  def apply(
      pov: Pov,
      data: play.api.libs.json.JsObject,
      socketVersion: SocketVersion,
      kif: String,
      analysis: Option[lila.analyse.Analysis],
      analysisStarted: Boolean,
      simul: Option[lila.simul.Simul],
      userTv: Option[lila.user.User],
      chatOption: Option[lila.chat.UserChat.Mine],
      bookmarked: Boolean,
  )(implicit ctx: Context) = {

    import pov._

    val chatJson = chatOption map { c =>
      views.html.chat.json(
        c.chat,
        name = trans.spectatorRoom.txt(),
        timeout = c.timeout,
        withNoteAge = ctx.isAuth option game.secondsSinceCreation,
        public = true,
        resourceId = lila.chat.Chat.ResourceId(s"game/${c.chat.id}"),
        palantir = ctx.me.exists(_.canPalantir),
      )
    }

    val backToGame = ctx.me.flatMap(pov.game.player)

    bits.layout(
      title = titleOf(pov),
      moreCss = frag(
        cssTag("analyse.round"),
        ctx.blind option cssTag("round.nvui"),
        variantPieceSprite(pov.game.variant),
      ),
      moreJs = frag(
        ctx.blind option analyseNvuiTag,
        moduleJsTag(
          "analyse",
          Json.obj(
            "mode"          -> "replay",
            "data"          -> data,
            "socketVersion" -> socketVersion.value,
            "userId"        -> ctx.userId,
            "playerId" -> ctx.userId.flatMap(userId => pov.game.playerByUserId(userId).map(_.id)),
            "chat"     -> chatJson,
            "hunter"   -> isGranted(_.Hunter),
          ),
        ),
      ),
      openGraph = povOpenGraph(pov).some,
    )(
      frag(
        main(
          cls := s"analyse ${mainVariantClass(pov.game.variant)} ${(pov.game.hasClock || pov.game.players
              .exists(p => p.isAi || p.hasUser || p.name.exists(_ != "?"))) ?? " has-player-bars"}",
        )(
          st.aside(cls := "analyse__side")(
            views.html.game
              .side(
                pov,
                tour = none,
                simul = simul,
                userTv = userTv,
                backToGame = backToGame,
                bookmarked = bookmarked,
              ),
            chatOption.map(_ => views.html.chat.frag),
          ),
          div(cls := s"analyse__board main-board ${variantClass(pov.game.variant)}")(
            shogigroundEmpty(pov.game.variant, pov.color),
          ),
          div(cls := "analyse__tools")(div(cls := "ceval")),
          div(cls := "analyse__controls"),
          if (ctx.blind)
            div(cls := "blind-content none")(
              h2(s"DOWNLOAD"),
              a(
                href := s"${routes.Game.exportOne(game.id)}",
              )(trans.kif()),
              pov.game.variant.standard option a(
                href := s"${routes.Game.exportOne(game.id)}?csa=1",
              )(trans.csa()),
            )
          else
            frag(
              div(cls := "analyse__underboard")(
                div(cls := "analyse__underboard__menu tabs-horiz")(
                  game.analysable option
                    span(
                      cls       := "computer-analysis",
                      dataPanel := "computer-analysis",
                    )(trans.computerAnalysis()),
                  (!game.isNotationImport && !game.isCorrespondence && game.plies > 1) option
                    span(dataPanel := "move-times")(
                      trans.moveTimes(),
                    ),
                  span(dataPanel := "game-export")(trans.export()),
                ),
                div(cls := "analyse__underboard__panels")(
                  game.analysable option div(cls := "computer-analysis")(
                    if (analysis.isDefined || analysisStarted)
                      div(id := "acpl-chart-container")(canvas(id := "acpl-chart"))
                    else
                      postForm(
                        cls    := s"future-game-analysis${ctx.isAnon ?? " must-login"}",
                        action := routes.Analyse.requestAnalysis(gameId),
                      )(
                        submitButton(cls := "button text")(
                          span(cls := "is3 text", dataIcon := Icons.barChart)(
                            trans.requestAComputerAnalysis(),
                          ),
                        ),
                      ),
                  ),
                  div(cls := "move-times")(
                    (game.plies > 1 && !game.isNotationImport) option div(
                      id := "movetimes-chart-container",
                    )(
                      canvas(id := "movetimes-chart"),
                    ),
                  ),
                  div(cls := "game-export")(
                    div(cls := "form-group")(
                      label(cls := "form-label")("SFEN"),
                      input(
                        readonly,
                        spellcheck := false,
                        cls        := "form-control autoselect analyse__underboard__sfen",
                      ),
                    ),
                    div(cls := "downloads")(
                      div(cls := "game-notation")(
                        a(
                          dataIcon := Icons.download,
                          cls      := "button text",
                          href     := s"${routes.Game.exportOne(game.id)}",
                        )(trans.kif()),
                        a(
                          dataIcon := Icons.download,
                          cls      := s"button text${!(pov.game.variant.standard) ?? " disabled"}",
                          href     := s"${routes.Game.exportOne(game.id)}?csa=1",
                        )(trans.csa()),
                        form(cls := "notation-options")(
                          List(
                            ("clocks", trans.clock.txt(), pov.game.imported),
                            ("evals", trans.search.analysis.txt(), !pov.game.metadata.analysed),
                            ("shiftJis", "SHIFT-JIS", false),
                          ).map(v =>
                            label(
                              frag(
                                input(
                                  id    := s"notation-option_${v._1}",
                                  value := v._1,
                                  tpe   := "checkbox",
                                  cls   := "regular-checkbox",
                                )(v._3 option (st.disabled := true)),
                                v._2,
                              ),
                            ),
                          ),
                        ),
                      ),
                      div(cls := "game-other")(
                        a(
                          dataIcon := Icons.share,
                          cls := s"button text${!Game.gifVariants.contains(pov.game.variant) ?? " disabled"}",
                          target := "_blank",
                          href   := cdnUrl(routes.Export.gif(pov.gameId, pov.color.name).url),
                        )("GIF"),
                        a(
                          dataIcon := Icons.screenFull,
                          cls      := "button text embed-howto",
                          target   := "_blank",
                          title    := trans.embedInYourWebsite.txt(),
                        )(
                          "HTML",
                        ),
                        (game.isKifImport || game.isCsaImport) option a(
                          dataIcon := Icons.download,
                          cls      := "button text",
                          href := s"${routes.Game.exportOne(game.id)}?imported=1${game.isCsaImport ?? "&csa=1"}",
                        )(trans.downloadImported()),
                      ),
                    ),
                    div(cls := "kif form-group")(
                      label(cls := "form-label")(trans.kif()),
                      textarea(
                        readonly,
                        spellcheck := false,
                        cls        := "form-control autoselect",
                      )(raw(kif)),
                    ),
                  ),
                ),
              ),
              div(cls := "analyse__acpl"),
              views.html.chat.members,
            ),
        ),
      ),
    )
  }
}
