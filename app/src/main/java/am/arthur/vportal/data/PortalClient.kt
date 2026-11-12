package am.arthur.vportal.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class PortalClient(val portal: Portal) {

    companion object {
        const val USER_AGENT = "VPortal/1.0 (Linux; Android TV)"
    }

    private val host = portal.baseUrl.toHttpUrlOrNull()?.host
    private val cache = ConcurrentHashMap<String, Page>()

    /**
     * Ключ портала передаётся к запросам на хост портала сразу тремя способами
     * (параметр key, X-Api-Key, Bearer) — какой из них нужен серверу, покажет диагностика.
     */
    private val keyInterceptor = Interceptor { chain ->
        val req = chain.request()
        val key = portal.key
        if (key == null || req.url.host != host) return@Interceptor chain.proceed(req)
        val url = if (req.url.queryParameter("key") == null)
            req.url.newBuilder().addQueryParameter("key", key).build() else req.url
        chain.proceed(
            req.newBuilder()
                .url(url)
                .header("X-Api-Key", key)
                .header("Authorization", "Bearer $key")
                .build()
        )
    }

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor(keyInterceptor)
        .build()

    suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json, */*")
            .build()
        http.newCall(req).execute().use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IOException("Сервер ответил ${r.code}. ${body.take(200)}")
            body
        }
    }

    fun resolve(base: String, ref: String): String =
        base.toHttpUrlOrNull()?.resolve(ref)?.toString() ?: ref

    fun cached(url: String): Page? = cache[url]

    fun clearCache() = cache.clear()

    suspend fun page(url: String, useCache: Boolean = true): Page {
        if (useCache) cache[url]?.let { return it }
        val body = get(url)
        val page = withContext(Dispatchers.Default) {
            try {
                PortalParser.parse(body, url, ::resolve)
            } catch (e: Exception) {
                throw IOException("Не удалось разобрать ответ портала: ${e.message}. Начало ответа: ${body.take(150)}")
            }
        }
        if (useCache) cache[url] = page
        return page
    }

    suspend fun root(): Page = page(portal.baseUrl)

    /** Поиск: сначала адрес поиска из ответа портала, затем типовые варианты, затем по уже загруженному. */
    suspend fun search(query: String, root: Page?): Page {
        val enc = URLEncoder.encode(query, "UTF-8")
        val base = portal.baseUrl
        val candidates = buildList {
            root?.searchUrl?.let { s ->
                if ("{query}" in s || "{q}" in s) add(s.replace("{query}", enc).replace("{q}", enc))
                else s.toHttpUrlOrNull()?.newBuilder()?.addQueryParameter("search", query)?.build()
                    ?.let { add(it.toString()) }
            }
            add(resolve(base, "search?query=$enc"))
            add(resolve(base, "search?q=$enc"))
            add(resolve(base, "search/$enc"))
        }.distinct()

        var lastError: Throwable? = null
        for (u in candidates) {
            try {
                val p = page(u, useCache = false)
                if (p.nodes.isNotEmpty()) return p.copy(title = "Поиск: $query")
            } catch (e: Exception) {
                lastError = e
            }
        }
        val local = cache.values.flatMap { it.nodes }
            .filter { it.title.contains(query, ignoreCase = true) }
            .distinctBy { it.key }
        if (local.isNotEmpty()) return Page("Поиск: $query", local)
        throw IOException(if (lastError != null) "Ничего не найдено" else "Ничего не найдено")
    }
}
