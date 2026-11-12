package am.arthur.vportal.data

import org.json.JSONObject

/**
 * Элемент медиатеки VPortal.
 * type: "category" — папка (раздел, жанр, сезон), "stream" — фильм или серия, "multistream" — сериал.
 * request — JSON-параметры, которыми портал просит открыть этот элемент.
 */
data class Node(
    val title: String,
    val type: String = TYPE_STREAM,
    val poster: String? = null,
    val description: String? = null,
    val meta: String? = null,
    val request: String? = null,
    val streamUrl: String? = null,
) {
    val isPlayable: Boolean get() = type == TYPE_STREAM
    val isSeries: Boolean get() = type == TYPE_SERIES
    val isFolder: Boolean get() = !isPlayable && !isSeries
    val key: String get() = request ?: streamUrl ?: title

    fun toJson(): JSONObject = JSONObject().apply {
        put("title", title)
        put("type", type)
        putOpt("poster", poster)
        putOpt("description", description)
        putOpt("meta", meta)
        putOpt("request", request)
        putOpt("streamUrl", streamUrl)
    }

    companion object {
        const val TYPE_STREAM = "stream"
        const val TYPE_SERIES = "multistream"
        const val TYPE_CATEGORY = "category"

        fun fromJson(o: JSONObject): Node = Node(
            title = o.optString("title"),
            type = o.str("type") ?: TYPE_STREAM,
            poster = o.str("poster"),
            description = o.str("description"),
            meta = o.str("meta"),
            request = o.str("request"),
            streamUrl = o.str("streamUrl"),
        )

        private fun JSONObject.str(k: String): String? =
            if (has(k) && !isNull(k)) optString(k).takeIf { it.isNotBlank() } else null
    }
}

/** Страница списка; nextRequest — запрос следующей страницы. */
data class Page(
    val title: String? = null,
    val nodes: List<Node>,
    val nextRequest: String? = null,
)

/** Корень медиатеки: разделы, фильтры (жанры, годы), поддержка поиска. */
data class RootInfo(
    val sections: List<Node>,
    val filters: List<Pair<String, List<Node>>>,
    val search: Boolean,
    val page: Page,
)

/** Карточка фильма или сериала. */
data class Card(
    val node: Node,
    val url: String?,
    val variants: List<Pair<String, String>>,
    val items: List<Node>,
)

/** Состояние загрузки. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ok<T>(val value: T) : Load<T>
    data class Err(val message: String) : Load<Nothing>
}

suspend fun <T> load(block: suspend () -> T): Load<T> = try {
    Load.Ok(block())
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Throwable) {
    Load.Err(e.message ?: e.javaClass.simpleName)
}
