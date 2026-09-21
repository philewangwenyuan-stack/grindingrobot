package com.sinelynx.grindingrobot.core.model.request.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    @SerialName("account") val account: String,
    @SerialName("password") val password: String,
)
