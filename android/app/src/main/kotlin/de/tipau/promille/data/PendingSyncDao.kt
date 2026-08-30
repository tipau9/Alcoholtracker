package de.tipau.promille.data

import androidx.room.*

@Dao
interface PendingSyncDao {
    @Query("SELECT * FROM pending_sync_operation ORDER BY createdAt ASC")
    suspend fun getPending(): List<PendingSyncOperationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(op: PendingSyncOperationEntity)

    @Delete
    suspend fun delete(op: PendingSyncOperationEntity)

    @Query("DELETE FROM pending_sync_operation WHERE operationType = :operationType")
    suspend fun deleteByType(operationType: String)

    @Update
    suspend fun update(op: PendingSyncOperationEntity)

    @Query("SELECT COUNT(*) FROM pending_sync_operation")
    suspend fun count(): Int
}
