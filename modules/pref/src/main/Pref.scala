package lila.pref

case class Pref(
    _id: String, // user id
    background: String,
    bgImg: Option[String],
    customBackground: Option[CustomBackground],
    theme: String,
    customTheme: Option[CustomTheme],
    pieceSet: String,
    chuPieceSet: String,
    kyoPieceSet: String,
    dobutsuPieceSet: String,
    soundSet: String,
    clockSoundSet: String,
    takeback: Int,
    moretime: Int,
    clockAudible: Int,
    clockTenths: Int,
    clockSound: Boolean,
    premove: Boolean,
    boardLayout: Int,
    animation: Int,
    coords: Int,
    clearHands: Boolean,
    handsBackground: Boolean,
    follow: Boolean,
    highlightLastDests: Boolean,
    highlightCheck: Boolean,
    squareOverlay: Boolean,
    destination: Boolean,
    dropDestination: Boolean,
    replay: Int,
    colorName: Int,
    challenge: Int,
    tourChallenge: Int,
    message: Int,
    studyInvite: Int,
    submitMove: Int,
    confirmResign: Int,
    insightsShare: Boolean,
    thickGrid: Int,
    keyboardMove: Int,
    zen: Int,
    noFlags: Int,
    moveEvent: Int,
    notation: Int,
    resizeHandle: Int,
    tags: Map[String, String] = Map.empty,
) {

  import Pref._

  def id = _id

  def isLightBackground =
    (background == "light") || (background == "custom" && ~customBackground.map(_.light))

  def isNewBackground = background.endsWith("-new")

  def activeCustomTheme = (theme == "custom") option customThemeOrDefault

  def activeCustomBackground = (background == "custom") option customBackgroundOrDefault

  def activeBgImgUrl = bgImg
    .ifTrue(background == "transp")
    .orElse(activeCustomBackground.map(_.bgImg))
    .map(cssBackgroundImageValue)
    .filterNot(_ == "none")

  def coordsClass = Coords cssClassOf coords

  def hasSeenVerifyTitle = tags contains Tag.verifyTitle

  def set(name: String, value: String): Option[Pref] =
    name match {
      case "bg" =>
        Background.allByKey get value map { bg =>
          copy(background = bg.key)
        }
      case "bgImg" => copy(bgImg = value.some).some
      case "theme" =>
        Theme.allByKey get value map { t =>
          copy(theme = t.key)
        }
      case "pieceSet" =>
        PieceSet.allByKey get value map { p =>
          copy(pieceSet = p.key)
        }
      case "chuPieceSet" =>
        ChuPieceSet.allByKey get value map { p =>
          copy(chuPieceSet = p.key)
        }
      case "kyoPieceSet" =>
        KyoPieceSet.allByKey get value map { p =>
          copy(kyoPieceSet = p.key)
        }
      case "dobutsuPieceSet" =>
        DobutsuPieceSet.allByKey get value map { p =>
          copy(dobutsuPieceSet = p.key)
        }
      case "soundSet" =>
        SoundSet.allByKey get value map { s =>
          copy(soundSet = s.key)
        }
      case "clockSoundSet" =>
        ClockSoundSet.allByKey get value map { s =>
          copy(clockSoundSet = s.key)
        }
      case "thickGrid" => copy(thickGrid = if (value == "1") 1 else 0).some
      case "zen"       => copy(zen = if (value == "1") 1 else 0).some
      case "notation" =>
        value.toIntOption flatMap { index =>
          Notations.allByIndex get index map { n =>
            copy(notation = n.index)
          }
        }
      case _ => none
    }

  def animationMillis: Int =
    animation match {
      case Animation.NONE   => 0
      case Animation.FAST   => 120
      case Animation.NORMAL => 250
      case Animation.SLOW   => 500
      case _                => 250
    }

  def customThemeOrDefault = customTheme | CustomTheme.default

  def isUsingThickGrid = thickGrid == ThickGrid.YES

  def gridWidth = customThemeOrDefault.gridWidth

  def customBackgroundOrDefault = customBackground | CustomBackground.default

  def isZen = zen == Zen.YES

  def wantsNoFlags = noFlags == NoFlags.YES
}

object Pref {

  trait BooleanPref {
    val NO      = 0
    val YES     = 1
    val choices = Seq(NO, YES)
  }

  object BooleanPref {
    val verify = (v: Int) => v == 0 || v == 1
  }

  object Tag {
    val verifyTitle = "verifyTitle"
  }

  object SubmitMove {
    val NEVER                    = 0
    val CORRESPONDENCE_ONLY      = 4
    val CORRESPONDENCE_UNLIMITED = 1
    val ALWAYS                   = 2

    val choices = Seq(
      NEVER,
      CORRESPONDENCE_ONLY,
      CORRESPONDENCE_UNLIMITED,
      ALWAYS,
    )
  }

  object ConfirmResign extends BooleanPref

  object ThickGrid extends BooleanPref

  object KeyboardMove extends BooleanPref

  object MoveEvent {
    val CLICK = 0
    val DRAG  = 1
    val BOTH  = 2

    val choices = Seq(
      CLICK,
      DRAG,
      BOTH,
    )
  }

