package com.llamaagent.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.llamaagent.LlamaEngine
import com.llamaagent.agent.AgentEngine
import com.llamaagent.agent.PromptBuilder
import com.llamaagent.agent.tools.CalculatorTool
import com.llamaagent.agent.tools.FileAccessTool
import com.llamaagent.agent.tools.SystemInfoTool
import com.llamaagent.agent.tools.WebSearchTool
import com.llamaagent.data.AppSettings
import com.llamaagent.data.ChatMessage
import com.llamaagent.data.ModelProfile
import com.llamaagent.data.ModelStorage
import com.llamaagent.data.PRESET_MODELS
import com.llamaagent.data.Role
import com.llamaagent.data.SettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Stan ekranu ładowania modelu. */
sealed class ModelLoadState {
    object None : ModelLoadState()
    data class Loading(val message: String) : ModelLoadState()
    data class Loaded(val profile: ModelProfile) : ModelLoadState()
    data class Error(val message: String) : ModelLoadState()
}

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val engine = LlamaEngine()
    private val settingsRepo = SettingsRepository(app)

    private val tools = listOf(
        WebSearchTool(),
        CalculatorTool(),
        FileAccessTool(),
        SystemInfoTool(app)
    )
    private val agentEngine = AgentEngine(engine, tools)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings.DEFAULT)
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _activeModel = MutableStateFlow<ModelProfile?>(null)
    val activeModel: StateFlow<ModelProfile?> = _activeModel.asStateFlow()

    private val _loadState = MutableStateFlow<ModelLoadState>(ModelLoadState.None)
    val loadState: StateFlow<ModelLoadState> = _loadState.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    init {
        viewModelScope.launch {
            _settings.value = settingsRepo.settingsFlow.first()
        }
    }

    // --- Ustawienia ---------------------------------------------------------

    fun updateSettings(newSettings: AppSettings) {
        _settings.value = newSettings
        viewModelScope.launch { settingsRepo.saveSettings(newSettings) }
    }

    fun resetSettings() = updateSettings(AppSettings.DEFAULT)

    // --- Ładowanie modelu ---------------------------------------------------

    /**
     * Ładuje model. Dla profilu 'custom' należy podać [customPath].
     */
    fun loadModel(profile: ModelProfile, customPath: String? = null) {
        viewModelScope.launch {
            val path: String? = if (profile.isCustom) {
                customPath
            } else {
                ModelStorage.resolveModelFile(profile)?.absolutePath
            }

            if (path.isNullOrBlank()) {
                _loadState.value = ModelLoadState.Error(
                    "Nie znaleziono pliku modelu. Pobierz plik GGUF i umieść go w:\n" +
                        ModelStorage.modelsDir().absolutePath
                )
                return@launch
            }
            if (!File(path).exists()) {
                _loadState.value = ModelLoadState.Error("Plik nie istnieje: $path")
                return@launch
            }

            _loadState.value = ModelLoadState.Loading("Ładowanie modelu ${profile.name}...")
            settingsRepo.saveSelectedModel(profile.id)
            if (profile.isCustom) settingsRepo.saveCustomModelPath(path)

            val s = _settings.value
            val ok = withContext(Dispatchers.Default) {
                try {
                    engine.nativeFree()
                    engine.nativeInit(path, s.threads, s.contextLength)
                } catch (e: Throwable) {
                    false
                }
            }

            if (ok) {
                _activeModel.value = profile
                _loadState.value = ModelLoadState.Loaded(profile)
                addSystemMessage("Model \"${profile.name}\" został załadowany. Możesz rozpocząć rozmowę.")
            } else {
                _loadState.value = ModelLoadState.Error(
                    "Nie udało się załadować modelu. Możliwe przyczyny: uszkodzony plik GGUF, " +
                        "za mało pamięci RAM lub nieobsługiwany format."
                )
            }
        }
    }

    fun clearLoadError() {
        if (_loadState.value is ModelLoadState.Error) _loadState.value = ModelLoadState.None
    }

    // --- Czat ---------------------------------------------------------------

    fun clearChat() {
        _messages.value = emptyList()
    }

    private fun addSystemMessage(text: String) {
        _messages.value = _messages.value + ChatMessage(role = Role.SYSTEM, content = text)
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _isGenerating.value) return
        if (!engine.nativeIsLoaded()) {
            addSystemMessage("Najpierw załaduj model (ekran wyboru modelu).")
            return
        }

        val userMsg = ChatMessage(role = Role.USER, content = trimmed)
        val history = _messages.value
        _messages.value = history + userMsg

        val assistantMsg = ChatMessage(role = Role.ASSISTANT, content = "", streaming = true)
        _messages.value = _messages.value + assistantMsg
        _isGenerating.value = true

        val s = _settings.value

        viewModelScope.launch {
            try {
                if (s.agentMode) {
                    runAgent(trimmed, history, assistantMsg.id, s)
                } else {
                    runPlainChat(trimmed, history, assistantMsg.id, s)
                }
            } catch (e: Exception) {
                appendToMessage(assistantMsg.id, "\n[Błąd: ${e.message}]")
            } finally {
                finishStreaming(assistantMsg.id)
                _isGenerating.value = false
            }
        }
    }

    private suspend fun runPlainChat(
        userMessage: String,
        history: List<ChatMessage>,
        assistantId: String,
        s: AppSettings
    ) {
        val turns = PromptBuilder.historyToTurns(history).toMutableList()
        turns.add(PromptBuilder.Turn("user", userMessage))
        val systemPrompt = "Jesteś pomocnym asystentem AI działającym lokalnie na smartfonie. " +
            "Odpowiadaj rzeczowo i w języku użytkownika (domyślnie po polsku)."
        val prompt = PromptBuilder.build(systemPrompt, turns)

        val deferred = CompletableDeferred<Unit>()
        withContext(Dispatchers.Default) {
            engine.nativeGenerateStream(
                prompt, s.maxTokens, s.temperature, s.topP, s.topK, s.repeatPenalty,
                object : LlamaEngine.TokenCallback {
                    override fun onToken(token: String) { appendToMessage(assistantId, token) }
                    override fun onComplete() { deferred.complete(Unit) }
                    override fun onError(error: String) {
                        appendToMessage(assistantId, "\n[Błąd: $error]")
                        deferred.complete(Unit)
                    }
                }
            )
        }
        deferred.await()
    }

    private suspend fun runAgent(
        userMessage: String,
        history: List<ChatMessage>,
        assistantId: String,
        s: AppSettings
    ) {
        agentEngine.run(
            userMessage = userMessage,
            history = history,
            settings = s,
            onTrace = { trace ->
                _messages.value = _messages.value + ChatMessage(
                    role = Role.TOOL, content = trace, isToolTrace = true
                )
            },
            onToken = { token -> appendToMessage(assistantId, token) }
        )
    }

    private fun appendToMessage(id: String, token: String) {
        _messages.value = _messages.value.map { m ->
            if (m.id == id) m.copy(content = m.content + token) else m
        }
    }

    private fun finishStreaming(id: String) {
        _messages.value = _messages.value.map { m ->
            if (m.id == id) m.copy(streaming = false) else m
        }
    }

    override fun onCleared() {
        super.onCleared()
        try { engine.nativeFree() } catch (_: Throwable) {}
    }

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return ChatViewModel(app) as T
                }
            }
    }
}
