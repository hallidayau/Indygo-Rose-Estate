package au.halliday.clipsync

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Both the relay topic and the AES key come from the pairing code, so only
 * phones with the same code can find or read each other's clips.
 */
class Crypto private constructor(passphrase: String) {
    val topic: String
    private val key: SecretKeySpec
    private val random = SecureRandom()

    init {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("clipsync-topic-v1:$passphrase".toByteArray(Charsets.UTF_8))
        topic = "clipsync_" + digest.joinToString("") { "%02x".format(it) }.take(40)

        val spec = PBEKeySpec(passphrase.toCharArray(), "clipsync-key-v1".toByteArray(Charsets.UTF_8), 100_000, 256)
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        key = SecretKeySpec(raw, "AES")
    }

    fun encrypt(plain: ByteArray): ByteArray {
        val iv = ByteArray(12)
        random.nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return iv + cipher.doFinal(plain)
    }

    fun decrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, data, 0, 12))
        return cipher.doFinal(data, 12, data.size - 12)
    }

    fun encryptToString(text: String): String =
        Base64.encodeToString(encrypt(text.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)

    fun decryptString(text: String): String =
        String(decrypt(Base64.decode(text, Base64.NO_WRAP)), Charsets.UTF_8)

    companion object {
        @Volatile
        private var cached: Pair<String, Crypto>? = null

        /** Key derivation is deliberately slow, so reuse the last instance. */
        fun get(passphrase: String): Crypto {
            cached?.let { if (it.first == passphrase) return it.second }
            return Crypto(passphrase).also { cached = passphrase to it }
        }
    }
}
