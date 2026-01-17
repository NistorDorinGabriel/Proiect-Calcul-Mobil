package com.example.utilitiestracker.data.remote.eurostat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class EurostatTariffClient(
    private val http: OkHttpClient = OkHttpClient()
) {
    /**
     * Eurostat Statistics API:
     * https://ec.europa.eu/eurostat/api/dissemination/statistics/1.0/data/{dataset}?format=JSON&lang=EN&filters...
     * lastTimePeriod = N returnează ultimele N perioade disponibile. :contentReference[oaicite:2]{index=2}
     */
    suspend fun fetchLatestEurPerKwh(datasetCode: String, geo: String = "RO"): Double = withContext(Dispatchers.IO) {
        val url =
            "https://ec.europa.eu/eurostat/api/dissemination/statistics/1.0/data/$datasetCode" +
                    "?format=JSON&lang=EN&geo=$geo&lastTimePeriod=1"

        val req = Request.Builder().url(url).get().build()
        val body = http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Eurostat HTTP ${resp.code}")
            resp.body?.string() ?: error("Empty response from Eurostat")
        }

        val json = JSONObject(body)
        EurostatJsonStat.pickBestEurPerKwh(json)
    }
}

private object EurostatJsonStat {

    fun pickBestEurPerKwh(root: JSONObject): Double {
        val ids = root.getJSONArray("id")
        val sizes = root.getJSONArray("size")
        val dims = root.getJSONObject("dimension")

        // Alege o categorie "cea mai bună" pe fiecare dimensiune (tax incl., unit €/kWh, etc.)
        val chosenPosByDim = mutableMapOf<String, Int>()

        for (i in 0 until ids.length()) {
            val dimId = ids.getString(i)
            val dimObj = dims.getJSONObject(dimId)
            val category = dimObj.getJSONObject("category")

            val labels = readLabels(category)
            val pos = choosePosition(dimId, category, labels)
            chosenPosByDim[dimId] = pos
        }

        val flatIndex = computeFlatIndex(ids, sizes, chosenPosByDim)
        val valueAny = root.get("value")

        val value = when (valueAny) {
            is JSONArray -> valueAny.optDouble(flatIndex, Double.NaN)
            is JSONObject -> valueAny.optDouble(flatIndex.toString(), Double.NaN)
            else -> Double.NaN
        }

        if (value.isNaN()) error("Nu am găsit o valoare €/kWh în răspunsul Eurostat (index=$flatIndex).")
        return value
    }

    private fun choosePosition(dimId: String, category: JSONObject, labels: List<Pair<String, String>>): Int {
        // dacă dimensiunea are o singură opțiune
        val n = labels.size
        if (n <= 1) return 0

        val dimLower = dimId.lowercase(Locale.ROOT)

        // Heuristici: preferăm "including taxes", "€/kWh", "medium band" etc.
        fun bestMatch(vararg needles: String): Int? {
            val idx = labels.indexOfFirst { (_, label) ->
                val l = label.lowercase(Locale.ROOT)
                needles.any { l.contains(it) }
            }
            return if (idx >= 0) idx else null
        }

        val chosenIdx = when {
            dimLower.contains("tax") -> bestMatch("including", "all taxes", "taxes and levies")
            dimLower.contains("unit") -> bestMatch("eur", "€", "kwh", "/kwh")
            dimLower.contains("nrg") || dimLower.contains("cons") -> bestMatch("medium", "standard", "2 500", "5 000", "20", "200")
            else -> null
        } ?: 0

        val code = labels[chosenIdx].first
        return codeToPosition(category, code)
    }

    private fun readLabels(category: JSONObject): List<Pair<String, String>> {
        val labelObj = category.optJSONObject("label") ?: JSONObject()
        val keys = labelObj.keys()
        val out = mutableListOf<Pair<String, String>>()
        while (keys.hasNext()) {
            val k = keys.next()
            out += k to labelObj.optString(k, k)
        }
        // stabilizăm ordinea după poziția din index, ca să fie determinist
        return out
    }

    private fun codeToPosition(category: JSONObject, code: String): Int {
        val indexAny = category.get("index")
        return when (indexAny) {
            is JSONArray -> {
                for (i in 0 until indexAny.length()) {
                    if (indexAny.getString(i).equals(code, ignoreCase = true)) return i
                }
                0
            }
            is JSONObject -> indexAny.optInt(code, 0)
            else -> 0
        }
    }

    private fun computeFlatIndex(ids: JSONArray, sizes: JSONArray, posByDim: Map<String, Int>): Int {
        // JSON-stat: ultima dimensiune variază cel mai rapid
        var idx = 0
        var stride = 1
        for (i in ids.length() - 1 downTo 0) {
            val dimId = ids.getString(i)
            val pos = posByDim[dimId] ?: 0
            idx += pos * stride
            stride *= sizes.getInt(i)
        }
        return idx
    }
}
