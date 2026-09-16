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
 * 夸克网盘登录状态管理。
 *
 * 持有 Cookie，提供扫码登录轮询，负责向 [QuarkApiClient] 注入当前凭据。
 */
class QuarkAuthManager {

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _state = MutableStateFlow<QuarkAuthState>(QuarkAuthState.LoggedOut)
    val state: StateFlow<QuarkAuthState> = _state.asStateFlow()

    /** 当前有效的 Cookie 串；未登录时为 null。 */
    var cookie: String? = null
        private set

    /** 是否已登录。 */
    val isLoggedIn: Boolean get() = cookie != null

    /** 用已有 Cookie 恢复登录状态（应用启动时从持久化恢复）。 */
    fun restoreFromCookie(savedCookie: String) {
        cookie = savedCookie
        _state.value = QuarkAuthState.LoggedIn(savedCookie)
    }

    /** 退出登录，清空 Cookie。 */
    fun logout() {
        cookie = null
        _state.value = QuarkAuthState.LoggedOut
    }

    /**
     * 开始扫码登录流程。
     *
     * 1. 申请二维码；
     * 2. 每 2 秒轮询一次状态；
     * 3. 扫码成功后回调 [onSuccess]（主线程执行）。
     */
    fun startQrLogin(
        onQrReady: (qrUrl: String) -> Unit,
        onScanned: () -> Unit,
        onSuccess: (cookie: String) -> Unit,
        onError: (msg: String) -> Unit
    ) {
        _state.value = QuarkAuthState.Loading
        scope.launch {
            try {
                val tempClient = QuarkApiClient(cookie = "")
                val session = tempClient.requestQrSession()
                _state.value = QuarkAuthState.QrReady(session.qrUrl)
                onQrReady(session.qrUrl)

                var attempts = 0
                while (attempts < 90) {
                    delay(2_000)
                    attempts++
                    when (val status = tempClient.pollQrStatus(session.token)) {
                        is QuarkQrStatus.Waiting -> Unit
                        is QuarkQrStatus.Scanned -> {
                            _state.value = QuarkAuthState.Scanned
                            onScanned()
                        }
                        is QuarkQrStatus.Authorized -> {
                            cookie = status.cookie
                            _state.value = QuarkAuthState.LoggedIn(status.cookie)
                            onSuccess(status.cookie)
                            return@launch
                        }
                        is QuarkQrStatus.Expired -> {
                            _state.value = QuarkAuthState.LoggedOut
                            onError("二维码已过期，请重新获取")
                            return@launch
                        }
                    }
                }
                _state.value = QuarkAuthState.LoggedOut
                onError("等待超时（3 分钟），请重试")
            } catch (e: Exception) {
                _state.value = QuarkAuthState.LoggedOut
                onError("登录失败：${e.message}")
            }
        }
    }

    /** 返回带当前 Cookie 的 API 客户端；未登录时抛出异常。 */
    fun apiClient(): QuarkApiClient {
        val c = cookie ?: throw IllegalStateException("未登录夸克网盘")
        return QuarkApiClient(c)
    }
}

sealed class QuarkAuthState {
    data object LoggedOut : QuarkAuthState()
    data object Loading : QuarkAuthState()
    data class QrReady(val qrUrl: String) : QuarkAuthState()
    data object Scanned : QuarkAuthState()
    data class LoggedIn(val cookie: String) : QuarkAuthState()
}
