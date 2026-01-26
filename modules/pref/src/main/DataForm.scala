package lila.pref

import play.api.data.Forms._
import play.api.data._

import lila.common.Form.urlText

object DataForm {

  private def checkedNumber(choices: Seq[(Int)]) =
    number.verifying(choices contains _)

  private lazy val booleanNumber =
    number.verifying(Pref.BooleanPref.verify)

  val pref = Form(
    mapping(
      "display" -> mapping(
        "animation"          -> checkedNumber(Pref.Animation.choices),
        "coords"             -> checkedNumber(Pref.Coords.choices),
        "colorName"          -> checkedNumber(Pref.ColorName.choices),
        "clearHands"         -> booleanNumber,
        "handsBackground"    -> booleanNumber,
        "highlightLastDests" -> booleanNumber,
        "highlightCheck"     -> booleanNumber,
        "squareOverlay"      -> booleanNumber,
        "destination"        -> booleanNumber,
        "dropDestination"    -> booleanNumber,
        "replay"             -> checkedNumber(Pref.Replay.choices),
        "boardLayout"        -> checkedNumber(Pref.BoardLayout.choices),
        "zen"                -> optional(booleanNumber),
        "noFlags"            -> optional(booleanNumber),
        "noRanks"            -> optional(booleanNumber),
        "resizeHandle"       -> optional(checkedNumber(Pref.ResizeHandle.choices)),
      )(DisplayData.apply)(DisplayData.unapply),
      "behavior" -> mapping(
        "moveEvent"     -> optional(checkedNumber(Pref.MoveEvent.choices)),
        "premove"       -> booleanNumber,
        "takeback"      -> checkedNumber(Pref.Takeback.choices),
        "submitMove"    -> checkedNumber(Pref.SubmitMove.choices),
        "confirmResign" -> checkedNumber(Pref.ConfirmResign.choices),
        "keyboardMove"  -> optional(booleanNumber),
      )(BehaviorData.apply)(BehaviorData.unapply),
      "clock" -> mapping(
        "audible"  -> checkedNumber(Pref.ClockAudible.choices),
        "tenths"   -> checkedNumber(Pref.ClockTenths.choices),
        "sound"    -> booleanNumber,
        "moretime" -> checkedNumber(Pref.Moretime.choices),
      )(ClockData.apply)(ClockData.unapply),
      "follow"        -> booleanNumber,
      "challenge"     -> checkedNumber(Pref.Challenge.choices),
      "tourChallenge" -> checkedNumber(Pref.Challenge.tourChoices),
      "message"       -> checkedNumber(Pref.Message.choices),
      "studyInvite"   -> optional(checkedNumber(Pref.StudyInvite.choices)),
      "insightsShare" -> booleanNumber,
    )(PrefData.apply)(PrefData.unapply),
  )

  case class DisplayData(
      animation: Int,
      coords: Int,
      colorName: Int,
      clearHands: Int,
      handsBackground: Int,
      highlightLastDests: Int,
      highlightCheck: Int,
      squareOverlay: Int,
      destination: Int,
      dropDestination: Int,
      replay: Int,
      boardLayout: Int,
      zen: Option[Int],
      noFlags: Option[Int],
      noRanks: Option[Int],
      resizeHandle: Option[Int],
  )

  case class BehaviorData(
      moveEvent: Option[Int],
      premove: Int,
      takeback: Int,
      submitMove: Int,
      confirmResign: Int,
      keyboardMove: Option[Int],
  )

  case class ClockData(
      audible: Int,
      tenths: Int,
      sound: Int,
      moretime: Int,
  )

  case class PrefData(
      display: DisplayData,
      behavior: BehaviorData,
      clock: ClockData,
      follow: Int,
      challenge: Int,
      tourChallenge: Int,
      message: Int,
      studyInvite: Option[Int],
      insightsShare: Int,
  ) {

    def apply(pref: Pref) =
      pref.copy(
        takeback = behavior.takeback,
        moretime = clock.moretime,
        clockAudible = clock.audible,
        clockTenths = clock.tenths,
        clockSound = clock.sound == 1,
        follow = follow == 1,
        highlightLastDests = display.highlightLastDests == 1,
        highlightCheck = display.highlightCheck == 1,
        squareOverlay = display.squareOverlay == 1,
        destination = display.destination == 1,
        dropDestination = display.dropDestination == 1,
        replay = display.replay,
        colorName = display.colorName,
        challenge = challenge,
        tourChallenge = tourChallenge,
        message = message,
        studyInvite = studyInvite | Pref.default.studyInvite,
        premove = behavior.premove == 1,
        boardLayout = display.boardLayout,
        animation = display.animation,
        coords = display.coords,
        clearHands = display.clearHands == 1,
        handsBackground = display.handsBackground == 1,
        submitMove = behavior.submitMove,
        insightsShare = insightsShare == 1,
        confirmResign = behavior.confirmResign,
        keyboardMove = behavior.keyboardMove | pref.keyboardMove,
        zen = display.zen | pref.zen,
        noFlags = display.noFlags | pref.noFlags,
        noRanks = display.noRanks | pref.noRanks,
        resizeHandle = display.resizeHandle | pref.resizeHandle,
        moveEvent = behavior.moveEvent | pref.moveEvent,
      )
  }

