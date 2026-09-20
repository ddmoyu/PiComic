package io.github.ddmoyu.picomic.auth

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

object AccountSlots {
    val titles = linkedMapOf("picacg" to "哔咔", "jmcomic" to "JM", "htcomic" to "绅士漫画", "ehentai" to "E-Hentai", "nhentai_key" to "nhentai API Key", "nhentai_web" to "nhentai 网页账号")
    val passwords = setOf("picacg", "jmcomic", "htcomic")
    fun accepts(source: String, kind: CredentialKind?) = source in titles && (kind == null || kind == when (source) {
        "picacg", "nhentai_web" -> CredentialKind.USER_TOKEN
        "nhentai_key" -> CredentialKind.API_KEY
        else -> CredentialKind.COOKIE
    })
}

class RememberedLogin(val username: String, val password: CharArray) : AutoCloseable {
    init { require(username.isNotBlank() && username.length <= 320 && username.none(Char::isISOControl) && password.size in 1..1024) }
    override fun close() { password.fill('\u0000') }
    override fun toString() = "RememberedLogin([redacted])"
}

sealed interface PasswordRetention {
    data object Preserve : PasswordRetention
    data object Forget : PasswordRetention
    class Remember(val login: RememberedLogin) : PasswordRetention {
        override fun toString() = "PasswordRetention.Remember([redacted])"
    }
}

/** Plaintext only in short-lived memory; the enclosing store or export always encrypts it. */
class StoredAccount(val displayName: String, val accountId: String?, val candidate: SessionCandidate?, val login: RememberedLogin?) : AutoCloseable {
    override fun close() { candidate?.value?.fill(0); login?.close() }
    override fun toString() = "StoredAccount([redacted])"
}

object StoredAccountCodec {
    const val MAX_BYTES = 56 * 1024
    fun encode(account: StoredAccount): ByteArray = ByteArrayOutputStream().also { buffer ->
        DataOutputStream(buffer).use { out ->
            out.writeInt(3); out.writeUTF(account.displayName); out.writeUTF(account.accountId.orEmpty())
            out.writeBoolean(account.candidate != null)
            account.candidate?.let { out.writeUTF(it.kind.name); out.writeInt(it.value.size); out.write(it.value) }
            out.writeBoolean(account.login != null)
            account.login?.let { login -> out.writeUTF(login.username); out.writeInt(login.password.size); login.password.forEach { out.writeChar(it.code) } }
        }
    }.toByteArray().also { bytes -> try { decode(bytes).close() } catch (e: Exception) { bytes.fill(0); throw e } }

    fun decode(bytes: ByteArray): StoredAccount {
        require(bytes.size in 1..MAX_BYTES)
        var candidate: SessionCandidate? = null
        var login: RememberedLogin? = null
        try {
            return DataInputStream(bytes.inputStream()).use { input ->
                val version = input.readInt(); require(version in 1..3)
                val oldKind = if (version < 3) CredentialKind.valueOf(input.readUTF()) else null
                val name = input.readUTF(); require(name.isNotBlank() && name.length <= 200 && name.none(Char::isISOControl))
                val identity = if (version >= 2) input.readUTF().ifEmpty { null } else null
                require(identity == null || identity.isNotBlank() && identity.length <= 512 && identity.none(Char::isISOControl))
                if (version < 3 || input.readBoolean()) {
                    val kind = oldKind ?: CredentialKind.valueOf(input.readUTF())
                    val size = input.readInt(); require(size in 1..(48 * 1024) && size <= input.available())
                    candidate = SessionCandidate(kind, ByteArray(size).also(input::readFully), identity)
                }
                if (version == 3 && input.readBoolean()) {
                    val username = input.readUTF()
                    val size = input.readInt(); require(size in 1..1024 && size * 2 <= input.available())
                    val password = CharArray(size) { input.readChar() }
                    try { login = RememberedLogin(username, password) } catch (e: Exception) { password.fill('\u0000'); throw e }
                }
                require(input.available() == 0 && (candidate != null || login != null))
                StoredAccount(name, identity, candidate, login)
            }
        } catch (e: Exception) { candidate?.value?.fill(0); login?.close(); throw e }
    }
}

class AccountTransfer(val source: String, val bytes: ByteArray) : AutoCloseable {
    init {
        try { StoredAccountCodec.decode(bytes).use { account ->
            require(AccountSlots.accepts(source, account.candidate?.kind) && (account.login == null || source in AccountSlots.passwords))
        } } catch (e: Exception) { bytes.fill(0); throw e }
    }
    override fun close() { bytes.fill(0) }
    override fun toString() = "AccountTransfer($source, [redacted])"
}
