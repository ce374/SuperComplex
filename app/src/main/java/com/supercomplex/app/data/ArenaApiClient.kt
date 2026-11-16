package com.supercomplex.app.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Are.na GraphQL + REST client.
 *
 * GraphQL traffic is sent to https://api.are.na/graphql with the public
 * X-APP-TOKEN shipped in the Are.na web bundle (see GraphQLQueries.APP_TOKEN).
 *
 * Search uses the unauthenticated legacy REST v2 endpoint
 * https://api.are.na/v2/search?q=... so it works with no credentials at all,
 * as required by the spec.
 */
class ArenaApiClient private constructor() {

    private val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val blockEnvelopeAdapter = moshi.adapter(BlockEnvelope::class.java)
    private val searchEnvelopeAdapter = moshi.adapter(SearchEnvelope::class.java)
    private val restSearchAdapter = moshi.adapter(RestSearchResponse::class.java)

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /** Run a GraphQL operation and return the top-level JSON object. */
    private suspend fun graphql(operationName: String, query: String, variables: Map<String, Any?>): JSONObject =
        withContext(Dispatchers.IO) {
            val payload = JSONObject()
                .put("operationName", operationName)
                .put("query", query)
                .put("variables", JSONObject(variables))
                .toString()

            val req = Request.Builder()
                .url(GRAPHQL_URL)
                .header("Content-Type", "application/json")
                .header("X-APP-TOKEN", GraphQLQueries.APP_TOKEN)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .post(payload.toRequestBody(JSON_MEDIA))
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("GraphQL $operationName HTTP ${resp.code}")
                val body = resp.body?.string().orEmpty()
                val root = JSONObject(body)
                if (root.has("errors")) {
                    val errs = root.getJSONArray("errors")
                    if (errs.length() > 0) {
                        throw IOException("GraphQL error: ${errs.getJSONObject(0).optString("message", "unknown")}")
                    }
                }
                root
            }
        }

    // ---- Tier 1: Explore stream ----

    suspend fun explore(
        whatFacets: List<String>,
        page: Int,
        per: Int,
        timestamp: String,
        seed: Int? = null,
        random: Boolean = false
    ): SearchResults = withContext(Dispatchers.IO) {
        val vars = mutableMapOf<String, Any?>(
            "what" to mapOf("facets" to whatFacets),
            "where" to listOf(mapOf("facet" to "ALL")),
            "order" to mapOf(
                "facet" to if (random) "RANDOM" else "UPDATED_AT",
                "dir" to "DESC"
            ),
            "page" to page,
            "per" to per,
            "timestamp" to timestamp
        )
        if (random && seed != null) vars["seed"] = seed

        val root = graphql("ExploreResultsQuery", GraphQLQueries.EXPLORE, vars)
        val adv = root.optJSONObject("data")?.optJSONObject("search")?.optJSONObject("advanced")
            ?: return@withContext SearchResults()
        val results = mutableListOf<IdRef>()
        adv.optJSONArray("results")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                results.add(IdRef(o.getLong("id"), o.getString("type")))
            }
        }
        SearchResults(adv.optInt("total"), results)
    }

    // ---- Tier 2: hydrate entities ----

    suspend fun block(id: Long): BlockDetail? = withContext(Dispatchers.IO) {
        val root = graphql("GridCellBlockQuery", GraphQLQueries.BLOCK, mapOf("id" to id.toString()))
        blockEnvelopeAdapter.fromJson(root.toString())?.data?.block
    }

    suspend fun channel(id: Long): ChannelDetail? = withContext(Dispatchers.IO) {
        val root = graphql("GridCellChannelQuery", GraphQLQueries.CHANNEL, mapOf("id" to id.toString()))
        blockEnvelopeAdapter.fromJson(root.toString())?.data?.channel
    }

    suspend fun channelContents(id: Long, page: Int, per: Int): ChannelDetail? = withContext(Dispatchers.IO) {
        val root = graphql(
            "ChannelContentsQuery",
            GraphQLQueries.CHANNEL_CONTENTS,
            mapOf("id" to id.toString(), "page" to page, "per" to per)
        )
        blockEnvelopeAdapter.fromJson(root.toString())?.data?.channel
    }

    suspend fun blockConnections(id: Long): List<ChannelDetail> = withContext(Dispatchers.IO) {
        val root = graphql("BlockMetadataConnectionsQuery", GraphQLQueries.BLOCK_CONNECTIONS, mapOf("id" to id.toString()))
        val block = root.optJSONObject("data")?.optJSONObject("block") ?: return@withContext emptyList()
        val arr = block.optJSONArray("connections") ?: return@withContext emptyList()
        val out = mutableListOf<ChannelDetail>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                ChannelDetail(
                    id = o.optLong("id", 0),
                    title = o.optString("title", ""),
                    href = o.optString("href", null),
                    visibility = o.optString("visibility_name", null),
                    owner = parseOwner(o.optJSONObject("owner")),
                    counts = parseCounts(o.optJSONObject("counts"))
                )
            )
        }
        out
    }

    suspend fun blockComments(id: Long): List<Comment> = withContext(Dispatchers.IO) {
        val root = graphql("BlockMetadataCommentsQuery", GraphQLQueries.BLOCK_COMMENTS, mapOf("id" to id.toString()))
        val block = root.optJSONObject("data")?.optJSONObject("block") ?: return@withContext emptyList()
        val arr = block.optJSONArray("comments") ?: return@withContext emptyList()
        val out = mutableListOf<Comment>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                Comment(
                    id = o.optLong("id", 0),
                    body = o.optString("body", null),
                    createdAt = o.optString("created_at", null),
                    user = parseOwner(o.optJSONObject("user"))
                )
            )
        }
        out
    }

    // ---- REST v2 search (no auth) ----

    suspend fun search(query: String, page: Int = 1, per: Int = 20): RestSearchResponse =
        withContext(Dispatchers.IO) {
            val url = "$REST_SEARCH_URL?q=${java.net.URLEncoder.encode(query, "UTF-8")}&page=$page&per=$per"
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("REST search HTTP ${resp.code}")
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return@use RestSearchResponse()
                // Are.na v2 sometimes returns a top-level array
                if (body.trimStart().startsWith("[")) {
                    val arr = JSONArray(body)
                    val channels = mutableListOf<RestChannel>()
                    val blocks = mutableListOf<RestBlock>()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        when (o.optString("class", "")) {
                            "Channel" -> channels.add(parseRestChannel(o))
                            else -> blocks.add(parseRestBlock(o))
                        }
                    }
                    RestSearchResponse(arr.length(), channels, blocks)
                } else {
                    restSearchAdapter.fromJson(body) ?: RestSearchResponse()
                }
            }
        }

    /** REST v2 channel contents (no auth) - used to bulk-download a whole channel. */
    suspend fun restChannelContents(slugOrId: String, page: Int = 1, per: Int = 100): Pair<List<RestBlock>, Int> =
        withContext(Dispatchers.IO) {
            val url = "$REST_BASE/channels/${java.net.URLEncoder.encode(slugOrId, "UTF-8")}?page=$page&per=$per"
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList<RestBlock>() to 0
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return@use emptyList<RestBlock>() to 0
                val root = JSONObject(body)
                val meta = root.optJSONObject("meta")
                val totalContents = meta?.optInt("total", 0) ?: 0
                val contents = root.optJSONArray("contents") ?: JSONArray()
                val out = mutableListOf<RestBlock>()
                for (i in 0 until contents.length()) {
                    out.add(parseRestBlock(contents.getJSONObject(i)))
                }
                out to totalContents
            }
        }

    /** REST v2 user's channels (no auth) - used to bulk-download a user. */
    suspend fun restUserChannels(slug: String, page: Int = 1, per: Int = 100): Pair<List<RestChannel>, Int> =
        withContext(Dispatchers.IO) {
            val url = "$REST_BASE/users/${java.net.URLEncoder.encode(slug, "UTF-8")}/channels?page=$page&per=$per"
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList<RestChannel>() to 0
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return@use emptyList<RestChannel>() to 0
                val root = JSONObject(body)
                val meta = root.optJSONObject("meta")
                val totalChannels = meta?.optInt("total", 0) ?: 0
                val ch = root.optJSONArray("channels") ?: JSONArray()
                val out = mutableListOf<RestChannel>()
                for (i in 0 until ch.length()) {
                    out.add(parseRestChannel(ch.getJSONObject(i)))
                }
                out to totalChannels
            }
        }

    private fun optStr(o: JSONObject?, key: String): String? {
        if (o == null || !o.has(key) || o.isNull(key)) return null
        val v = o.optString(key, "")
        return if (v.isEmpty()) null else v
    }

    private fun parseOwner(o: JSONObject?): Owner? {
        if (o == null) return null
        return Owner(
            typename = optStr(o, "__typename"),
            id = if (o.has("id") && !o.isNull("id")) o.getLong("id") else null,
            name = optStr(o, "name"),
            slug = optStr(o, "slug")
        )
    }

    private fun parseCounts(o: JSONObject?): ChannelCounts? {
        if (o == null) return null
        return ChannelCounts(if (o.has("contents") && !o.isNull("contents")) o.optInt("contents", 0) else null)
    }

    private fun parseRestChannel(o: JSONObject): RestChannel = RestChannel(
        id = o.optLong("id", 0),
        title = optStr(o, "title"),
        slug = optStr(o, "slug"),
        createdAt = optStr(o, "created_at"),
        updatedAt = optStr(o, "updated_at"),
        visibility = optStr(o, "visibility"),
        user = parseOwner(o.optJSONObject("user")),
        counts = parseCounts(o.optJSONObject("counts"))
    )

    private fun parseRestBlock(o: JSONObject): RestBlock = RestBlock(
        id = o.optLong("id", 0),
        title = optStr(o, "title"),
        klass = optStr(o, "class"),
        content = optStr(o, "content"),
        imageUrl = optStr(o, "image_url"),
        embedHtml = optStr(o, "embed_html"),
        sourceUrl = optStr(o, "source_url"),
        createdAt = optStr(o, "created_at")
    )

    companion object {
        private const val GRAPHQL_URL = "https://api.are.na/graphql"
        private const val REST_BASE = "https://api.are.na/v2"
        private const val REST_SEARCH_URL = "$REST_BASE/search"
        private const val USER_AGENT = "SuperComplex/1.0 (Android)"

        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        @Volatile private var INSTANCE: ArenaApiClient? = null
        fun get(): ArenaApiClient =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ArenaApiClient().also { INSTANCE = it }
            }
    }
}
