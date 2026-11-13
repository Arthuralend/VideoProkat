package am.arthur.vportal.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress

/** Проверка связи с медиатекой: DNS, затем каждый формат запроса. Ключ в отчёте скрыт. */
object Diagnostics {

    data class Result(val title: String, val text: String)

    suspend fun run(portal: Portal, storage: Storage): List<Result> = withContext(Dispatchers.IO) {
        val client = PortalClient(portal, storage)
        val out = mutableListOf<Result>()
        val host = portal.baseUrl.toHttpUrlOrNull()?.host

        out += Result(
            "Медиатека",
            "Ключ: ${portal.key?.let { "есть" } ?: "нет"}\n" +
                "Запомненный формат: ${storage.proto ?: "—"}${if (storage.protoOk) " (подтверждён)" else ""}\n" +
                "ВидеоПрокат ${am.arthur.vportal.BuildConfig.VERSION_NAME}"
        )

        out += try {
            val ips = InetAddress.getAllByName(host).joinToString { it.hostAddress ?: "?" }
            Result("DNS — OK", "адрес сервера найден (${ips.split(",").size} IP)")
        } catch (e: Exception) {
            Result("DNS — ошибка", "адрес сервера не находится.\nВозможно, он доступен только через VPN или у провайдера.")
        }

        for (p in PortalClient.PROTOS) {
            out += try {
                val t0 = System.currentTimeMillis()
                val (code, text) = client.exchange(p, JSONObject().put("limit", 20))
                val ms = System.currentTimeMillis() - t0
                val j = Json.lenient(text)
                val verdict = when {
                    j == null -> "не JSON"
                    j.optString("type") == "error" -> "ошибка портала"
                    else -> "OK, элементов: ${j.optJSONArray("items")?.length() ?: 0}"
                }
                Result(
                    "POST [$p] — HTTP $code · $verdict",
                    "$ms мс · ${text.length} симв.\n\n" + client.mask(text.take(1500)).ifEmpty { "(пустой ответ)" }
                )
            } catch (e: Exception) {
                Result("POST [$p] — ошибка", "${e.javaClass.simpleName}: ${client.mask(e.message ?: "")}")
            }
        }

        out += try {
            val req = Request.Builder().url(portal.baseUrl).header("User-Agent", PortalClient.USER_AGENT).build()
            client.http.newCall(req).execute().use { r ->
                Result("GET (для сравнения) — HTTP ${r.code}", client.mask(r.body?.string().orEmpty().take(400)))
            }
        } catch (e: Exception) {
            Result("GET (для сравнения) — ошибка", "${e.javaClass.simpleName}: ${client.mask(e.message ?: "")}")
        }
        out
    }
}
