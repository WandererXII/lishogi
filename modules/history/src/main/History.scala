package lila.history

import scala.util.Success

// Keep ultraBullet, bullet, blitz, rapid, classical for now - for ratinch chart
case class History(
    standard: RatingsMap,
    minishogi: RatingsMap,
    chushogi: RatingsMap,
    annanshogi: RatingsMap,
    kyotoshogi: RatingsMap,
    checkshogi: RatingsMap,
    dobutsu: RatingsMap,
    ultraBullet: RatingsMap,
    bullet: RatingsMap,
    blitz: RatingsMap,
    rapid: RatingsMap,
    classical: RatingsMap,
    realTime: RatingsMap,
    correspondence: RatingsMap,
    puzzle: RatingsMap,
) {

  def apply(key: String): RatingsMap =
    key.toLowerCase match {
      case "standard"       => standard
      case "bullet"         => bullet
      case "blitz"          => blitz
      case "rapid"          => rapid
      case "classical"      => classical
      case "realtime"       => realTime
      case "correspondence" => correspondence
      case "puzzle"         => puzzle
      case "ultrabullet"    => ultraBullet
      case "minishogi"      => minishogi
      case "chushogi"       => chushogi
      case "annanshogi"     => annanshogi
      case "kyotoshogi"     => kyotoshogi
      case "checkshogi"     => checkshogi
      case "dobutsu"        => dobutsu
      case _                => Nil
    }
}

object History {

  import reactivemongo.api.bson._

  implicit private[history] val RatingsMapReader: BSONDocumentReader[RatingsMap] =
    new BSONDocumentReader[RatingsMap] {
      def readDocument(doc: BSONDocument) =
        Success(
          doc.elements
            .flatMap {
              case BSONElement(k, BSONInteger(v)) => k.toIntOption map (_ -> v)
              case _                              => none[(Int, Int)]
            }
            .sortBy(_._1)
            .toList,
        )
    }

  implicit private[history] val HistoryBSONReader: BSONDocumentReader[History] =
    new BSONDocumentReader[History] {
      def readDocument(doc: BSONDocument) =
        Success {
          def ratingsMap(key: String): RatingsMap = ~doc.getAsOpt[RatingsMap](key)
          History(
            standard = ratingsMap("standard"),
            minishogi = ratingsMap("minishogi"),
            chushogi = ratingsMap("chushogi"),
            annanshogi = ratingsMap("annanshogi"),
            kyotoshogi = ratingsMap("kyotoshogi"),
            checkshogi = ratingsMap("checkshogi"),
            dobutsu = ratingsMap("dobutsu"),
            ultraBullet = ratingsMap("ultraBullet"),
            bullet = ratingsMap("bullet"),
            blitz = ratingsMap("blitz"),
            rapid = ratingsMap("rapid"),
            classical = ratingsMap("classical"),
            realTime = ratingsMap("realTime"),
            correspondence = ratingsMap("correspondence"),
            puzzle = ratingsMap("puzzle"),
          )
        }
    }
}
