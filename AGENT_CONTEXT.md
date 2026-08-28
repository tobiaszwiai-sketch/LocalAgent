# AGENT_CONTEXT.md
> Ten plik służy jako "wspólna pamięć" dla różnych modeli AI (Claude, Codex, Gemini itp.)
> które mogą współpracować nad tym projektem. Aktualizuj go po każdej znaczącej zmianie.

---

## Stan projektu
- **Wersja:** 1.2.0
- **Ostatnia aktualizacja:** 2025-08 przez Claude (Abacus AI Agent)
- **Status:** ✅ APK skompilowany — poprawka krytycznego błędu ładowania modelu

---

## Czym jest ten projekt
Aplikacja Android (Kotlin + Jetpack Compose) do lokalnego uruchamiania modeli LLM w formacie
GGUF bezpośrednio na telefonie, bez internetu. Zawiera tryb agentowy z narzędziami:
web search, kalkulator, dostęp do plików, info systemowe.

Docelowe urządzenie: **Samsung Galaxy S26 Ultra (12GB RAM, Snapdragon 8 Elite)**

---

## Architektura techniczna

### Warstwa natywna (C++ / JNI)
- **Biblioteka:** `llama.cpp` tag **b10665** (ggml-org/llama.cpp)
- **Podejście:** prebuilt `.so` z GitHub Releases (nie kompilujemy od zera)
- **Pliki .so:** `app/src/main/jniLibs/arm64-v8a/` — libllama.so, libggml.so, libggml-base.so + CPU variants
- **Headery:** `app/src/main/cpp/include/` — pobrane z master branch
- **JNI bridge:** `app/src/main/cpp/llama_jni.cpp`
- **ABI:** tylko `arm64-v8a` (nowoczesne telefony 64-bit)

### Kluczowe API llama.cpp (b10665) — zmiany vs starsze wersje
```
llama_model_load_from_file()  → ładowanie modelu
llama_init_from_model()       → tworzenie kontekstu (było: llama_new_context_with_model)
llama_model_free()            → zwolnienie modelu
llama_free()                  → zwolnienie kontekstu
llama_get_memory(ctx)         → uchwyt pamięci KV cache
llama_memory_clear(mem, false)→ czyszczenie KV cache (było: llama_kv_cache_clear)
llama_model_get_vocab(model)  → uchwyt słownika
llama_vocab_n_tokens(vocab)   → rozmiar słownika
llama_vocab_eos(vocab)        → token EOS
llama_vocab_eot(vocab)        → token EOT
llama_batch_get_one()         → batch dla jednej sekwencji
llama_decode()                → forward pass
llama_get_logits()            → logity po decode
llama_token_to_piece()        → token → string
llama_tokenize()              → string → tokeny
llama_n_ctx()                 → rozmiar kontekstu
llama_backend_init/free()     → init/cleanup backendu
```

### Warstwa Kotlin
```
app/src/main/java/com/llamaagent/
├── LlamaEngine.kt          # Kotlin wrapper JNI + TokenCallback interface
├── MainActivity.kt         # Nawigacja Compose
├── agent/
│   ├── AgentEngine.kt      # Pętla agentowa (ReAct, XML tool-calling)
│   └── tools/
│       ├── WebSearchTool.kt    # DuckDuckGo Instant Answer API
│       ├── CalculatorTool.kt   # Parser wyrażeń matematycznych
│       ├── FileAccessTool.kt   # Dostęp do /storage/emulated/0/
│       └── SystemInfoTool.kt   # RAM, bateria, CPU (ActivityManager)
├── data/
│   ├── ModelProfile.kt     # Profile modeli (6 presetów + Custom)
│   ├── ChatMessage.kt      # Model wiadomości
│   └── AppSettings.kt      # Ustawienia (DataStore)
├── ui/
│   ├── ChatScreen.kt       # Główny ekran czatu
│   ├── MarkdownText.kt     # Prosty renderer Markdown
│   ├── ModelSelectScreen.kt # Wybór modelu
│   ├── ModelManagerScreen.kt# Menedżer pobierania GGUF
│   ├── SettingsScreen.kt   # Ustawienia parametrów
│   └── theme/              # Material3 dark theme
└── viewmodel/
    └── ChatViewModel.kt    # ViewModel (StateFlow, coroutines)
```

---

## Presetowe modele (ModelProfile.kt)
| ID | Nazwa | Rozmiar | RAM min | Uwagi |
|----|-------|---------|---------|-------|
| qwen3_1.7b | Qwen 3 1.7B Q4_K_M | ~1.1 GB | 4 GB | Bardzo szybki |
| qwen3_4b | Qwen 3 4B Q4_K_M | ~2.6 GB | 6 GB | Dobry balans |
| qwen3_8b | Qwen 3 8B Q4_K_M | ~5.0 GB | 8 GB | **Zalecany dla S26 12GB** |
| qwen25_7b | Qwen 2.5 7B Q4_K_M | ~4.7 GB | 8 GB | Alternatywa stabilna |
| qwen3_27b | Qwen 3 32B Q2_K | ~10.5 GB | 14 GB | Eksperymentalny, 16GB+ |
| custom | Własny model | - | - | File picker z pamięci |

Pliki GGUF umieszczamy w: `/storage/emulated/0/Download/LlamaAgent/`

---

## Tryb agentowy
Format tool-calling w systemowym prompcie (XML):
```xml
<tool_call>
<tool_name>web_search</tool_name>
<parameters>{"query": "..."}</parameters>
</tool_call>
```
Dostępne narzędzia: `web_search`, `calculator`, `read_file`, `system_info`
Pętla: max 5 iteracji, model sam decyduje kiedy zatrzymać.

