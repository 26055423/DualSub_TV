package com.dualsub.tv.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dualsub.tv.network.RemoteLocation
import com.dualsub.tv.network.RemoteType
import com.dualsub.tv.player.SubtitleSource
import com.dualsub.tv.player.SubtitleStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "dualsub_settings")

/** 字段分隔符：Unit Separator，几乎不可能出现在文件名或 URI 里。 */
private const val SEPARATOR = "\u001F"

/** 记录分隔符：Record Separator，用于把多条网络位置串成一个字符串。 */
private const val RECORD_SEPARATOR = "\u001E"

private const val KEY_REMOTE_LOCATIONS = "remote_locations"

/**
 * 偏好持久化。
 *
 * - 字幕样式是全局的（一次调好，所有片子通用）；
 * - 「这片用哪两路字幕」按视频分别记录，播放进度不落盘；
 * - 网络位置（SMB / DLNA 服务器）连**密码**一起明文存在应用私有目录。
 */
class SettingsStore(private val context: Context) {

    init {
        // 清理遗留的断点续播 key（旧版落盘，新版不再使用）
        CoroutineScope(Dispatchers.IO).launch {
            context.settingsDataStore.edit { prefs ->
                prefs.asMap().keys.filter { it.name.startsWith("position$SEPARATOR") }
                    .forEach { prefs.remove(it) }
            }
        }
    }

    val primaryStyle: Flow<SubtitleStyle> = context.settingsDataStore.data
        .map { it.readStyle(PREFIX_PRIMARY, SubtitleStyle.PRIMARY) }

    val secondaryStyle: Flow<SubtitleStyle> = context.settingsDataStore.data
        .map { it.readStyle(PREFIX_SECONDARY, SubtitleStyle.SECONDARY) }

    fun primarySource(videoKey: String): Flow<SubtitleSource> = context.settingsDataStore.data
        .map { decodeSource(it[stringPreferencesKey("primary_source$SEPARATOR$videoKey")]) }

    fun hasPrimarySource(videoKey: String): Flow<Boolean> = context.settingsDataStore.data
        .map { it.contains(stringPreferencesKey("primary_source$SEPARATOR$videoKey")) }

    fun secondarySource(videoKey: String): Flow<SubtitleSource> = context.settingsDataStore.data
        .map { decodeSource(it[stringPreferencesKey("secondary_source$SEPARATOR$videoKey")]) }

    suspend fun setPrimarySource(videoKey: String, source: SubtitleSource) {
        context.settingsDataStore.edit { prefs ->
            prefs[stringPreferencesKey("primary_source$SEPARATOR$videoKey")] = encodeSource(source)
        }
    }

    suspend fun setSecondarySource(videoKey: String, source: SubtitleSource) {
        context.settingsDataStore.edit { prefs ->
            prefs[stringPreferencesKey("secondary_source$SEPARATOR$videoKey")] = encodeSource(source)
        }
    }

    suspend fun setPrimaryStyle(style: SubtitleStyle) = writeStyle(PREFIX_PRIMARY, style)

    suspend fun setSecondaryStyle(style: SubtitleStyle) = writeStyle(PREFIX_SECONDARY, style)

    // ---------------------------------------------------------------- 网络位置

    fun remoteLocations(): Flow<List<RemoteLocation>> = context.settingsDataStore.data
        .map { prefs -> decodeLocations(prefs[stringPreferencesKey(KEY_REMOTE_LOCATIONS)]) }

    suspend fun saveRemoteLocations(locations: List<RemoteLocation>) {
        context.settingsDataStore.edit { prefs ->
            prefs[stringPreferencesKey(KEY_REMOTE_LOCATIONS)] =
                locations.joinToString(RECORD_SEPARATOR) { encodeLocation(it) }
        }
    }

    private suspend fun writeStyle(prefix: String, style: SubtitleStyle) {
        context.settingsDataStore.edit { prefs ->
            prefs[intPreferencesKey("${prefix}_font_size")] = style.fontSizeSp
            prefs[intPreferencesKey("${prefix}_text_color")] = style.textColor
            prefs[intPreferencesKey("${prefix}_outline_color")] = style.outlineColor
            prefs[floatPreferencesKey("${prefix}_outline_width")] = style.outlineWidth
            prefs[intPreferencesKey("${prefix}_bottom_padding")] = style.bottomPaddingDp
            prefs[booleanPreferencesKey("${prefix}_bold")] = style.bold
        }
    }

