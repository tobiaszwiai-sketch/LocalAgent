package com.llamaagent.agent.tools

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Informacje o urządzeniu: RAM, bateria, wolne miejsce na dysku.
 */
class SystemInfoTool(private val context: Context) : AgentTool {
    override val name = "system_info"
    override val description = "system_info() - Zwraca informacje o urządzeniu: RAM, bateria, wolne miejsce"

    override suspend fun execute(params: Map<String, Any?>): String = withContext(Dispatchers.Default) {
        try {
            val sb = StringBuilder()

            // RAM
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            val totalRamMb = memInfo.totalMem / (1024 * 1024)
            val availRamMb = memInfo.availMem / (1024 * 1024)
            sb.append("RAM: ${availRamMb} MB wolne z ${totalRamMb} MB\n")
            sb.append("Stan niskiej pamięci: ${if (memInfo.lowMemory) "TAK" else "nie"}\n")

            // Bateria
            val batteryIntent: Intent? = context.registerReceiver(
                null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            if (batteryIntent != null) {
                val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
                val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
                sb.append("Bateria: ${pct}% ${if (charging) "(ładowanie)" else ""}\n")
            }

            // Miejsce na dysku
            val stat = StatFs(Environment.getExternalStorageDirectory().path)
            val freeBytes = stat.availableBytes
            val totalBytes = stat.totalBytes
            val freeGb = freeBytes / (1024.0 * 1024.0 * 1024.0)
            val totalGb = totalBytes / (1024.0 * 1024.0 * 1024.0)
            sb.append(String.format("Pamięć: %.1f GB wolne z %.1f GB\n", freeGb, totalGb))

            // Procesory
            sb.append("Rdzenie CPU: ${Runtime.getRuntime().availableProcessors()}")

            sb.toString().trim()
        } catch (e: Exception) {
            "Błąd pobierania informacji o systemie: ${e.message}"
        }
    }
}
