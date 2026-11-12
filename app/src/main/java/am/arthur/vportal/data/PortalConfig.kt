package am.arthur.vportal.data

/** Адрес портала: базовый URL и необязательный ключ. */
data class Portal(val baseUrl: String, val key: String?, val raw: String)

object PortalConfig {
    /** Короткий код: набираешь 22184 — и приложение само подключается к встроенному порталу. */
    const val SHORT_CODE = "22184"

    const val BUILTIN =
        "portal::[key:9ff494ce340dfd6-57308e9eb1ec747]http://2e1f56aeefe2.jmeekos.com/api/v1/"

    private val PORTAL_RE = Regex("""^portal::\[key:([^\]]*)]\s*(\S+)$""", RegexOption.IGNORE_CASE)

    /** Подменяет короткий код полной строкой портала. */
    fun resolve(input: String): String {
        val t = input.trim()
        return if (t == SHORT_CODE) BUILTIN else t
    }

    fun parse(input: String): Portal? {
        val s = resolve(input)
        PORTAL_RE.matchEntire(s)?.let { m ->
            val url = m.groupValues[2]
            if (!isHttp(url)) return null
            return Portal(url, m.groupValues[1].trim().ifEmpty { null }, s)
        }
        if (isHttp(s)) return Portal(s, null, s)
        return null
    }

    /** Как показывать адрес пользователю: встроенный портал — просто кодом. */
    fun display(raw: String?): String = when {
        raw == null -> ""
        raw == BUILTIN -> SHORT_CODE
        else -> raw
    }

    private fun isHttp(s: String) =
        s.startsWith("http://", ignoreCase = true) || s.startsWith("https://", ignoreCase = true)
}
