package lishogi.base

import ornicar.scalalib.ValidTypes._

trait LishogiException extends Exception {
  val message: String

  override def getMessage = message
  override def toString   = message
}

case class LishogiInvalid(message: String) extends LishogiException

object LishogiException extends scalaz.syntax.ToShowOps {

  def apply(msg: String) =
    new LishogiException {
      val message = msg
    }

  def apply(msg: Failures): LishogiException = apply(msg.shows)
}
