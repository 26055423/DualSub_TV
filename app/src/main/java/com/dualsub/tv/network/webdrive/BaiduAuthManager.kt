package com.dualsub.tv.network.webdrive

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
     * 开始二维码登录流程。
     *
     * 流程：获取 QR 码 → UI 显示 → 每 3 秒轮询扫码状态 → 授权后用 code 换 token。
     * [onSuccess] 授权完成后调用，携带 access_token 和 refresh_token。
     */
    fun startQrLogin(
        onSuccess: (accessToken: String, refreshToken: String) -> Unit,
        onError: (msg: String) -> Unit
    ) {
        _state.value = BaiduAuthState.Loading
        scope.launch {
            try {
                val session = api.getQrCode()
                _state.value = BaiduAuthState.QrReady(session)

                val maxAttempts = session.expiresIn / 3
                repeat(maxAttempts) { attempt ->
                    delay(3_000)
                    when (val status = api.pollQrStatus(session.qrcodeKey)) {
                        is BaiduQrStatus.Waiting -> Unit
                        is BaiduQrStatus.Scanned -> _state.value = BaiduAuthState.Scanned
                        is BaiduQrStatus.Authorized -> {
                            val token = api.exchangeToken(status.code)
                            accessToken = token.accessToken
                            refreshToken = token.refreshToken
                            _state.value = BaiduAuthState.LoggedIn(token.accessToken)
                            onSuccess(token.accessToken, token.refreshToken)
                            return@launch
                        }
                        is BaiduQrStatus.Denied -> {
                            _state.value = BaiduAuthState.LoggedOut
                            onError("用户取消授权")
                            return@launch
                        }
                        is BaiduQrStatus.Expired -> {
                            _state.value = BaiduAuthState.LoggedOut
                            onError("二维码已过期，请重新获取")
                            return@launch
                        }
                    }
                }
                _state.value = BaiduAuthState.LoggedOut
                onError("等待扫码超时，请重试")
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
    data class QrReady(val session: BaiduQrSession) : BaiduAuthState()
    data object Scanned : BaiduAuthState()
    data class LoggedIn(val accessToken: String) : BaiduAuthState()
}
