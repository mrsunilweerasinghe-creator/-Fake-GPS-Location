package com.example.data

import kotlinx.coroutines.flow.Flow

class LocationRepository(private val locationDao: LocationDao) {
    val allFavorites: Flow<List<FavoriteLocation>> = locationDao.getAllFavorites()
    val searchHistory: Flow<List<SearchHistory>> = locationDao.getSearchHistory()
    val allRoutes: Flow<List<SimulatedRoute>> = locationDao.getAllRoutes()

    suspend fun insertFavorite(favorite: FavoriteLocation) {
        locationDao.insertFavorite(favorite)
    }

    suspend fun deleteFavorite(favorite: FavoriteLocation) {
        locationDao.deleteFavorite(favorite)
    }

    suspend fun deleteFavoriteById(id: Int) {
        locationDao.deleteFavoriteById(id)
    }

    suspend fun insertSearch(query: String) {
        if (query.isNotBlank()) {
            locationDao.insertSearch(SearchHistory(query = query.trim()))
        }
    }

    suspend fun clearSearchHistory() {
        locationDao.clearSearchHistory()
    }

    suspend fun insertRoute(route: SimulatedRoute) {
        locationDao.insertRoute(route)
    }

    suspend fun deleteRoute(route: SimulatedRoute) {
        locationDao.deleteRoute(route)
    }
}
