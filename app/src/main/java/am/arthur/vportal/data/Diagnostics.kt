package am.arthur.vportal.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Пробует обратиться к порталу разными способами передачи ключа и показывает сырые ответы.
 * Нужна, чтобы настроить подключение под конкретный API.
 */
object Diagnostics {

    data class Result(val title: String, val text: String)

    private val plain = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun run(portal: Portal): List<Result> = withContext(Dispatchers.IO) {
        val base = portal.baseUrl
        val key = portal.key
        val out = mutableListOf<Result>()
        out += Result(
            "Портал",
            "Адрес: $base\nКлюч: ${key ?: "нет"}\nVPortal ${am.arthur.vportal.BuildConfig.VERSION_NAME}"
        )

        fun withQuery(url: String, name: String, value: String): String =
            url.toHttpUrlOrNull()?.newBuilder()?.addQueryParameter(name, value)?.build()?.toString() ?: url

        val tries = mutableListOf<Triple<String, String, Map<String, String>>>()
        tries += Triple("Без ключа", base, emptyMap())
        if (key != null) {
            tries += Triple("Параметр ?key=", withQuery(base, "key", key), emptyMap())
            tries += Triple("Параметр ?api_key=", withQuery(base, "api_key", key), emptyMap())
            tries += Triple("Параметр ?token=", withQuery(base, "token", key), emptyMap())
            tries += Triple("Заголовок X-Api-Key", base, mapOf("X-Api-Key" to key))
            tries += Triple("Заголовок Authorization: Bearer", base, mapOf("Authorization" to "Bearer $key"))
            tries += Triple("Ключ в пути", base.trimEnd('/') + "/" + key, emptyMap())
        }
        val client = PortalClient(portal)
        for (path in listOf("categories", "menu", "catalog", "genres", "movies", "films", "search?query=test")) {
            tries += Triple("Все способы ключа: /$path", client.resolve(base, path), emptyMap())
        }

        for ((title, url, headers) in tries) {
            val usesClient = title.startsWith("Все способы")
            out += try {
                val req = Request.Builder().url(url)
                    .header("User-Agent", PortalClient.USER_AGENT)
                    .header("Accept", "application/json, */*")
                    .apply { headers.forEach { (k, v) -> header(k, v) } }
                    .build()
                (if (usesClient) client.http else plain).newCall(req).execute().use { r ->
                    val body = r.body?.string().orEmpty()
                    Result(
                        "$title — HTTP ${r.code}",
                        "$url\n${r.header("Content-Type") ?: ""} · ${body.length} байт\n\n" +
                            body.take(1500).ifEmpty { "(пустой ответ)" }
                    )
                }
            } catch (e: Exception) {
                Result("$title — ошибка", "$url\n${e.javaClass.simpleName}: ${e.message}")
            }
        }
        out
    }
}
