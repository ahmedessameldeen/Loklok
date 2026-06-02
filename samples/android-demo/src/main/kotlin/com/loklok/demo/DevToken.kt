package com.loklok.demo

import android.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Dev-only token minting so the demo is self-contained. In a real app the token is
 * issued by YOUR backend after the user logs in — never ship the signing secret in a client.
 */
object DevToken {
    fun mint(userId: String, name: String, secret: String = "dev-secret-change-me"): String {
        fun b64(bytes: ByteArray) =
            Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

        val header = b64("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
        val exp = System.currentTimeMillis() / 1000 + 60 * 60 * 24
        val payload = b64("""{"sub":"$userId","name":"$name","exp":$exp}""".toByteArray())
        val signingInput = "$header.$payload"
        val mac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        }
        val sig = b64(mac.doFinal(signingInput.toByteArray()))
        return "$signingInput.$sig"
    }
}
