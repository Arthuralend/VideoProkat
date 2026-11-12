package am.arthur.vportal.data

import androidx.core.text.HtmlCompat
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

/**
 * Разбор ответа портала. Формат API заранее неизвестен, поэтому разбор «гибкий»:
 * ищет в JSON списки объектов с названиями и угадывает по типовым полям постер,
 * описание, ссылку на видео и ссылку на вложенный раздел. Поддерживает и M3U.
 */
object PortalParser {

    private val TITLE = listOf(
        "title", "name", "caption", "title_ru", "name_ru", "ru_title", "label",
        "original_title", "original_name", "quality",
    )
    private val POSTER = listOf(
        "poster", "poster_url", "logo_30x30", "logo", "image", "img", "cover", "thumbnail",
        "thumb", "picture", "preview", "icon", "backdrop", "screenshot",
    )
    private val DESC = listOf("description", "desc", "overview", "plot", "about", "info", "text", "short_description")
    private val STREAM = listOf(
        "stream_url", "stream", "play_url", "video_url", "file", "video", "hls", "m3u8", "mp4", "src", "source",
    )
    private val FOLDER = listOf("playlist_url", "items_url", "folder_url", "api_url", "href", "folder")
    private val ANY_URL = listOf("url", "link")

    /** Где искать список элементов на странице. */
    private val PAGE_ARRAYS = listOf(
        "channels", "items", "results", "data", "list", "movies", "films", "serials", "series", "menu",
        "categories", "genres", "content", "videos", "episodes", "seasons", "files", "children",
        "playlist", "folders", "sections", "collections", "catalog",
    )

    /** Вложенные списки внутри элемента (сезоны, серии, качества). */
    private val CHILD_ARRAYS = listOf(
        "seasons", "episodes", "series", "items", "channels", "children", "files", "folders", "playlist", "videos", "list",
    )

    private val MEDIA = Regex("""\.(m3u8|m3u|mp4|mkv|avi|ts|mov|webm|mpd|flv|m4v)(\?|#|$)""", RegexOption.IGNORE_CASE)

    fun parse(body: String, pageUrl: String, resolve: (String, String) -> String): Page {
        val t = body.trimStart('﻿', ' ', '\n', '\r', '\t')
        if (t.startsWith("#EXTM3U")) return M3uParser.parse(t, pageUrl, resolve)
        val root: Any = when {
            t.startsWith("[") -> JSONArray(t)
            t.startsWith("{") -> JSONObject(t)
            else -> throw IllegalArgumentException("ответ не JSON и не M3U")
        }
        return pageFrom(root, pageUrl, resolve, null)
    }

    private fun pageFrom(root: Any, pageUrl: String, resolve: (String, String) -> String, title: String?): Page {
        val obj = root as? JSONObject
        val arr = findArray(root, PAGE_ARRAYS, 0) ?: JSONArray()
        val nodes = objects(arr).mapNotNull { nodeFrom(it, pageUrl, resolve) }

        val next = obj?.let { urlOf(it, listOf("next_page_url", "next_page", "next", "nextPage")) }
            ?.let { resolve(pageUrl, it) }
            ?.takeIf { it != pageUrl }
        val search = obj?.let { urlOf(it, listOf("search_url", "search_on", "search")) }
            ?.takeIf { it.contains("/") }
            ?.let { resolve(pageUrl, it) }
        val pageTitle = title ?: obj?.let { str(it, listOf("title", "name")) }
        return Page(pageTitle, nodes, next, search)
    }

    private fun objects(arr: JSONArray): List<JSONObject> =
        (0 until arr.length()).mapNotNull { arr.opt(it) as? JSONObject }

    private fun hasObjects(arr: JSONArray) = (0 until arr.length()).any { arr.opt(it) is JSONObject }

    private fun findArray(any: Any?, keys: List<String>, depth: Int): JSONArray? {
        if (depth > 3) return null
        when (any) {
            is JSONArray -> return if (hasObjects(any)) any else null
            is JSONObject -> {
                for (k in keys) {
                    when (val v = any.opt(k)) {
                        is JSONArray -> if (hasObjects(v)) return v
                        is JSONObject -> findArray(v, keys, depth + 1)?.let { return it }
                    }
                }
                // Иначе — самый длинный список объектов в любом поле.
                var best: JSONArray? = null
                val it = any.keys()
                while (it.hasNext()) {
                    val v = any.opt(it.next())
                    if (v is JSONArray && hasObjects(v) && v.length() > (best?.length() ?: 0)) best = v
                }
                if (best != null) return best
                val it2 = any.keys()
                while (it2.hasNext()) {
                    val v = any.opt(it2.next())
                    if (v is JSONObject) findArray(v, keys, depth + 1)?.let { return it }
                }
            }
        }
        return null
    }

