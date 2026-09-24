package io.github.ddmoyu.picomic.source.picacg

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import java.time.LocalDate

enum class RegistrationPhase { PREPARED, SUBMITTED, REGISTERED }

/** Generated platform profile, not personal information inferred about the user. */
class PicacgRegistration(
    val username: String, val password: CharArray, val nickname: String, val birthday: String,
    val questions: List<String>, val answers: List<String>, val phase: RegistrationPhase = RegistrationPhase.PREPARED,
) : AutoCloseable {
    val gender = "bot"
    init {
        require(username.matches(Regex("[a-z0-9]{2,30}")) && password.size in 8..128)
        require(nickname.length in 2..50 && nickname.none(Char::isISOControl))
        LocalDate.parse(birthday)
        require(questions.size == 3 && answers.size == 3)
        require((questions + answers).all { it.length in 1..100 && it.none(Char::isISOControl) })
    }
    override fun close() { password.fill('\u0000') }
    override fun toString() = "PicacgRegistration([redacted], $phase)"

    fun displayText(includePassword: Boolean = true): String = buildString {
        appendLine("=== 账号信息 ===")
        appendLine("昵称: $nickname")
        appendLine("用户名: $username")
        appendLine("密码: ${if (includePassword) String(password) else "••••••••"}")
        questions.indices.forEach { index ->
            appendLine("安全问题${index + 1}: ${questions[index]}")
            appendLine("安全答案${index + 1}: ${answers[index]}")
        }
        appendLine("生日: $birthday")
        appendLine("性别: 机器人")
        appendLine()
        append("*** 请妥善保存此信息 ***")
    }

    fun encode(phase: RegistrationPhase = this.phase): ByteArray = ByteArrayOutputStream().also { buffer ->
        DataOutputStream(buffer).use { out ->
            out.writeInt(1); out.writeUTF(phase.name); out.writeUTF(username)
            out.writeInt(password.size); password.forEach { out.writeChar(it.code) }
            out.writeUTF(nickname); out.writeUTF(birthday)
            questions.zip(answers).forEach { (question, answer) -> out.writeUTF(question); out.writeUTF(answer) }
        }
    }.toByteArray()

    companion object {
        fun generate(today: LocalDate = LocalDate.now(), random: SecureRandom = SecureRandom()): PicacgRegistration {
            val alphabet = "abcdefghijkmnpqrstuvwxyz23456789"
            fun randomText(length: Int) = buildString { repeat(length) { append(alphabet[random.nextInt(alphabet.length)]) } }
            return PicacgRegistration("pc" + randomText(18), randomText(24).toCharArray(), "PiComic" + randomText(10),
                today.minusYears(20).toString(), List(3) { "Recovery key ${it + 1}" }, List(3) { randomText(24) })
        }
        fun decode(bytes: ByteArray): PicacgRegistration {
            require(bytes.size in 1..8192)
            var secret: CharArray? = null
            try {
                return DataInputStream(bytes.inputStream()).use { input ->
                    require(input.readInt() == 1)
                    val phase = RegistrationPhase.valueOf(input.readUTF()); val username = input.readUTF()
                    val size = input.readInt(); require(size in 8..128 && size * 2 <= input.available())
                    val password = CharArray(size) { input.readChar() }.also { secret = it }
                    val name = input.readUTF(); val birthday = input.readUTF()
                    val pairs = List(3) { input.readUTF() to input.readUTF() }
                    require(input.available() == 0)
                    PicacgRegistration(username, password, name, birthday, pairs.map { it.first }, pairs.map { it.second }, phase)
                }
            } catch (error: Exception) { secret?.fill('\u0000'); throw error }
        }
    }
}

interface PicacgRegistrationApi : PicacgAuthApi {
    suspend fun register(details: PicacgRegistration)
}
