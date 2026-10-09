package io.github.ercanakalar.restrictions.core

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.DayOfWeek

/** Stores schedules as a small JSON array in SharedPreferences. */
object ScheduleJson {

    fun encode(schedules: List<Schedule>): String {
        val array = JSONArray()
        for (s in schedules) {
            array.put(
                JSONObject()
                    .put("id", s.id)
                    .put("name", s.name)
                    .put("start", s.startMinute)
                    .put("end", s.endMinute)
                    .put("days", JSONArray(s.days.map { it.value }.sorted()))
                    .put("enabled", s.enabled),
            )
        }
        return array.toString()
    }

    fun decode(json: String?): List<Schedule> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { parse(array.optJSONObject(it)) }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    private fun parse(obj: JSONObject?): Schedule? {
        if (obj == null) return null
        val dayValues = obj.optJSONArray("days") ?: JSONArray()
        val days = (0 until dayValues.length())
            .mapNotNull { index -> dayValues.optInt(index).takeIf { it in 1..7 }?.let(DayOfWeek::of) }
            .toSet()
        return try {
            Schedule(
                id = obj.getString("id"),
                name = obj.optString("name"),
                startMinute = obj.getInt("start"),
                endMinute = obj.getInt("end"),
                days = days,
                enabled = obj.optBoolean("enabled", true),
            )
        } catch (e: JSONException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