  object Takeback {
    val NEVER  = 1
    val CASUAL = 2
    val ALWAYS = 3

    val choices = Seq(
      NEVER,
      ALWAYS,
      CASUAL,
    )
  }

  object Moretime {
    val NEVER  = 1
    val CASUAL = 2
    val ALWAYS = 3

    val choices = Seq(
      NEVER,
      ALWAYS,
      CASUAL,
    )
  }

  object BoardLayout {
    val DEFAULT = 0
    val COMPACT = 1
    val SMALL   = 2

    val choices = Seq(
      DEFAULT,
      COMPACT,
      SMALL,
    )
  }

  object Animation {
    val NONE   = 0
    val FAST   = 1
    val NORMAL = 2
    val SLOW   = 3

    val choices = Seq(
      NONE,
      FAST,
      NORMAL,
      SLOW,
    )
  }

  object Coords {
    val NONE    = 0
    val INSIDE  = 1
    val OUTSIDE = 2
    val EDGE    = 3

    val choices = Seq(
      NONE,
      INSIDE,
      OUTSIDE,
      EDGE,
    )

    def cssClassOf(v: Int) =
      v match {
        case INSIDE  => "in"
        case OUTSIDE => "out"
        case EDGE    => "edge"
        case _       => "no"
      }
  }

  object Replay {
    val NEVER  = 0
    val SLOW   = 1
    val ALWAYS = 2

    val choices = Seq(
      NEVER,
      SLOW,
      ALWAYS,
    )
  }

  object ColorName {
    val LANG    = 0
    val SENTEJP = 1
    val SENTE   = 2
    val BLACK   = 3

    val choices = Seq(
      LANG,
      SENTEJP,
      SENTE,
      BLACK,
    )
  }

  object ClockAudible {
    val MINE   = 0
    val MYGAME = 1
    val ALL    = 2

    val choices = Seq(
      MINE,
      MYGAME,
      ALL,
    )
  }

  object ClockTenths {
    val NEVER   = 0
    val LOWTIME = 1
    val ALWAYS  = 2

    val choices = Seq(
      NEVER,
      LOWTIME,
      ALWAYS,
    )
  }

  object Challenge {
    val NEVER  = 1
    val RATING = 2
    val FRIEND = 3
    val ALWAYS = 4

    val ratingThreshold = 300

    val choices = Seq(
      NEVER,
      RATING,
      FRIEND,
      ALWAYS,
    )

    val tourChoices = Seq(
      NEVER,
      FRIEND,
      ALWAYS,
    )
  }

  object Message {
    val NEVER  = 1
    val FRIEND = 2
    val ALWAYS = 3

    val choices = Seq(
      NEVER,
      FRIEND,
      ALWAYS,
    )
  }

  object StudyInvite {
    val NEVER  = 1
    val FRIEND = 2
    val ALWAYS = 3

    val choices = Seq(
      NEVER,
      FRIEND,
      ALWAYS,
    )
  }

  object ResizeHandle {
    val NEVER   = 0
    val INITIAL = 1
    val ALWAYS  = 2

    val choices = Seq(
      NEVER,
      INITIAL,
      ALWAYS,
    )
  }

  object Zen extends BooleanPref

  object NoFlags extends BooleanPref

  def create(id: String) = default.copy(_id = id)

  lazy val default = Pref(
    _id = "",
    background = Background.default.key,
    bgImg = none,
    customBackground = none,
    theme = Theme.default.key,
    customTheme = none,
    pieceSet = PieceSet.default.key,
    chuPieceSet = ChuPieceSet.default.key,
    kyoPieceSet = KyoPieceSet.default.key,
    dobutsuPieceSet = DobutsuPieceSet.default.key,
    soundSet = SoundSet.default.key,
    clockSoundSet = ClockSoundSet.default.key,
    takeback = Takeback.ALWAYS,
    moretime = Moretime.ALWAYS,
    clockSound = true,
    premove = true,
    boardLayout = BoardLayout.DEFAULT,
    animation = 2,
    coords = Coords.OUTSIDE,
    clearHands = false,
    handsBackground = false,
    follow = true,
    highlightLastDests = true,
    highlightCheck = true,
    squareOverlay = true,
    destination = true,
    dropDestination = true,
    replay = Replay.ALWAYS,
    colorName = ColorName.LANG,
    clockAudible = ClockAudible.MINE,
    clockTenths = ClockTenths.LOWTIME,
    challenge = Challenge.ALWAYS,
    tourChallenge = Challenge.ALWAYS,
    message = Message.ALWAYS,
    studyInvite = StudyInvite.ALWAYS,
    submitMove = SubmitMove.CORRESPONDENCE_ONLY,
    confirmResign = ConfirmResign.YES,
    insightsShare = false,
    thickGrid = ThickGrid.NO,
    keyboardMove = KeyboardMove.NO,
    zen = Zen.NO,
    noFlags = NoFlags.NO,
    moveEvent = MoveEvent.BOTH,
    notation = Notations.western.index,
    resizeHandle = ResizeHandle.INITIAL,
    tags = Map.empty,
  )

  import alleycats.Zero
  implicit def PrefZero: Zero[Pref] = Zero(default)
}
