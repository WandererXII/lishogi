package lishogi.app
package actor

import akka.actor._

import lishogi.game.Pov
import views.{ html => V }

final private[app] class Renderer extends Actor {

  def receive = {

    case lishogi.tv.actorApi.RenderFeaturedJs(game) =>
      sender() ! V.game.bits.featuredJs(Pov first game).render

    case lishogi.tournament.Tournament.TournamentTable(tours) =>
      sender() ! V.tournament.bits.enterable(tours).render

    case lishogi.simul.actorApi.SimulTable(simuls) =>
      sender() ! V.simul.bits.allCreated(simuls)(lishogi.i18n.defaultLang).render

    case lishogi.puzzle.DailyPuzzle.Render(puzzle, fen, lastMove) =>
      sender() ! V.puzzle.bits.daily(puzzle, fen, lastMove).render

    case streams: lishogi.streamer.LiveStreams.WithTitles =>
      sender() ! V.streamer.bits.liveStreams(streams).render
  }
}
