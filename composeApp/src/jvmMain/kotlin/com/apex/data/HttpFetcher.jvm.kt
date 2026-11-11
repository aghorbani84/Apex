package com.apex.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI

actual suspend fun httpGet(url: String): String = withContext(Dispatchers.IO) {
    val connection = URI(url).toURL().openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.setRequestProperty("User-Agent", "APEX/0.1.0 (XAUUSD Research)")
    connection.connectTimeout = 10000
    connection.readTimeout = 10000
    connection.inputStream.bufferedReader().use { it.readText() }
}
