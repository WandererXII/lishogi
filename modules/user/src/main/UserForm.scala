package lila.user

import play.api.data.Forms._
import play.api.data._
import play.api.data.validation.Constraints

import lila.common.Form.cleanNonEmptyText
import lila.common.Form.cleanText
import lila.user.User.ClearPassword

final class UserForm(authenticator: Authenticator) {

  val note = Form(
    mapping(
      "text" -> cleanText(minLength = 3, maxLength = 2000),
      "mod"  -> boolean,
      "dox"  -> optional(boolean),
    )(NoteData.apply)(NoteData.unapply),
  )

  case class NoteData(text: String, mod: Boolean, dox: Option[Boolean])

  def username(user: User): Form[String] =
    Form(
      single(
        "username" -> cleanText.verifying(
          "changeUsernameNotSame",
          name => name.toLowerCase == user.username.toLowerCase && name != user.username,
        ),
      ),
    ).fill(user.username)

  def usernameOf(user: User) = username(user) fill user.username

  val profile = Form(
    mapping(
      "country"   -> optional(text.verifying(Countries.codeSet contains _)),
      "location"  -> optional(cleanNonEmptyText(maxLength = 80)),
      "bio"       -> optional(cleanNonEmptyText(maxLength = 600)),
      "firstName" -> nameField,
      "lastName"  -> nameField,
      "links"     -> optional(cleanNonEmptyText(maxLength = 3000)),
    )(Profile.apply)(Profile.unapply),
  )

  def profileOf(user: User) = profile fill user.profileOrDefault

  private def nameField = optional(cleanText(minLength = 2, maxLength = 20))

  case class Passwd(
      oldPasswd: String,
      newPasswd1: String,
      newPasswd2: String,
  ) {
    def samePasswords = newPasswd1 == newPasswd2
  }

  def passwd(u: User)(implicit ec: scala.concurrent.ExecutionContext) =
    authenticator loginCandidate u map { candidate =>
      Form(
        mapping(
          "oldPasswd" -> nonEmptyText.verifying(
            "incorrectPassword",
            p => candidate.check(ClearPassword(p)),
          ),
          "newPasswd1" -> UserForm.newPassword,
          "newPasswd2" -> UserForm.newPassword,
        )(Passwd.apply)(Passwd.unapply).verifying("the new passwords don't match", _.samePasswords),
      )
    }
}

object UserForm {

  // Only for choosing a password, existing ones may not match
  val newPassword = nonEmptyText(minLength = 4, maxLength = 999)

  val title = Form(single("title" -> optional(nonEmptyText)))

  lazy val usernameConstraints = Seq(
    Constraints minLength 2,
    Constraints maxLength 30,
    Constraints.pattern(regex = User.newUsernameRegex),
  )
  lazy val usernameField = text.verifying(usernameConstraints: _*)
}
