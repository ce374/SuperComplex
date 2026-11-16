package com.supercomplex.app.data

/**
 * GraphQL operation strings extracted from the Are.na Explore documentation.
 * Tier 1: ExploreResultsQuery returns only { id, type } pairs.
 * Tier 2: blokk(id) / channel(id) hydrate the concrete entity.
 *
 * The public X-APP-TOKEN is shipped with the Are.na web client bundle and
 * grants read access to all public Explore content, blocks, channels and
 * search without requiring a user session.
 */
object GraphQLQueries {

    const val APP_TOKEN = "pL4YhrdwXq7Bm7t8s6Yt"

    val EXPLORE = """
        query ExploreResultsQuery(
          ${'$'}what: What
          ${'$'}where: [Where!]
          ${'$'}order: Order
          ${'$'}page: Int
          ${'$'}per: Int
          ${'$'}seed: Int
          ${'$'}timestamp: DateTime
        ) {
          search {
            advanced(
              mode: EXPLORE
              what: ${'$'}what
              where: ${'$'}where
              order: ${'$'}order
              page: ${'$'}page
              per: ${'$'}per
              seed: ${'$'}seed
              timestamp: ${'$'}timestamp
            ) {
              total
              results { id type }
            }
          }
        }
    """.trimIndent()

    val BLOCK = """
        query GridCellBlockQuery(${'$'}id: ID!) {
          block: blokk(id: ${'$'}id) {
            __typename
            ... on Model { id created_at(relative: true) updated_at(relative: true) }
            ... on ConnectableInterface { title href description(format: HTML) }
            ... on Image {
              title href alt_text
              image_url(size: ORIGINAL)
              resized_image {
                blurhash original_url
                grid_cell_resized_image: resized(width: 400 height: 400 quality: 75 flatten: true) {
                  id width height src_1x src_2x
                }
              }
            }
            ... on Text {
              title href
              content(format: HTML)
              raw_content: content(format: MARKDOWN)
              truncated_content: content(format: HTML truncate: 1000 no_links: true)
              sub_type { __typename ... on HexColor { value } }
            }
            ... on Link {
              title href source_url
              source { url provider_name }
              canonical_link { id title provider_name provider_url content state }
              resized_image {
                grid_cell_resized_image: resized(width: 400 height: 400) { src_1x src_2x }
              }
            }
            ... on Embed {
              title href embed_html embed_width embed_height source_url
              source { url provider_name }
              resized_image {
                grid_cell_resized_image: resized(width: 400 height: 400) { src_1x src_2x }
              }
            }
            ... on Attachment {
              title href file_name file_url file_size file_extension file_content_type
              resized_image {
                grid_cell_resized_image: resized(width: 400 height: 400) { src_1x src_2x }
              }
            }
          }
        }
    """.trimIndent()

    val CHANNEL = """
        query GridCellChannelQuery(${'$'}id: ID!) {
          channel(id: ${'$'}id) {
            id title href visibility_name is_nsfw
            created_at(relative: true) added_to_at(relative: true)
            counts { contents }
            owner { __typename ... on User { id name slug } ... on Group { id name slug } }
          }
        }
    """.trimIndent()

    val CHANNEL_CONTENTS = """
        query ChannelContentsQuery(${'$'}id: ID!, ${'$'}page: Int, ${'$'}per: Int) {
          channel(id: ${'$'}id) {
            id title href visibility_name
            counts { contents }
            owner { __typename ... on User { id name slug } ... on Group { id name slug } }
            contents(page: ${'$'}page, per: ${'$'}per) {
              __typename
              ... on Model { id created_at(relative: true) }
              ... on ConnectableInterface { title href }
              ... on Image {
                title href
                image_url(size: ORIGINAL)
                resized_image {
                  original_url
                  grid_cell_resized_image: resized(width: 400 height: 400 quality: 75) { src_1x src_2x }
                }
              }
              ... on Text {
                title href content(format: HTML)
                raw_content: content(format: MARKDOWN)
                sub_type { __typename ... on HexColor { value } }
              }
              ... on Link {
                title href source_url
                source { url provider_name }
                resized_image { grid_cell_resized_image: resized(width: 400 height: 400) { src_1x src_2x } }
              }
              ... on Embed {
                title href embed_html embed_width embed_height source_url
                source { url provider_name }
                resized_image { grid_cell_resized_image: resized(width: 400 height: 400) { src_1x src_2x } }
              }
              ... on Attachment {
                title href file_name file_url file_size file_extension file_content_type
                resized_image { grid_cell_resized_image: resized(width: 400 height: 400) { src_1x src_2x } }
              }
            }
          }
        }
    """.trimIndent()

    val BLOCK_CONNECTIONS = """
        query BlockMetadataConnectionsQuery(${'$'}id: ID!) {
          block: blokk(id: ${'$'}id) {
            ... on ConnectableInterface {
              connections(filter: ALL) {
                id title href visibility_name
                counts { contents }
                owner { __typename ... on User { id name slug } ... on Group { id name slug } }
              }
            }
          }
        }
    """.trimIndent()

    val BLOCK_COMMENTS = """
        query BlockMetadataCommentsQuery(${'$'}id: ID!) {
          block: blokk(id: ${'$'}id) {
            ... on Block {
              can { comment }
              comments(direction: ASC) {
                id body(format: HTML) created_at(relative: true)
                user { id name }
              }
            }
          }
        }
    """.trimIndent()
}
