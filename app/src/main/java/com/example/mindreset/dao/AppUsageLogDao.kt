package com.example.mindreset.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.mindreset.models.AppUsageLog
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class AppUsageTotal(
    val packageName: String,
    val totalTime: Long
)

@Dao
interface AppUsageLogDao {
    @Insert
    suspend fun insertLog(log: AppUsageLog)

    @Update
    suspend fun updateLog(log: AppUsageLog)

    @Query("SELECT * FROM app_usage_logs WHERE date = :date ORDER BY openedAt DESC")
    fun getLogsForDate(date: String = LocalDate.now().toString()): Flow<List<AppUsageLog>>

    @Query(
        """
        SELECT packageName, COALESCE(SUM(COALESCE(closedAt, :now) - openedAt), 0) as totalTime
        FROM app_usage_logs
        WHERE date = :date
        GROUP BY packageName
        """
    )
    suspend fun getTotalTimeByPackage(
        date: String = LocalDate.now().toString(),
        now: Long = System.currentTimeMillis()
    ): List<AppUsageTotal>
}