  object PrefData {
    def apply(pref: Pref): PrefData =
      PrefData(
        display = DisplayData(
          highlightLastDests = if (pref.highlightLastDests) 1 else 0,
          highlightCheck = if (pref.highlightCheck) 1 else 0,
          squareOverlay = if (pref.squareOverlay) 1 else 0,
          destination = if (pref.destination) 1 else 0,
          dropDestination = if (pref.dropDestination) 1 else 0,
          boardLayout = pref.boardLayout,
          animation = pref.animation,
          coords = pref.coords,
          clearHands = if (pref.clearHands) 1 else 0,
          handsBackground = if (pref.handsBackground) 1 else 0,
          replay = pref.replay,
          colorName = pref.colorName,
          zen = pref.zen.some,
          noFlags = pref.noFlags.some,
          noRanks = pref.noRanks.some,
          resizeHandle = pref.resizeHandle.some,
        ),
        behavior = BehaviorData(
          moveEvent = pref.moveEvent.some,
          premove = if (pref.premove) 1 else 0,
          takeback = pref.takeback,
          submitMove = pref.submitMove,
          confirmResign = pref.confirmResign,
          keyboardMove = pref.keyboardMove.some,
        ),
        clock = ClockData(
          audible = pref.clockAudible,
          tenths = pref.clockTenths,
          sound = if (pref.clockSound) 1 else 0,
          moretime = pref.moretime,
        ),
        follow = if (pref.follow) 1 else 0,
        challenge = pref.challenge,
        tourChallenge = pref.tourChallenge,
        message = pref.message,
        studyInvite = pref.studyInvite.some,
        insightsShare = if (pref.insightsShare) 1 else 0,
      )
  }

  def prefOf(p: Pref): Form[PrefData] = pref fill PrefData(p)

  val theme = Form(
    single(
      "theme" -> text.verifying(Theme contains _),
    ),
  )

  val pieceSet = Form(
    single(
      "set" -> text.verifying(PieceSet contains _),
    ),
  )

  val chuPieceSet = Form(
    single(
      "set" -> text.verifying(ChuPieceSet contains _),
    ),
  )

  val kyoPieceSet = Form(
    single(
      "set" -> text.verifying(KyoPieceSet contains _),
    ),
  )

  val dobutsuPieceSet = Form(
    single(
      "set" -> text.verifying(DobutsuPieceSet contains _),
    ),
  )

  val soundSet = Form(
    single(
      "set" -> text.verifying(SoundSet contains _),
    ),
  )

  val clockSoundSet = Form(
    single(
      "set" -> text.verifying(ClockSoundSet contains _),
    ),
  )

  val bg = Form(
    single(
      "bg" -> text.verifying(Background contains _),
    ),
  )

  val bgImg = Form(
    single(
      "bgImg" -> urlText,
    ),
  )

  val thickGrid = Form(
    single(
      "thickGrid" -> text.verifying(Set("0", "1") contains _),
    ),
  )

  val zen = Form(
    single(
      "zen" -> text.verifying(Set("0", "1") contains _),
    ),
  )

  val notation = Form(
    single(
      "notation" -> text.verifying(Notations.allToString contains _),
    ),
  )

  val customTheme = Form(
    mapping(
      "boardColor" -> text(maxLength = 30),
      "boardImg"   -> urlText,
      "gridColor"  -> text(maxLength = 30),
      "gridWidth"  -> number.verifying(Set(0, 1, 2, 3) contains _),
      "handsColor" -> text(maxLength = 30),
      "handsImg"   -> urlText,
    )(CustomTheme.apply)(CustomTheme.unapply),
  )

  val customBackground = Form(
    mapping(
      "light"     -> boolean,
      "bgPage"    -> text(maxLength = 30),
      "bgImg"     -> urlText,
      "font"      -> text(maxLength = 30),
      "accent"    -> text(maxLength = 30),
      "primary"   -> text(maxLength = 30),
      "secondary" -> text(maxLength = 30),
      "brag"      -> text(maxLength = 30),
      "green"     -> text(maxLength = 30),
      "red"       -> text(maxLength = 30),
    )(CustomBackground.apply)(CustomBackground.unapply),
  )

}
