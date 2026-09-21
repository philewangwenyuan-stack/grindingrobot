package com.sinelynx.grindingrobot.core.network.service

import com.sinelynx.grindingrobot.core.model.request.auth.ChangePasswordRequest
import com.sinelynx.grindingrobot.core.model.request.auth.LoginRequest
import com.sinelynx.grindingrobot.core.model.request.auth.RegisterRequest
import com.sinelynx.grindingrobot.core.model.request.auth.ResetPasswordRequest
import com.sinelynx.grindingrobot.core.model.request.auth.SendCodeRequest
import com.sinelynx.grindingrobot.core.model.request.auth.VerifyCodeCheckRequest
import com.sinelynx.grindingrobot.core.model.request.auth.VerifyLoginRequest
import com.sinelynx.grindingrobot.core.model.response.UserApiResponse
import com.sinelynx.grindingrobot.core.model.response.auth.AuthSessionResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthService {

    @POST("api/0.4/user/verify/send")
    suspend fun sendVerifyCode(@Body request: SendCodeRequest): UserApiResponse<Unit>

    @POST("api/0.4/user/register")
    suspend fun register(@Body request: RegisterRequest): UserApiResponse<AuthSessionResponse>

    @POST("api/0.4/user/login")
    suspend fun login(@Body request: LoginRequest): UserApiResponse<AuthSessionResponse>

    @POST("api/0.4/user/password/reset")
    suspend fun resetPassword(@Body request: ResetPasswordRequest): UserApiResponse<Unit>

    @POST("api/0.4/user/password/change")
    suspend fun changePassword(@Body request: ChangePasswordRequest): UserApiResponse<Unit>

    @POST("api/0.4/user/verify/check")
    suspend fun verifyCodeCheck(@Body request: VerifyCodeCheckRequest): UserApiResponse<Unit>

    @POST("api/0.4/user/login/verify")
    suspend fun verifyCodeLogin(@Body request: VerifyLoginRequest): UserApiResponse<AuthSessionResponse>
}
