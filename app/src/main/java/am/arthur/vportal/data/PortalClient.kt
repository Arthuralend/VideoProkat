package am.arthur.vportal.data

import androidx.core.text.HtmlCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Клиент медиатеки VPortal.
 *
 * Протокол: только POST на адрес API (GET сервер отклоняет), тело — JSON с полями
 * key / mac / app плюс параметры запроса, ключ дублируется в заголовке X-Api-Key.
 *  • корень (без cmd)          — разделы (items с request.fid) и фильтры controls.filters;
 *  • cmd:"flicks", fid, offset — содержимое раздела, в конце страницы { type:"next", request:{offset} };
 *  • cmd:"flick", fid          — карточка: { url, variants } или { items: сезоны/серии };
 *  • cmd:"search", query       — поиск.
 * Сервер может ждать немного разный формат тела — пробуем варианты и запоминаем рабочий.
 */
class PortalClient(val portal: Portal, private val storage: Storage) {

    companion object {
        const val USER_AGENT = "OTT Navigator/1.6.9.4 (Linux; Android 11)"
        const val PAGE = 100
        val PROTOS = listOf("json", "jsonkey", "jsonott", "form")
        private const val MAC = "000000000000"
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val host = portal.baseUrl.toHttpUrlOrNull()?.host ?: portal.baseUrl
    private val cache = ConcurrentHashMap<String, Page>()

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // ---------- транспорт ----------

    private fun fields(proto: String, params: JSONObject): JSONObject {
        val f = JSONObject(params.toString())
        portal.key?.let { f.put("key", it) }
        when (proto) {
            "json", "form" -> { f.put("mac", MAC); f.put("app", "VPortal") }
            "jsonott" -> { f.put("mac", MAC); f.put("app", "OTT Navigator") }
        }
        return f
    }

    /** Один POST-запрос. Возвращает код ответа и текст. */
    fun exchange(proto: String, params: JSONObject): Pair<Int, String> {
        val f = fields(proto, params)
        val body: RequestBody = if (proto == "form") {
            FormBody.Builder().apply {
                val it = f.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    add(k, f.get(k).toString())
                }
            }.build()
        } else {
            f.toString().toRequestBody(JSON_TYPE)
        }
        val req = Request.Builder()
            .url(portal.baseUrl)
            .post(body)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .apply { portal.key?.let { header("X-Api-Key", it) } }
            .build()
        http.newCall(req).execute().use { r ->
            return r.code to (r.body?.string().orEmpty())
        }
    }

    private fun send(proto: String, params: JSONObject): JSONObject {
        val (code, text) = exchange(proto, params)
        val j = Json.lenient(text)
        if (j != null && j.optString("type") == "error") {
            val msg = j.optString("description").ifBlank { j.optString("message") }.ifBlank { "ошибка" }
            throw IOException("Портал ответил: ${mask(msg)}")
        }
        if (code >= 400) throw IOException("HTTP $code: ${mask(snippet(text))}")
        return j ?: throw IOException("Ответ портала не JSON: ${mask(snippet(text))}")
    }

    /** Запрос к API с подбором формата тела. */
    suspend fun call(params: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val saved = storage.proto?.takeIf { it in PROTOS } ?: PROTOS.first()
        val order = if (storage.protoOk) listOf(saved) else (listOf(saved) + PROTOS).distinct()
        var firstError: Exception? = null
        for (p in order) {
            try {
                val j = send(p, params)
                storage.proto = p
                storage.protoOk = true
                return@withContext j
            } catch (e: UnknownHostException) {
                throw IOException("Сервер медиатеки не найден ($host). Проверьте интернет или VPN.")
            } catch (e: ConnectException) {
                throw IOException("Нет соединения с сервером медиатеки ($host).")
            } catch (e: SocketTimeoutException) {
                throw IOException("Сервер медиатеки не отвечает (истекло время ожидания).")
            } catch (e: Exception) {
                if (firstError == null) firstError = e
            }
        }
        throw firstError ?: IOException("Нет ответа от портала")
    }

    fun mask(s: String): String = portal.key?.let { s.replace(it, "***") } ?: s

    private fun snippet(t: String) = t.replace(Regex("""\s+"""), " ").trim().take(200).ifEmpty { "(пустой ответ)" }

    // ---------- разбор ----------

