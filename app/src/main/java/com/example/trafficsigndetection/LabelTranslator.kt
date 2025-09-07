package com.example.trafficsigndetection

import android.content.Context
import org.json.JSONObject

class LabelTranslator(context: Context) {
    private val labelMap: Map<String, String>

    init {
        val json = context.assets.open("labels_map_vi.json")
            .bufferedReader().use { it.readText() }
        val jsonObject = JSONObject(json)
        labelMap = jsonObject.keys().asSequence().associateWith { jsonObject.getString(it) }
    }

    fun translate(label: String): String {
        return labelMap[label] ?: label
    }
}
