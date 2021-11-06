package lishogi.storm

import com.github.blemale.scaffeine.LoadingCache
import com.roundeights.hasher.Algo
import scala.concurrent.duration._

import lishogi.common.config
import lishogi.common.config.Secret
import lishogi.common.ThreadLocalRandom
import lishogi.common.Uptime
import lishogi.memo.CacheApi
import lishogi.user.User

final class StormSign(secret: Secret, cacheApi: CacheApi) {

  private val store: LoadingCache[User.ID, String] =
    cacheApi.scaffeine
      .expireAfterAccess(24 hours)
      .build(_ => ThreadLocalRandom nextString 12)

  private val signer = Algo hmac secret.value

  def getPrev(user: User): String = store get user.id

  def check(user: User, signed: String): Boolean = signed != "undefined" && {
    val correct =
      !Uptime.startedSinceMinutes(5) || {
        signer.sha1(store.get(user.id)) hash_= signed
      }
    if (correct) store.put(user.id, signed)
    correct
  }
}
