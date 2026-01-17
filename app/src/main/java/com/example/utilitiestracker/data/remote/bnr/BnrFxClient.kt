package com.example.utilitiestracker.data.remote.bnr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class BnrFxClient(
    private val http: OkHttpClient = OkHttpClient()
) {
    // Feed oficial BNR: https://www.bnr.ro/nbrfxrates.xml :contentReference[oaicite:4]{index=4}
    suspend fun fetchEurToRon(): Double = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("https://www.bnr.ro/nbrfxrates.xml").get().build()
        val xml = http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("BNR HTTP ${resp.code}")
            resp.body?.string() ?: error("Empty response from BNR")
        }
        parseEurRate(xml)
    }

    private fun parseEurRate(xml: String): Double {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(xml.reader())

        var event = parser.eventType
        var insideRate = false
        var currencyAttr: String? = null

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (parser.name.equals("Rate", ignoreCase = true)) {
                        insideRate = true
                        currencyAttr = null
                        for (i in 0 until parser.attributeCount) {
                            if (parser.getAttributeName(i).equals("currency", ignoreCase = true)) {
                                currencyAttr = parser.getAttributeValue(i)
                            }
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (insideRate && currencyAttr.equals("EUR", ignoreCase = true)) {
                        return parser.text.trim().replace(",", ".").toDouble()
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.equals("Rate", ignoreCase = true)) {
                        insideRate = false
                    }
                }
            }
            event = parser.next()
        }
        error("Nu am găsit cursul EUR în XML-ul BNR.")
    }
}
