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
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getStatus(repoRoot: File): RepoStatus = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                val s = git.status().call()
                val branch = git.repository.branch
                RepoStatus(branch, s.untracked, s.modified, s.added, s.removed)
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

    // 获取保存的远程地址、用户名和 Token
    suspend fun getRemoteConfig(repoRoot: File): Triple<String, String, String> = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                val config = git.repository.config
                val url = config.getString("remote", "origin", "url") ?: ""
                val user = config.getString("gitmobile", "auth", "user") ?: ""
                val token = config.getString("gitmobile", "auth", "token") ?: ""
                Triple(url, user, token)
            }
        } catch (e: Exception) { Triple("", "", "") }
    }

    // 保存远程配置，包含 Token
    suspend fun saveRemoteConfig(repoRoot: File, url: String, username: String, token: String) = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                val config = git.repository.config
                config.setString("remote", "origin", "url", url)
                config.setString("gitmobile", "auth", "user", username)
                config.setString("gitmobile", "auth", "token", token)
                config.save()
            }
        } catch (e: Exception) { }
    }

    suspend fun pull(repoRoot: File, username: String, token: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                val cp = UsernamePasswordCredentialsProvider(username, token)
                git.pull().setCredentialsProvider(cp).setRemote("origin").call()
                Result.success("拉取成功")
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun push(repoRoot: File, username: String, token: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                val cp = UsernamePasswordCredentialsProvider(username, token)
                git.push().setRemote("origin").setCredentialsProvider(cp).call()
                Result.success("推送成功")
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun sync(repoRoot: File, remoteUrl: String, username: String, token: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                val cp = UsernamePasswordCredentialsProvider(username, token)

                // 1. 先尝试 Pull (拉取)
                try {
                    git.pull().setCredentialsProvider(cp).setRemote("origin").call()
                } catch (e: Exception) {
                    // 忽略拉取错误（例如由于历史不相关导致的失败）
                }

                // 2. 再执行 Push (推送)
                git.push().setRemote("origin").setCredentialsProvider(cp).call()

                // 3. 只有当 Push 成功没有抛出异常时，才保存配置到本地
                saveRemoteConfig(repoRoot, remoteUrl, username, token)

                Result.success("同步成功")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clone(dir: File, url: String, username: String, token: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cp = UsernamePasswordCredentialsProvider(username, token)
            Git.cloneRepository()
                .setURI(url)
                .setDirectory(dir)
                .setCredentialsProvider(cp)
                .call()
                .use { _ ->
                    // 克隆成功后，保存认证信息到本地 Git 配置中（方便后续同步）
                    saveRemoteConfig(dir, url, username, token)
                    Result.success("克隆成功")
                }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getHistory(repoRoot: File): List<CommitInfo> = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                // 获取远程分支的最新提交 ID (尝试 main 或 master)
                val remoteHead = git.repository.findRef("refs/remotes/origin/main")?.objectId
                    ?: git.repository.findRef("refs/remotes/origin/master")?.objectId
                val remoteHeadName = remoteHead?.name

                val log = git.log().setMaxCount(20).call()
                log.map { rev ->
                    CommitInfo(
                        id = rev.name, // 使用全量 ID
                        author = rev.authorIdent.name,
                        message = rev.shortMessage,
                        time = rev.commitTime.toLong() * 1000,
                        isRemote = rev.name == remoteHeadName // 标记是否为云端位置
                    )
                }
            }
        } catch (e: Exception) { emptyList() }
    }

    // 获取某个提交的变更文件列表
    suspend fun getCommitChanges(repoRoot: File, commitId: String): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                val repository = git.repository
                val objectId = repository.resolve(commitId)
                val revWalk = org.eclipse.jgit.revwalk.RevWalk(repository)
                val commit = revWalk.parseCommit(objectId)
                val parent = if (commit.parentCount > 0) revWalk.parseCommit(commit.getParent(0).id) else null
                
                val df = org.eclipse.jgit.diff.DiffFormatter(java.io.ByteArrayOutputStream())
                df.setRepository(repository)
                val diffs = if (parent != null) {
                    df.scan(parent.tree, commit.tree)
                } else {
                    // 第一个提交，与空树比较
                    df.scan(org.eclipse.jgit.treewalk.EmptyTreeIterator(), org.eclipse.jgit.treewalk.CanonicalTreeParser(null, repository.newObjectReader(), commit.tree))
                }
                
                diffs.map { diff ->
                    val path = if (diff.changeType == org.eclipse.jgit.diff.DiffEntry.ChangeType.DELETE) diff.oldPath else diff.newPath
                    path to diff.changeType.name
                }
            }
        } catch (e: Exception) { emptyList() }
    }

    // 获取某个文件在某个提交中的 Diff
    suspend fun getFileDiff(repoRoot: File, commitId: String, filePath: String): String = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                val repository = git.repository
                val objectId = repository.resolve(commitId)
                val revWalk = org.eclipse.jgit.revwalk.RevWalk(repository)
                val commit = revWalk.parseCommit(objectId)
                val parent = if (commit.parentCount > 0) revWalk.parseCommit(commit.getParent(0).id) else null
                
                val out = java.io.ByteArrayOutputStream()
                org.eclipse.jgit.diff.DiffFormatter(out).use { df ->
                    df.setRepository(repository)
                    df.setPathFilter(org.eclipse.jgit.treewalk.filter.PathFilter.create(filePath))
                    if (parent != null) {
                        df.format(parent.tree, commit.tree)
                    } else {
                        df.format(org.eclipse.jgit.treewalk.EmptyTreeIterator(), org.eclipse.jgit.treewalk.CanonicalTreeParser(null, repository.newObjectReader(), commit.tree))
                    }
                }
                out.toString("UTF-8")
            }
        } catch (e: Exception) { "无法获取 Diff" }
    }
}