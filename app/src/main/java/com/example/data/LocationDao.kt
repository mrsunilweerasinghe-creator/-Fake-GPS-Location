package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    // Favorites queries
    @Query("SELECT * FROM favorite_locations ORDER BY timestamp DESC")
    fun getAllFavorites(): Flow<List<FavoriteLocation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteLocation)

    @Delete
    suspend fun deleteFavorite(favorite: FavoriteLocation)

    @Query("DELETE FROM favorite_locations WHERE id = :id")
    suspend fun deleteFavoriteById(id: Int)

    // Search History queries
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 30")
    fun getSearchHistory(): Flow<List<SearchHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearch(search: SearchHistory)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()

    // Simulated Routes
    @Query("SELECT * FROM simulated_routes ORDER BY timestamp DESC")
    fun getAllRoutes(): Flow<List<SimulatedRoute>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoute(route: SimulatedRoute)

    @Delete
    suspend fun deleteRoute(route: SimulatedRoute)
}
