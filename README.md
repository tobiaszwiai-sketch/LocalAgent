# LlamaAgent — lokalne modele LLM na Androidzie

Aplikacja na Androida (Kotlin + Jetpack Compose) do uruchamiania modeli językowych
**lokalnie na telefonie** za pomocą [llama.cpp](https://github.com/ggerganov/llama.cpp)
przez JNI (Android NDK). Zawiera prosty czat, konfigurowalne parametry generacji
oraz **tryb agentowy** ze zdolnością korzystania z narzędzi (wyszukiwarka, kalkulator,
dostęp do plików, informacje o urządzeniu).

Zoptymalizowana pod nowoczesne telefony z 12 GB RAM (np. Samsung Galaxy S26 Ultra),
ale działa na dowolnym urządzeniu z Androidem 9+ (API 28) i procesorem ARM64.

---

## Spis treści
1. [Wymagania](#wymagania)
2. [Jak otworzyć i zbudować projekt](#jak-otworzyć-i-zbudować-projekt)
3. [Pobieranie modeli GGUF](#pobieranie-modeli-gguf)
4. [Korzystanie z aplikacji](#korzystanie-z-aplikacji)
5. [Tryb agentowy i narzędzia](#tryb-agentowy-i-narzędzia)
6. [Ustawienia](#ustawienia)
7. [Architektura](#architektura)
8. [FAQ](#faq)

---

## Wymagania

- **Android Studio** Hedgehog (2023.1) lub nowszy (zalecany Iguana/Koala).
- **Android NDK r26+** oraz **CMake 3.22.1** (instalowane przez SDK Manager).
- Telefon lub emulator z **Androidem 9+ (API 28)**, architektura **arm64-v8a**.
- Połączenie z internetem przy **pierwszym** buildzie — CMake pobiera źródła
  llama.cpp (tag `b3456`) przez `FetchContent`.
- Modele GGUF pobierane oddzielnie (nie są zawarte w APK — są za duże).

## Jak otworzyć i zbudować projekt

1. **Zainstaluj NDK i CMake** w Android Studio:
   `Settings → Languages & Frameworks → Android SDK → SDK Tools` →
   zaznacz **NDK (Side by side)** oraz **CMake** → Apply.

2. **Otwórz projekt**: `File → Open` → wskaż katalog `LlamaAgentAndroid`.

3. Poczekaj na synchronizację Gradle. Przy pierwszym buildzie llama.cpp zostanie
   pobrane i skompilowane (może potrwać kilka–kilkanaście minut).

4. Podłącz telefon (z włączonym **debugowaniem USB**) lub uruchom emulator ARM64
   i kliknij **Run ▶**.

> **Uwaga:** projekt buduje bibliotekę natywną tylko dla `arm64-v8a`
> (zdefiniowane w `app/build.gradle.kts` → `abiFilters`). Emulatory x86 nie są
> obsługiwane — użyj fizycznego telefonu ARM64 lub emulatora arm64.

## Pobieranie modeli GGUF

Aplikacja NIE zawiera modeli. Pobierz wybrany plik `.gguf` i umieść go w katalogu:

```
/storage/emulated/0/Download/LlamaAgent/
```

W aplikacji ekran **Menedżer modeli** pokazuje dokładne nazwy plików, docelowe
ścieżki oraz przyciski kopiowania linków do pobrania.

### Wbudowane profile modeli

| Model | Kwantyzacja | Rozmiar | Zalecane RAM |
|-------|-------------|---------|--------------|
| Qwen 3 1.7B | Q4_K_M | ~1.1 GB | 4 GB |
| Qwen 3 4B | Q4_K_M | ~2.6 GB | 6 GB |
| Qwen 3 8B | Q4_K_M | ~5.0 GB | 8 GB (najlepszy dla 12 GB RAM) |
| Qwen 2.5 7B Instruct | Q4_K_M | ~4.7 GB | 8 GB |
| Qwen 3 32B ⚠️ | Q2_K | ~12.3 GB | 16 GB+ (eksperymentalny) |
| Własny model | dowolna | — | zależnie od pliku |

Modele pochodzą z oficjalnych repozytoriów **Qwen** na Hugging Face. Linki do
pobrania znajdują się w ekranie „Menedżer modeli" oraz w `data/ModelProfile.kt`.

**Własny model:** wybierz profil „Własny model (GGUF)" i wskaż plik `.gguf`
z pamięci telefonu — zostanie skopiowany do katalogu modeli i załadowany.

## Korzystanie z aplikacji

1. Uruchom aplikację i przyznaj uprawnienia dostępu do plików.
2. Dotknij ikony **pamięci** (prawy górny róg) → wybierz i **Załaduj** model.
3. Po komunikacie o załadowaniu wpisz wiadomość i wyślij.
4. Odpowiedź jest generowana **strumieniowo** (token po tokenie) na CPU telefonu.

## Tryb agentowy i narzędzia

Włącz przełącznik **Agent** (w pasku wpisywania lub w Ustawieniach). W tym trybie
model może wywoływać narzędzia, aby zdobyć dane, a następnie sformułować odpowiedź.

Dostępne narzędzia:

- **web_search(query)** — wyszukiwanie w sieci (DuckDuckGo Instant Answer API).
- **calculator(expression)** — obliczenia matematyczne (własny parser wyrażeń).
- **read_file(path)** — odczyt pliku tekstowego z pamięci (np. katalog Download).
- **system_info()** — RAM, poziom baterii, wolne miejsce, liczba rdzeni CPU.

Model komunikuje wywołanie narzędzia w formacie XML:

```
<tool_call>
<tool_name>calculator</tool_name>
<parameters>{"expression": "2*(3+4)^2"}</parameters>
</tool_call>
```

Wynik wraca do modelu jako `<tool_result>...</tool_result>`, a pętla powtarza się
maksymalnie przez ustawioną liczbę iteracji (domyślnie 5). Kroki agenta są
widoczne w czacie jako drobne wpisy techniczne.

## Ustawienia

- **Temperatura** (0.0–2.0) — losowość generacji.
- **Długość kontekstu** (512–8192) — wymaga ponownego załadowania modelu.
- **Top-P** (0.0–1.0), **Top-K** (1–100), **Kara za powtórzenia** (1.0–1.5).
- **Maks. tokenów** odpowiedzi.
- **Wątki CPU** (1–8) — wymaga ponownego załadowania modelu.
- **Tryb agentowy** oraz **maks. iteracji agenta** (1–10).
- **Resetuj do domyślnych**.

Ustawienia są zapisywane trwale (DataStore).

## Architektura

```
app/src/main/
├── cpp/                     # Warstwa natywna
│   ├── CMakeLists.txt       # Build NDK + FetchContent llama.cpp
│   └── llama_jni.cpp        # Most JNI (ładowanie modelu, sampling, streaming)
├── java/com/llamaagent/
│   ├── MainActivity.kt      # Nawigacja (Compose Navigation), uprawnienia
│   ├── LlamaEngine.kt       # Wrapper Kotlin na funkcje natywne
│   ├── data/                # ModelProfile, ChatMessage, AppSettings, repozytoria
│   ├── agent/               # AgentEngine, PromptBuilder (ChatML)
│   │   └── tools/           # WebSearch, Calculator, FileAccess, SystemInfo
│   ├── viewmodel/           # ChatViewModel (StateFlow + coroutines)
│   └── ui/                  # Ekrany Compose (Chat, ModelSelect, Settings, Manager)
└── res/                     # Zasoby (motyw ciemny Material3, ikony, teksty)
```

- **Generacja** działa w coroutine (Dispatchers.Default), UI aktualizowane
  strumieniowo przez callback JNI (`TokenCallback`).
- **Sampling** (temperatura, top-k, top-p, repeat penalty) zaimplementowany
  ręcznie w C++, aby uniezależnić kod od zmian w API samplera llama.cpp.
- **Format promptu**: ChatML (`<|im_start|>` / `<|im_end|>`), zgodny z Qwen.

## FAQ

**Ile RAM potrzebuję?**
Model w pamięci zajmuje mniej więcej tyle, ile rozmiar pliku GGUF, plus bufor
kontekstu. Dla 12 GB RAM najlepszy jest Qwen 3 8B (Q4_K_M). Modele 7–8B działają
komfortowo; 32B (Q2_K) jest eksperymentalny i wymaga 16 GB+.

**Dlaczego pierwszy build trwa długo?**
CMake pobiera i kompiluje llama.cpp ze źródeł (`FetchContent`). Kolejne buildy są
znacznie szybsze (wynik jest cache'owany).

**Czy działa bez internetu?**
Tak — sama generacja jest w pełni lokalna. Internet jest potrzebny tylko przy
pierwszym buildzie (pobranie llama.cpp) oraz dla narzędzia `web_search`.

**Jakie formaty modeli są obsługiwane?**
Pliki **GGUF** kompatybilne z llama.cpp (tag `b3456`). Zalecane kwantyzacje:
Q4_K_M (dobry balans) lub Q2_K (mniejsze zużycie RAM, niższa jakość).

**Jak zmienić wersję llama.cpp?**
Edytuj `GIT_TAG` w `app/src/main/cpp/CMakeLists.txt`. Uwaga: nowsze wersje mogą
mieć zmienione API — może być konieczna aktualizacja `llama_jni.cpp`.

**Obsługiwane architektury?**
Tylko `arm64-v8a` (64-bitowe ARM). Aby dodać inne, rozszerz `abiFilters`
w `app/build.gradle.kts`.
