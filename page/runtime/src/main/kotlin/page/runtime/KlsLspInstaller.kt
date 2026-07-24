package page.runtime

import java.nio.file.Path

class KlsLspInstaller : LspInstaller {

    override val languageId: String = "kotlin"
    override val displayName: String = "kotlin-language-server"
    override val precheck: LspInstaller.Precheck = LspInstaller.Precheck.Ok

    override fun isInstalled(): Boolean =
        KlsInstaller.activeLabel() != null || KlsInstaller.isInstalled()

    override fun executable(): Path? {
        val active = KlsInstaller.activeLabel()
        if (active != null) {
            val dir = KlsInstaller.installRootForLabel(active)
            if (dir != null) return KlsInstaller.executable(dir)
        }
        return KlsInstaller.executable()
    }

    override fun defaultVersion(): String? = labelOf(KlsInstaller.VERSION, FORK)

    override fun installedVersion(): String? {
        val active = KlsInstaller.activeLabel()
        if (active != null) return active
        return if (KlsInstaller.isInstalled()) labelOf(KlsInstaller.VERSION, FORK) else null
    }

    override fun installedVersions(): List<String> {
        val labels = KlsInstaller.installedLabels().toMutableList()
        if (labels.isEmpty() && KlsInstaller.isInstalled()) {
            labels.add(labelOf(KlsInstaller.VERSION, FORK))
        }
        return labels.distinct()
    }

    override fun activeVersion(): String? = installedVersion()

    override fun applyVersion(version: String): Boolean {
        KlsInstaller.setActiveLabel(version)
        return true
    }

    override fun uninstall(version: String) {
        KlsInstaller.uninstallLabel(version)
    }

    override fun availableVersions(): List<String> {
        val now = System.currentTimeMillis()
        val cached = LspReleasesCache.load()
        if (LspReleasesCache.isFresh(cached, now)) {
            return labelsOf(cached!!)
        }

        val manifest = KlsReleasesManifest.fetch(ifNoneMatch = cached?.etagManifest)
        if (manifest != null && manifest.notModified && cached != null) {
            val refreshed = cached.copy(fetchedAt = now)
            LspReleasesCache.save(refreshed)
            return labelsOf(refreshed)
        }
        if (manifest != null && (manifest.fork.isNotEmpty() || manifest.upstream.isNotEmpty())) {
            val refreshed = LspReleasesCache.Cached(
                fetchedAt = now,
                source = "manifest",
                etagFork = cached?.etagFork,
                etagUpstream = cached?.etagUpstream,
                etagManifest = manifest.etag ?: cached?.etagManifest,
                fork = manifest.fork,
                upstream = manifest.upstream,
            )
            LspReleasesCache.save(refreshed)
            return labelsOf(refreshed)
        }

        val forkResp = runCatching {
            GitHubReleases.listReleasesWithEtag(FORK_OWNER, REPO, cached?.etagFork)
        }.getOrNull()
        val upstreamResp = runCatching {
            GitHubReleases.listReleasesWithEtag(UPSTREAM_OWNER, REPO, cached?.etagUpstream)
        }.getOrNull()

        val forkList = when {
            forkResp == null -> cached?.fork.orEmpty()
            forkResp.notModified -> cached?.fork.orEmpty()
            else -> forkResp.releases.map { LspReleasesCache.TaggedRelease(it.tagName, it.publishedAt) }
        }
        val upstreamList = when {
            upstreamResp == null -> cached?.upstream.orEmpty()
            upstreamResp.notModified -> cached?.upstream.orEmpty()
            else -> upstreamResp.releases.map { LspReleasesCache.TaggedRelease(it.tagName, it.publishedAt) }
        }

        if (forkList.isNotEmpty() || upstreamList.isNotEmpty()) {
            val refreshed = LspReleasesCache.Cached(
                fetchedAt = now,
                source = "github-api",
                etagFork = forkResp?.etag ?: cached?.etagFork,
                etagUpstream = upstreamResp?.etag ?: cached?.etagUpstream,
                etagManifest = cached?.etagManifest,
                fork = forkList,
                upstream = upstreamList,
            )
            LspReleasesCache.save(refreshed)
            return labelsOf(refreshed)
        }

        val fallback = mutableListOf<String>()
        if (forkList.isEmpty()) fallback.add(labelOf(KlsInstaller.VERSION, FORK))
        fallback.addAll(installedVersions())
        return fallback.distinct()
    }

    private fun labelsOf(cached: LspReleasesCache.Cached): List<String> {
        val fork = cached.fork.map { labelOf(it.tag, FORK) }
        val upstream = cached.upstream.map { labelOf(it.tag, UPSTREAM) }
        val merged = mutableListOf<String>()
        merged.addAll(fork)
        merged.addAll(upstream)
        if (fork.isEmpty()) merged.add(labelOf(KlsInstaller.VERSION, FORK))
        merged.addAll(installedVersions())
        return merged.distinct()
    }

    override fun install(version: String?, onProgress: (LspInstaller.Progress) -> Unit) {
        val label = version ?: defaultVersion() ?: labelOf(KlsInstaller.VERSION, FORK)
        val (tag, source) = parseLabel(label)
        val owner = if (source == UPSTREAM) UPSTREAM_OWNER else FORK_OWNER
        val url = "https://github.com/$owner/$REPO/releases/download/$tag/server.zip"
        KlsInstaller.installLabeled(label = label, downloadUrl = url) { p ->
            onProgress(mapProgress(p))
        }
    }

    private fun mapProgress(p: KlsInstaller.Progress): LspInstaller.Progress = when (p) {
        is KlsInstaller.Progress.Downloading -> LspInstaller.Progress.Downloading(p.bytesRead, p.total)
        KlsInstaller.Progress.Extracting -> LspInstaller.Progress.Extracting()
        is KlsInstaller.Progress.Done -> LspInstaller.Progress.Done(p.executable)
        is KlsInstaller.Progress.Failed -> LspInstaller.Progress.Failed(p.error)
    }

    companion object {
        const val FORK = "fork"
        const val UPSTREAM = "upstream"
        const val FORK_OWNER = "Monkshark"
        const val UPSTREAM_OWNER = "fwcd"
        const val REPO = "kotlin-language-server"

        fun labelOf(tag: String, source: String): String = "$tag ($source)"

        fun parseLabel(label: String): Pair<String, String> = when {
            label.endsWith(" ($FORK)") -> label.removeSuffix(" ($FORK)") to FORK
            label.endsWith(" ($UPSTREAM)") -> label.removeSuffix(" ($UPSTREAM)") to UPSTREAM
            else -> label to FORK
        }
    }
}
