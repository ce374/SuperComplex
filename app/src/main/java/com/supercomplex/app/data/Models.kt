package com.supercomplex.app.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Domain models for Are.na content.
 * Tier 1 returns { id, type } pairs; Tier 2 hydrates a Block or Channel.
 */

enum class BlockType(val graphqlFacet: String) {
    ALL("ALL"),
    CHANNEL("CHANNEL"),
    IMAGE("IMAGE"),
    TEXT("TEXT"),
    LINK("LINK"),
    MEDIA("MEDIA"),
    ATTACHMENT("ATTACHMENT"),
    BLOCK("BLOCK");

    companion object {
        fun fromTypename(name: String): BlockType = when (name) {
            "Image" -> IMAGE
            "Text" -> TEXT
            "Link" -> LINK
            "Embed" -> MEDIA
            "Attachment" -> ATTACHMENT
            "Channel" -> CHANNEL
            else -> BLOCK
        }
    }
}

@JsonClass(generateAdapter = true)
data class SearchResults(
    @Json(name = "total") val total: Int? = 0,
    @Json(name = "results") val results: List<IdRef>? = null
)

@JsonClass(generateAdapter = true)
data class IdRef(
    @Json(name = "id") val id: Long,
    @Json(name = "type") val type: String
)

@JsonClass(generateAdapter = true)
data class SearchEnvelope(
    @Json(name = "data") val data: SearchData?
)

@JsonClass(generateAdapter = true)
data class SearchData(
    @Json(name = "search") val search: SearchInner?
)

@JsonClass(generateAdapter = true)
data class SearchInner(
    @Json(name = "advanced") val advanced: SearchResults?
)

/** Block / Channel detail payloads */
@JsonClass(generateAdapter = true)
data class BlockEnvelope(
    @Json(name = "data") val data: BlockData?
)

@JsonClass(generateAdapter = true)
data class BlockData(
    @Json(name = "block") val block: BlockDetail? = null,
    @Json(name = "channel") val channel: ChannelDetail? = null
)

@JsonClass(generateAdapter = true)
data class BlockDetail(
    @Json(name = "__typename") val typename: String?,
    @Json(name = "id") val id: Long?,
    @Json(name = "title") val title: String? = null,
    @Json(name = "href") val href: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "image_url") val imageUrl: String? = null,
    @Json(name = "alt_text") val altText: String? = null,
    @Json(name = "content") val contentHtml: String? = null,
    @Json(name = "raw_content") val rawContent: String? = null,
    @Json(name = "truncated_content") val truncatedContent: String? = null,
    @Json(name = "source_url") val sourceUrl: String? = null,
    @Json(name = "source") val source: Source? = null,
    @Json(name = "embed_html") val embedHtml: String? = null,
    @Json(name = "embed_width") val embedWidth: Int? = null,
    @Json(name = "embed_height") val embedHeight: Int? = null,
    @Json(name = "file_name") val fileName: String? = null,
    @Json(name = "file_url") val fileUrl: String? = null,
    @Json(name = "file_size") val fileSize: String? = null,
    @Json(name = "file_extension") val fileExtension: String? = null,
    @Json(name = "file_content_type") val fileContentType: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null,
    @Json(name = "resized_image") val resizedImage: ResizedImage? = null,
    @Json(name = "sub_type") val subType: SubType? = null,
    @Json(name = "connections") val connections: List<ChannelDetail>? = null,
    @Json(name = "comments") val comments: List<Comment>? = null,
    @Json(name = "can") val can: Can? = null
) {
    fun type(): BlockType = BlockType.fromTypename(typename ?: "")

    fun previewImageUrl(): String? {
        resizedImage?.gridCellResizedImage?.src1x?.let { return it }
        resizedImage?.gridCellResizedImage?.src2x?.let { return it }
        resizedImage?.gridCellResizedImage?.src?.let { return it }
        imageUrl?.let { return it }
        return null
    }

    /** Resolve the canonical download URL based on block type. */
    fun downloadUrl(): String? = when (type()) {
        BlockType.IMAGE -> resizedImage?.originalUrl ?: imageUrl
        BlockType.LINK -> resizedImage?.originalUrl ?: source?.url ?: sourceUrl
        BlockType.MEDIA -> sourceUrl
        BlockType.ATTACHMENT -> fileUrl
        else -> null
    }

    fun downloadExtension(): String = when (type()) {
        BlockType.IMAGE -> "jpg"
        BlockType.ATTACHMENT -> fileExtension ?: "bin"
        BlockType.MEDIA -> "mp4"
        BlockType.LINK -> "png"
        BlockType.TEXT -> "txt"
        else -> "bin"
    }
}

