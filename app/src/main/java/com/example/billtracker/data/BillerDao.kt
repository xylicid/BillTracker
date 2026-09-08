package com.example.billtracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BillerDao {

    // Unpaid bills first (in creation order), paid bills after (in the order they were paid).
    @Query("SELECT * FROM billers ORDER BY isPaid ASC, position ASC, paidAt ASC")
    fun getAll(): Flow<List<Biller>>

    @Query("SELECT * FROM billers ORDER BY position ASC")
    suspend fun getAllOnce(): List<Biller>

    @Insert
    suspend fun insert(biller: Biller): Long

    // Used for "Undo" after a swipe-to-delete. Biller carries its original id, so
    // Room will re-insert it with that same id (autoGenerate only kicks in for id = 0).
    @Insert
    suspend fun restore(biller: Biller)

    @Update
    suspend fun update(biller: Biller)

    // Batch update used after a manual drag-to-reorder to persist the new positions.
    @Update
    suspend fun updateAll(billers: List<Biller>)

    @Delete
    suspend fun delete(biller: Biller)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM billers")
    suspend fun nextPosition(): Int

    @Query("UPDATE billers SET isPaid = 0, paidAt = NULL")
    suspend fun resetAllPaidStatus()
}
