package net.gschimmel.cryptomako.vault

import com.google.gson.JsonParser
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Format-8 vault.cryptomator is a JWT signed with HMAC over the 64-byte raw masterkey
 * (encKey ‖ macKey). Decode unverified first for format gate; verify after unlock.
 */
internal object VaultJwt {
    data class Header(val alg: String, val kid: String?)
    data class Payload(
        val format: Int,
        val shorteningThreshold: Int?,
        val cipherCombo: String,
        val jti: String?,
    )

    fun decodeUnverified(token: String): Triple<Header, Payload, String> {
        val parts = token.trim().split('.')
        if (parts.size != 3) throw VaultException.InvalidJwt()
        val headerJson = String(b64UrlDecode(parts[0]), StandardCharsets.UTF_8)
        val payloadJson = String(b64UrlDecode(parts[1]), StandardCharsets.UTF_8)
        val headerObj = JsonParser.parseString(headerJson).asJsonObject
        val payloadObj = JsonParser.parseString(payloadJson).asJsonObject
        val header = Header(
            alg = headerObj.get("alg").asString,
            kid = headerObj.get("kid")?.takeUnless { it.isJsonNull }?.asString,
        )
        val payload = Payload(
            format = payloadObj.get("format").asInt,
            shorteningThreshold = payloadObj.get("shorteningThreshold")?.takeUnless { it.isJsonNull }?.asInt,
            cipherCombo = payloadObj.get("cipherCombo").asString,
            jti = payloadObj.get("jti")?.takeUnless { it.isJsonNull }?.asString,
        )
        return Triple(header, payload, parts[0] + "." + parts[1])
    }

    fun verify(token: String, rawKey: ByteArray): Payload {
        val (header, payload, signingInput) = decodeUnverified(token)
        val parts = token.trim().split('.')
        if (parts.size != 3) throw VaultException.InvalidJwt()
        val expected = b64UrlDecode(parts[2])
        val macAlg = when (header.alg.uppercase()) {
            "HS256" -> "HmacSHA256"
            "HS384" -> "HmacSHA384"
            "HS512" -> "HmacSHA512"
            else -> throw VaultException.InvalidJwt()
        }
        val mac = Mac.getInstance(macAlg)
        mac.init(SecretKeySpec(rawKey, macAlg))
        val actual = mac.doFinal(signingInput.toByteArray(StandardCharsets.US_ASCII))
        if (!constantTimeEquals(actual, expected)) throw VaultException.InvalidJwt()
        return payload
    }

    private fun b64UrlDecode(s: String): ByteArray {
        var p = s.replace('-', '+').replace('_', '/')
        val pad = (4 - p.length % 4) % 4
        p += "=".repeat(pad)
        return Base64.getDecoder().decode(p)
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var r = 0
        for (i in a.indices) r = r or (a[i].toInt() xor b[i].toInt())
        return r == 0
    }
}
