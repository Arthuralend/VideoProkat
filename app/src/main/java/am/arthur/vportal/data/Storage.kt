package am.arthur.vportal.data

import android.content.Context
import org.json.JSONArray

/** Настройки, позиции просмотра, «Продолжить» и «Избранное». */
class Storage(context: Context) {
    private val p = context.getSharedPreferences("vportal", Context.MODE_PRIVATE)

    var lastPortal: String?
        get() = p.getString("portal", null)
        set(v) {
            p.edit().putString("portal", v).apply()
        }

    fun position(url: String): Long = p.getLong("pos:$url", 0L)

    fun resetPosition(url: String) {
        p.edit().remove("pos:$url").apply()
    }

    fun savePosition(node: Node, position: Long, duration: Long) {
        val url = node.streamUrl ?: return
        val finished = duration > 0 && position > duration - minOf(90_000L, duration / 10)
        val editor = p.edit()
        if (finished || position < 15_000) editor.remove("pos:$url") else editor.putLong("pos:$url", position)
        editor.apply()

        val list = continueNodes().filter { it.key != node.key }.toMutableList()
        if (!finished && position >= 15_000) list.add(0, node.copy(inline = null))
        writeList("continue", list.take(40))
    }

    fun continueNodes(): List<Node> = readList("continue")

    fun favorites(): List<Node> = readList("favorites")

    fun isFavorite(node: Node): Boolean = favorites().any { it.key == node.key }

    /** Возвращает новое состояние: true — добавлено в избранное. */
    fun toggleFavorite(node: Node): Boolean {
        val list = favorites().toMutableList()
        val existed = list.removeAll { it.key == node.key }
        if (!existed) list.add(0, node.copy(inline = null))
        writeList("favorites", list)
        return !existed
    }

    private fun readList(key: String): List<Node> = try {
        val arr = JSONArray(p.getString(key, "[]"))
        (0 until arr.length()).map { Node.fromJson(arr.getJSONObject(it)) }
    } catch (e: Exception) {
        emptyList()
    }

    private fun writeList(key: String, list: List<Node>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        p.edit().putString(key, arr.toString()).apply()
    }
}
