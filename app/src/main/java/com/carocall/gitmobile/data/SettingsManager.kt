package com.carocall.gitmobile.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.carocall.gitmobile.data.model.GitAccount
import com.carocall.gitmobile.data.model.LocalRepo
import com.carocall.gitmobile.data.model.RecentFile
import com.carocall.gitmobile.ui.screens.RepoSortOrder
import com.carocall.gitmobile.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {

    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val REPO_SORT_ORDER_KEY = stringPreferencesKey("repo_sort_order")
        private val NOVEL_FONT_SIZE_KEY = floatPreferencesKey("novel_font_size")
        private val NOVEL_IS_SERIF_KEY = booleanPreferencesKey("novel_is_serif")
        private val NOVEL_BG_COLOR_KEY = intPreferencesKey("novel_bg_color")
        private val GLOBAL_GIT_NAME_KEY = stringPreferencesKey("global_git_name")
        private val GLOBAL_GIT_EMAIL_KEY = stringPreferencesKey("global_git_email")
        private val GIT_ACCOUNTS_KEY = stringPreferencesKey("git_accounts")
        private val RECENT_FILES_KEY = stringPreferencesKey("recent_files")
        private val LOCAL_REPOS_KEY = stringPreferencesKey("local_repos")
    }

    val localReposFlow: Flow<List<LocalRepo>> = context.dataStore.data
        .map { preferences ->
            val json = preferences[LOCAL_REPOS_KEY] ?: "[]"
            parseLocalRepos(json)
        }

    suspend fun saveLocalRepos(repos: List<LocalRepo>) {
        context.dataStore.edit { preferences ->
            preferences[LOCAL_REPOS_KEY] = serializeLocalRepos(repos)
        }
    }

    suspend fun updateLocalRepo(repo: LocalRepo) {
        context.dataStore.edit { preferences ->
            val repos = parseLocalRepos(preferences[LOCAL_REPOS_KEY] ?: "[]").toMutableList()
            val index = repos.indexOfFirst { it.path == repo.path }
            if (index != -1) {
                repos[index] = repo
            } else {
                repos.add(repo)
            }
            preferences[LOCAL_REPOS_KEY] = serializeLocalRepos(repos)
        }
    }

    suspend fun deleteLocalRepo(path: String) {
        context.dataStore.edit { preferences ->
            val repos = parseLocalRepos(preferences[LOCAL_REPOS_KEY] ?: "[]").filter { it.path != path }
            preferences[LOCAL_REPOS_KEY] = serializeLocalRepos(repos)
        }
    }

    private fun parseLocalRepos(json: String): List<LocalRepo> {
        return try {
            val list = mutableListOf<LocalRepo>()
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(LocalRepo(
                    path = obj.getString("path"),
                    name = obj.getString("name"),
                    alias = obj.optString("alias", ""),
                    lastOpened = obj.optLong("lastOpened", 0L),
                    isStarred = obj.optBoolean("isStarred", false)
                ))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun serializeLocalRepos(repos: List<LocalRepo>): String {
        val array = JSONArray()
        repos.forEach { repo ->
            val obj = JSONObject()
            obj.put("path", repo.path)
            obj.put("name", repo.name)
            obj.put("alias", repo.alias)
            obj.put("lastOpened", repo.lastOpened)
            obj.put("isStarred", repo.isStarred)
            array.put(obj)
        }
        return array.toString()
    }

    val recentFilesFlow: Flow<List<RecentFile>> = context.dataStore.data
        .map { preferences ->
            val json = preferences[RECENT_FILES_KEY] ?: "[]"
            parseRecentFiles(json)
        }

    suspend fun addRecentFile(file: RecentFile) {
        context.dataStore.edit { preferences ->
            val files = parseRecentFiles(preferences[RECENT_FILES_KEY] ?: "[]").toMutableList()
            // Remove if already exists (to update timestamp and move to front)
            files.removeAll { it.path == file.path }
            files.add(0, file)
            // Limit to 10 recent files
            val limited = files.take(10)
            preferences[RECENT_FILES_KEY] = serializeRecentFiles(limited)
        }
    }

    private fun parseRecentFiles(json: String): List<RecentFile> {
        return try {
            val list = mutableListOf<RecentFile>()
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(RecentFile(
                    path = obj.getString("path"),
                    name = obj.getString("name"),
                    repoName = obj.getString("repoName"),
                    lastAccessed = obj.getLong("lastAccessed")
                ))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun serializeRecentFiles(files: List<RecentFile>): String {
        val array = JSONArray()
        files.forEach { file ->
            val obj = JSONObject()
            obj.put("path", file.path)
            obj.put("name", file.name)
            obj.put("repoName", file.repoName)
            obj.put("lastAccessed", file.lastAccessed)
            array.put(obj)
        }
        return array.toString()
    }

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data
        .map { preferences ->
            val name = preferences[THEME_MODE_KEY] ?: ThemeMode.SYSTEM.name
            ThemeMode.valueOf(name)
        }

    val repoSortOrderFlow: Flow<RepoSortOrder> = context.dataStore.data
        .map { preferences ->
            val name = preferences[REPO_SORT_ORDER_KEY] ?: RepoSortOrder.TIME.name
            RepoSortOrder.valueOf(name)
        }

    val novelFontSizeFlow: Flow<Float> = context.dataStore.data
        .map { it[NOVEL_FONT_SIZE_KEY] ?: 18f }

    val novelIsSerifFlow: Flow<Boolean> = context.dataStore.data
        .map { it[NOVEL_IS_SERIF_KEY] ?: true }

    val novelBgColorFlow: Flow<Int?> = context.dataStore.data
        .map { it[NOVEL_BG_COLOR_KEY] }

    val globalGitNameFlow: Flow<String> = context.dataStore.data
        .map { it[GLOBAL_GIT_NAME_KEY] ?: "" }

    val globalGitEmailFlow: Flow<String> = context.dataStore.data
        .map { it[GLOBAL_GIT_EMAIL_KEY] ?: "" }

    val gitAccountsFlow: Flow<List<GitAccount>> = context.dataStore.data
        .map { preferences ->
            val json = preferences[GIT_ACCOUNTS_KEY] ?: "[]"
            parseGitAccounts(json)
        }

    suspend fun saveThemeMode(themeMode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = themeMode.name
        }
    }

    suspend fun saveRepoSortOrder(sortOrder: RepoSortOrder) {
        context.dataStore.edit { preferences ->
            preferences[REPO_SORT_ORDER_KEY] = sortOrder.name
        }
    }

    suspend fun saveNovelFontSize(size: Float) {
        context.dataStore.edit { it[NOVEL_FONT_SIZE_KEY] = size }
    }

    suspend fun saveNovelIsSerif(isSerif: Boolean) {
        context.dataStore.edit { it[NOVEL_IS_SERIF_KEY] = isSerif }
    }

    suspend fun saveNovelBgColor(color: Int) {
        context.dataStore.edit { it[NOVEL_BG_COLOR_KEY] = color }
    }

    suspend fun saveGlobalGitIdentity(name: String, email: String) {
        context.dataStore.edit { preferences ->
            preferences[GLOBAL_GIT_NAME_KEY] = name
            preferences[GLOBAL_GIT_EMAIL_KEY] = email
        }
    }

    suspend fun saveGitAccount(account: GitAccount) {
        context.dataStore.edit { preferences ->
            val accounts = parseGitAccounts(preferences[GIT_ACCOUNTS_KEY] ?: "[]").toMutableList()
            val index = accounts.indexOfFirst { it.id == account.id }
            if (index != -1) {
                accounts[index] = account
            } else {
                accounts.add(account)
            }
            preferences[GIT_ACCOUNTS_KEY] = serializeGitAccounts(accounts)
        }
    }

    suspend fun deleteGitAccount(accountId: String) {
        context.dataStore.edit { preferences ->
            val accounts = parseGitAccounts(preferences[GIT_ACCOUNTS_KEY] ?: "[]").filter { it.id != accountId }
            preferences[GIT_ACCOUNTS_KEY] = serializeGitAccounts(accounts)
        }
    }

    private fun parseGitAccounts(json: String): List<GitAccount> {
        return try {
            val list = mutableListOf<GitAccount>()
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(GitAccount(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    username = obj.getString("username"),
                    token = obj.getString("token")
                ))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun serializeGitAccounts(accounts: List<GitAccount>): String {
        val array = JSONArray()
        accounts.forEach { account ->
            val obj = JSONObject()
            obj.put("id", account.id)
            obj.put("name", account.name)
            obj.put("username", account.username)
            obj.put("token", account.token)
            array.put(obj)
        }
        return array.toString()
    }
}
