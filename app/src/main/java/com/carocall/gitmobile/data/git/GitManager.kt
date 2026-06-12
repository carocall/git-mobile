package com.carocall.gitmobile.data.git

import com.carocall.gitmobile.data.model.CommitInfo
import com.carocall.gitmobile.data.model.RepoStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File


// --- Git 管理器 ---

object GitManager {
    fun isGitRepo(dir: File): Boolean = File(dir, ".git").exists()

    suspend fun initRepo(dir: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            Git.init().setDirectory(dir).call().use { Result.success("初始化成功") }
        } catch (e: Exception) { Result.failure(e)

        }
    }

    suspend fun getStatus(repoRoot: File): RepoStatus = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                val s = git.status().call()
                RepoStatus(s.untracked, s.modified, s.added, s.removed)
            }
        } catch (e: Exception) { RepoStatus() }
    }

    suspend fun commit(repoRoot: File, message: String, files: List<String>): Result<String> = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                val add = git.add()
                if (files.isEmpty()) {
                    add.addFilepattern(".")
                } else {
                    files.forEach { add.addFilepattern(it) }
                }
                add.call()
                git.commit().setMessage(message).call()
                Result.success("提交成功")
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getRemoteConfig(repoRoot: File): Pair<String, String> = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                val config = git.repository.config
                val url = config.getString("remote", "origin", "url") ?: ""
                val token = config.getString("gitmobile", "auth", "token") ?: ""
                url to token
            }
        } catch (e: Exception) { "" to "" }
    }

    suspend fun saveRemoteConfig(repoRoot: File, url: String, token: String) = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                val config = git.repository.config
                config.setString("remote", "origin", "url", url)
                config.setString("gitmobile", "auth", "token", token)
                config.save()
            }
        } catch (e: Exception) { }
    }

    suspend fun sync(repoRoot: File, remoteUrl: String, token: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                saveRemoteConfig(repoRoot, remoteUrl, token)
                val cp = UsernamePasswordCredentialsProvider(token, "")

                // 先尝试 Pull
                try {
                    git.pull().setCredentialsProvider(cp).setRemote("origin").call()
                } catch (e: Exception) {
                    // 如果是新仓库或没有远程分支，忽略错误
                }

                // 再执行 Push
                git.push().setRemote("origin").setCredentialsProvider(cp).call()
                Result.success("同步成功")
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getHistory(repoRoot: File): List<CommitInfo> = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                val log = git.log().setMaxCount(20).call()
                log.map { rev ->
                    CommitInfo(
                        id = rev.name.take(7),
                        author = rev.authorIdent.name,
                        message = rev.shortMessage,
                        time = rev.commitTime.toLong() * 1000
                    )
                }
            }
        } catch (e: Exception) { emptyList() }
    }
}