package lila.common

import java.nio.charset.StandardCharsets
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.util.Base64

import com.roundeights.hasher.Implicits._

object ImageStorage {

  def uploadSecret(userId: String, key: String) =
    MessageDigest
      .getInstance("MD5")
      .digest(
        (key + userId).getBytes(UTF_8),
      )
      .map("%02x".format(_))
      .mkString

  private val uploadMaxMb            = 10
  private val uploadMaxMbRecommended = (uploadMaxMb - 2) atLeast 1
  private val storageFormat          = "webp"

  private val allowedFormats = Set(
    "jpeg",
    "jpg",
    "png",
    "webp",
    "gif",
    "avif",
  )
  val allowedMimeTypes = allowedFormats.map(t => s"image/$t")

  val recommendations =
    s"Accepted size: ${uploadMaxMbRecommended}MB; Accepted formats: ${allowedFormats.mkString(", ")}"

  object Imgproxy {

    private def makeUrlSafe(base64: String): String =
      base64
        .replaceAll("=", "")
        .replaceAll("\\+", "-")
        .replaceAll("/", "_")

    // Decode a hex-encoded string (e.g. imgproxy key/salt) into raw bytes
    private def hexToBytes(hex: String): Array[Byte] = {
      require(hex.length % 2 == 0, s"Hex string must have even length: $hex")
      hex.grouped(2).map(Integer.parseInt(_, 16).toByte).toArray
    }

    def opts(
        width: Int,
        height: Int,
        resize: String = "fill",
        quality: Int = 90,
        enlarge: Boolean = false,
    ): String = Seq(
      s"w:${width atMost 1920}",
      s"h:${height atMost 1080}",
      s"rt:$resize",
      s"q:$quality",
      s"el:${if (enlarge) 1 else 0}",
    ).mkString("/")

    def generateSignedPath(
        keyHex: String,
        saltHex: String,
        processingOps: Option[String],
        key: String,
    ): String = {

      val encodedSourceUrl = makeUrlSafe(
        Base64.getEncoder.encodeToString(
          s"local:///$key.${storageFormat}".getBytes(StandardCharsets.UTF_8),
        ),
      )

      val pathToSign = processingOps.fold(s"/$encodedSourceUrl")(ops => s"/$ops/$encodedSourceUrl")

      val keyBytes  = hexToBytes(keyHex)
      val saltBytes = hexToBytes(saltHex)
      val pathBytes = pathToSign.getBytes(StandardCharsets.UTF_8)

      val saltAndPath = saltBytes ++ pathBytes

      val signatureHash  = saltAndPath.hmac(keyBytes).sha256
      val signatureBytes = signatureHash.bytes

      val signature = makeUrlSafe(Base64.getEncoder.encodeToString(signatureBytes))

      s"$signature$pathToSign"
    }
  }
}
