package lishogi.oauth

import lishogi.db.dsl._
import lishogi.user.User

final class PersonalTokenApi(colls: OauthColls)(implicit ec: scala.concurrent.ExecutionContext) {

  import PersonalToken._
  import AccessToken.accessTokenIdHandler
  import AccessToken.{ BSONFields => F, _ }

  def list(u: User): Fu[List[AccessToken]] =
    colls.token {
      _.ext
        .find(
          $doc(
            F.userId   -> u.id,
            F.clientId -> clientId
          )
        )
        .sort($sort desc F.createdAt)
        .cursor[AccessToken]().list(100)
    }

  def create(token: AccessToken) = colls.token(_.insert.one(token).void)

  def deleteBy(tokenId: AccessToken.Id, user: User) =
    colls.token {
      _.delete
        .one(
          $doc(
            F.id       -> tokenId,
            F.clientId -> clientId,
            F.userId   -> user.id
          )
        )
        .void
    }
}

object PersonalToken {

  val clientId = "lishogi_personal_token"
}
