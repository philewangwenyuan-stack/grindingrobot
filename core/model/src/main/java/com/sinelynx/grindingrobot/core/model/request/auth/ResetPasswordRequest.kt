package com.sinelynx.grindingrobot.core.model.request.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ResetPasswordRequest(
    @SerialName("identity") val identity: String,
    @SerialName("verifyCode") val verifyCode: String,
    @SerialName("newPassword") val newPassword: String,
)
