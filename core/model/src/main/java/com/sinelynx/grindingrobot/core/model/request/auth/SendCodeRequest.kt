package com.sinelynx.grindingrobot.core.model.request.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SendCodeRequest(
    @SerialName("identity") val identity: String,
    @SerialName("purpose") val purpose: String,
)
