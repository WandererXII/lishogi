package lila.analyse

import org.joda.time.DateTime

import shogi.Color

case class Analysis(
    id: String, // game ID, or chapter ID if studyId is set
    studyId: Option[String],
    postGameStudies: Set[Analysis.PostGameStudy],
    infos: List[Info],
    startPly: Int,
    uid: Option[String], // requester lishogi ID
    by: Option[String],  // analyser lishogi ID
    date: DateTime,
) {

  def requestedBy = uid | "lishogi"

  def providedBy = by | "lishogi"

  def providedByLishogi = by exists (_ startsWith "lishogi-")

  lazy val infoAdvices: InfoAdvices = {
    (Info.start(startPly) :: infos) sliding 2 collect { case List(prev, info) =>
      info -> {
        info.hasVariation ?? Advice(prev, info)
      }
    }
  }.toList

  lazy val advices: List[Advice] = infoAdvices.flatMap(_._2)

  def summary: List[(Color, List[(Advice.Judgement, Int)])] =
    Color.all map { color =>
      color -> (Advice.Judgement.all map { judgment =>
        judgment -> (advices count { adv =>
          adv.color == color && adv.judgment == judgment
        })
      })
    }

  def valid = infos.nonEmpty

  def nbEmptyInfos       = infos.count(_.isEmpty)
  def emptyRatio: Double = nbEmptyInfos.toDouble / infos.size.atLeast(1)
}

object Analysis {

  import reactivemongo.api.bson._

  import lila.db.BSON

  case class Analyzed(game: lila.game.Game, analysis: Analysis)

  case class PostGameStudy(studyId: String, chapterId: String)

  type ID = String

  implicit private[analyse] val postGameStudyBSONHandler: BSONDocumentHandler[PostGameStudy] =
    Macros.handler[PostGameStudy]

  implicit private[analyse] val analysisBSONHandler: BSON[Analysis] = new BSON[Analysis] {
    def reads(r: BSON.Reader) = {
      val startPly = r intD "ply"
      val raw      = r str "data"
      Analysis(
        id = r str "_id",
        studyId = r strO "studyId",
        postGameStudies = r.getD[Set[PostGameStudy]]("pgs", Set.empty[PostGameStudy]),
        infos = Info.decodeList(raw, startPly),
        startPly = startPly,
        uid = r strO "uid",
        by = r strO "by",
        date = r date "date",
      )
    }
    def writes(w: BSON.Writer, o: Analysis) =
      BSONDocument(
        "_id"     -> o.id,
        "studyId" -> o.studyId,
        "pgs"     -> (o.postGameStudies.nonEmpty).option(o.postGameStudies),
        "data"    -> Info.encodeList(o.infos),
        "ply"     -> w.intO(o.startPly),
        "uid"     -> o.uid,
        "by"      -> o.by,
        "date"    -> w.date(o.date),
      )
  }
}
