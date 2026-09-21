package com.sinelynx.grindingrobot.core.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserApiResponse<T>(
    @SerialName("resultCode")
    val resultCode: Int = 0,
    @SerialName("resultInfo")
    val resultInfo: String? = null,
    @SerialName("data")
    val data: T? = null,
    @SerialName("total")
    val total: Int? = null,
    @SerialName("page")
    val page: Int? = null,
    @SerialName("size")
    val size: Int? = null,
) {
    val isSucceeded: Boolean
        get() = resultCode == 0
}