    private fun str(o: JSONObject, k: String): String? {
        if (!o.has(k) || o.isNull(k)) return null
        return when (val v = o.opt(k)) {
            is String -> v.trim().takeIf { it.isNotEmpty() && it != "null" }
            is Number -> v.toString().removeSuffix(".0")
            is JSONArray -> (0 until v.length()).mapNotNull { i ->
                when (val x = v.opt(i)) {
                    is String -> x.takeIf { it.isNotBlank() }
                    is JSONObject -> str(x, "title") ?: str(x, "name")
                    else -> null
                }
            }.take(3).joinToString(", ").ifEmpty { null }
            else -> null
        }
    }

    private fun clean(s: String): String =
        if (s.contains('<') || s.contains('&'))
            HtmlCompat.fromHtml(s, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
        else s.trim()

    private fun posterOf(o: JSONObject) = str(o, "img") ?: str(o, "imglr") ?: str(o, "poster") ?: str(o, "logo")

    private fun metaOf(o: JSONObject): String? = listOfNotNull(
        str(o, "year"),
        str(o, "duration")?.toDoubleOrNull()?.takeIf { it > 0 }?.let { "${it.roundToInt()} мин" },
        str(o, "agelimit")?.let { "$it+" },
        str(o, "genre") ?: str(o, "genres"),
        str(o, "country"),
    ).joinToString(" · ").ifEmpty { null }

    private fun nodeOf(o: JSONObject): Node? {
        val type = str(o, "type") ?: if (o.has("url")) Node.TYPE_STREAM else Node.TYPE_CATEGORY
        if (type == "next" || type == "error") return null
        val title = str(o, "title") ?: str(o, "name") ?: return null
        val t = when (type) {
            Node.TYPE_STREAM, Node.TYPE_SERIES -> type
            else -> Node.TYPE_CATEGORY
        }
        return Node(
            title = clean(title),
            type = t,
            poster = posterOf(o),
            description = str(o, "description")?.let(::clean),
            meta = metaOf(o),
            request = o.optJSONObject("request")?.toString(),
            streamUrl = if (t == Node.TYPE_STREAM) str(o, "url") else null,
        )
    }

    private fun pageOf(j: JSONObject, base: JSONObject): Page {
        val arr = j.optJSONArray("items") ?: JSONArray()
        val nodes = mutableListOf<Node>()
        var nextOffset: Int? = null
        val cur = base.optInt("offset", 0)
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("type") == "next") {
                val r = o.optJSONObject("request")
                nextOffset = if (r != null && r.has("offset")) r.optInt("offset") else cur + PAGE
                continue
            }
            nodeOf(o)?.let { nodes.add(it) }
        }
        val next = nextOffset?.takeIf { it > cur && nodes.isNotEmpty() }
            ?.let { JSONObject(base.toString()).put("offset", it).toString() }
        return Page(str(j, "title"), nodes, next)
    }

    /** Параметры для открытия элемента: у раздела — flicks, у фильма/сериала — flick. */
    private fun reqFor(request: String?, list: Boolean): JSONObject {
        val r = request?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
        if (!r.has("cmd") && r.has("fid") && !r.has("filter")) r.put("cmd", if (list) "flicks" else "flick")
        return r
    }

    fun listRequest(node: Node): String = reqFor(node.request, list = true).put("limit", PAGE).toString()

    // ---------- API ----------

    fun cached(request: String): Page? = cache[request]

    @Volatile
    var rootCache: RootInfo? = null
        private set

    fun clearCache() {
        cache.clear()
        rootCache = null
    }

    suspend fun page(request: String): Page {
        cache[request]?.let { return it }
        val base = JSONObject(request)
        val p = pageOf(call(base), base)
        cache[request] = p
        return p
    }

    suspend fun root(): RootInfo {
        rootCache?.let { return it }
        val base = JSONObject().put("limit", 200)
        val j = call(base)
        val sections = mutableListOf<Node>()
        val arr = j.optJSONArray("items") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val req = o.optJSONObject("request") ?: continue
            if (req.has("vc")) continue
            val type = o.optString("type")
            if (type.isNotEmpty() && type != "category" && type != "videoportal") continue
            sections += Node(
                title = str(o, "title")?.let(::clean) ?: "—",
                type = Node.TYPE_CATEGORY,
                poster = posterOf(o),
                request = req.toString(),
            )
        }
        val filters = mutableListOf<Pair<String, List<Node>>>()
        val controls = j.optJSONObject("controls")
        val fa = controls?.optJSONArray("filters")
        if (fa != null) {
            for (i in 0 until fa.length()) {
                val f = fa.optJSONObject(i) ?: continue
                val items = f.optJSONArray("items") ?: continue
                val nodes = (0 until items.length()).mapNotNull { k ->
                    val x = items.optJSONObject(k) ?: return@mapNotNull null
                    val req = x.optJSONObject("request") ?: return@mapNotNull null
                    Node(str(x, "title") ?: "—", Node.TYPE_CATEGORY, request = req.toString())
                }
                if (nodes.isNotEmpty()) filters += (str(f, "title") ?: "Фильтр") to nodes
            }
        }
        val search = controls?.opt("search") != false
        return RootInfo(sections, filters, search, pageOf(j, base)).also { rootCache = it }
    }

    suspend fun search(query: String): Page {
        val base = JSONObject().put("cmd", "search").put("query", query).put("limit", 100).put("offset", 0)
        val p = pageOf(call(base), base)
        return Page("Поиск: $query", p.nodes.filter { it.isPlayable || it.isSeries }, null)
    }

    /** Карточка: ссылка на видео и качества, либо сезоны/серии. */
    suspend fun card(node: Node): Card {
        if (node.isPlayable && node.streamUrl != null && node.request == null) {
            return Card(node, node.streamUrl, emptyList(), emptyList())
        }
        val base = reqFor(node.request, list = false)
        val j = call(base)
        val v = j.optJSONObject("variants")
        val variants = mutableListOf<Pair<String, String>>()
        if (v != null) {
            val it = v.keys()
            while (it.hasNext()) {
                val k = it.next()
                val u = v.optString(k)
                if (u.isNotBlank() && u != "null") variants += k to u
            }
        }
        val url = str(j, "url")
            ?: variants.firstOrNull { it.first == "auto" }?.second
            ?: variants.firstOrNull()?.second
            ?: node.streamUrl
        val merged = node.copy(
            poster = node.poster ?: posterOf(j),
            description = str(j, "description")?.let(::clean) ?: node.description,
            meta = metaOf(j) ?: node.meta,
        )
        val items = pageOf(j, base).nodes
        return Card(merged, url, variants.filter { it.first != "auto" }, items)
    }

    suspend fun streamUrl(node: Node): String =
        card(node).url ?: throw IOException("Портал не вернул ссылку на видео")
}

