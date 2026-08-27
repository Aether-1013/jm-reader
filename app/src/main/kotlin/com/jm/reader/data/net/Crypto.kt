package com.jm.reader.data.net

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Crypto helpers that mirror the CryptoJS usage in the JMComic3 web app.
 *
 * The web app uses:
 *  - md5 for tokens and AES key derivation
 *  - AES-256-ECB (PKCS7/PKCS5) where the key is the ASCII bytes of an md5 hex string
 *  - Base64 for ciphertext transport
 */
object Crypto {

    fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Decrypts a base64 AES-ECB payload.
     *
     * @param base64Ciphertext base64-encoded ciphertext (matches CryptoJS.AES.decrypt(string) which
     *        treats the string as base64)
     * @param keyMd5Hex md5 hex string; its ASCII bytes form the AES key (32 bytes -> AES-256)
     */
    fun aesEcbDecrypt(base64Ciphertext: String, keyMd5Hex: String): String? {
        return try {
            val key = SecretKeySpec(keyMd5Hex.toByteArray(Charsets.UTF_8), "AES")
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, key)
            val plain = cipher.doFinal(Base64.decode(base64Ciphertext, Base64.DEFAULT))
            String(plain, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Decrypts the AES-encrypted host bootstrap file.
     * Key = ASCII(md5("diosfjckwpqpdfjkvnqQjsik")).
     */
    fun decryptHostText(encryptedText: String): String? {
        return aesEcbDecrypt(encryptedText, md5Hex("diosfjckwpqpdfjkvnqQjsik"))
    }
}
