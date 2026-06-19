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
import com.carocall.gitmobile.ui.screens.RepoSortOrder
import com.carocall.gitmobile.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {

    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val REPO_SORT_ORDER_KEY = stringPreferencesKey("repo_sort_order")
        private val NOVEL_FONT_SIZE_KEY = floatPreferencesKey("novel_font_size")
        private val NOVEL_IS_SERIF_KEY = booleanPreferencesKey("novel_is_serif")
        private val NOVEL_BG_COLOR_KEY = intPreferencesKey("novel_bg_color")
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
}
