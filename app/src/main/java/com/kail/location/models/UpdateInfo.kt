package com.kail.location.models

/**
 * 应用更新信息的数据类。
 *
 * @property version 新版本号字符串。
 * @property content 更新说明或变更内容。
 * @property downloadUrl 更新包下载地址。
 * @property filename 下载文件名。
 * @property forceUpdate 是否强制更新（为 true 时用户不可取消）。
 * @property fileSize 安装包大小（字节）。
 * @property fallbackUrl 主下载地址失败时的备用下载地址（GitHub）。
 * @property fallbackFilename 备用下载地址对应的文件名。
 */
data class UpdateInfo(
    val version: String,
    val content: String,
    val downloadUrl: String,
    val filename: String,
    val forceUpdate: Boolean = false,
    val fileSize: Long = 0L,
    val fallbackUrl: String? = null,
    val fallbackFilename: String = "KailLocation.apk"
)
