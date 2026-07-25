package com.kail.location.utils

import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.database.DatabaseUtils
import android.net.Uri
import androidx.preference.PreferenceManager
import com.kail.location.repositories.DataBaseHistoryLocation
import org.json.JSONArray
import org.json.JSONObject

/**
 * 数据导出 / 导入管理器。
 *
 * 负责将各导航菜单所持久化的偏好数据导出为可命名的 `.bak` 备份文件，以及从备份
 * 文件中按菜单选择性导入恢复。
 *
 * ### 运行模式（4 种）
 * 本应用有 4 种运行模式：`root` / `developer` / `sandbox` / `xposed`，存于默认
 * SharedPreferences 的 `setting_run_mode`。不同模式下抽屉菜单不同、数据存储位置
 * 也不同，因此导出 / 导入时按「模式分组 + 菜单」组织类别：
 *
 * - **通用（所有模式，含 developer）**：位置模拟、设置、路线模拟、NFC 模拟 —— 位置模拟
 *   存 SQLite（`HistoryLocation.db`），其余均存默认 SharedPreferences，所有模式共享。
 * - **Root 模式**：独立模拟、WiFi 模拟、基站模拟 —— 存默认 SharedPreferences，但仅在
 *   root 模式菜单中出现。
 * - **沙盒模式**：沙盒引擎 —— 存 BlackBox 独立的 `AppSharedPreferenceDelegate`
 *   SharedPreferences 文件。
 * - **Xposed 模式**：Xposed 设置 —— 存默认 SharedPreferences 中一组专属 `setting_*`
 *   键（与主设置页部分键不同，见下方分类登记）。
 *
 * developer 模式无专属菜单，仅复用「通用」分组数据，故不单列。
 *
 * ### 备份文件结构
 * 备份文件本质是 JSON 文本（`.bak` 仅为后缀）。类别值有两种形态：
 * SharedPreferences 类别为「键值对象」，SQLite 类别为「行数组」。
 * ```json
 * {
 *   "format": "kail_location_backup",
 *   "version": 1,
 *   "exportedAt": 1700000000000,
 *   "appVersion": "x.y.z",
 *   "categories": {
 *     "<prefsCategoryId>": {
 *       "<prefKey>": { "t": "s|b|i|f|l|ss", "v": <value> }
 *     },
 *     "<sqliteCategoryId>": [
 *       { "<column>": { "t": "s|i|l|f", "v": <value> } }
 *     ]
 *   }
 * }
 * ```
 * 每个偏好项 / 列值保留其原始类型（String / Boolean / Int / Float / Long / StringSet）。
 */
object DataTransferManager {

    const val FILE_FORMAT = "kail_location_backup"
    const val FILE_VERSION = 1

    /** 备份文件默认扩展名。 */
    const val FILE_EXTENSION = "bak"

    /** NFC 历史使用的独立 SharedPreferences 文件名。 */
    private const val NFC_PREFS_NAME = "nfc_history"

    /** 沙盒引擎（BlackBox）使用的独立 SharedPreferences 文件名。 */
    private const val SANDBOX_PREFS_NAME = "AppSharedPreferenceDelegate"

    /**
     * 数据存储后端。大多数类别存 SharedPreferences；位置模拟历史存 SQLite。
     */
    enum class Source { PREFS, SQLITE }

    /**
     * 数据类别所属的运行模式分组，用于在 UI 上把菜单按模式分清楚。
     *
     * 应用共有 4 种运行模式：root / developer / sandbox / xposed。其中 developer 模式
     * 没有专属菜单或专属数据，仅使用「通用」分组里的设置 + 位置/路线，故不单列分组。
     *
     * @property labelRes 分组标题字符串资源 id。
     */
    enum class ModeGroup(val labelRes: Int) {
        /** 通用（所有模式共享，含 developer 模式）。 */
        GENERAL(com.kail.location.R.string.data_transfer_group_general),
        /** Root 模式专属菜单。 */
        ROOT(com.kail.location.R.string.run_mode_root),
        /** 沙盒模式专属菜单。 */
        SANDBOX(com.kail.location.R.string.run_mode_sandbox),
        /** Xposed 模式专属菜单。 */
        XPOSED(com.kail.location.R.string.run_mode_xposed)
    }

