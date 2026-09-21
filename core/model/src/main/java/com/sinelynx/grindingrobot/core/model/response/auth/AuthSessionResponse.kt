package com.sinelynx.grindingrobot.core.model.response.auth

import com.sinelynx.grindingrobot.core.model.entity.User
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthSessionResponse(
    @SerialName("token") val token: String? = null,
    @SerialName("user") val user: User? = null,
)
