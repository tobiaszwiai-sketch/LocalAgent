package com.llamaagent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.llamaagent.data.AppSettings
import com.llamaagent.viewmodel.ChatViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    fun update(block: (AppSettings) -> AppSettings) = viewModel.updateSettings(block(settings))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ustawienia") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SectionTitle("Generacja")

            SliderSetting(
                label = "Temperatura",
                value = settings.temperature,
                range = 0f..2f,
                display = { String.format("%.2f", it) }
            ) { update { s -> s.copy(temperature = it) } }

            DropdownSetting(
                label = "Długość kontekstu (n_ctx)",
                current = settings.contextLength,
                options = AppSettings.CONTEXT_OPTIONS
            ) { update { s -> s.copy(contextLength = it) } }
            Text(
                "Uwaga: zmiana długości kontekstu wymaga ponownego załadowania modelu.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SliderSetting(
                label = "Top-P",
                value = settings.topP,
                range = 0f..1f,
                display = { String.format("%.2f", it) }
            ) { update { s -> s.copy(topP = it) } }

            SliderSetting(
                label = "Top-K",
                value = settings.topK.toFloat(),
                range = 1f..100f,
                steps = 98,
                display = { it.roundToInt().toString() }
            ) { update { s -> s.copy(topK = it.roundToInt()) } }

            SliderSetting(
                label = "Kara za powtórzenia (repeat penalty)",
                value = settings.repeatPenalty,
                range = 1f..1.5f,
                display = { String.format("%.2f", it) }
            ) { update { s -> s.copy(repeatPenalty = it) } }

            SliderSetting(
                label = "Maks. liczba tokenów odpowiedzi",
                value = settings.maxTokens.toFloat(),
                range = 128f..4096f,
                display = { it.roundToInt().toString() }
            ) { update { s -> s.copy(maxTokens = it.roundToInt()) } }

            SectionTitle("Wydajność")

            SliderSetting(
                label = "Wątki CPU",
                value = settings.threads.toFloat(),
                range = 1f..8f,
                steps = 6,
                display = { it.roundToInt().toString() }
            ) { update { s -> s.copy(threads = it.roundToInt()) } }
            Text(
                "Uwaga: zmiana liczby wątków wymaga ponownego załadowania modelu.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SectionTitle("Agent")

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Tryb agentowy", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Pozwala modelowi używać narzędzi (wyszukiwanie, kalkulator, pliki, info o urządzeniu).",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.agentMode,
                    onCheckedChange = { update { s -> s.copy(agentMode = it) } }
                )
            }

            SliderSetting(
                label = "Maks. iteracji agenta",
                value = settings.maxAgentIterations.toFloat(),
                range = 1f..10f,
                steps = 8,
                display = { it.roundToInt().toString() }
            ) { update { s -> s.copy(maxAgentIterations = it.roundToInt()) } }

            OutlinedButton(
                onClick = { viewModel.resetSettings() },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                Text("Resetuj do domyślnych")
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SliderSetting(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    display: (Float) -> String,
    onChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(display(value), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSetting(
    label: String,
    current: Int,
    options: List<Int>,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row {
            Button(onClick = { expanded = true }) {
                Text(current.toString())
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt.toString()) },
                        onClick = { onSelect(opt); expanded = false }
                    )
                }
            }
        }
    }
}
