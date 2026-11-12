package am.arthur.vportal.data

import org.json.JSONObject

/** Элемент каталога: папка (раздел, сериал, сезон) или то, что можно воспроизвести. */
data class Node(
    val title: String,
    val poster: String? = null,
    val description: String? = null,
    val meta: String? = null,
    val folderUrl: String? = null,
    val streamUrl: String? = null,
    val inline: Page? = null,
) {
    val isPlayable: Boolean get() = streamUrl != null
    val isFolder: Boolean get() = !isPlayable && (folderUrl != null || inline != null)
    val key: String get() = streamUrl ?: folderUrl ?: title

    fun toJson(): JSONObject = JSONObject().apply {
        put("title", title)
        putOpt("poster", poster)
        putOpt("description", description)
        putOpt("meta", meta)
        putOpt("folderUrl", folderUrl)
        putOpt("streamUrl", streamUrl)
    }

    companion object {
        fun fromJson(o: JSONObject): Node = Node(
            title = o.optString("title"),
            poster = o.str("poster"),
            description = o.str("description"),
            meta = o.str("meta"),
            folderUrl = o.str("folderUrl"),
            streamUrl = o.str("streamUrl"),
        )

        private fun JSONObject.str(k: String): String? =
            if (has(k) && !isNull(k)) optString(k).takeIf { it.isNotBlank() } else null
    }
}

/** Страница каталога. */
data class Page(
    val title: String? = null,
    val nodes: List<Node>,
    val nextUrl: String? = null,
    val searchUrl: String? = null,
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
