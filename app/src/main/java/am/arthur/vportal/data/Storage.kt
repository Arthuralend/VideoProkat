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

    /** Формат запроса, на который портал ответил без ошибки. */
    var proto: String?
        get() = p.getString("proto", null)
        set(v) {
            p.edit().putString("proto", v).apply()
        }

    var protoOk: Boolean
        get() = p.getBoolean("protoOk", false)
        set(v) {
            p.edit().putBoolean("protoOk", v).apply()
        }

    fun resetProto() {
        p.edit().remove("proto").remove("protoOk").apply()
    }

    /** Видео на телефоне растянуто во весь экран (без рамок, с сохранением пропорций). */
    var videoZoom: Boolean
        get() = p.getBoolean("videoZoom", false)
        set(v) {
            p.edit().putBoolean("videoZoom", v).apply()
        }

    fun position(key: String): Long = p.getLong("pos:$key", 0L)

    fun resetPosition(key: String) {
        p.edit().remove("pos:$key").apply()
    }

    fun savePosition(node: Node, position: Long, duration: Long) {
        if (duration <= 0) return
        val key = node.key
        val finished = position > duration - minOf(90_000L, duration / 10)
        val editor = p.edit()
        if (finished || position < 15_000) editor.remove("pos:$key") else editor.putLong("pos:$key", position)
        editor.apply()

        val list = continueNodes().filter { it.key != key }.toMutableList()
        if (!finished && position >= 15_000) list.add(0, node)
        writeList("continue", list.take(40))
    }

    fun continueNodes(): List<Node> = readList("continue")

    fun favorites(): List<Node> = readList("favorites")

    fun isFavorite(node: Node): Boolean = favorites().any { it.key == node.key }

    /** Возвращает новое состояние: true — добавлено в избранное. */
    fun toggleFavorite(node: Node): Boolean {
        val list = favorites().toMutableList()
        val existed = list.removeAll { it.key == node.key }
        if (!existed) list.add(0, node)
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
