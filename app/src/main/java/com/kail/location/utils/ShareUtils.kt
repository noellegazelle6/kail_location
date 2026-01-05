package com.kail.location.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * 文件与文本分享工具。
 */
object ShareUtils {
    /**
     * 通过 FileProvider 将文件转换为 URI。
     *
     * @param context 用于获取包名的上下文。
     * @param file 目标文件。
     * @return 文件的 URI。
     */
    @JvmStatic
    fun getUriFromFile(context: Context, file: File): Uri {
        val authority = context.packageName + ".fileProvider"
        return FileProvider.getUriForFile(context, authority, file)
    }

    /**
     * 通过系统分享面板分享文件。
     *
     * @param context 用于启动分享的上下文。
     * @param file 要分享的文件。
     * @param title 选择器标题。
     */
    @JvmStatic
    fun shareFile(context: Context, file: File, title: String?) {
        val share = Intent(Intent.ACTION_SEND)
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        share.putExtra(Intent.EXTRA_STREAM, getUriFromFile(context, file))
        share.type = "application/octet-stream"
        context.startActivity(Intent.createChooser(share, title))
    }

    /**
     * 通过系统分享面板分享文本。
     *
     * @param context 用于启动分享的上下文。
     * @param title 选择器标题及主题。
     * @param text 要分享的文本。
     */
    @JvmStatic
    fun shareText(context: Context, title: String?, text: String?) {
        val share = Intent(Intent.ACTION_SEND)
        share.type = "application/plain"
        share.putExtra(Intent.EXTRA_TEXT, text)
        share.putExtra(Intent.EXTRA_SUBJECT, title)
        context.startActivity(Intent.createChooser(share, title))
    }
}
