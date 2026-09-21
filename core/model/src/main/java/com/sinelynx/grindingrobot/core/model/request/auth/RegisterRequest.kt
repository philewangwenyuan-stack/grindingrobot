package com.sinelynx.grindingrobot.core.model.request.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    @SerialName("identity") val identity: String,
    @SerialName("verifyCode") val verifyCode: String,
    @SerialName("password") val password: String,
    @SerialName("customerCode") val customerCode: String = "DEFAULT",
    @SerialName("userName") val userName: String? = null,
    @SerialName("realName") val realName: String? = null,
)
