package com.nhlfantasygm.multiplayer

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Handles Multiplayer Invite Code generation, deep-link parsing, unguessable room topic derivation,
 * and authenticated end-to-end room payload encryption (GZIP + AES-256-GCM keyed from the Room Code).
 *
 * No private API keys or static secrets are stored in the APK; only invited friends who possess
 * the 6-character Room Invite Code can derive the topic or decrypt/authenticate room packets.
 */
object RoomCrypto {

    // Unambiguous uppercase alphanumeric alphabet (excludes 0/O and 1/I)
    private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private const val DEFAULT_CODE_LENGTH = 6
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val PBKDF2_ITERATIONS = 4_096

    private val secureRandom = SecureRandom()

    fun generateInviteCode(length: Int = DEFAULT_CODE_LENGTH): String {
        val sb = StringBuilder(length)
        repeat(length) {
            sb.append(CODE_ALPHABET[secureRandom.nextInt(CODE_ALPHABET.length)])
        }
        return sb.toString()
    }

    /**
     * Normalizes user-entered invite codes (strips dashes, spaces, 'NHL-' prefix, uppercases).
     */
    fun normalizeInviteCode(raw: String): String {
        val cleaned = raw.trim()
            .uppercase(Locale.ROOT)
            .removePrefix("NHL-")
            .filter { it.isLetterOrDigit() }
        return cleaned.take(10)
    }

    /**
     * Formats a 6-char code `K9M4P2` as `K9M-4P2` for easy reading and voice sharing.
     */
    fun formatInviteCodeForDisplay(code: String): String {
        val norm = normalizeInviteCode(code)
        return if (norm.length == 6) "${norm.substring(0, 3)}-${norm.substring(3, 6)}" else norm
    }

    /**
     * Extracts an invite code from either a deep link (`nhlfantasygm://join?code=K9M4P2`),
     * an HTTPS invite URL (`...?code=K9M4P2`), or raw text.
     */
    fun extractInviteCodeFromLinkOrText(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null

        if (trimmed.contains("://") || trimmed.contains("code=", ignoreCase = true)) {
            runCatching {
                val uri = URI(trimmed)
                val query = uri.rawQuery.orEmpty()
                query.split("&").forEach { param ->
                    val parts = param.split("=", limit = 2)
                    if (parts.size == 2 && parts[0].equals("code", ignoreCase = true)) {
                        val code = normalizeInviteCode(parts[1])
                        if (code.length >= 4) return code
                    }
                }
            }
            val regexMatch = Regex("""[?&]code=([A-Za-z0-9\-]+)""", RegexOption.IGNORE_CASE)
                .find(trimmed)
                ?.groupValues
                ?.getOrNull(1)
            if (!regexMatch.isNullOrBlank()) {
                val norm = normalizeInviteCode(regexMatch)
                if (norm.length >= 4) return norm
            }
        }

        val norm = normalizeInviteCode(trimmed)
        return norm.takeIf { it.length in 4..10 }
    }

    fun buildDeepLink(inviteCode: String): String {
        val norm = normalizeInviteCode(inviteCode)
        return "nhlfantasygm://join?code=$norm"
    }

    /**
     * Derives an unguessable MQTT / WebSocket room channel topic from the invite code.
     */
    fun deriveRoomTopic(inviteCode: String): String {
        val norm = normalizeInviteCode(inviteCode)
        require(norm.length >= 4) { "Invite code must be at least 4 characters" }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("nhl-fantasy-gm-v1-topic:$norm".toByteArray(StandardCharsets.UTF_8))
        val hex = digest.take(16).joinToString("") { "%02x".format(it) }
        return "nhlfantasygm/v1/rooms/$hex"
    }

    /**
     * Compresses UTF-8 JSON with GZIP and encrypts with AES-256-GCM keyed from [inviteCode].
     * Output is URL-safe/standard Base64 (`IV || CIPHERTEXT_WITH_TAG`).
     */
    fun encryptRoomPayload(inviteCode: String, plainJson: String): String {
        val key = deriveAesKey(inviteCode)
        val compressed = gzipCompress(plainJson.toByteArray(StandardCharsets.UTF_8))
        val iv = ByteArray(GCM_IV_BYTES).also { secureRandom.nextBytes(it) }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD("nhl-fantasy-gm-aad-v1".toByteArray(StandardCharsets.UTF_8))
        val cipherBytes = cipher.doFinal(compressed)

        val combined = ByteArray(iv.size + cipherBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)
        return Base64.getEncoder().encodeToString(combined)
    }

    /**
     * Authenticates, decrypts, and GZIP-decompresses an encrypted room payload.
     * Throws on wrong room code, corrupted packet, or tampered AAD/tag.
     */
    fun decryptRoomPayload(inviteCode: String, encodedPayload: String): String {
        val combined = Base64.getDecoder().decode(encodedPayload.trim())
        require(combined.size > GCM_IV_BYTES + 16) { "Encrypted payload too short" }

        val iv = combined.copyOfRange(0, GCM_IV_BYTES)
        val cipherBytes = combined.copyOfRange(GCM_IV_BYTES, combined.size)
        val key = deriveAesKey(inviteCode)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD("nhl-fantasy-gm-aad-v1".toByteArray(StandardCharsets.UTF_8))
        val decompressed = gzipDecompress(cipher.doFinal(cipherBytes))
        return String(decompressed, StandardCharsets.UTF_8)
    }

    private fun deriveAesKey(inviteCode: String): SecretKeySpec {
        val norm = normalizeInviteCode(inviteCode)
        require(norm.length >= 4) { "Invalid invite code" }
        val salt = MessageDigest.getInstance("SHA-256")
            .digest("nhl-fantasy-gm-key-salt:$norm".toByteArray(StandardCharsets.UTF_8))
            .copyOf(16)
        val spec = PBEKeySpec(norm.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val secret = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec)
        return SecretKeySpec(secret.encoded, "AES")
    }

    private fun gzipCompress(input: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(input) }
        return bos.toByteArray()
    }

    private fun gzipDecompress(compressed: ByteArray): ByteArray {
        GZIPInputStream(ByteArrayInputStream(compressed)).use { gis ->
            val bos = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val read = gis.read(buffer)
                if (read <= 0) break
                bos.write(buffer, 0, read)
            }
            return bos.toByteArray()
        }
    }
}
