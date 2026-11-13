package am.arthur.vportal.data

/** Адрес медиатеки: адрес API и ключ. */
data class Portal(val baseUrl: String, val key: String?, val raw: String)

object PortalConfig {
    /** Код подключения: набираешь его — и приложение подключается к встроенной медиатеке. */
    const val SHORT_CODE = "22184"

    /** Встроенная строка подключения. Пользователю нигде не показывается. */
    private const val BUILTIN =
        "portal::[key:9ff494ce340dfd6-57308e9eb1ec747]http://2e1f56aeefe2.jmeekos.com/api/v1/"

    private val PORTAL_RE =
        Regex("""^portal::(?:\[|%5b)key:([^\]]*?)(?:]|%5d)\s*(https?://\S+)$""", RegexOption.IGNORE_CASE)

    /** Код → встроенная строка; иначе убирает пробелы и кавычки вокруг. */
    fun normalize(input: String): String {
        val s = input.trim().trim('"', '\'', '«', '»', '“', '”', '„', '`').trim()
        return if (s == SHORT_CODE) BUILTIN else s
    }

    fun parse(input: String): Portal? {
        val s = normalize(input)
        PORTAL_RE.matchEntire(s)?.let { m ->
            return Portal(m.groupValues[2], m.groupValues[1].trim().ifEmpty { null }, s)
        }
        if (s.startsWith("http://", true) || s.startsWith("https://", true)) return Portal(s, null, s)
        return null
    }
}
