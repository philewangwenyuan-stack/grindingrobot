package com.sinelynx.grindingrobot.core.model.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthUser(
    @SerialName("userCode") val userCode: String? = null,
    @SerialName("userName") val userName: String? = null,
    @SerialName("realName") val realName: String? = null,
    @SerialName("mobile") val mobile: String? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("customerCode") val customerCode: String? = null,
    @SerialName("avatar") val avatar: String? = null,
)
