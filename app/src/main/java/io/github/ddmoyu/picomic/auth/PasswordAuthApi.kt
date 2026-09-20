package io.github.ddmoyu.picomic.auth

interface PasswordAuthApi {
    suspend fun probe()
    suspend fun signIn(email: String, password: CharArray): SessionCandidate
    suspend fun profile(candidate: SessionCandidate): String
    suspend fun validate(candidate: SessionCandidate): ValidationResult.Verified = ValidationResult.Verified(profile(candidate))
}
