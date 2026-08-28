package de.tipau.promille.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CrewMemberDao {
    @Query("SELECT * FROM crew_member ORDER BY name ASC")
    fun getAll(): Flow<List<CrewMemberEntity>>

    @Query("SELECT * FROM crew_member ORDER BY name ASC")
    suspend fun getAllOnce(): List<CrewMemberEntity>

    @Query("SELECT COUNT(*) FROM crew_member WHERE isSelf = 0")
    suspend fun countNonSelf(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(member: CrewMemberEntity)

    @Update
    suspend fun update(member: CrewMemberEntity)

    /**
     * Only the columns the server owns. A whole-row REPLACE would carry the
     * poll's stale copy of the user-owned flags back over an edit made in the
     * friend sheet while the request was in flight.
     */
    @Query(
        """UPDATE crew_member SET currentBAC = :currentBac,
           lastDrinkTimestamp = :lastDrinkTimestamp,
           isProbationaryDriver = :isProbationaryDriver,
           sosActive = :sosActive,
           highAlertFired = :highAlertFired
           WHERE id = :id"""
    )
    suspend fun applyServerUpdate(
        id: String,
        currentBac: Double,
        lastDrinkTimestamp: Long?,
        isProbationaryDriver: Boolean,
        sosActive: Boolean,
        highAlertFired: Boolean
    )

    @Delete
    suspend fun delete(member: CrewMemberEntity)

    @Query("DELETE FROM crew_member")
    suspend fun deleteAll()
}
