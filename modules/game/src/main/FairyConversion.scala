package lila.game

import shogi.format.forsyth.Sfen
import shogi.format.usi.Usi
import shogi.variant.Variant

// for bots and shoginet
object FairyConversion {

  def validVariant(variant: Variant): Boolean =
    variant.kyotoshogi || variant.dobutsu

  def makeFairySfen(variant: Variant, sfen: Sfen): Option[Sfen] =
    if (variant.kyotoshogi) Kyoto.makeFairySfen(sfen).some
    else if (variant.dobutsu) Dobutsu.makeFairySfen(sfen).some
    else none

  def makeFairyUsiList(
      variant: Variant,
      usiList: Seq[Usi],
      initialSfen: Option[Sfen],
  ): Option[List[String]] =
    if (variant.kyotoshogi) Kyoto.makeFairyUsiList(usiList, initialSfen).some
    else if (variant.dobutsu) Dobutsu.makeFairyUsiList(usiList).some
    else none

  def readFairyUsi(variant: Variant, usiStr: String): Option[Usi] =
    if (variant.kyotoshogi) Kyoto.readFairyUsi(usiStr)
    else if (variant.dobutsu) Dobutsu.readFairyUsi(usiStr)
    else none

  object Kyoto {

    def makeFairySfen(sfen: Sfen): Sfen =
      Sfen(
        List(
          sfen.boardString.fold("") { _.flatMap(c => kyotoBoardMap.getOrElse(c, c.toString)) },
          sfen.color.map(_.letter.toString) | "b",
          sfen.handsString.fold("-") { _.map(c => kyotoHandsMap.getOrElse(c, c)) },
        ).mkString(" "),
      )

    def readFairyUsi(usiStr: String): Option[Usi] =
      Usi(fairyToUsi(usiStr))

    def readFairyUsiList(moves: String): Option[List[Usi]] =
      Usi.readList(moves.split(' ').toList.map(fairyToUsi))

    def makeFairyUsiList(usiList: Seq[Usi], initialSfen: Option[Sfen]): List[String] =
      shogi.Replay
        .usiWithRoleWhilePossible(
          usiList,
          initialSfen,
          shogi.variant.Kyotoshogi,
        )
        .map(uwr => usiWithRoleToFairy(uwr))

    def usiWithRoleToFairy(usiWithRole: Usi.WithRole): String = {
      val usi        = usiWithRole.usi
      val usiStr     = usi.usi
      val roleLetter = usiWithRole.role.name.head.toUpper
      usi match {
        case move: Usi.Move =>
          if (move.promotion && kyotoBoardMap.contains(roleLetter))
            usiStr.replace("+", "-")
          else usiStr
        case _: Usi.Drop =>
          kyotoBoardMap.get(roleLetter).fold(usiStr) { c =>
            s"$c${usiStr.drop(1)}"
          }
      }
    }

    def fairyToUsi(str: String): String =
      if (str.startsWith("+")) dropRoles.get(str.take(2)).fold(str)(rc => s"$rc${str.drop(2)}")
      else if (str.endsWith("-")) str.replace('-', '+')
      else str

    private val kyotoBoardMap: Map[Char, String] = Map(
      'g' -> "+n",
      'G' -> "+N",
      't' -> "+l",
      'T' -> "+L",
      'b' -> "+s",
      'B' -> "+S",
      'r' -> "+p",
      'R' -> "+P",
    )
    private val kyotoHandsMap: Map[Char, Char] = Map(
      'g' -> 'n',
      'G' -> 'N',
      't' -> 'l',
      'T' -> 'L',
    )
    private val dropRoles: Map[String, Char] = kyotoBoardMap.map { case (k, v) => (v, k) } toMap

  }

  object Dobutsu {

    def makeFairySfen(sfen: Sfen): Sfen =
      Sfen(
        List(
          sfen.boardString.fold("") {
            _.flatMap(c => dobutsuBoardMap.getOrElse(c, c.toString))
          },
          sfen.color.map(_.letter.toString) | "b",
          sfen.handsString | "-",
        ).mkString(" "),
      )

    def readFairyUsi(usiStr: String): Option[Usi] =
      Usi(fairyToUsi(usiStr))

    def readFairyUsiList(moves: String): Option[List[Usi]] =
      Usi.readList(moves.split(' ').toList.map(fairyToUsi))

    def makeFairyUsiList(usiList: Seq[Usi]): List[String] =
      usiList.map(_.usi).map(usiToFairy).toList

    def usiToFairy(usi: String): String =
      // P*2b -> C*2b
      if (usi.length >= 2 && usi.charAt(1) == '*')
        dobutsuDropMap.get(usi.head).fold(usi)(c => s"$c${usi.drop(1)}")
      else usi

    def fairyToUsi(str: String): String =
      // C*2b -> P*2b
      if (str.length >= 2 && str.charAt(1) == '*')
        fairyDropMap.get(str.head).fold(str)(c => s"$c${str.drop(1)}")
      else str

    // board + drop mapping
    private val dobutsuBoardMap: Map[Char, String] = Map(
      'r' -> "g",
      'R' -> "G", // giraffe
      'k' -> "l",
      'K' -> "L", // lion
      'b' -> "e",
      'B' -> "E", // elephant
      'p' -> "c",
      'P' -> "C", // chick
    )

    private val dobutsuDropMap: Map[Char, Char] =
      dobutsuBoardMap.map { case (k, v) => (k, v.head) }

    private val fairyDropMap: Map[Char, Char] =
      dobutsuDropMap.map { case (k, v) => (v, k) }
  }
}
