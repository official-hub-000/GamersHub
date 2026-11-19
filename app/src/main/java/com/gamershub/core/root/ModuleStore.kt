package com.gamershub.core.root

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Module store — the "download and install" half of the Root screen.
 *
 * Two kinds of source, both real and both remote:
 *
 *  1. [Catalog.KERNELSU] — modules.kernelsu.org, the official KernelSU repository.
 *     Curated, and every entry is published as an immutable GitHub release.
 *  2. [Catalog.MMRL] — MMRL repositories (ZG-R, Yukari0201), the community format
 *     used by most KernelSU module managers. Gives versioned zips plus size and
 *     category.
 *
 * Anything the catalogs do not list can still be installed from a direct URL or a
 * local zip on the Root screen, so the store is never a gate.
 *
 * Two rules this file keeps, because they are what make a module store safe:
 *
 *  - **Nothing is installed that was not inspected.** Every download is opened as
 *    a zip and checked for a real module layout before it reaches the installer.
 *    Wrapped archives (`repo-tag.zip` style) are re-packed with `module.prop` at
 *    the top level, because flashing a wrapped zip fails or bootloops.
 *  - **A failed download never leaves a half-written file** that looks flashable.
 *    Downloads go to `.part` and are renamed only after the byte count matches.
 */
object ModuleStore {

    // ------------------------------------------------------------------ model

    data class Entry(
        val id: String,
        val name: String,
        val version: String,
        val author: String,
        val description: String,
        val zipUrl: String,
        val size: Long,
        val stars: Int,
        val categories: List<String> = emptyList(),
        val source: String = "",
        val updated: Long = 0L,
    ) {
        /** Gaming-relevant, for the default filter. */
        val gaming: Boolean
            get() {
                val hay = (id + " " + name + " " + description + " " +
                    categories.joinToString(" ")).lowercase()
                return GAMING_WORDS.any { hay.contains(it) }
            }

        val sizeLabel: String
            get() = when {
                size <= 0L -> ""
                size < 1_000_000 -> "${size / 1024} KB"
                else -> String.format("%.1f MB", size / 1_000_000.0)
            }
    }

    private val GAMING_WORDS = listOf(
        "game", "gaming", "fps", "gpu", "perf", "boost", "thermal", "tune",
        "overlayfs", "zygisk", "integrity", "spoof", "hide", "memory", "ram",
    )

    enum class Catalog(val label: String) {
        KERNELSU("KernelSU Store"),
        MMRL("Community (MMRL)"),
    }

    data class Progress(val read: Long, val total: Long) {
        val fraction: Float get() = if (total > 0) (read.toFloat() / total).coerceIn(0f, 1f) else 0f
    }

    // ------------------------------------------------------------------ catalogs

    /**
     * The KernelSU repository publishes its index as a Nuxt payload. The page
     * itself is rendered from `_payload.json`; that file is the catalog.
     */
    private const val KSU_PAYLOAD = "https://modules.kernelsu.org/_payload.json"

    /** MMRL-format community repositories, each a single modules.json. */
    private val MMRL_REPOS = listOf(
        "https://raw.githubusercontent.com/ZGUATION-PROJECTS/ZG-R/main/json/modules.json",
        "https://raw.githubusercontent.com/Yukari0201/mmrl-repo/main/json/modules.json",
    )

    /**
     * Always-present picks. These are verified real GitHub releases, so the store
     * is useful even when the remote catalogs are unreachable or change shape.
     */
    private val CURATED = listOf(
        Curated("PlayIntegrityFork", "Play Integrity Fix (fork)",
            "https://api.github.com/repos/osm0sis/PlayIntegrityFork", "osm0sis",
            "Passes Play Integrity / SafetyNet so banking and some games stop complaining."),
        Curated("ZygiskNext", "Zygisk Next",
            "https://api.github.com/repos/Dr-TSNG/ZygiskNext", "Dr-TSNG",
            "Standalone Zygisk implementation — required by most gaming modules on KernelSU/APatch."),
        Curated("TrickyStore", "Tricky Store",
            "https://api.github.com/repos/5ec1cff/TrickyStore", "5ec1cff",
            "Keystore-backed hardware attestation for integrity checks."),
        Curated("Sui", "Sui (Shizuku-style root)",
            "https://api.github.com/repos/RikkaApps/Sui", "RikkaApps",
            "Zygisk-based su implementation; also what GamersHub's Shizuku path pairs well with."),
        Curated("LSPosed", "LSPosed",
            "https://api.github.com/repos/LSPosed/LSPosed", "LSPosed",
            "The Xposed framework most mods target."),
    )

    private data class Curated(
        val id: String,
        val name: String,
        val repoApi: String,
        val author: String,
        val description: String,
    )

    /**
     * Loads everything. Each source is isolated so one broken catalog cannot take
     * the whole store down — that is why this returns entries and a list of the
     * failures rather than throwing.
     */
    suspend fun loadAll(): Pair<List<Entry>, List<String>> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Entry>()
        val problems = mutableListOf<String>()

