package lila.pref

import play.api.libs.json._

object JsonView {

  implicit val customThemeWriter: OWrites[CustomTheme]           = Json.writes[CustomTheme]
  implicit val customBackgroundWriter: OWrites[CustomBackground] = Json.writes[CustomBackground]

  implicit val prefJsonWriter: OWrites[Pref] = OWrites[Pref] { p =>
    Json.obj(
      "background"         -> p.background,
      "bgImg"              -> p.bgImg,
      "customBackground"   -> p.customBackground,
      "theme"              -> p.theme,
      "customTheme"        -> p.customTheme,
      "pieceSet"           -> p.pieceSet,
      "chuPieceSet"        -> p.chuPieceSet,
      "kyoPieceSet"        -> p.kyoPieceSet,
      "dobutsuPieceSet"    -> p.dobutsuPieceSet,
      "soundSet"           -> p.soundSet,
      "clockSoundSet"      -> p.clockSoundSet,
      "notation"           -> p.notation,
      "takeback"           -> p.takeback,
      "moretime"           -> p.moretime,
      "clockAudible"       -> p.clockAudible,
      "clockTenths"        -> p.clockTenths,
      "clockSound"         -> p.clockSound,
      "premove"            -> p.premove,
      "boardLayout"        -> p.boardLayout,
      "animation"          -> p.animation,
      "clearHands"         -> p.clearHands,
      "handsBackground"    -> p.handsBackground,
      "follow"             -> p.follow,
      "highlightLastDests" -> p.highlightLastDests,
      "highlightCheck"     -> p.highlightCheck,
      "squareOverlay"      -> p.squareOverlay,
      "destination"        -> p.destination,
      "dropDestination"    -> p.dropDestination,
      "coords"             -> p.coords,
      "replay"             -> p.replay,
      "colorName"          -> p.colorName,
      "challenge"          -> p.challenge,
      "tourChallenge"      -> p.tourChallenge,
      "message"            -> p.message,
      "submitMove"         -> p.submitMove,
      "confirmResign"      -> p.confirmResign,
      "insightsShare"      -> p.insightsShare,
      "thickGrid"          -> p.thickGrid,
      "keyboardMove"       -> p.keyboardMove,
      "zen"                -> p.zen,
      "noFlags"            -> p.noFlags,
      "moveEvent"          -> p.moveEvent,
    )
  }
}
