package com.sinelynx.grindingrobot.core.database
import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
object DatabaseConverters {
    private val json = Json { encodeDefaults = true }
    private val serializer = ListSerializer(String.serializer())
    @TypeConverter
    fun fromStringList(value: List<String>?): String = json.encodeToString(serializer, value.orEmpty())
    @TypeConverter
    fun toStringList(value: String?): List<String> =
        value?.takeIf { it.isNotBlank() }?.let { json.decodeFromString(serializer, it) } ?: emptyList()
}
