package lila.app
package templating

import play.api.i18n.Lang

import shogi.Mode
import shogi.variant.Variant

import lila.i18n.{ I18nKeys => trans }
import lila.pref.Pref
import lila.report.Reason

trait SetupHelper { self: I18nHelper =>

  type SelectChoice = (String, String, Option[String])

  def translatedCorresDaysChoices(implicit lang: Lang) =
    List(1, 2, 3, 5, 7, 10, 14) map { d =>
      (d.toString, trans.nbDays.pluralSameTxt(d), none)
    }

  def translatedReasonChoices(implicit lang: Lang) =
    List(
      (Reason.Cheat.key, trans.cheat.txt()),
      (Reason.Comm.key, trans.insult.txt()),
      (Reason.Comm.key, trans.troll.txt()),
      (Reason.Other.key, trans.other.txt()),
    )

  def translatedModeChoices(implicit lang: Lang) =
    List(
      (Mode.Casual.id.toString, trans.casual.txt(), none),
      (Mode.Rated.id.toString, trans.rated.txt(), none),
    )

  def translatedBooleanFilterChoices(implicit lang: Lang) =
    List(
      (1, trans.yes.txt(), none),
      (0, trans.no.txt(), none),
    )

  def translatedBooleanYesFilterChoice(implicit lang: Lang) =
    List(
      (1, trans.yes.txt(), none),
    )

  def translatedModeChoicesTournament(implicit lang: Lang) =
    List(
      (Mode.Casual.id.toString, trans.casualTournament.txt(), none),
      (Mode.Rated.id.toString, trans.ratedTournament.txt(), none),
    )

  private val encodeId = (v: Variant) => v.id.toString

  private def variantTuple(encode: Variant => String)(variant: Variant)(implicit lang: Lang) =
    (encode(variant), transKeyTxt(variant.key), transKeyTxt(s"${variant.key}Description").some)

  def standardChoice(implicit lang: Lang): SelectChoice =
    standardChoice(encodeId)

  def standardChoice(encode: Variant => String)(implicit lang: Lang): SelectChoice =
    (encode(shogi.variant.Standard), trans.standard.txt(), trans.standardDescription.txt().some)

  def translatedVariantChoices(implicit lang: Lang): List[SelectChoice] =
    standardChoice ::
      List(
        shogi.variant.Minishogi,
        shogi.variant.Chushogi,
        shogi.variant.Annanshogi,
        shogi.variant.Kyotoshogi,
        shogi.variant.Checkshogi,
        shogi.variant.Dobutsu,
      ).map(variantTuple(encodeId))

  def translatedVariantChoices(
      encode: Variant => String,
  )(implicit lang: Lang): List[SelectChoice] =
    standardChoice(encode) :: List(
      shogi.variant.Minishogi,
      shogi.variant.Chushogi,
      shogi.variant.Annanshogi,
      shogi.variant.Kyotoshogi,
      shogi.variant.Checkshogi,
      shogi.variant.Dobutsu,
    ).map(variantTuple(encode))

  def translatedBoardLayoutChoices(implicit lang: Lang) =
    List(
      (Pref.BoardLayout.DEFAULT, trans.default.txt()),
      (Pref.BoardLayout.COMPACT, trans.compact.txt()),
      (Pref.BoardLayout.SMALL, trans.preferences.smallMoves.txt()),
    )

  def translatedAnimationChoices(implicit lang: Lang) =
    List(
      (Pref.Animation.NONE, trans.none.txt()),
      (Pref.Animation.FAST, trans.fast.txt()),
      (Pref.Animation.NORMAL, trans.normal.txt()),
      (Pref.Animation.SLOW, trans.slow.txt()),
    )

  def translatedBoardCoordinateChoices(implicit lang: Lang) =
    List(
      (Pref.Coords.NONE, trans.none.txt()),
      (Pref.Coords.INSIDE, trans.insideTheBoard.txt()),
      (Pref.Coords.OUTSIDE, trans.outsideTheBoard.txt()),
      (Pref.Coords.EDGE, trans.edgeOfTheBoard.txt()),
    )

  def translatedMoveListWhilePlayingChoices(implicit lang: Lang) =
    List(
      (Pref.Replay.NEVER, trans.never.txt()),
      (Pref.Replay.SLOW, trans.onSlowGames.txt()),
      (Pref.Replay.ALWAYS, trans.always.txt()),
    )