    /**
     * 可导出 / 导入的数据类别，每一项对应一个导航菜单。
     *
     * @property id 稳定的类别标识（写入备份文件，切勿随意更改）。
     * @property titleRes 菜单名称对应的字符串资源 id。
     * @property group 该菜单所属的运行模式分组。
     * @property prefsName 若非 null，表示该类别数据存储在独立的命名 SharedPreferences 中；
     *   为 null 时表示存储在默认 SharedPreferences。
     * @property explicitKeys 若非 null，则只导出 / 导入这些指定的 key（对默认 prefs 与
     *   命名 prefs 均生效）。用于：1）命名 prefs 只取业务相关 key，避免导出内部无关状态；
     *   2）默认 prefs 中按「页面」而非「前缀」精确划定一组 key（如 Xposed 设置页）。
     * @property matches 用于判断默认 SharedPreferences 中某个 key 是否属于本类别
     *   （仅在 [prefsName] 与 [explicitKeys] 均为 null 时使用）。
     * @property source 数据存储后端：[Source.PREFS]（默认）走 SharedPreferences 逻辑；
     *   [Source.SQLITE] 表示该类别数据存于 SQLite 表，导出 / 导入走行级序列化。
     */
    data class Category(
        val id: String,
        val titleRes: Int,
        val group: ModeGroup,
        val prefsName: String? = null,
        val explicitKeys: List<String>? = null,
        val matches: (String) -> Boolean = { false },
        val source: Source = Source.PREFS
    )

    /**
     * 全部数据类别。新增菜单数据时在此登记即可。
     */
    val categories: List<Category> = listOf(
        // ── 通用（所有模式） ──
        Category(
            id = "location",
            titleRes = com.kail.location.R.string.nav_menu_location_simulation,
            group = ModeGroup.GENERAL,
            source = Source.SQLITE
        ),
        Category(
            id = "settings",
            titleRes = com.kail.location.R.string.nav_menu_settings,
            group = ModeGroup.GENERAL,
            matches = { key ->
                key.startsWith("setting_") ||
                    key == "kail_global_mode" ||
                    key == "kail_target_packages"
            }
        ),
        Category(
            id = "route",
            titleRes = com.kail.location.R.string.nav_menu_route_simulation,
            group = ModeGroup.GENERAL,
            matches = { key -> key.startsWith("route_sim_") || key == "saved_routes" }
        ),
        Category(
            id = "nfc",
            titleRes = com.kail.location.R.string.drawer_nfc_sim,
            group = ModeGroup.GENERAL,
            prefsName = NFC_PREFS_NAME
        ),
        // ── Root 模式专属 ──
        Category(
            id = "independent",
            titleRes = com.kail.location.R.string.nav_menu_independent_sim,
            group = ModeGroup.ROOT,
            matches = { key -> key.startsWith("independent_") }
        ),
        Category(
            id = "wifi",
            titleRes = com.kail.location.R.string.nav_menu_wifi_sim,
            group = ModeGroup.ROOT,
            matches = { key -> key.startsWith("wifi_sim_") }
        ),
        Category(
            id = "cell",
            titleRes = com.kail.location.R.string.nav_menu_cell_sim,
            group = ModeGroup.ROOT,
            matches = { key -> key.startsWith("cell_sim_") }
        ),
        // ── 沙盒模式专属 ──
        Category(
            id = "sandbox",
            titleRes = com.kail.location.R.string.drawer_sandbox,
            group = ModeGroup.SANDBOX,
            prefsName = SANDBOX_PREFS_NAME,
            explicitKeys = listOf(
                "mHideRoot",
                "mDaemonEnable",
                "mUseVpnNetwork",
                "mDisableFlagSecure"
            )
        ),
        // ── Xposed 模式专属 ──
        // Xposed 设置页（XposedSettingsScreen）管理的一组 key，存默认 prefs。
        // 这些 key 与主设置页不同（如 setting_disable_fused / setting_downgrade_cdma /
        // setting_anti_pullback 为 Xposed 专属），故用显式 key 精确划定。
        Category(
            id = "xposed",
            titleRes = com.kail.location.R.string.drawer_xposed_settings,
            group = ModeGroup.XPOSED,
            explicitKeys = listOf(
                "setting_gps_satellite_sim",
                "setting_disable_fused",
                "setting_hide_mock",
                "setting_disable_wifi_scan",
                "setting_downgrade_cdma",
                "setting_anti_pullback",
                "setting_min_satellites",
                "setting_report_interval"
            )
        )
    )

