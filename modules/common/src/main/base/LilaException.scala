package lila.base

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait LilaException extends Exception {
  val message: String

  override def getMessage = message
  override def toString   = message
}

case class LilaInvalid(message: String) extends LilaException

case class LilaServiceUnavailable(service: String, message: String)
    extends LilaException
    with scala.util.control.NoStackTrace

object LilaServiceUnavailable {

  def recover[A](service: String)(fu: Future[A])(implicit ec: ExecutionContext): Future[A] =
    fu recoverWith {
      case e: LilaException => Future.failed(e)
      case e: Exception =>
        Future.failed(
          LilaServiceUnavailable(service, s"$service ${e.getClass.getSimpleName}: ${e.getMessage}"),
        )
    }
}

object LilaException {

  def apply(msg: String) =
    new LilaException {
      val message = msg
    }
}
