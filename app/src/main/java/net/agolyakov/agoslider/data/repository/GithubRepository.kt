package net.agolyakov.agoslider.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.agolyakov.agoslider.data.model.github.GithubAsset
import net.agolyakov.agoslider.data.model.github.GithubRelease
import net.agolyakov.agoslider.data.remote.api.GithubApiService
import okhttp3.ResponseBody
import retrofit2.http.Streaming
import java.io.IOException
import javax.inject.Inject

class GithubRepository @Inject constructor(
    private val githubApiService: GithubApiService,
    private val owner: String,
    private val repo: String
) {
    suspend fun getLatestRelease(): GithubRelease {
        return githubApiService.getLatestRelease(owner, repo)
    }

    suspend fun getFirmwareAsset(version: String? = null): GithubAsset {
        val release = getLatestRelease()
        return release.assets.firstOrNull { asset ->
            asset.name.endsWith("_debug_16mb_fw.bin", ignoreCase = true) &&
                    (version == null || asset.name.contains(version, ignoreCase = true))
        } ?: throw IOException("No releases found")
    }

    @Streaming
    suspend fun downloadAsset(assetId: String): ResponseBody {
        return githubApiService.downloadAsset(
            owner = owner,
            repo = repo,
            assetId = assetId,
            accept = "application/octet-stream"
        )
    }

    fun extractVersionFromUrl(url: String): String? {
        val pattern = """download/([^/]+)/""".toRegex()
        return pattern.find(url)?.groupValues?.get(1)
    }

    suspend fun getLatestRelease(includePreReleases: Boolean): GithubRelease {
        return withContext(Dispatchers.IO) {
            if (includePreReleases) {
                githubApiService.getAllReleases(owner, repo)
                    .firstOrNull()
                    ?: throw IOException("No releases found")
            } else {
                githubApiService.getLatestRelease(owner, repo)
            }
        }
    }
}