---

## Parametry konfiguracyjne (UI)
- Temperatura: 0.0–2.0 (domyślnie 0.7)
- Context length: 512/1024/2048/4096/8192 (domyślnie 4096)
- Top-P: 0.0–1.0 (domyślnie 0.9)
- Top-K: 1–100 (domyślnie 40)
- Repeat penalty: 1.0–1.5 (domyślnie 1.1)
- Threads: 1–8 (domyślnie 4)
- Tryb agentowy: ON/OFF
- Max iteracji agenta: 1–10

---

## Jak zaktualizować wersję llama.cpp
1. Sprawdź nowy tag: `https://github.com/ggml-org/llama.cpp/releases`
2. Pobierz `llama-XTAG-bin-android-arm64.tar.gz`
3. Zastąp pliki w `app/src/main/jniLibs/arm64-v8a/`
4. Pobierz nowe headery do `app/src/main/cpp/include/`
5. Sprawdź czy API się zmieniło (szczególnie funkcje w llama_jni.cpp)
6. Zaktualizuj ten plik (wersja, zmiany API)
7. Skompiluj: `./gradlew assembleDebug`

---

## Jak skompilować APK
```bash
# Wymagania: Android SDK, NDK 26.1, JDK 17 z jlink
export ANDROID_HOME=/path/to/android-sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
export JAVA_HOME=/path/to/jdk17  # musi zawierać bin/jlink!
export PATH=$JAVA_HOME/bin:$PATH

cd LlamaAgentAndroid
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

**Uwaga:** Na maszynie Abacus AI Agent kompilacja zajmuje ~45s dzięki prebuilt .so.
Na świeżej maszynie z FetchContent (stare podejście) trwałoby 30-60 minut.

---

## Znane ograniczenia / TODO
- [ ] APK jest debug (nie release) — brak podpisu produkcyjnego
- [ ] Brak pobierania modeli w aplikacji (tylko link do HuggingFace)
- [ ] Streaming tokenów przez JNI callback działa, ale UI może mieć drobne opóźnienia
- [ ] Model 27B jest eksperymentalny na 12GB RAM — może crashować
- [ ] Brak obsługi GPU (Vulkan/OpenCL) — tylko CPU
- [ ] MarkdownText.kt to prosty renderer — nie obsługuje tabel, złożonych struktur
- [ ] Brak historii czatu zapisywanej na dysk (tylko w pamięci)

## Propozycje rozszerzeń
- [ ] Dodać pobieranie modeli bezpośrednio w aplikacji (DownloadManager)
- [ ] Integracja z Vulkan backend (llama.cpp ma wsparcie dla mobilnych GPU)
- [ ] Eksport historii czatu do pliku
- [ ] Widget Android z szybkim dostępem do czatu
- [ ] Obsługa multimodalnych modeli (llava)
- [ ] TTS (Text-to-Speech) dla odpowiedzi modelu

---

## Historia zmian
| Data | Model | Zmiana |
|------|-------|--------|
| 2025-08 | Claude (Abacus AI) | Inicjalny projekt, JNI + Compose UI + Agent |
| 2025-08 | Claude (Abacus AI) | Przejście na prebuilt .so (b10665), fix API llama.cpp, kompilacja APK |
| 2025-08 | Claude (Abacus AI) | **v1.2.0** — krytyczna poprawka: backend CPU nie ładował się na Androidzie |

---

## Szczegóły naprawy v1.2.0 (błąd "Nie udało się załadować modelu")

### Przyczyna
`libggml.so` korzysta z `ggml_backend_load_all()` do znalezienia backendów CPU
(pliki `libggml-cpu-android_armv*.so`). Domyślnie szuka ich przez `/proc/self/exe`
lub zmienną `GGML_BACKEND_PATH`. **Na Androidzie żadna z tych metod nie działa:**
`/proc/self/exe` wskazuje na Zygote, nie na katalog `.so` aplikacji.
Backendy nie były ładowane → model nie miał zdefiniowanego backendu obliczeń →
`llama_model_load_from_file()` zwracał NULL.

### Rozwiązanie
1. **Nowa klasa `LlamaAgentApp` (Application)** — wywołuje `LlamaEngine.nativeInitBackends(nativeLibDir)`
   przy starcie procesu, gdzie `nativeLibDir = applicationInfo.nativeLibraryDir`
   (np. `/data/app/com.llamaagent-xxx/lib/arm64`).

2. **Nowa funkcja JNI** `Java_com_llamaagent_LlamaEngine_nativeInitBackends` —
   wywołuje `ggml_backend_load_all_from_path(dir)` z dokładnym katalogiem natywnym.

3. **Jawna kolejność ładowania bibliotek w Kotlin** (companion object init bloku):
   ```
   System.loadLibrary("ggml-base")  // bez jawnego ładowania mogą być race conditions
   System.loadLibrary("ggml")
   System.loadLibrary("llama")
   System.loadLibrary("llamaagent")
   ```

4. **AndroidManifest.xml** — dodano `android:name=".LlamaAgentApp"`.

### Zmodyfikowane pliki
- `app/src/main/cpp/llama_jni.cpp` — nowa funkcja nativeInitBackends + #include ggml-backend.h
- `app/src/main/java/com/llamaagent/LlamaEngine.kt` — kolejność loadLibrary + external fun
- `app/src/main/java/com/llamaagent/LlamaAgentApp.kt` — NOWY plik
- `app/src/main/AndroidManifest.xml` — android:name=".LlamaAgentApp"
