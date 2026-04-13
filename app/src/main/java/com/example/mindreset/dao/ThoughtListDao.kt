package com.example.mindreset.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.mindreset.models.ThoughtList
import kotlinx.coroutines.flow.Flow

@Dao
interface ThoughtListDao {
    @Query("SELECT * FROM thought_lists ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ThoughtList>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ThoughtList): Long

    @Delete
    suspend fun delete(item: ThoughtList)

    @Update
    suspend fun update(item: ThoughtList)
}
