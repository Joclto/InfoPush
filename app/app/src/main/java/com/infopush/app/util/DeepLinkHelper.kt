package com.infopush.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri

object DeepLinkHelper {

    /**
     * 创建打开 URL 的 Intent
     * Android 系统会自动根据已安装的 App 的 intent-filter 匹配：
     * - 如果有 App 注册了该 URL 的 App Links / Deep Links，会直接跳转到该 App
     * - 例如网易 BUFF 的链接会被 BUFF App 拦截并打开
     * - 如果没有匹配的 App，则用默认浏览器打开
     */
    fun createIntent(context: Context, url: String): Intent {
        val uri = Uri.parse(url)
        return Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * 打开 URL，优先尝试跳转到对应 App
     */
    fun openUrl(context: Context, url: String) {
        val intent = createIntent(context, url)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // 如果没有任何 App 可以处理，用浏览器打开
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(browserIntent)
            } catch (_: Exception) {
                // 无法打开
            }
        }
    }
}