  def translatedColorNameChoices(implicit lang: Lang) =
    List(
      (
        Pref.ColorName.LANG,
        s"${trans.language.txt()} - (${trans.sente.txt()}/${trans.gote.txt()})",
      ),
      (Pref.ColorName.SENTEJP, "先手/後手"),
      (Pref.ColorName.SENTE, "Sente/Gote"),
      (Pref.ColorName.BLACK, s"${trans.black.txt()}/${trans.white.txt()}"),
    )

  def translatedClockAudibleChoices(implicit lang: Lang) =
    List(
      (Pref.ClockAudible.MINE, trans.preferences.myClock.txt()),
      (Pref.ClockAudible.MYGAME, trans.preferences.myAndOpponentClock.txt()),
      (Pref.ClockAudible.ALL, trans.preferences.allClocks.txt()),
    )

  def translatedClockTenthsChoices(implicit lang: Lang) =
    List(
      (Pref.ClockTenths.NEVER, trans.never.txt()),
      (Pref.ClockTenths.LOWTIME, trans.preferences.whenTimeRemainingLessThanTenSeconds.txt()),
      (Pref.ClockTenths.ALWAYS, trans.always.txt()),
    )

  def translatedMoveEventChoices(implicit lang: Lang) =
    List(
      (Pref.MoveEvent.CLICK, trans.preferences.clickTwoSquares.txt()),
      (Pref.MoveEvent.DRAG, trans.preferences.dragPiece.txt()),
      (Pref.MoveEvent.BOTH, trans.preferences.bothClicksAndDrag.txt()),
    )

  def translatedTakebackChoices(implicit lang: Lang) =
    List(
      (Pref.Takeback.NEVER, trans.never.txt()),
      (Pref.Takeback.ALWAYS, trans.always.txt()),
      (Pref.Takeback.CASUAL, trans.preferences.inCasualGamesOnly.txt()),
    )

  def translatedMoretimeChoices(implicit lang: Lang) =
    List(
      (Pref.Moretime.NEVER, trans.never.txt()),
      (Pref.Moretime.ALWAYS, trans.always.txt()),
      (Pref.Moretime.CASUAL, trans.preferences.inCasualGamesOnly.txt()),
    )

  def submitMoveChoices(implicit lang: Lang) =
    List(
      (Pref.SubmitMove.NEVER, trans.never.txt()),
      (Pref.SubmitMove.CORRESPONDENCE_ONLY, trans.preferences.inCorrespondenceGames.txt()),
      (
        Pref.SubmitMove.CORRESPONDENCE_UNLIMITED,
        trans.preferences.correspondenceAndUnlimited.txt(),
      ),
      (Pref.SubmitMove.ALWAYS, trans.always.txt()),
    )

  def confirmResignChoices(implicit lang: Lang) =
    List(
      (Pref.ConfirmResign.NO, trans.no.txt()),
      (Pref.ConfirmResign.YES, trans.yes.txt()),
    )

  def translatedChallengeChoices(implicit lang: Lang) =
    List(
      (Pref.Challenge.NEVER, trans.never.txt()),
      (
        Pref.Challenge.RATING,
        trans.ifRatingIsPlusMinusX.txt(lila.pref.Pref.Challenge.ratingThreshold),
      ),
      (Pref.Challenge.FRIEND, trans.onlyFriends.txt()),
      (Pref.Challenge.ALWAYS, trans.always.txt()),
    )

  def translatedTourChallengeChoices(implicit lang: Lang) =
    List(
      (Pref.Challenge.NEVER, trans.never.txt()),
      (Pref.Challenge.FRIEND, trans.onlyFriends.txt()),
      (Pref.Challenge.ALWAYS, trans.always.txt()),
    )

  def translatedMessageChoices(implicit lang: Lang)     = privacyBaseChoices
  def translatedStudyInviteChoices(implicit lang: Lang) = privacyBaseChoices
  def translatedPalantirChoices(implicit lang: Lang)    = privacyBaseChoices

  def privacyBaseChoices(implicit lang: Lang) =
    List(
      (Pref.StudyInvite.NEVER, trans.never.txt()),
      (Pref.StudyInvite.FRIEND, trans.onlyFriends.txt()),
      (Pref.StudyInvite.ALWAYS, trans.always.txt()),
    )

  def translatedBoardResizeHandleChoices(implicit lang: Lang) =
    List(
      (Pref.ResizeHandle.NEVER, trans.never.txt()),
      (Pref.ResizeHandle.INITIAL, trans.preferences.onlyOnInitialPosition.txt()),
      (Pref.ResizeHandle.ALWAYS, trans.always.txt()),
    )

}
