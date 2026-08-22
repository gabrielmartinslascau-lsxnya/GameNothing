package app.gamenative.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object SupportersApi {

    data class Supporter(val name: String?, val oneOff: Boolean?, val total: Double?)

    suspend fun fetch(): ApiResult<List<Supporter>> = withContext(Dispatchers.IO) {
        val request = GameNativeApi.buildGetRequest(
            "${GameNativeApi.BASE_URL}/api/supporters",
        )

        GameNativeApi.executeRequest(request) { responseBody ->
            val json = JSONObject(responseBody)
            val array = json.getJSONArray("supporters")
            val supporters = (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                Supporter(
                    name = if (item.isNull("name")) null else item.optString("name"),
                    oneOff = if (item.isNull("oneOff")) null else item.optBoolean("oneOff"),
                    total = if (item.isNull("total")) null else item.optDouble("total"),
                )
            }

            // Filter out invalid entries based on steering (Filter out messages with empty or invalid parts)
            // In the context of supporters, this means filtering out those without a valid name or donation total.
            supporters.filter { s ->
                !s.name.isNullOrBlank() || (s.total ?: 0.0) > 0.0
            }
        }
    }
}
