package lila.user

import com.roundeights.hasher.Implicits._
import reactivemongo.api.bson._

import lila.common.NormalizedEmailAddress
import lila.db.dsl._
import lila.user.User.ClearPassword
import lila.user.User.{ BSONFields => F }

final class Authenticator(
    passHasher: PasswordHasher,
    userRepo: UserRepo,
)(implicit ec: scala.concurrent.ExecutionContext) {
  import Authenticator._

  def passEnc(p: ClearPassword): HashedPassword = passHasher.hash(p)

  def compare(auth: AuthData, p: ClearPassword): Boolean =
    passHasher.check(auth.bpass, p)

  def loginCandidate(u: User): Fu[User.LoginCandidate] =
    loginCandidateById(u.id) dmap { _ | User.LoginCandidate(u, _ => false) }

  def loginCandidateById(id: User.ID): Fu[Option[User.LoginCandidate]] =
    loginCandidate($id(id))

  def loginCandidateByEmail(email: NormalizedEmailAddress): Fu[Option[User.LoginCandidate]] =
    loginCandidate($doc(F.email -> email))

  def setPassword(id: User.ID, p: ClearPassword): Funit =
    userRepo.coll.update
      .one(
        $id(id),
        $set(F.bpass -> passEnc(p).bytes),
      )
      .void

  private def loginCandidate(select: Bdoc): Fu[Option[User.LoginCandidate]] =
    userRepo.coll.one[AuthData](select, authProjection.some)(AuthDataBSONHandler) zip userRepo.coll
      .one[User](select) map {
      case (Some(authData), Some(user)) if user.enabled || !user.lameOrTroll =>
        User.LoginCandidate(user, compare(authData, _)).some
      case _ => none
    }
}

object Authenticator {

  case class AuthData(
      _id: User.ID,
      bpass: HashedPassword,
  ) {

    def hashToken: String = bpass.bytes.sha512.hex
  }

  val authProjection = $doc(F.bpass -> true)

  implicit private[user] val HashedPasswordBsonHandler: BSONHandler[HashedPassword] =
    quickHandler[HashedPassword](
      { case v: BSONBinary => HashedPassword(v.byteArray) },
      v => BSONBinary(v.bytes, Subtype.GenericBinarySubtype),
    )

  implicit val AuthDataBSONHandler: BSONDocumentHandler[AuthData] = Macros.handler[AuthData]
}
