package com.kail.location.models

/**
 * 应用更新信息的数据类。
 *
 * @property version 新版本号字符串。
 * @property content 更新说明或变更内容。
 * @property downloadUrl 更新包下载地址。
 * @property filename 下载文件名。
 */
data class UpdateInfo(
    val version: String,
    val content: String,
    val downloadUrl: String,
    val filename: String
)
