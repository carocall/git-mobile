package com.carocall.gitmobile.data.git

import com.carocall.gitmobile.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.RepositoryState
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File

// --- Git 管理器 ---

object GitManager {
    fun isGitRepo(dir: File): Boolean = File(dir, ".git").exists()

    // 获取首选远程库名称（默认为 origin）
    private fun getRemoteName(git: Git): String {
        val remotes = git.remoteList().call()
        return if (remotes.isNotEmpty()) remotes[0].name else "origin"
    }

    suspend fun initRepo(dir: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            Git.init().setDirectory(dir).call().use { Result.success("初始化成功") }
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getBranches(repoRoot: File): List<BranchInfo> = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                val repository = git.repository
                val currentFullBranch = repository.fullBranch
                
                // 1. 获取本地分支
                val localRefs = git.branchList().call()
                val localBranchNames = localRefs.map { it.name }.toSet()
                
                val result = mutableListOf<BranchInfo>()
                
                // 处理本地分支
                localRefs.forEach { ref ->
                    result.add(BranchInfo(
                        fullRefName = ref.name,
                        displayName = repository.shortenRemoteBranchName(ref.name) ?: ref.name.substringAfter(Constants.R_HEADS),
                        type = BranchType.LOCAL,
                        isCurrent = ref.name == currentFullBranch,
                        shortHash = ref.objectId.abbreviate(7).name()
                    ))
                }
                
                // 2. 获取远程分支
                val remoteRefs = git.branchList().setListMode(ListBranchCommand.ListMode.REMOTE).call()
                remoteRefs.forEach { ref ->
                    val displayName = repository.shortenRemoteBranchName(ref.name) ?: ref.name.substringAfter(Constants.R_REMOTES)
                    // 检查是否已被本地追踪（这里简单通过名字匹配，实际可更深层判断）
                    val isTracked = localBranchNames.any { it.substringAfter(Constants.R_HEADS) == displayName.substringAfter("/") }
                    
                    result.add(BranchInfo(
                        fullRefName = ref.name,
                        displayName = displayName,
                        type = BranchType.REMOTE,
                        isCurrent = false,
                        shortHash = ref.objectId.abbreviate(7).name(),
                        isTracked = isTracked
                    ))
                }
                
                // 3. 获取标签 (Tags)
                val tagRefs = git.tagList().call()
                tagRefs.forEach { ref ->
                    result.add(BranchInfo(
                        fullRefName = ref.name,
                        displayName = ref.name.substringAfter(Constants.R_TAGS),
                        type = BranchType.TAG,
                        isCurrent = false,
                        shortHash = ref.objectId.abbreviate(7).name()
                    ))
                }
                
                result
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
                val repository = git.repository
                val state = repository.repositoryState
                
                RepoStatus(
                    branch = repository.branch,
                    untracked = s.untracked,
                    modified = s.modified,
                    added = s.added,
                    removed = s.removed + s.missing,
                    isMerging = state == RepositoryState.MERGING,
                    isRebasing = state == RepositoryState.REBASING || state == RepositoryState.REBASING_INTERACTIVE,
                    hasConflicts = s.conflicting.isNotEmpty(),
                    conflicts = s.conflicting
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

    suspend fun testConnection(url: String, user: String, token: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val credentialsProvider = if (user.isNotBlank() && token.isNotBlank()) {
                UsernamePasswordCredentialsProvider(user, token)
            } else null
            
            Git.lsRemoteRepository()
                .setRemote(url)
                .setCredentialsProvider(credentialsProvider)
                .call()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 获取保存的远程地址、用户名和 Token
    suspend fun getRemoteConfig(repoRoot: File): Triple<String, String, String> = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                val config = git.repository.config
                val remoteName = getRemoteName(git)
                val url = config.getString("remote", remoteName, "url") ?: ""
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
                val remoteName = getRemoteName(git)
                config.setString("remote", remoteName, "url", url)
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

    suspend fun fetch(repoRoot: File, username: String, token: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                val remoteName = getRemoteName(git)
                val fetchCmd = git.fetch().setRemote(remoteName)
                    .setRemoveDeletedRefs(true)
                if (username.isNotBlank() && token.isNotBlank()) {
                    fetchCmd.setCredentialsProvider(UsernamePasswordCredentialsProvider(username, token))
                }
                fetchCmd.call()
                Result.success(Unit)
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun pull(repoRoot: File, username: String, token: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                val remoteName = getRemoteName(git)
                val pullCmd = git.pull().setRemote(remoteName)
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
                val remoteName = getRemoteName(git)
                // 显式推送当前分支到远程对应分支，提高 Gitee 等服务器的兼容性
                val pushCmd = git.push()
                    .setRemote(remoteName)
                    .setCredentialsProvider(UsernamePasswordCredentialsProvider(username, token))

                pushCmd.call()
                Result.success("推送成功")
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun sync(
        repoRoot: File,
        remoteUrl: String,
        username: String,
        token: String,
        pullFailedMsg: String,
        pushFailedPrefix: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (username.isBlank() || token.isBlank()) {
            return@withContext Result.failure(Exception("AUTH_MISSING"))
        }
        try {
            Git.open(repoRoot).use { git ->
                val cp = UsernamePasswordCredentialsProvider(username, token)
                val remoteName = getRemoteName(git)

                // 1. 先执行 Pull
                val pullResult = git.pull()
                    .setRemote(remoteName)
                    .setCredentialsProvider(cp)
                    .call()

                if (!pullResult.isSuccessful) {
                    // 如果拉取不成功（比如有冲突）
                    return@withContext Result.failure(Exception(pullFailedMsg))
                }

                // 2. 只有 Pull 成功（或已经是最新）后，再执行 Push
                val pushResults = git.push()
                    .setRemote(remoteName)
                    .setCredentialsProvider(cp)
                    .call()
                
                // 检查推送结果
                for (pushResult in pushResults) {
                    for (updates in pushResult.remoteUpdates) {
                        if (updates.status != org.eclipse.jgit.transport.RemoteRefUpdate.Status.OK && 
                            updates.status != org.eclipse.jgit.transport.RemoteRefUpdate.Status.UP_TO_DATE) {
                            return@withContext Result.failure(Exception("$pushFailedPrefix: ${updates.status}"))
                        }
                    }
                }

                // 3. 全部成功后保存配置
                saveRemoteConfig(repoRoot, remoteUrl, username, token)

                Result.success(Unit)
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
                val remoteName = getRemoteName(git)
                // 获取远程分支的最新提交 ID (尝试 main 或 master)
                val remoteHead = git.repository.findRef("refs/remotes/$remoteName/main")?.objectId
                    ?: git.repository.findRef("refs/remotes/$remoteName/master")?.objectId
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
