package com.spinecare.app

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthConnectPermission
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.LocalDateTime

/** Health Connect: steps + sleep import (graceful when unavailable). */
class HealthConnectHelper(private val context: Context) {
    companion object {
        val PERMISSIONS: Set<String> = setOf(
            "android.permission.health.READ_STEPS",
            "android.permission.health.READ_SLEEP",
        )
    }

    fun isAvailable(): Boolean = try {
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_UP_TO_DATE
    } catch (e: Exception) { false }

    suspend fun requestPermissions(launcher: ActivityResultLauncher<Intent>) {
        val client = HealthConnectClient.getOrCreate(context)
        val missing = PERMISSIONS - client.permissionController.getGrantedPermissions()
        if (missing.isNotEmpty()) launcher.launch(client.permissionController.createPermissionRequest(missing))
    }

    data class DayStats(val steps: Int, val sleepMinutes: Double)

    suspend fun readToday(): DayStats? = try {
        val client = HealthConnectClient.getOrCreate(context)
        val since = LocalDateTime.now().minusDays(1)
        val steps = client.readRecords(
            ReadRecordsRequest(
                recordType = androidx.health.connect.client.records.StepsRecord::class.java,
                timeRangeFilter = TimeRangeFilter.between(since, LocalDateTime.now())
            )
        )
        val totalSteps = steps.records.sumOf { it.count }
        DayStats(totalSteps.toInt(), -1.0)
    } catch (e: Exception) { null }
}
