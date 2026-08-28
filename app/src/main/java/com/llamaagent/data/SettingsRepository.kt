package com.llamaagent.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "llama_agent_settings")

/**
 * Utrwalanie ustawień aplikacji oraz id ostatnio wybranego modelu przez DataStore.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val TEMPERATURE = floatPreferencesKey("temperature")
        val CONTEXT_LENGTH = intPreferencesKey("context_length")
        val TOP_P = floatPreferencesKey("top_p")
        val TOP_K = intPreferencesKey("top_k")
        val REPEAT_PENALTY = floatPreferencesKey("repeat_penalty")
        val THREADS = intPreferencesKey("threads")
        val MAX_TOKENS = intPreferencesKey("max_tokens")
        val AGENT_MODE = booleanPreferencesKey("agent_mode")
        val MAX_AGENT_ITER = intPreferencesKey("max_agent_iterations")
        val SELECTED_MODEL = stringPreferencesKey("selected_model_id")
        val CUSTOM_MODEL_PATH = stringPreferencesKey("custom_model_path")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            temperature = p[Keys.TEMPERATURE] ?: AppSettings.DEFAULT.temperature,
            contextLength = p[Keys.CONTEXT_LENGTH] ?: AppSettings.DEFAULT.contextLength,
            topP = p[Keys.TOP_P] ?: AppSettings.DEFAULT.topP,
            topK = p[Keys.TOP_K] ?: AppSettings.DEFAULT.topK,
            repeatPenalty = p[Keys.REPEAT_PENALTY] ?: AppSettings.DEFAULT.repeatPenalty,
            threads = p[Keys.THREADS] ?: AppSettings.DEFAULT.threads,
            maxTokens = p[Keys.MAX_TOKENS] ?: AppSettings.DEFAULT.maxTokens,
            agentMode = p[Keys.AGENT_MODE] ?: AppSettings.DEFAULT.agentMode,
            maxAgentIterations = p[Keys.MAX_AGENT_ITER] ?: AppSettings.DEFAULT.maxAgentIterations
        )
    }

    val selectedModelIdFlow: Flow<String?> = context.dataStore.data.map { it[Keys.SELECTED_MODEL] }
    val customModelPathFlow: Flow<String?> = context.dataStore.data.map { it[Keys.CUSTOM_MODEL_PATH] }

    suspend fun saveSettings(s: AppSettings) {
        context.dataStore.edit { p ->
            p[Keys.TEMPERATURE] = s.temperature
            p[Keys.CONTEXT_LENGTH] = s.contextLength
            p[Keys.TOP_P] = s.topP
            p[Keys.TOP_K] = s.topK
            p[Keys.REPEAT_PENALTY] = s.repeatPenalty
            p[Keys.THREADS] = s.threads
            p[Keys.MAX_TOKENS] = s.maxTokens
            p[Keys.AGENT_MODE] = s.agentMode
            p[Keys.MAX_AGENT_ITER] = s.maxAgentIterations
        }
    }

    suspend fun saveSelectedModel(id: String) {
        context.dataStore.edit { it[Keys.SELECTED_MODEL] = id }
    }

    suspend fun saveCustomModelPath(path: String) {
        context.dataStore.edit { it[Keys.CUSTOM_MODEL_PATH] = path }
    }
}
