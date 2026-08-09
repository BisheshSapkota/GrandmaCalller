package com.grandmacaller.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * A relative grandma can call.
 * messengerId = the person's Messenger username or phone number linked to Messenger,
 * used to build the deep link (m.me/<messengerId>).
 * spokenNames = list of Nepali words/phrases she might say for this person
 * (e.g. ["बुबा", "बा"] for dad), used for fuzzy matching against speech.
 */
data class Relative(
    val id: String,
    val displayName: String,
    val messengerId: String,
    val spokenNames: List<String>
)

object RelativeStore {
    private const val PREFS = "grandma_caller_prefs"
    private const val KEY_RELATIVES = "relatives_json"

    fun load(context: Context): MutableList<Relative> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_RELATIVES, null) ?: return defaultSeed().toMutableList()
        val arr = JSONArray(json)
        val out = mutableListOf<Relative>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val names = mutableListOf<String>()
            val namesArr = o.getJSONArray("spokenNames")
            for (j in 0 until namesArr.length()) names.add(namesArr.getString(j))
            out.add(
                Relative(
                    id = o.getString("id"),
                    displayName = o.getString("displayName"),
                    messengerId = o.getString("messengerId"),
                    spokenNames = names
                )
            )
        }
        return out
    }

    fun save(context: Context, relatives: List<Relative>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = JSONArray()
        for (r in relatives) {
            val o = JSONObject()
            o.put("id", r.id)
            o.put("displayName", r.displayName)
            o.put("messengerId", r.messengerId)
            o.put("spokenNames", JSONArray(r.spokenNames))
            arr.put(o)
        }
        prefs.edit().putString(KEY_RELATIVES, arr.toString()).apply()
    }

    // Empty by default — you fill these in for your grandmother via Settings.
    private fun defaultSeed(): List<Relative> = emptyList()
}