    private fun categoryById(id: String): Category? = categories.firstOrNull { it.id == id }

    private fun prefsFor(context: Context, category: Category): SharedPreferences {
        return if (category.prefsName != null) {
            context.getSharedPreferences(category.prefsName, Context.MODE_PRIVATE)
        } else {
            PreferenceManager.getDefaultSharedPreferences(context)
        }
    }

    /**
     * 收集某个类别当前已持久化的键值。
     */
    private fun collectEntries(context: Context, category: Category): Map<String, Any?> {
        val prefs = prefsFor(context, category)
        val all = prefs.all
        return when {
            // 指定了显式 key（默认 prefs 或命名 prefs 均适用）：只取已存在的指定 key
            category.explicitKeys != null ->
                all.filterKeys { it in category.explicitKeys }
            // 命名 prefs（整体）：如 NFC 历史
            category.prefsName != null -> all
            // 默认 prefs：按前缀匹配
            else -> all.filterKeys { category.matches(it) }
        }
    }

    /**
     * 返回当前设备上各类别已存在的数据条目数量。用于在 UI 上提示哪些菜单有可导出的数据。
     */
    fun availableEntryCounts(context: Context): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        categories.forEach { category ->
            result[category.id] = when (category.source) {
                Source.SQLITE -> sqliteRowCount(context, category)
                Source.PREFS -> collectEntries(context, category).size
            }
        }
        return result
    }

    /**
     * 将选中的类别数据导出到指定 Uri。
     *
     * @param selectedCategoryIds 要导出的类别 id 集合。
     * @return 成功返回 true。
     */
    fun export(context: Context, uri: Uri, selectedCategoryIds: Set<String>): Boolean {
        return runCatching {
            val root = JSONObject()
            root.put("format", FILE_FORMAT)
            root.put("version", FILE_VERSION)
            root.put("exportedAt", System.currentTimeMillis())
            root.put("appVersion", runCatching { GoUtils.getVersionName(context) }.getOrDefault(""))

            val categoriesJson = JSONObject()
            categories.filter { it.id in selectedCategoryIds }.forEach { category ->
                when (category.source) {
                    Source.SQLITE -> categoriesJson.put(category.id, exportSqlite(context, category))
                    Source.PREFS -> {
                        val entries = collectEntries(context, category)
                        val entriesJson = JSONObject()
                        entries.forEach { (key, value) ->
                            val typed = encodeValue(value) ?: return@forEach
                            entriesJson.put(key, typed)
                        }
                        categoriesJson.put(category.id, entriesJson)
                    }
                }
            }
            root.put("categories", categoriesJson)

            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                output.write(root.toString(2).toByteArray(Charsets.UTF_8))
            } ?: return false
            true
        }.getOrElse {
            KailLog.e(context, "DataTransfer", "export failed: ${it.message}")
            false
        }
    }

    /**
     * 解析备份文件，返回其中包含的类别 id 集合（用于在导入前让用户勾选）。
     * 解析失败返回 null。
     */
    fun parseBackup(context: Context, uri: Uri): ParsedBackup? {
        return runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            } ?: return null
            val root = JSONObject(text)
            if (root.optString("format") != FILE_FORMAT) return null
            val categoriesJson = root.optJSONObject("categories") ?: JSONObject()
            val present = mutableMapOf<String, Int>()
            val keys = categoriesJson.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val category = categoryById(id) ?: continue
                present[id] = when (category.source) {
                    // SQLite 类别：值为行数组
                    Source.SQLITE -> (categoriesJson.optJSONArray(id) ?: JSONArray()).length()
                    // Prefs 类别：值为键值对象
                    Source.PREFS -> (categoriesJson.optJSONObject(id) ?: JSONObject()).length()
                }
            }
            ParsedBackup(
                exportedAt = root.optLong("exportedAt", 0L),
                appVersion = root.optString("appVersion", ""),
                json = root,
                categoryEntryCounts = present
            )
        }.getOrElse {
            KailLog.e(context, "DataTransfer", "parse failed: ${it.message}")
            null
        }
    }

    /**
     * 将解析后的备份中、选中的类别数据写回 SharedPreferences。
     *
     * @return 实际成功导入的类别 id 集合。
     */
    fun import(context: Context, backup: ParsedBackup, selectedCategoryIds: Set<String>): Set<String> {
        val imported = mutableSetOf<String>()
        val categoriesJson = backup.json.optJSONObject("categories") ?: return imported
        selectedCategoryIds.forEach { id ->
            val category = categoryById(id) ?: return@forEach
            runCatching {
                when (category.source) {
                    Source.SQLITE -> {
                        val arr = categoriesJson.optJSONArray(id) ?: return@forEach
                        importSqlite(context, category, arr)
                        imported.add(id)
                    }
                    Source.PREFS -> {
                        val entriesJson = categoriesJson.optJSONObject(id) ?: return@forEach
                        val prefs = prefsFor(context, category)
                        val editor = prefs.edit()
                        val keys = entriesJson.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            // 命名 prefs 限定了 key 范围时，跳过范围外的 key，避免污染目标文件
                            if (category.explicitKeys != null && key !in category.explicitKeys) continue
                            val typed = entriesJson.optJSONObject(key) ?: continue
                            applyValue(editor, key, typed)
                        }
                        editor.apply()
                        imported.add(id)
                    }
                }
            }.onFailure {
                KailLog.e(context, "DataTransfer", "import category $id failed: ${it.message}")
            }
        }
        return imported
    }

    // ───────────────────────── 类型编解码 ─────────────────────────

    private fun encodeValue(value: Any?): JSONObject? {
        val obj = JSONObject()
        when (value) {
            is String -> { obj.put("t", "s"); obj.put("v", value) }
            is Boolean -> { obj.put("t", "b"); obj.put("v", value) }
            is Int -> { obj.put("t", "i"); obj.put("v", value) }
            is Long -> { obj.put("t", "l"); obj.put("v", value) }
            is Float -> { obj.put("t", "f"); obj.put("v", value.toDouble()) }
            is Set<*> -> {
                obj.put("t", "ss")
                val arr = JSONArray()
                value.forEach { if (it is String) arr.put(it) }
                obj.put("v", arr)
            }
            else -> return null
        }
        return obj
    }

    private fun applyValue(editor: SharedPreferences.Editor, key: String, typed: JSONObject) {
        when (typed.optString("t")) {
            "s" -> editor.putString(key, typed.optString("v", ""))
            "b" -> editor.putBoolean(key, typed.optBoolean("v", false))
            "i" -> editor.putInt(key, typed.optInt("v", 0))
            "l" -> editor.putLong(key, typed.optLong("v", 0L))
            "f" -> editor.putFloat(key, typed.optDouble("v", 0.0).toFloat())
            "ss" -> {
                val arr = typed.optJSONArray("v") ?: JSONArray()
                val set = HashSet<String>()
                for (i in 0 until arr.length()) set.add(arr.optString(i))
                editor.putStringSet(key, set)
            }
        }
    }

    // ───────────────────────── SQLite 导出 / 导入 ─────────────────────────

    /**
     * 位置模拟历史（[DataBaseHistoryLocation]）导出 / 导入的列集合（不含自增主键 ID）。
     * ID 在导入时由数据库自增生成，无需也不应跨设备保留。
     */
    private val SQLITE_LOCATION_COLUMNS = listOf(
        DataBaseHistoryLocation.DB_COLUMN_LOCATION,
        DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_WGS84,
        DataBaseHistoryLocation.DB_COLUMN_LATITUDE_WGS84,
        DataBaseHistoryLocation.DB_COLUMN_TIMESTAMP,
        DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_CUSTOM,
        DataBaseHistoryLocation.DB_COLUMN_LATITUDE_CUSTOM,
        DataBaseHistoryLocation.DB_COLUMN_FAVORITE
    )

    /** 统计某个 SQLite 类别当前的行数。 */
    private fun sqliteRowCount(context: Context, category: Category): Int {
        if (category.id != "location") return 0
        return runCatching {
            val helper = DataBaseHistoryLocation(context)
            try {
                DatabaseUtils.queryNumEntries(
                    helper.readableDatabase,
                    DataBaseHistoryLocation.TABLE_NAME
                ).toInt()
            } finally {
                helper.close()
            }
        }.getOrDefault(0)
    }

    /** 将某个 SQLite 类别的全部行导出为 JSON 行数组。 */
    private fun exportSqlite(context: Context, category: Category): JSONArray {
        val arr = JSONArray()
        if (category.id != "location") return arr
        val helper = DataBaseHistoryLocation(context)
        try {
            val cursor = helper.readableDatabase.query(
                DataBaseHistoryLocation.TABLE_NAME,
                null, null, null, null, null,
                DataBaseHistoryLocation.DB_COLUMN_TIMESTAMP + " ASC"
            )
            cursor.use { c ->
                while (c.moveToNext()) {
                    arr.put(rowToJson(c, SQLITE_LOCATION_COLUMNS))
                }
            }
        } finally {
            helper.close()
        }
        return arr
    }

    /** 将备份中的 JSON 行数组写回某个 SQLite 类别。 */
    private fun importSqlite(context: Context, category: Category, arr: JSONArray) {
        if (category.id != "location") return
        val helper = DataBaseHistoryLocation(context)
        try {
            val db = helper.writableDatabase
            for (i in 0 until arr.length()) {
                val row = arr.optJSONObject(i) ?: continue
                val lonWgs84 = cellString(row, DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_WGS84)
                val latWgs84 = cellString(row, DataBaseHistoryLocation.DB_COLUMN_LATITUDE_WGS84)
                // WGS84 经纬度为 NOT NULL 且是去重键，缺失则跳过该行
                if (lonWgs84.isEmpty() || latWgs84.isEmpty()) continue
                DataBaseHistoryLocation.addHistoryLocation(
                    db,
                    cellString(row, DataBaseHistoryLocation.DB_COLUMN_LOCATION),
                    lonWgs84,
                    latWgs84,
                    cellString(row, DataBaseHistoryLocation.DB_COLUMN_TIMESTAMP)
                        .ifEmpty { (System.currentTimeMillis() / 1000).toString() },
                    cellString(row, DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_CUSTOM),
                    cellString(row, DataBaseHistoryLocation.DB_COLUMN_LATITUDE_CUSTOM)
                )
            }
        } finally {
            helper.close()
        }
    }

    /** 把游标当前行的指定列编码为 `{ "<col>": { "t", "v" } }` 形式。 */
    private fun rowToJson(cursor: Cursor, columns: List<String>): JSONObject {
        val obj = JSONObject()
        columns.forEach { col ->
            val idx = cursor.getColumnIndex(col)
            if (idx < 0) return@forEach
            val cell = JSONObject()
            when (cursor.getType(idx)) {
                Cursor.FIELD_TYPE_INTEGER -> { cell.put("t", "l"); cell.put("v", cursor.getLong(idx)) }
                Cursor.FIELD_TYPE_FLOAT -> { cell.put("t", "f"); cell.put("v", cursor.getDouble(idx)) }
                Cursor.FIELD_TYPE_NULL -> { cell.put("t", "s"); cell.put("v", "") }
                else -> { cell.put("t", "s"); cell.put("v", cursor.getString(idx) ?: "") }
            }
            obj.put(col, cell)
        }
        return obj
    }

    /** 从一行 JSON 中按列名取出值并统一转为字符串（供 SQLite 写回使用）。 */
    private fun cellString(row: JSONObject, column: String): String {
        val cell = row.optJSONObject(column) ?: return ""
        if (cell.isNull("v")) return ""
        return cell.opt("v")?.toString() ?: ""
    }

    /**
     * 解析后的备份内容。
     *
     * @property categoryEntryCounts 备份中包含的类别 id -> 条目数量。
     */
    data class ParsedBackup(
        val exportedAt: Long,
        val appVersion: String,
        val json: JSONObject,
        val categoryEntryCounts: Map<String, Int>
    )
}