    private fun nodeFrom(o: JSONObject, pageUrl: String, resolve: (String, String) -> String): Node? {
        val title = str(o, TITLE)?.let(::clean) ?: return null
        val poster = urlOf(o, POSTER)?.let { resolve(pageUrl, it) }
        val description = str(o, DESC)?.let(::clean)
        val meta = listOfNotNull(
            str(o, listOf("year", "release_year", "released", "date")),
            str(o, listOf("rating", "rating_kp", "kp", "rating_imdb", "imdb", "vote_average"))?.let { "★ $it" },
            str(o, listOf("genre", "genres")),
            str(o, listOf("country", "countries")),
            str(o, listOf("duration", "time", "runtime")),
        ).joinToString(" · ").ifEmpty { null }

        var stream = urlOf(o, STREAM)?.takeIf { looksLikeUrl(it) }
        var folder = urlOf(o, FOLDER)?.takeIf { looksLikeUrl(it) }
        urlOf(o, ANY_URL)?.takeIf { looksLikeUrl(it) }?.let { u ->
            if (stream == null && folder == null) {
                if (MEDIA.containsMatchIn(u) || !sameHost(u, pageUrl)) stream = u else folder = u
            }
        }
        // Явная ссылка «папки», указывающая на медиафайл, — это видео.
        if (stream == null && folder != null && MEDIA.containsMatchIn(folder!!) && !folder!!.contains(".m3u", true)) {
            stream = folder; folder = null
        }

        var inline: Page? = null
        if (stream == null) {
            val children = findArray(o, CHILD_ARRAYS, 3)?.takeIf { arr -> CHILD_ARRAYS.any { o.opt(it) === arr } }
            if (children != null) {
                val nodes = objects(children).mapNotNull { nodeFrom(it, pageUrl, resolve) }
                if (nodes.isNotEmpty()) inline = Page(title, nodes)
            }
        }

        return Node(
            title = title,
            poster = poster,
            description = description,
            meta = meta,
            folderUrl = folder?.let { resolve(pageUrl, it) },
            streamUrl = stream?.let { resolve(pageUrl, it) },
            inline = inline,
        )
    }

    private fun looksLikeUrl(s: String) =
        s.startsWith("http", true) || s.startsWith("/") || s.contains("/") || s.contains(".")

    private fun sameHost(u: String, pageUrl: String): Boolean {
        val h = u.toHttpUrlOrNull()?.host ?: return true // относительная ссылка — тот же портал
        return h == pageUrl.toHttpUrlOrNull()?.host
    }

    private fun clean(s: String): String =
        if (s.contains('<') || s.contains('&'))
            HtmlCompat.fromHtml(s, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
        else s.trim()

    private fun str(o: JSONObject, keys: List<String>): String? {
        for (k in keys) valueOf(o.opt(k))?.let { return it }
        return null
    }

    private fun valueOf(v: Any?): String? = when (v) {
        null -> null
        JSONObject.NULL -> null
        is String -> v.trim().takeIf { it.isNotEmpty() && it != "null" }
        is Number -> v.toString()
        is JSONObject -> str(v, listOf("ru", "name", "title", "value", "en"))
        is JSONArray -> (0 until v.length()).mapNotNull { valueOf(v.opt(it)) }
            .take(3).joinToString(", ").ifEmpty { null }
        else -> null
    }

    private fun urlOf(o: JSONObject, keys: List<String>): String? {
        for (k in keys) firstUrl(o.opt(k))?.let { return it }
        return null
    }

    private fun firstUrl(v: Any?): String? = when (v) {
        null -> null
        JSONObject.NULL -> null
        is String -> v.trim().takeIf { it.isNotEmpty() && it != "null" }
        is JSONObject -> urlOf(v, listOf("url", "src", "file", "link", "original", "large", "medium", "small", "hls"))
        is JSONArray -> (0 until v.length()).firstNotNullOfOrNull { firstUrl(v.opt(it)) }
        else -> null
    }
}

object M3uParser {
    private val LOGO = Regex("""tvg-logo="([^"]*)"""")
    private val GROUP = Regex("""group-title="([^"]*)"""")

    fun parse(text: String, pageUrl: String, resolve: (String, String) -> String): Page {
        val groups = LinkedHashMap<String, MutableList<Node>>()
        var title: String? = null
        var logo: String? = null
        var group = "Все"
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF", true) -> {
                    title = line.substringAfterLast(',').trim().ifEmpty { null }
                    logo = LOGO.find(line)?.groupValues?.get(1)?.ifBlank { null }
                    group = GROUP.find(line)?.groupValues?.get(1)?.ifBlank { null } ?: "Все"
                }
                line.startsWith("#EXTGRP:", true) -> group = line.substringAfter(':').trim().ifEmpty { group }
                line.isEmpty() || line.startsWith("#") -> Unit
                else -> {
                    val url = resolve(pageUrl, line)
                    groups.getOrPut(group) { mutableListOf() }
                        .add(Node(title ?: url.substringAfterLast('/'), poster = logo, streamUrl = url))
                    title = null; logo = null
                }
            }
        }
        if (groups.size <= 1) return Page(null, groups.values.firstOrNull().orEmpty())
        return Page(null, groups.map { (name, list) -> Node(name, inline = Page(name, list)) })
    }
}
