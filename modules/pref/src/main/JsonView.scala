package lila.pref

import play.api.libs.json._

object JsonView {

  implicit val CustomThemeWriter = Json.writes[CustomTheme]

  implicit val prefJsonWriter = OWrites[Pref] { p =>
    Json.obj(
      "dark"               -> p.dark,
      "transp"             -> p.transp,
      "bgImg"              -> p.bgImgOrDefault,
      "theme"              -> p.theme,
      "customTheme"        -> p.customThemeOrDefault,
      "pieceSet"           -> p.pieceSet,
      "chuPieceSet"        -> p.chuPieceSet,
      "soundSet"           -> p.soundSet,
      "notation"           -> p.notation,
      "blindfold"          -> p.blindfold,
      "takeback"           -> p.takeback,
      "moretime"           -> p.moretime,
      "clockTenths"        -> p.clockTenths,
      "clockCountdown"     -> p.clockCountdown,
      "clockSound"         -> p.clockSound,
      "premove"            -> p.premove,
      "boardLayout"        -> p.boardLayout,
      "animation"          -> p.animation,
      "follow"             -> p.follow,
      "highlightLastDests" -> p.highlightLastDests,
      "highlightCheck"     -> p.highlightCheck,
      "squareOverlay"      -> p.squareOverlay,
      "destination"        -> p.destination,
      "dropDestination"    -> p.dropDestination,
      "coords"             -> p.coords,
      "replay"             -> p.replay,
      "challenge"          -> p.challenge,
      "message"            -> p.message,
      "coordColor"         -> p.coordColor,
      "submitMove"         -> p.submitMove,
      "confirmResign"      -> p.confirmResign,
      "insightShare"       -> p.insightShare,
      "keyboardMove"       -> p.keyboardMove,
      "zen"                -> p.zen,
      "moveEvent"          -> p.moveEvent
    )
  }
}
