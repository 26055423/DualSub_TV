package com.dualsub.tv.ai

import com.dualsub.tv.data.SettingsStore
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * TV 端内嵌 HTTP server（NanoHTTPD，端口 18080）。
 *
 * 用途：手机扫二维码后打开浏览器，在网页表单里填写 AI API 配置并提交，
 * TV 端通过 [configReceived] StateFlow 感知到新配置已保存。
 *
 * 生命周期由 AiSettingsScreen 管理：进入页面时 start()，离开时 stop()。
 */
class AiSubtitleConfigServer(
    private val settings: SettingsStore
) : NanoHTTPD(PORT) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _configReceived = MutableStateFlow(0)

    /** 每收到一次成功提交，值 +1，Compose 侧 LaunchedEffect 监听变化刷新 UI。 */
    val configReceived: StateFlow<Int> = _configReceived

    override fun serve(session: IHTTPSession): Response {
        return when {
            session.method == Method.GET && session.uri == "/" -> serveForm()
            session.method == Method.POST && session.uri == "/save" -> handleSave(session)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }
    }

    private fun serveForm(): Response {
        val html = """
            <!DOCTYPE html>
            <html lang="zh">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>DualSub TV · AI 字幕配置</title>
              <style>
                body { font-family: -apple-system, sans-serif; max-width: 480px;
                       margin: 32px auto; padding: 0 16px; background: #f5f5f5; color: #222; }
                h1 { font-size: 1.2rem; margin-bottom: 24px; }
                label { display: block; margin-top: 16px; font-size: .9rem; color: #555; }
                input, select { width: 100%; box-sizing: border-box;
                                padding: 10px; margin-top: 4px; border: 1px solid #ccc;
                                border-radius: 6px; font-size: 1rem; }
                button { margin-top: 24px; width: 100%; padding: 12px;
                         background: #1976D2; color: #fff; border: none;
                         border-radius: 6px; font-size: 1.1rem; cursor: pointer; }
                .tip { font-size: .8rem; color: #888; margin-top: 4px; }
                .success { color: green; margin-top: 16px; font-weight: bold; }
              </style>
            </head>
            <body>
              <h1>DualSub TV · AI 字幕配置</h1>
              <form method="post" action="/save">
                <label>API Base URL
                  <input name="base_url" type="url" required
                         placeholder="https://dashscope.aliyuncs.com/compatible-mode/v1">
                </label>
                <p class="tip">阿里百炼默认地址，也支持任何 OpenAI 兼容接口</p>
                <label>API Key
                  <input name="api_key" type="password" required placeholder="sk-...">
                </label>
                <label>模型
                  <input name="model" value="qwen3.8-omni-flash" required>
                </label>
                <label>目标语言
                  <input name="target_lang" value="中文">
                </label>
                <label>附加提示词（可选）
                  <input name="prompt" placeholder="例：本片为科幻题材，注意专有名词准确性">
                </label>
                <button type="submit">保存到电视</button>
              </form>
            </body>
            </html>
        """.trimIndent()
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
    }

    private fun handleSave(session: IHTTPSession): Response {
        val body = mutableMapOf<String, String>()
        session.parseBody(body)
        val params = session.parameters
        fun param(key: String) = params[key]?.firstOrNull().orEmpty()

        val config = AiSubtitleConfig(
            baseUrl    = param("base_url").ifBlank { "https://dashscope.aliyuncs.com/compatible-mode/v1" },
            apiKey     = param("api_key"),
            model      = param("model").ifBlank { "qwen3.8-omni-flash" },
            targetLang = param("target_lang").ifBlank { "中文" },
            prompt     = param("prompt")
        )

        scope.launch {
            settings.saveAiConfig(config)
            _configReceived.value++
        }

        val html = """
            <!DOCTYPE html><html lang="zh"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>已保存</title>
            <style>body{font-family:-apple-system,sans-serif;text-align:center;padding:48px 16px;}
            h1{color:#388E3C;}p{color:#555;}</style></head>
            <body><h1>✅ 已保存</h1><p>配置已同步到电视，可以关闭本页面了。</p></body></html>
        """.trimIndent()
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
    }

    companion object {
        const val PORT = 18080
    }
}
