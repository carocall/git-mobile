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
                val headExists = git.repository.resolve(Constants.HEAD) != null

                // 1. 先取消暂存 (Reset)
                if (headExists) {
                    val resetCmd = git.reset().setRef(Constants.HEAD)
                    paths.forEach { resetCmd.addPath(it) }
                    resetCmd.call()
                } else {
                    // 初始仓库，通过 rm --cached 取消暂存
                    val statusBefore = git.status().call()
                    val staged = paths.filter { !statusBefore.untracked.contains(it) && !statusBefore.untrackedFolders.any { f -> it.startsWith(f) } }
                    if (staged.isNotEmpty()) {
                        val rmCmd = git.rm().setCached(true)
                        staged.forEach { rmCmd.addFilepattern(it) }
                        try { rmCmd.call() } catch (e: Exception) { }
                    }
                }

                // 2. 获取最新状态，决定如何处理工作区
                val s = git.status().call()
                
                for (path in paths) {
                    if (s.untracked.contains(path) || s.untrackedFolders.any { path.startsWith(it) }) {
                        // 未追踪的文件（包括刚取消暂存的新文件）：直接删除
                        val file = File(repoRoot, path)
                        if (file.exists()) {
                            if (file.isDirectory) file.deleteRecursively() else file.delete()
                        }
                    } else {
                        // 已追踪的文件：通过 checkout 恢复工作区
                        git.checkout().addPath(path).call()
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
                    modified = s.modified + s.changed,
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

    suspend fun commit(repoRoot: File, message: String, files: List<String>, authorName: String? = null, authorEmail: String? = null): Result<String> = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                if (files.isEmpty()) {
                    git.add().addFilepattern(".").call()
                    git.add().addFilepattern(".").setUpdate(true).call()
                } else {
                    for (path in files) {
                        val file = File(repoRoot, path)
                        if (file.exists()) {
                            git.add().addFilepattern(path).call()
                        } else {
                            try { git.rm().addFilepattern(path).call() } catch (e: Exception) { }
                        }
                    }
                }
                
                val commitCmd = git.commit().setMessage(message)
                
                // 显式设置作者和提交者，防止回退到 "root"
                if (!authorName.isNullOrBlank() && !authorEmail.isNullOrBlank()) {
                    val ident = org.eclipse.jgit.lib.PersonIdent(authorName, authorEmail)
                    commitCmd.setAuthor(ident).setCommitter(ident)
                }
                
                commitCmd.call()
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

    suspend fun getRemoteConfig(repoRoot: File): RemoteProfile = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                val config = git.repository.config
                val remoteName = getRemoteName(git)
                val url = config.getString("remote", remoteName, "url") ?: ""
                val accountId = config.getString("gitmobile", "auth", "accountId")
                RemoteProfile("Active", url, accountId)
            }
        } catch (e: Exception) { RemoteProfile("", "", null) }
    }

    // 保存远程配置
    suspend fun saveRemoteConfig(repoRoot: File, profile: RemoteProfile) = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                val config = git.repository.config
                val remoteName = getRemoteName(git)
                config.setString("remote", remoteName, "url", profile.url)
                config.setString("gitmobile", "auth", "accountId", profile.accountId)
                config.save()
            }
        } catch (e: Exception) { }
    }

    // 获取仓库级别的本地身份
    suspend fun getLocalIdentity(repoRoot: File): Pair<String, String> = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                val config = git.repository.config
                val name = config.getString("user", null, "name") ?: ""
                val email = config.getString("user", null, "email") ?: ""
                name to email
            }
        } catch (e: Exception) { "" to "" }
    }

    // 保存仓库级别的本地身份
    suspend fun saveLocalIdentity(repoRoot: File, name: String, email: String) = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                val config = git.repository.config
                config.setString("user", null, "name", name)
                config.setString("user", null, "email", email)
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
                        accountId = config.getString("gitmobile-profile", name, "accountId")
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
                config.setString("gitmobile-profile", profile.name, "accountId", profile.accountId)
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

    suspend fun fetch(
        repoRoot: File,
        username: String,
        token: String,
        progressMonitor: org.eclipse.jgit.lib.ProgressMonitor? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                val remoteName = getRemoteName(git)
                val fetchCmd = git.fetch().setRemote(remoteName)
                    .setRemoveDeletedRefs(true)
                    .setProgressMonitor(progressMonitor)
                if (username.isNotBlank() && token.isNotBlank()) {
                    fetchCmd.setCredentialsProvider(UsernamePasswordCredentialsProvider(username, token))
                }
                fetchCmd.call()
                Result.success(Unit)
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun pull(
        repoRoot: File,
        username: String,
        token: String,
        progressMonitor: org.eclipse.jgit.lib.ProgressMonitor? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                val remoteName = getRemoteName(git)
                val pullCmd = git.pull().setRemote(remoteName)
                    .setProgressMonitor(progressMonitor)
                if (username.isNotBlank() && token.isNotBlank()) {
                    pullCmd.setCredentialsProvider(UsernamePasswordCredentialsProvider(username, token))
                }
                pullCmd.call()
                Result.success("拉取成功")
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun push(
        repoRoot: File,
        username: String,
        token: String,
        progressMonitor: org.eclipse.jgit.lib.ProgressMonitor? = null
    ): Result<String> = withContext(Dispatchers.IO) {
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
                    .setProgressMonitor(progressMonitor)

                pushCmd.call()
                Result.success("推送成功")
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun sync(
        repoRoot: File,
        username: String,
        token: String,
        pullFailedMsg: String,
        pushFailedPrefix: String,
        progressMonitor: org.eclipse.jgit.lib.ProgressMonitor? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (username.isBlank() || token.isBlank()) {
            return@withContext Result.failure(Exception("AUTH_REQUIRED"))
        }
        try {
            Git.open(repoRoot).use { git ->
                val cp = UsernamePasswordCredentialsProvider(username, token)
                val remoteName = getRemoteName(git)

                // 1. 先执行 Pull
                val pullResult = git.pull()
                    .setRemote(remoteName)
                    .setCredentialsProvider(cp)
                    .setProgressMonitor(progressMonitor)
                    .call()

                if (!pullResult.isSuccessful) {
                    // 如果拉取不成功（比如有冲突）
                    return@withContext Result.failure(Exception(pullFailedMsg))
                }

                // 2. 只有 Pull 成功（或已经是最新）后，再执行 Push
                val pushResults = git.push()
                    .setRemote(remoteName)
                    .setCredentialsProvider(cp)
                    .setProgressMonitor(progressMonitor)
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
        accountId: String? = null,
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
                // 克隆成功后，保存远程配置
                val profile = RemoteProfile(name = "origin", url = url, accountId = accountId)
                saveRemoteConfig(dir, profile)
                saveRemoteProfile(dir, profile)
                Result.success("克隆成功")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCommitInfo(repoRoot: File, commitId: String): CommitInfo? = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                val repository = git.repository
                val objectId = repository.resolve(commitId)
                val revWalk = org.eclipse.jgit.revwalk.RevWalk(repository)
                val rev = revWalk.parseCommit(objectId)
                
                val remoteName = getRemoteName(git)
                val remoteHead = repository.findRef("refs/remotes/$remoteName/main")?.objectId
                    ?: repository.findRef("refs/remotes/$remoteName/master")?.objectId
                
                CommitInfo(
                    id = rev.name,
                    author = rev.authorIdent.name,
                    message = rev.shortMessage,
                    time = rev.commitTime.toLong() * 1000,
                    isRemote = rev.name == remoteHead?.name
                )
            }
        } catch (e: Exception) { null }
    }

    suspend fun getHistory(repoRoot: File, skip: Int = 0, limit: Int = 20): List<CommitInfo> = withContext(Dispatchers.IO) {
        try {
            Git.open(repoRoot).use { git ->
                val remoteName = getRemoteName(git)
                // 获取远程分支的最新提交 ID (尝试 main 或 master)
                val remoteHead = git.repository.findRef("refs/remotes/$remoteName/main")?.objectId
                    ?: git.repository.findRef("refs/remotes/$remoteName/master")?.objectId
                val remoteHeadName = remoteHead?.name

                val log = git.log().setSkip(skip).setMaxCount(limit).call()
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
