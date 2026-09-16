package com.dualsub.tv.network.webdrive

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 百度网盘 Device Code Flow 登录状态管理。
 *
 * 用法：
 * 1. 调 [startDeviceLogin]，等待回调 [onCodeReady]；
 * 2. UI 展示 user_code 和验证链接（二维码 + 说明文字）；
 * 3. 用户在手机/PC 上授权后，自动轮询拿到 token，回调 [onSuccess]；
 * 4. 应用保存 token 后调 [restoreFromToken] 恢复状态。
 */
class BaiduAuthManager {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val api = BaiduApiClient()

    private val _state = MutableStateFlow<BaiduAuthState>(BaiduAuthState.LoggedOut)
    val state: StateFlow<BaiduAuthState> = _state.asStateFlow()

    var accessToken: String? = null
        private set
    var refreshToken: String? = null
        private set

    val isLoggedIn: Boolean get() = accessToken != null

    /** 用持久化的 token 恢复登录态（无网络请求）。 */
    fun restoreFromToken(access: String, refresh: String) {
        accessToken = access
        refreshToken = refresh
        _state.value = BaiduAuthState.LoggedIn(access)
    }

    /** 退出登录，清空 token。 */
    fun logout() {
        accessToken = null
        refreshToken = null
        _state.value = BaiduAuthState.LoggedOut
    }

    /**
     * 开始 Device Code 登录流程。
     *
     * [onCodeReady] 在拿到 Device Code 后调用（切到主线程前由调用者用 withContext 处理）；
     * [onSuccess] 在授权完成后调用，携带新 access_token 和 refresh_token。
     */
    fun startDeviceLogin(
        onCodeReady: (result: BaiduDeviceCodeResult) -> Unit,
        onSuccess: (accessToken: String, refreshToken: String) -> Unit,
        onError: (msg: String) -> Unit
    ) {
        _state.value = BaiduAuthState.Loading
        scope.launch {
            try {
                val codeResult = api.getDeviceCode()
                _state.value = BaiduAuthState.WaitingForUser(codeResult)
                onCodeReady(codeResult)

                var attempts = 0
                val maxAttempts = codeResult.expiresIn / codeResult.interval.coerceAtLeast(5)
                var intervalMs = codeResult.interval * 1000L

                while (attempts < maxAttempts) {
                    delay(intervalMs)
                    attempts++
                    when (val result = api.pollToken(codeResult.deviceCode)) {
                        is BaiduPollResult.Success -> {
                            accessToken = result.token.accessToken
                            refreshToken = result.token.refreshToken
                            _state.value = BaiduAuthState.LoggedIn(result.token.accessToken)
                            onSuccess(result.token.accessToken, result.token.refreshToken)
                            return@launch
                        }
                        is BaiduPollResult.Pending -> Unit
                        is BaiduPollResult.SlowDown -> intervalMs += 5_000
                        is BaiduPollResult.Denied -> {
                            _state.value = BaiduAuthState.LoggedOut
                            onError("用户拒绝授权")
                            return@launch
                        }
                        is BaiduPollResult.Expired -> {
                            _state.value = BaiduAuthState.LoggedOut
                            onError("验证码已过期，请重新获取")
                            return@launch
                        }
                        is BaiduPollResult.Error -> {
                            _state.value = BaiduAuthState.LoggedOut
                            onError(result.message)
                            return@launch
                        }
                    }
                }
                _state.value = BaiduAuthState.LoggedOut
                onError("等待授权超时，请重试")
            } catch (e: Exception) {
                _state.value = BaiduAuthState.LoggedOut
                onError("登录失败：${e.message}")
            }
        }
    }

    /**
     * 自动刷新 access_token（在收到 [BaiduTokenExpiredException] 时调用）。
     * 成功后更新内部状态，返回新 access_token；失败时返回 null（需重新登录）。
     */
    suspend fun tryRefreshToken(): String? {
        val refresh = refreshToken ?: return null
        return try {
            val newToken = api.refreshToken(refresh)
            accessToken = newToken.accessToken
            refreshToken = newToken.refreshToken
            _state.value = BaiduAuthState.LoggedIn(newToken.accessToken)
            newToken.accessToken
        } catch (e: Exception) {
            null
        }
    }

    fun apiClient(): BaiduApiClient = api
}

sealed class BaiduAuthState {
    data object LoggedOut : BaiduAuthState()
    data object Loading : BaiduAuthState()
    data class WaitingForUser(val codeResult: BaiduDeviceCodeResult) : BaiduAuthState()
    data class LoggedIn(val accessToken: String) : BaiduAuthState()
}