        runCatching { loadCurated() }
            .onSuccess { out += it }
            .onFailure { problems += "curated: ${it.message}" }

        runCatching { loadKernelSu() }
            .onSuccess { out += it }
            .onFailure { problems += "kernelsu.org: ${it.message}" }

        MMRL_REPOS.forEach { url ->
            val host = url.substringAfter("//").substringBefore("/")
            runCatching { loadMmrl(url) }
                .onSuccess { out += it }
                .onFailure { problems += "$host: ${it.message}" }
        }

        // Deduplicate by id, keeping the first (curated wins).
        val seen = mutableSetOf<String>()
        val unique = out.filter { seen.add(it.id.lowercase()) }
        unique.sortedWith(compareByDescending<Entry> { it.gaming }.thenByDescending { it.stars })
            .let { it to problems }
    }

    private fun loadCurated(): List<Entry> = CURATED.mapNotNull { c ->
        runCatching {
            val json = JSONObject(httpGet("${c.repoApi}/releases/latest", 12_000))
            val assets = json.optJSONArray("assets") ?: JSONArray()
            var zip: JSONObject? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".zip", true)) { zip = a; break }
            }
            val z = zip ?: return@runCatching null
            Entry(
                id = c.id,
                name = c.name,
                version = json.optString("tag_name", "—"),
                author = c.author,
                description = c.description,
                zipUrl = z.optString("browser_download_url"),
                size = z.optLong("size"),
                stars = 0,
                categories = listOf("Integrity", "Zygisk"),
                source = "curated",
                updated = 0L,
            )
        }.getOrNull()
    }

    /**
     * KernelSU's catalog. The payload shape is Nuxt's (nested arrays keyed by
     * string indices), so this walks the tree looking for objects that carry a
     * module id and a download url instead of assuming a fixed path. That keeps
     * it working when the site changes its layout.
     */
    private fun loadKernelSu(): List<Entry> {
        val raw = httpGet(KSU_PAYLOAD, 15_000)
        val found = mutableListOf<Entry>()
        val seen = mutableSetOf<String>()

        fun urlOf(o: JSONObject): String? {
            o.optString("zipUrl").takeIf { it.startsWith("http") }?.let { return it }
            o.optString("zip_url").takeIf { it.startsWith("http") }?.let { return it }
            o.optString("downloadUrl").takeIf { it.startsWith("http") }?.let { return it }
            o.optString("url").takeIf { it.startsWith("http") && it.contains(".zip") }?.let { return it }
            val assets = o.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                val n = a.optString("name")
                if (n.endsWith(".zip", true)) {
                    a.optString("browser_download_url").takeIf { it.startsWith("http") }?.let { return it }
                    a.optString("url").takeIf { it.startsWith("http") }?.let { return it }
                }
            }
            return null
        }

        fun walk(node: Any?) {
            when (node) {
                is JSONObject -> {
                    val id = node.optString("id")
                    val url = urlOf(node)
                    if (id.isNotBlank() && url != null && seen.add(id.lowercase())) {
                        found += Entry(
                            id = id,
                            name = node.optString("name").ifBlank { id },
                            version = node.optString("version").ifBlank { "—" },
                            author = node.optString("author").ifBlank {
                                node.optString("display").ifBlank { "unknown" }
                            },
                            description = node.optString("description"),
                            zipUrl = url,
                            size = node.optLong("size"),
                            stars = node.optInt("stars"),
                            categories = node.optJSONArray("categories")?.let { arr ->
                                (0 until arr.length()).map { arr.optString(it) }
                            } ?: emptyList(),
                            source = "kernelsu.org",
                            updated = node.optLong("updated"),
                        )
                    }
                    node.keys().forEach { walk(node.opt(it)) }
                }
                is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i))
            }
        }

        walk(JSONObject(raw))
        return found
    }

    /** MMRL repository: one modules.json with a `modules` array of versioned zips. */
    private fun loadMmrl(url: String): List<Entry> {
        val root = JSONObject(httpGet(url, 15_000))
        val mods = root.optJSONArray("modules") ?: return emptyList()
        val out = mutableListOf<Entry>()
        for (i in 0 until mods.length()) {
            val m = mods.optJSONObject(i) ?: continue
            val versions = m.optJSONArray("versions") ?: continue
            if (versions.length() == 0) continue

            // Highest versionCode wins; the array is not guaranteed to be ordered.
            var best: JSONObject? = null
            var bestCode = Long.MIN_VALUE
            for (j in 0 until versions.length()) {
                val v = versions.optJSONObject(j) ?: continue
                val code = v.optLong("versionCode")
                if (code >= bestCode) { bestCode = code; best = v }
            }
            val v = best ?: continue
            val zip = v.optString("zipUrl")
            if (!zip.startsWith("http")) continue

            out += Entry(
                id = m.optString("id"),
                name = m.optString("name").ifBlank { m.optString("id") },
                version = m.optString("version").ifBlank { v.optString("version", "—") },
                author = m.optString("author"),
                description = m.optString("description"),
                zipUrl = zip,
                size = v.optLong("size", m.optLong("size")),
                stars = 0,
                categories = m.optJSONArray("categories")?.let { arr ->
                    (0 until arr.length()).map { arr.optString(it) }
                } ?: emptyList(),
                source = "mmrl",
                updated = (v.optDouble("timestamp", 0.0) * 1000).toLong(),
            )
        }
        return out
    }

    // ------------------------------------------------------------------ download

    /**
     * Downloads [entry] and returns a local zip that is ready to flash.
     *
     * The result is guaranteed to be a zip whose top level contains either
     * `module.prop` or `META-INF` — the same check [ModuleManager.install] makes.
     * Wrapped GitHub archives are unwrapped here rather than being handed to the
     * installer to fail.
     */
    suspend fun download(
        entry: Entry,
        cacheDir: File,
        onProgress: (Progress) -> Unit = {},
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val safeName = entry.id.replace(Regex("[^A-Za-z0-9._-]"), "_").take(40)
            val target = File(cacheDir, "store_${safeName}_${entry.version.replace(Regex("[^A-Za-z0-9._-]"), "_")}.zip")
            if (target.exists() && target.length() > 1024) {
                // Already downloaded and previously validated.
                if (inspect(target) != null) return@runCatching target
            }

            val part = File(cacheDir, target.name + ".part")
            val total = fetchTo(entry.zipUrl, part, onProgress)
            if (total > 0 && part.length() != total) {
                part.delete()
                throw IOException("incomplete download (${part.length()} of $total bytes)")
            }
            if (part.length() < 1024) {
                part.delete()
                throw IOException("downloaded file is too small to be a module")
            }

            val fixed = prepare(part, target)
            part.delete()
            fixed
        }
    }

    /**
     * Validates the zip and normalises its layout.
     *
     * Returns the flashable file, or throws with a message that says what is
     * actually wrong with the download.
     */
    private fun prepare(source: File, target: File): File {
        val layout = inspect(source)
            ?: throw IOException("not a module zip (no module.prop or META-INF)")

        // Top level already correct: just move it into place.
        if (layout.atRoot) {
            if (source.renameTo(target)) return target
            source.copyTo(target, overwrite = true)
            return target
        }

        // Wrapped archive: repack with the single top folder's contents at root.
        ZipFile(source).use { zip ->
            val entries = zip.entries().toList()
            val prefix = layout.prefix
            java.util.zip.ZipOutputStream(target.outputStream().buffered()).use { out ->
                var written = 0
                for (e in entries) {
                    if (e.isDirectory) continue
                    val name = e.name
                    if (!name.startsWith(prefix)) continue
                    val rel = name.removePrefix(prefix)
                    if (rel.isBlank() || rel.contains("..")) continue
                    out.putNextEntry(java.util.zip.ZipEntry(rel))
                    zip.getInputStream(e).use { it.copyTo(out) }
                    out.closeEntry()
                    written++
                }
                if (written == 0) {
                    target.delete()
                    throw IOException("the archive has no files to install")
                }
            }
        }
        if (inspect(target) == null) {
            target.delete()
            throw IOException("could not normalise the zip into a flashable module")
        }
        return target
    }

    private data class Layout(val atRoot: Boolean, val prefix: String)

    /** Reads the zip's entry names to decide whether it is flashable as-is. */
    private fun inspect(file: File): Layout? = runCatching {
        ZipFile(file).use { zip ->
            val names = zip.entries().toList().map { it.name }
            if (names.any { it == "module.prop" || it.startsWith("META-INF/") }) {
                return@use Layout(true, "")
            }
            // One wrapper directory, e.g. "repo-tag/module.prop".
            val roots = names.mapNotNull { it.substringBefore('/').takeIf { p -> p.isNotBlank() } }
                .distinct()
            if (roots.size == 1) {
                val p = roots.first() + "/"
                if (names.any { it == p + "module.prop" || it.startsWith(p + "META-INF/") }) {
                    return@use Layout(false, p)
                }
            }
            null
        }
    }.getOrNull()

    // ------------------------------------------------------------------ http

    private fun httpGet(url: String, timeoutMs: Int): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "GamersHub-Android")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** Streams a URL to [dest], reporting progress. Returns content length (or -1). */
    private fun fetchTo(url: String, dest: File, onProgress: (Progress) -> Unit): Long {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "GamersHub-Android")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code from ${url.substringAfter("//").substringBefore("/")}")

            val total = conn.contentLengthLong
            var read = 0L
            conn.inputStream.use { input ->
                dest.outputStream().buffered().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var lastTick = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        read += n
                        // Throttle UI updates so a fast download does not flood Compose.
                        if (read - lastTick > 256 * 1024) {
                            lastTick = read
                            onProgress(Progress(read, total))
                        }
                    }
                }
            }
            onProgress(Progress(read, total))
            return total
        } finally {
            conn.disconnect()
        }
    }
}
