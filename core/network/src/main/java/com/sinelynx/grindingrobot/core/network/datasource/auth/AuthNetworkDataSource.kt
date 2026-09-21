package com.sinelynx.grindingrobot.core.network.datasource.auth

import com.sinelynx.grindingrobot.core.model.request.auth.ChangePasswordRequest
import com.sinelynx.grindingrobot.core.model.request.auth.LoginRequest
import com.sinelynx.grindingrobot.core.model.request.auth.RegisterRequest
import com.sinelynx.grindingrobot.core.model.request.auth.ResetPasswordRequest
import com.sinelynx.grindingrobot.core.model.request.auth.SendCodeRequest
import com.sinelynx.grindingrobot.core.model.request.auth.VerifyCodeCheckRequest
import com.sinelynx.grindingrobot.core.model.request.auth.VerifyLoginRequest
import com.sinelynx.grindingrobot.core.network.datasource.base.NetworkDataSource
import com.sinelynx.grindingrobot.core.model.response.UserApiResponse
import com.sinelynx.grindingrobot.core.model.response.auth.AuthSessionResponse

interface AuthNetworkDataSource : NetworkDataSource {
    suspend fun sendVerifyCode(request: SendCodeRequest): UserApiResponse<Unit>
    suspend fun register(request: RegisterRequest): UserApiResponse<AuthSessionResponse>
    suspend fun login(request: LoginRequest): UserApiResponse<AuthSessionResponse>
    suspend fun resetPassword(request: ResetPasswordRequest): UserApiResponse<Unit>
    suspend fun changePassword(request: ChangePasswordRequest): UserApiResponse<Unit>
    suspend fun verifyCodeCheck(request: VerifyCodeCheckRequest): UserApiResponse<Unit>
    suspend fun verifyCodeLogin(request: VerifyLoginRequest): UserApiResponse<AuthSessionResponse>
}