@JsonClass(generateAdapter = true)
data class ResizedImage(
    @Json(name = "blurhash") val blurhash: String? = null,
    @Json(name = "original_url") val originalUrl: String? = null,
    @Json(name = "grid_cell_resized_image") val gridCellResizedImage: GridResized? = null
)

@JsonClass(generateAdapter = true)
data class GridResized(
    @Json(name = "src_1x") val src1x: String? = null,
    @Json(name = "src_2x") val src2x: String? = null,
    @Json(name = "src") val src: String? = null,
    @Json(name = "width") val width: Int? = null,
    @Json(name = "height") val height: Int? = null
)

@JsonClass(generateAdapter = true)
data class Source(
    @Json(name = "url") val url: String? = null,
    @Json(name = "provider_name") val providerName: String? = null
)

@JsonClass(generateAdapter = true)
data class SubType(
    @Json(name = "__typename") val typename: String? = null,
    @Json(name = "value") val value: String? = null
)

@JsonClass(generateAdapter = true)
data class ChannelDetail(
    @Json(name = "id") val id: Long?,
    @Json(name = "title") val title: String? = null,
    @Json(name = "href") val href: String? = null,
    @Json(name = "visibility_name") val visibility: String? = null,
    @Json(name = "is_nsfw") val nsfw: Boolean? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "added_to_at") val addedToAt: String? = null,
    @Json(name = "counts") val counts: ChannelCounts? = null,
    @Json(name = "owner") val owner: Owner? = null,
    @Json(name = "contents") val contents: List<BlockDetail>? = null
)

@JsonClass(generateAdapter = true)
data class ChannelCounts(
    @Json(name = "contents") val contents: Int? = 0
)

@JsonClass(generateAdapter = true)
data class Owner(
    @Json(name = "__typename") val typename: String? = null,
    @Json(name = "id") val id: Long? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "slug") val slug: String? = null
)

@JsonClass(generateAdapter = true)
data class Comment(
    @Json(name = "id") val id: Long? = null,
    @Json(name = "body") val body: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "user") val user: Owner? = null
)

@JsonClass(generateAdapter = true)
data class Can(
    @Json(name = "comment") val comment: Boolean? = null
)

/** REST v2 search response (no auth required) */
@JsonClass(generateAdapter = true)
data class RestSearchResponse(
    @Json(name = "total") val total: Int = 0,
    @Json(name = "channels") val channels: List<RestChannel>? = null,
    @Json(name = "blocks") val blocks: List<RestBlock>? = null
)

@JsonClass(generateAdapter = true)
data class RestChannel(
    @Json(name = "id") val id: Long,
    @Json(name = "title") val title: String?,
    @Json(name = "slug") val slug: String?,
    @Json(name = "created_at") val createdAt: String?,
    @Json(name = "updated_at") val updatedAt: String?,
    @Json(name = "visibility") val visibility: String?,
    @Json(name = "user") val user: Owner?,
    @Json(name = "counts") val counts: ChannelCounts?
)

@JsonClass(generateAdapter = true)
data class RestBlock(
    @Json(name = "id") val id: Long,
    @Json(name = "title") val title: String?,
    @Json(name = "class") val klass: String?,
    @Json(name = "content") val content: String?,
    @Json(name = "image_url") val imageUrl: String?,
    @Json(name = "embed_html") val embedHtml: String?,
    @Json(name = "source_url") val sourceUrl: String?,
    @Json(name = "created_at") val createdAt: String?
)
