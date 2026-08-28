package com.llamaagent.ui

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.llamaagent.data.ModelProfile
import com.llamaagent.data.ModelStorage
import com.llamaagent.data.PRESET_MODELS
import com.llamaagent.viewmodel.ChatViewModel
import com.llamaagent.viewmodel.ModelLoadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onOpenManager: () -> Unit
) {
    val context = LocalContext.current
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val activeModel by viewModel.activeModel.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var copying by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            copying = true
            scope.launch {
                val path = withContext(Dispatchers.IO) { copyUriToModels(context, uri) }
                copying = false
                if (path != null) {
                    val custom = PRESET_MODELS.first { it.isCustom }
                    viewModel.loadModel(custom, path)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wybór modelu") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                },
                actions = {
                    TextButton(onClick = onOpenManager) { Text("Menedżer") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            if (loadState is ModelLoadState.Loading || copying) {
                val msg = if (copying) "Kopiowanie pliku modelu…"
                          else (loadState as ModelLoadState.Loading).message
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(msg, style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }

            (loadState as? ModelLoadState.Error)?.let { err ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer)
                        Text("  ${err.message}", color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f).padding(start = 4.dp))
                        TextButton(onClick = { viewModel.clearLoadError() }) { Text("OK") }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(PRESET_MODELS, key = { it.id }) { profile ->
                    ModelCard(
                        profile = profile,
                        context = context,
                        isActive = activeModel?.id == profile.id,
                        onLoad = { viewModel.loadModel(profile) },
                        onDownload = {
                            if (profile.downloadUrl.isNotBlank()) {
                                context.startActivity(Intent(Intent.ACTION_VIEW, profile.downloadUrl.toUri()))
                            }
                        },
                        onPickCustom = { filePicker.launch(arrayOf("*/*")) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelCard(
    profile: ModelProfile,
    context: Context,
    isActive: Boolean,
    onLoad: () -> Unit,
    onDownload: () -> Unit,
    onPickCustom: () -> Unit
) {
    val downloaded = remember(profile.id) { ModelStorage.isDownloaded(profile) }
    val totalRamMb = remember { deviceTotalRamMb(context) }
    val ramWarning = profile.recommendedForRam > totalRamMb && profile.recommendedForRam > 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer
                             else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(profile.name, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (isActive) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = "Aktywny",
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
            Text(profile.description, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp))

            if (!profile.isCustom) {
                Text(
                    "Rozmiar: ${profile.sizeGB} GB • Kwantyzacja: ${profile.quantization} • " +
                        "Zalecane RAM: ${profile.recommendedForRam} MB",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
                // Pasek zużycia RAM
                val fraction = if (totalRamMb > 0)
                    (profile.recommendedForRam.toFloat() / totalRamMb).coerceIn(0f, 1f) else 0f
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    color = if (ramWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Text(
                    if (ramWarning) "\u26A0\uFE0F Model może przekroczyć RAM urządzenia (${totalRamMb} MB)"
                    else "Twój RAM: ${totalRamMb} MB",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (ramWarning) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Text(
                    if (downloaded) "Status: pobrany \u2713" else "Status: nie pobrany",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (downloaded) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (profile.isCustom) {
                    Button(onClick = onPickCustom, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null,
                            modifier = Modifier.size(18.dp))
                        Text("  Wybierz plik .gguf")
                    }
                } else {
                    Button(
                        onClick = onLoad,
                        enabled = downloaded,
                        modifier = Modifier.weight(1f)
                    ) { Text("Załaduj") }
                    OutlinedButton(onClick = onDownload, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Download, contentDescription = null,
                            modifier = Modifier.size(18.dp))
                        Text("  Pobierz")
                    }
                }
            }
        }
    }
}

private fun deviceTotalRamMb(context: Context): Int {
    return try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        (info.totalMem / (1024 * 1024)).toInt()
    } catch (e: Exception) { 0 }
}

/** Kopiuje wybrany plik (SAF URI) do katalogu modeli i zwraca ścieżkę. */
private fun copyUriToModels(context: Context, uri: Uri): String? {
    return try {
        val name = queryFileName(context, uri) ?: "custom_model.gguf"
        val dest = File(ModelStorage.modelsDir(), name)
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output, 1 shl 20) }
        }
        if (dest.exists() && dest.length() > 0) dest.absolutePath else null
    } catch (e: Exception) {
        null
    }
}

private fun queryFileName(context: Context, uri: Uri): String? {
    var result: String? = null
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && c.moveToFirst()) result = c.getString(idx)
    }
    return result
}
