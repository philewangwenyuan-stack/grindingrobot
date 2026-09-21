package com.sinelynx.grindingrobot.core.datastore.datasource.auth

import com.sinelynx.grindingrobot.core.model.entity.Auth
import com.sinelynx.grindingrobot.core.model.entity.User

/**
 * 本地用户认证相关数据源接口
 *
 * @author Dreamj
 */
interface AuthStoreDataSource {

    /**
     * 保存认证信息
     *
     * @param auth 认证信息对象
     * @author Dreamj
     */
    suspend fun saveAuth(auth: Auth)

    /**
     * 获取认证信息
     *
     * @return 认证信息对象，如不存在则返回null
     * @author Dreamj
     */
    suspend fun getAuth(): Auth?

    /**
     * 获取用户 token
     *
     * @return token字符串，如不存在则返回null
     * @author Dreamj
     */
    suspend fun getToken(): String?

    /**
     * 清除认证信息
     *
     * @author Dreamj
     */
    suspend fun clearAuth()

    /**
     * 检查是否已登录（有认证信息且未过期）
     *
     * @return 是否已登录
     * @author Dreamj
     */
    suspend fun isLoggedIn(): Boolean

    /**
     * 保存用户信息
     *
     * @param user 用户信息对象
     * @author Dreamj
     */
    suspend fun saveUserInfo(user: User)

    /**
     * 获取用户信息
     *
     * @return 用户信息对象，如不存在则返回null
     * @author Dreamj
     */
    suspend fun getUserInfo(): User?

    /**
     * 清除用户信息
     *
     * @author Dreamj
     */
    suspend fun clearUserInfo()

    /**
     * 清除所有信息
     *
     * @author Dreamj
     */
    suspend fun clearAll()
}
