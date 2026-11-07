package dev.sumanth.goodbuy.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM tracked_groups")
    fun getTrackedGroups(): Flow<List<TrackedGroupEntity>>

    @Query("SELECT * FROM tracked_groups")
    suspend fun getTrackedGroupsList(): List<TrackedGroupEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: TrackedGroupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroups(groups: List<TrackedGroupEntity>)

    @Query("DELETE FROM tracked_groups WHERE id = :groupId")
    suspend fun deleteGroupById(groupId: String)

    @Query("DELETE FROM tracked_groups")
    suspend fun deleteAllGroups()
}
