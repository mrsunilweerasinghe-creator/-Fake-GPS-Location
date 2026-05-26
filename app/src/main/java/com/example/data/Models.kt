package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_locations")
data class FavoriteLocation(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "search_history")
data class SearchHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val query: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "simulated_routes")
data class SimulatedRoute(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val pointsJson: String, // Storing serialized coordinates (JSON array of pairs)
    val speedKmh: Double = 15.0, // Default 15.0 km/h
    val timestamp: Long = System.currentTimeMillis()
)
