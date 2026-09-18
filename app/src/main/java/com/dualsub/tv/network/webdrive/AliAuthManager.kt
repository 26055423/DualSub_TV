package com.dualsub.tv.network.webdrive

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AliAuthManager {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val api = AliApiClient()

    private val _state = MutableStateFlow<AliAuthState>(AliAuthState.LoggedOut)
    val state: StateFlow<AliAuthState> = _state.asStateFlow()

    @Volatile var accessToken: String? = null
        private set
    @Volatile var refreshToken: String? = null
        private set
    @Volatile var driveId: String? = null
        private set

    val isLoggedIn: Boolean get() = accessToken != null

    fun restoreFromToken(access: String, refresh: String, drive: String) {
        accessToken = access
        refreshToken = refresh
        driveId = drive
        api.updateToken(access)
        _state.value = AliAuthState.LoggedIn(access)
    }

    fun logout() {
        accessToken = null
        refreshToken = null
        driveId = null
        _state.value = AliAuthState.LoggedOut
    }

    fun startQrLogin(
        onSuccess: (accessToken: String, refreshToken: String, driveId: String) -> Unit,
        onError: (msg: String) -> Unit
    ) {
        _state.value = AliAuthState.Loading
        scope.launch {
            try {
                val session = api.getQrCode()
                _state.value = AliAuthState.QrReady(session.qrCodeUrl, session.sid)

                repeat(100) { // 最多轮询 100 次，约 5 分钟
                    delay(3_000)
                    when (val status = api.pollQrStatus(session.sid)) {
                        is AliQrStatus.Waiting -> Unit
                        is AliQrStatus.Scanned -> _state.value = AliAuthState.Scanned
                        is AliQrStatus.Authorized -> {
                            val token = api.exchangeToken(status.authCode)
                            api.updateToken(token.accessToken)
                            val userInfo = api.getUserInfo()
                            accessToken = token.accessToken
                            refreshToken = token.refreshToken
                            driveId = userInfo.driveId
                            _state.value = AliAuthState.LoggedIn(token.accessToken)
                            onSuccess(token.accessToken, token.refreshToken, userInfo.driveId)
                            return@launch
                        }
                        is AliQrStatus.Expired -> {
                            _state.value = AliAuthState.LoggedOut
                            onError("二维码已过期，请重新获取")
                            return@launch
                        }
                    }
                }
                _state.value = AliAuthState.LoggedOut
                onError("等待扫码超时，请重试")
            } catch (e: Exception) {
                _state.value = AliAuthState.LoggedOut
                onError("登录失败：${e.message}")
            }
        }
    }

    suspend fun tryRefreshToken(): String? {
        val refresh = refreshToken ?: return null
        return try {
            val newToken = api.refreshToken(refresh)
            accessToken = newToken.accessToken
            refreshToken = newToken.refreshToken
            api.updateToken(newToken.accessToken)
            _state.value = AliAuthState.LoggedIn(newToken.accessToken)
            newToken.accessToken
        } catch (e: Exception) {
            null
        }
    }

    fun apiClient(): AliApiClient = api
}

sealed class AliAuthState {
    data object LoggedOut : AliAuthState()
    data object Loading : AliAuthState()
    data class QrReady(val qrUrl: String, val sid: String) : AliAuthState()
    data object Scanned : AliAuthState()
    data class LoggedIn(val accessToken: String) : AliAuthState()
}
