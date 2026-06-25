package com.carocall.gitmobile.data.git

import com.carocall.gitmobile.data.model.GitProgress
import org.eclipse.jgit.lib.ProgressMonitor

class ComposeGitProgressMonitor(
    private val onProgress: (GitProgress) -> Unit
) : ProgressMonitor {
    private var currentTask: String = ""
    private var totalWork: Int = 0
    private var completedWork: Int = 0

    override fun start(totalTasks: Int) {}

    override fun beginTask(title: String, totalWork: Int) {
        this.currentTask = title
        this.totalWork = if (totalWork > 0) totalWork else 0
        this.completedWork = 0
        report()
    }

    override fun update(completed: Int) {
        this.completedWork += completed
        if (totalWork > 0 && completedWork > totalWork) {
            // 有时 JGit 的进度会超过 total，做个保护
        }
        report()
    }

    override fun endTask() {}

    override fun showDuration(show: Boolean) {}

    override fun isCancelled(): Boolean = false

    private fun report() {
        onProgress(
            GitProgress(
                taskName = currentTask,
                completed = completedWork,
                total = totalWork,
                indeterminate = totalWork <= 0
            )
        )
    }
}
