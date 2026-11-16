# SuperComplex

An Are.na client for Android, with grid-based browsing and bulk-resumable
downloads of channels and users.

## Features

- **Explore** — Tier 1 `ExploreResultsQuery` GraphQL feed with filter chips
  (All / Channels / Blocks / Images / Text / Links / Embeds / Files),
  infinite scroll, timestamp-locked offset pagination, and seed-locked
  random sort.
- **Tier 2 hydration** — Each cell is fetched lazily via `blokk(id)` /
  `channel(id)` and resolved against the `Konnectable` union so Image,
  Text, Link, Embed, and Attachment blocks all render correctly.
- **Search** — Unauthenticated REST v2 search endpoint, no key required.
- **Block detail** — Image / Text / Link / Embed / Attachment render,
  connections list, comments list, share, open-in-browser, bookmark,
  and download-this-block.
- **Channel detail** — Grid of channel contents with pagination; opens
  BlockActivity on tap; header has bulk-download button.
- **Bulk download** — Downloads an entire channel into a structured
  folder: `SuperComplex/<channel-slug>/<images|texts|links|embeds|attachments>/`.
  Or downloads an entire user: `SuperComplex/<user-slug>/<channel-slug>/<types>/`.
- **Resumable multi-connection downloader (aria2-style)** — Probes the
  server for `Accept-Ranges`, splits larger files into N concurrent
  `Range: bytes=...` chunks, persists progress in Room so the download
  resumes after a process restart, and falls back to a single stream
  when the server ignores Range.
- **Download tracking & backup** — Every download is recorded in Room
  (status, bytes, path, type, source slug). The Downloads tab shows a
  live summary (count, total size) plus an export/restore JSON backup
  of completed downloads.
- **Foreground service** — `DownloadService` drains the queue while the
  app is backgrounded, with a low-priority notification.

## UI design choices

- **Grid based** — `GridLayoutManager` with dynamic span based on screen
  width, square cells for visual rhythm.
- **No Material UI** — Theme inherits from `Theme.AppCompat.Light.NoActionBar`
  (NOT MaterialComponents). All UI surfaces are custom: minimal headers,
  no FABs, no Material cards, no Material buttons.
- **No unnecessary texts or buttons** — Single-row header bars with
  sparse icons; chips use a custom background; nav bar is a hand-rolled
  LinearLayout with three icon+text cells.
- **Great icon lib** — All icons are hand-crafted vector drawables
  (`res/drawable/ic_*.xml`) so they tint cleanly to match the palette.

## Build

GitHub Actions workflow in `.github/workflows/build.yml` builds a signed
release APK with a CI-generated keystore (no secrets required) and
publishes it to `public/SuperComplex-<version>.apk` on every push to
`main` or `workflow_dispatch` trigger.

To build locally:

```
./gradlew assembleRelease
# APK appears at app/build/outputs/apk/release/app-release.apk
```

## Architecture

| Layer | Files |
| :--- | :--- |
| Networking (GraphQL + REST) | `data/ArenaApiClient.kt`, `data/GraphQLQueries.kt` |
| Repository | `data/ArenaRepository.kt` |
| Persistence | `data/AppDatabase.kt`, `data/Daos.kt` |
| Download engine | `download/DownloadManager.kt`, `download/BulkDownloader.kt`, `download/DownloadTracker.kt`, `download/DownloadService.kt` |
| UI — Explore / Search / Downloads | `ui/explore/`, `ui/search/`, `ui/downloads/` |
| UI — Detail screens | `ui/channel/`, `ui/block/`, `ui/user/` |
| UI — shared | `ui/common/SquareImageView.kt`, `ui/common/GridAdapter.kt`, `ui/common/SimpleTextAdapter.kt` |

## Are.na API notes

The public web client token `X-APP-TOKEN: pL4YhrdwXq7Bm7t8s6Yt`
shipped in the Are.na web bundle grants read access to public Explore
content, blocks, channels and search without requiring a user session.
It is used for the GraphQL traffic in this app. Search uses the legacy
REST v2 endpoint `https://api.are.na/v2/search` with no authentication
at all, per the spec.
