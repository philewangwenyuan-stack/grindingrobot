package com.sinelynx.grindingrobot.core.network.datasource.auth

import com.sinelynx.grindingrobot.core.model.request.auth.ChangePasswordRequest
import com.sinelynx.grindingrobot.core.model.request.auth.LoginRequest
import com.sinelynx.grindingrobot.core.model.request.auth.RegisterRequest
import com.sinelynx.grindingrobot.core.model.request.auth.ResetPasswordRequest
import com.sinelynx.grindingrobot.core.model.request.auth.SendCodeRequest
import com.sinelynx.grindingrobot.core.model.request.auth.VerifyCodeCheckRequest
import com.sinelynx.grindingrobot.core.model.request.auth.VerifyLoginRequest
import com.sinelynx.grindingrobot.core.model.response.UserApiResponse
import com.sinelynx.grindingrobot.core.model.response.auth.AuthSessionResponse
import com.sinelynx.grindingrobot.core.network.base.BaseNetworkDataSource
import com.sinelynx.grindingrobot.core.network.service.AuthService
import javax.inject.Inject

class AuthNetworkDataSourceImpl @Inject constructor(
    private val authService: AuthService,
) : BaseNetworkDataSource(), AuthNetworkDataSource {

    override suspend fun sendVerifyCode(request: SendCodeRequest): UserApiResponse<Unit> {
        return authService.sendVerifyCode(request)
    }

    override suspend fun register(request: RegisterRequest): UserApiResponse<AuthSessionResponse> {
        return authService.register(request)
    }

    override suspend fun login(request: LoginRequest): UserApiResponse<AuthSessionResponse> {
        return authService.login(request)
    }

    override suspend fun resetPassword(request: ResetPasswordRequest): UserApiResponse<Unit> {
        return authService.resetPassword(request)
    }

    override suspend fun changePassword(request: ChangePasswordRequest): UserApiResponse<Unit> {
        return authService.changePassword(request)
    }

    override suspend fun verifyCodeCheck(request: VerifyCodeCheckRequest): UserApiResponse<Unit> {
        return authService.verifyCodeCheck(request)
    }

    override suspend fun verifyCodeLogin(request: VerifyLoginRequest): UserApiResponse<AuthSessionResponse> {
        return authService.verifyCodeLogin(request)
    }
}
