package com.notizie.app

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Result of a fetch/trigger call. Using a sealed type instead of a nullable
 * DailyBrief? so failures are distinguishable (network vs. worker error vs.
 * bad JSON) instead of all collapsing into "null" and being invisible to the user.
 */
sealed class BriefResult {
    data class Success(val brief: DailyBrief) : BriefResult()
    data class Failure(val message: String) : BriefResult()
}

object DataFetcher {
    // Quick reads from the cache (GET /) should be fast.
    private val readClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // /trigger does real work server-side (RSS fetch -> Gemini call -> KV write),
    // so it needs a much longer timeout or it will fail every time under load.
    private val triggerClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /** Strips a trailing slash and any accidental "/trigger" the user may have typed in. */
    fun normalizeBaseUrl(rawUrl: String): String {
        var url = rawUrl.trim()
        while (url.endsWith("/")) url = url.dropLast(1)
        if (url.endsWith("/trigger")) url = url.removeSuffix("/trigger")
        return url
    }

    /** Cheap read of whatever is currently cached in KV. Used for daily background refresh. */
    fun fetchBrief(baseUrl: String): BriefResult = execute(readClient, baseUrl)

    /** Forces the worker to run the full pipeline (RSS -> Gemini -> KV) and returns the fresh result. */
    fun triggerBrief(baseUrl: String): BriefResult = execute(triggerClient, "$baseUrl/trigger")

    private fun execute(client: OkHttpClient, url: String): BriefResult {
        return try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                    ?: return BriefResult.Failure("Empty response from server")

                if (!response.isSuccessful) {
                    return BriefResult.Failure("Server error (${response.code}): ${body.take(200)}")
                }

                try {
                    BriefResult.Success(json.decodeFromString<DailyBrief>(body))
                } catch (e: Exception) {
                    BriefResult.Failure("Could not parse response: ${e.message}")
                }
            }
        } catch (e: IOException) {
            BriefResult.Failure("Network error: ${e.message}")
        } catch (e: Exception) {
            BriefResult.Failure("Unexpected error: ${e.message}")
        }
    }
}