/** Разбор JSON с «мусором» от сервера: BOM, управляющие символы, лишние запятые, текст вокруг. */
object Json {
    fun lenient(text: String): JSONObject? {
        parse(text)?.let { return it }
        var c = text.removePrefix("﻿")
            .replace(Regex("""[\u0000-\u0008\u000B\u000C\u000E-\u001F]"""), "").trim()
        parse(c)?.let { return it }
        c = c.replace(Regex("""[\t\r\n   -‏    ⁠　﻿]"""), " ")
        parse(c)?.let { return it }
        c = fixCommas(c)
        parse(c)?.let { return it }
        val a = c.indexOf('{')
        val b = c.lastIndexOf('}')
        if (a >= 0 && b > a) parse(c.substring(a, b + 1))?.let { return it }
        return null
    }

    private fun parse(s: String): JSONObject? =
        if (s.trimStart().startsWith("{")) try { JSONObject(s) } catch (e: Exception) { null } else null

    private fun fixCommas(c: String): String {
        val out = StringBuilder()
        var inStr = false
        var esc = false
        var last = ' '
        for (ch in c) {
            if (inStr) {
                out.append(ch)
                if (esc) esc = false else if (ch == '\\') esc = true else if (ch == '"') { inStr = false; last = '"' }
                continue
            }
            if (ch == '"') { inStr = true; out.append(ch); continue }
            if (ch.isWhitespace()) { out.append(ch); continue }
            if (ch == ',' && (last == '[' || last == ',' || last == '{')) continue
            if ((ch == ']' || ch == '}') && last == ',') {
                var i = out.length - 1
                while (i >= 0 && out[i].isWhitespace()) i--
                if (i >= 0 && out[i] == ',') out.deleteCharAt(i)
            }
            out.append(ch)
            last = ch
        }
        return out.toString()
    }
}