    private fun Preferences.readStyle(prefix: String, fallback: SubtitleStyle): SubtitleStyle = SubtitleStyle(
        fontSizeSp = this[intPreferencesKey("${prefix}_font_size")] ?: fallback.fontSizeSp,
        textColor = this[intPreferencesKey("${prefix}_text_color")] ?: fallback.textColor,
        outlineColor = this[intPreferencesKey("${prefix}_outline_color")] ?: fallback.outlineColor,
        outlineWidth = this[floatPreferencesKey("${prefix}_outline_width")] ?: fallback.outlineWidth,
        bottomPaddingDp = this[intPreferencesKey("${prefix}_bottom_padding")] ?: fallback.bottomPaddingDp,
        bold = this[booleanPreferencesKey("${prefix}_bold")] ?: fallback.bold
    )

    private fun encodeSource(source: SubtitleSource): String = when (source) {
        SubtitleSource.None -> "none"
        is SubtitleSource.ExternalFile -> listOf("file", source.uri, source.displayName).joinToString(SEPARATOR)
        is SubtitleSource.EmbeddedTrack -> listOf(
            "embedded",
            source.trackIndex.toString(),
            source.mimeType.orEmpty(),
            source.language.orEmpty(),
            source.label
        ).joinToString(SEPARATOR)
    }

    private fun decodeSource(raw: String?): SubtitleSource {
        if (raw.isNullOrBlank()) return SubtitleSource.None
        val parts = raw.split(SEPARATOR)
        return when (parts.firstOrNull()) {
            "file" -> if (parts.size >= 3) {
                SubtitleSource.ExternalFile(parts[1], parts[2])
            } else {
                SubtitleSource.None
            }

            "embedded" -> {
                val index = parts.getOrNull(1)?.toIntOrNull()
                if (index == null) {
                    SubtitleSource.None
                } else {
                    SubtitleSource.EmbeddedTrack(
                        trackIndex = index,
                        mimeType = parts.getOrNull(2)?.ifBlank { null },
                        language = parts.getOrNull(3)?.ifBlank { null },
                        label = parts.getOrNull(4).orEmpty()
                    )
                }
            }

            else -> SubtitleSource.None
        }
    }

    private fun encodeLocation(location: RemoteLocation): String = listOf(
        location.id,
        location.type.name,
        location.displayName,
        location.host,
        location.share.orEmpty(),
        location.username.orEmpty(),
        location.password.orEmpty(),
        location.domain.orEmpty(),
        location.descriptionUrl.orEmpty(),
        location.controlUrl.orEmpty(),
        location.token.orEmpty(),
        location.refreshToken.orEmpty()
    ).joinToString(SEPARATOR)

    private fun decodeLocations(raw: String?): List<RemoteLocation> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(RECORD_SEPARATOR).mapNotNull { decodeLocation(it) }
    }

    private fun decodeLocation(raw: String): RemoteLocation? {
        val parts = raw.split(SEPARATOR)
        if (parts.size < 4) return null
        val type = RemoteType.entries.firstOrNull { it.name == parts[1] } ?: return null
        return RemoteLocation(
            id = parts[0],
            type = type,
            displayName = parts[2],
            host = parts[3],
            share = parts.getOrNull(4)?.ifBlank { null },
            username = parts.getOrNull(5)?.ifBlank { null },
            password = parts.getOrNull(6)?.ifBlank { null },
            domain = parts.getOrNull(7)?.ifBlank { null },
            descriptionUrl = parts.getOrNull(8)?.ifBlank { null },
            controlUrl = parts.getOrNull(9)?.ifBlank { null },
            token = parts.getOrNull(10)?.ifBlank { null },
            refreshToken = parts.getOrNull(11)?.ifBlank { null }
        )
    }

    private companion object {
        const val PREFIX_PRIMARY = "primary"
        const val PREFIX_SECONDARY = "secondary"
    }
}
