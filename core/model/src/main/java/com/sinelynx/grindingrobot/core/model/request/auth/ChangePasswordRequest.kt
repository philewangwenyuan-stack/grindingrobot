package com.sinelynx.grindingrobot.core.model.request.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChangePasswordRequest(
    @SerialName("oldPassword") val oldPassword: String,
    @SerialName("newPassword") val newPassword: String,
)
