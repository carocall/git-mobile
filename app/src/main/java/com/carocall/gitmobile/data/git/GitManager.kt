package com.carocall.gitmobile.data.git

import com.carocall.gitmobile.data.model.BranchInfo
import com.carocall.gitmobile.data.model.CommitInfo
import com.carocall.gitmobile.data.model.RepoStatus
import com.carocall.gitmobile.data.model.RemoteProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.lib.Constants
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

    suspend fun getBranches(repoRoot: File): List<BranchInfo> = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                val currentBranch = git.repository.branch
                val branches = git.branchList().setListMode(ListBranchCommand.ListMode.ALL).call()
                branches.map { ref ->
                    val name = ref.name
                    val shortName = git.repository.shortenRemoteBranchName(name) ?: name.substringAfter(Constants.R_HEADS).substringAfter(Constants.R_REMOTES)
                    val isCurrent = shortName == currentBranch
                    val isRemote = name.startsWith(Constants.R_REMOTES)
                    BranchInfo(
                        name = shortName,
                        isCurrent = isCurrent,
                        isRemote = isRemote,
                        shortHash = ref.objectId.abbreviate(7).name()
                    )
                }
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun checkoutBranch(repoRoot: File, branchName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                git.checkout().setName(branchName).call()
                Result.success(Unit)
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun createBranch(repoRoot: File, branchName: String, startPoint: String = "HEAD", checkout: Boolean = true): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                git.branchCreate().setName(branchName).setStartPoint(startPoint).call()
                if (checkout) {
                    git.checkout().setName(branchName).call()
                }
                Result.success(Unit)
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun deleteBranch(repoRoot: File, branchName: String, force: Boolean = false): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                git.branchDelete().setBranchNames(branchName).setForce(force).call()
                Result.success(Unit)
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun discardChanges(repoRoot: File, paths: List<String>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                val status = git.status().call()
                val toCheckout = mutableListOf<String>()
                val toDelete = mutableListOf<String>()

                for (path in paths) {
                    if (status.untracked.contains(path) || status.untrackedFolders.any { path.startsWith(it) }) {
                        toDelete.add(path)
                    } else {
                        toCheckout.add(path)
                    }
                }

                if (toCheckout.isNotEmpty()) {
                    val checkoutCmd = git.checkout()
                    toCheckout.forEach { checkoutCmd.addPath(it) }
                    checkoutCmd.call()
                }

                if (toDelete.isNotEmpty()) {
                    toDelete.forEach { path ->
                        val file = File(repoRoot, path)
                        if (file.exists()) {
                            if (file.isDirectory) file.deleteRecursively() else file.delete()
                        }
                    }
                }
                Result.success(Unit)
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getStatus(repoRoot: File): RepoStatus = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                val s = git.status().call()
                val branch = git.repository.branch
                
                RepoStatus(
                    branch = branch,
                    untracked = s.untracked,
                    modified = s.modified,
                    added = s.added,
                    removed = s.removed + s.missing
                )
            }
        } catch (e: Exception) { RepoStatus() }
    }

    suspend fun commit(repoRoot: File, message: String, files: List<String>): Result<String> = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                if (files.isEmpty()) {
                    // 提交所有变更，包含新增、修改和删除
                    git.add().addFilepattern(".").call() // 处理新增和修改
                    git.add().addFilepattern(".").setUpdate(true).call() // 处理修改和删除
                } else {
                    // 针对选中的文件处理
                    for (path in files) {
                        val file = File(repoRoot, path)
                        if (file.exists()) {
                            git.add().addFilepattern(path).call()
                        } else {
                            // 文件不存在，说明是删除操作，需要从索引中移除
                            try {
                                git.rm().addFilepattern(path).call()
                            } catch (e: Exception) {
                                // 如果文件从未被 track 过就删除了，rm 会报错，这里忽略即可
                            }
                        }
                    }
                }
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

    suspend fun getRemoteProfiles(repoRoot: File): List<RemoteProfile> = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                val config = git.repository.config
                val subsections = config.getSubsections("gitmobile-profile")
                subsections.map { name ->
                    RemoteProfile(
                        name = name,
                        url = config.getString("gitmobile-profile", name, "url") ?: "",
                        user = config.getString("gitmobile-profile", name, "user") ?: "",
                        token = config.getString("gitmobile-profile", name, "token") ?: ""
                    )
                }
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun saveRemoteProfile(repoRoot: File, profile: RemoteProfile) = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                val config = git.repository.config
                config.setString("gitmobile-profile", profile.name, "url", profile.url)
                config.setString("gitmobile-profile", profile.name, "user", profile.user)
                config.setString("gitmobile-profile", profile.name, "token", profile.token)
                config.save()
            }
        } catch (e: Exception) { }
    }

    suspend fun deleteRemoteProfile(repoRoot: File, name: String) = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                val config = git.repository.config
                config.unsetSection("gitmobile-profile", name)
                config.save()
            }
        } catch (e: Exception) { }
    }

    suspend fun pull(repoRoot: File, username: String, token: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                val pullCmd = git.pull().setRemote("origin")
                if (username.isNotBlank() && token.isNotBlank()) {
                    pullCmd.setCredentialsProvider(UsernamePasswordCredentialsProvider(username, token))
                }
                pullCmd.call()
                Result.success("拉取成功")
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun push(repoRoot: File, username: String, token: String): Result<String> = withContext(Dispatchers.IO) {
        if (username.isBlank() || token.isBlank()) {
            return@withContext Result.failure(Exception("该仓库未配置身份"))
        }
        try {
            Git.open(repoRoot).use { git ->
                val pushCmd = git.push().setRemote("origin")
                pushCmd.setCredentialsProvider(UsernamePasswordCredentialsProvider(username, token))
                pushCmd.call()
                Result.success("推送成功")
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun sync(repoRoot: File, remoteUrl: String, username: String, token: String): Result<String> = withContext(Dispatchers.IO) {
        if (username.isBlank() || token.isBlank()) {
            return@withContext Result.failure(Exception("该仓库未配置身份"))
        }
        try {
            Git.open(repoRoot).use { git ->
                val cp = UsernamePasswordCredentialsProvider(username, token)

                // 1. 先尝试 Pull (拉取)
                try {
                    val pullCmd = git.pull().setRemote("origin")
                    pullCmd.setCredentialsProvider(cp)
                    pullCmd.call()
                } catch (e: Exception) {
                    // 忽略拉取错误（例如由于历史不相关导致的失败）
                }

                // 2. 再执行 Push (推送)
                val pushCmd = git.push().setRemote("origin")
                pushCmd.setCredentialsProvider(cp)
                pushCmd.call()

                // 3. 只有当 Push 成功没有抛出异常时，保存配置到本地
                saveRemoteConfig(repoRoot, remoteUrl, username, token)

                Result.success("同步成功")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clone(
        dir: File,
        url: String,
        username: String? = null,
        token: String? = null,
        branch: String? = null,
        progressMonitor: org.eclipse.jgit.lib.ProgressMonitor? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cloneCommand = Git.cloneRepository()
                .setURI(url)
                .setDirectory(dir)
                .setProgressMonitor(progressMonitor)

            if (!username.isNullOrBlank() && !token.isNullOrBlank()) {
                val cp = UsernamePasswordCredentialsProvider(username, token)
                cloneCommand.setCredentialsProvider(cp)
            }

            if (!branch.isNullOrBlank()) {
                cloneCommand.setBranch(branch)
            }

            cloneCommand.call().use { _ ->
                // 克隆成功后，如果有认证信息，保存认证信息到本地 Git 配置中，并放入远程列表
                if (!username.isNullOrBlank() && !token.isNullOrBlank()) {
                    saveRemoteConfig(dir, url, username, token)
                    saveRemoteProfile(dir, RemoteProfile(name = "Default", url = url, user = username, token = token))
                }
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

    suspend fun addTag(repoRoot: File, tagName: String, commitId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                git.tag().setName(tagName).setObjectId(git.repository.parseCommit(git.repository.resolve(commitId))).call()
                Result.success(Unit)
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun checkoutCommit(repoRoot: File, commitId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                git.checkout().setName(commitId).call()
                Result.success(Unit)
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun cherryPick(repoRoot: File, commitId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                git.cherryPick().include(git.repository.resolve(commitId)).call()
                Result.success(Unit)
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun revertCommit(repoRoot: File, commitId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                git.revert().include(git.repository.resolve(commitId)).call()
                Result.success(Unit)
            }
        } catch (e: Exception) { Result.failure(e) }
    }
}
