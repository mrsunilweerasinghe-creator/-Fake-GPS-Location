package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.service.MockLocationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class LocationViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = LocationRepository(db.locationDao)

    // Current focused map marker location (Defaults to Colombo, Sri Lanka!)
    val activeLatitude = mutableStateOf(6.9271)
    val activeLongitude = mutableStateOf(79.8612)
    val activeLocationName = mutableStateOf("Colombo, Sri Lanka")

    // Map Zoom Level (virtual)
    val mapZoom = mutableStateOf(14)

    // Search query states
    val searchQuery = mutableStateOf("")
    val isSearching = mutableStateOf(false)
    val searchResults = mutableStateOf<List<LocationSearchItem>>(emptyList())

    // Route Drafting Builder lists
    val draftRoutePoints = mutableStateListOf<Pair<Double, Double>>()
    val draftRouteName = mutableStateOf("My Custom Route")
    val draftRouteSpeed = mutableStateOf(25.0) // km/h

    // Room Database states
    val favoriteLocations: StateFlow<List<FavoriteLocation>> = repository.allFavorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchHistory: StateFlow<List<SearchHistory>> = repository.searchHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedRoutes: StateFlow<List<SimulatedRoute>> = repository.allRoutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Mock location stats (bound directly to Foreground Service's live companion StateFlows!)
    val isMockActive: StateFlow<Boolean> = MockLocationService.isServiceRunning
    val liveLatitude: StateFlow<Double> = MockLocationService.currentMockLatitude
    val liveLongitude: StateFlow<Double> = MockLocationService.currentMockLongitude
    val liveMode: StateFlow<String> = MockLocationService.currentMockMode
    val liveStateLabel: StateFlow<String> = MockLocationService.currentMockStateLabel
    val liveRouteIndex: StateFlow<Int> = MockLocationService.currentRouteIndex
    val liveRouteTotal: StateFlow<Int> = MockLocationService.routePointsCount
    val liveError: StateFlow<String?> = MockLocationService.errorFlow

    init {
        // Clear old errors on start
        MockLocationService.errorFlow.value = null
    }

    fun selectLocation(latitude: Double, longitude: Double, name: String) {
        activeLatitude.value = latitude
        activeLongitude.value = longitude
        activeLocationName.value = name
        // Adjust zoom level dynamically for better visual focus
        mapZoom.value = 15
    }

    fun performSearch() {
        val query = searchQuery.value
        if (query.isBlank()) return

        isSearching.value = true
        viewModelScope.launch {
            try {
                // Perform DB & Network actions safely on Dispatchers.IO
                val results = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    repository.insertSearch(query)
                    GeocodingHelper.searchLocations(getApplication(), query)
                }

                // Update Compose State cleanly on the Main thread
                searchResults.value = results
                
                if (results.isNotEmpty()) {
                    val first = results.first()
                    selectLocation(first.latitude, first.longitude, first.name)
                }
            } catch (e: Exception) {
                Log.e("LocationViewModel", "Search error", e)
            } finally {
                isSearching.value = false
            }
        }
    }

    fun clearSearch() {
        searchQuery.value = ""
        searchResults.value = emptyList()
    }

    // Database Actions
    fun toggleFavoriteCurrent() {
        val lat = activeLatitude.value
        val lng = activeLongitude.value
        val name = activeLocationName.value

        viewModelScope.launch {
            val exists = favoriteLocations.value.firstOrNull { 
                kotlin.math.abs(it.latitude - lat) < 0.0001 && kotlin.math.abs(it.longitude - lng) < 0.0001 
            }
            if (exists != null) {
                repository.deleteFavorite(exists)
            } else {
                repository.insertFavorite(FavoriteLocation(name = name, latitude = lat, longitude = lng))
            }
        }
    }

    fun deleteFavorite(fav: FavoriteLocation) {
        viewModelScope.launch {
            repository.deleteFavorite(fav)
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            repository.clearSearchHistory()
        }
    }

    // Route drafting
    fun addPointToDraftRoute() {
        draftRoutePoints.add(Pair(activeLatitude.value, activeLongitude.value))
    }

    fun removePointFromDraft(index: Int) {
        if (index in draftRoutePoints.indices) {
            draftRoutePoints.removeAt(index)
        }
    }

    fun clearDraftRoute() {
        draftRoutePoints.clear()
    }

    fun saveAndSimulateDraftRoute(context: Context) {
        if (draftRoutePoints.size < 2) return

        val routePointsStr = draftRoutePoints.joinToString(";") { "${it.first},${it.second}" }
        val name = draftRouteName.value.ifBlank { "Custom Route Run" }
        val speed = draftRouteSpeed.value

        viewModelScope.launch {
            val dbRoute = SimulatedRoute(
                name = name,
                pointsJson = routePointsStr,
                speedKmh = speed
            )
            repository.insertRoute(dbRoute)
            
            // Clear builder & Start Service
            draftRoutePoints.clear()
            startRouteMockingService(context, routePointsStr, speed)
        }
    }

    fun playSavedRoute(context: Context, route: SimulatedRoute) {
        startRouteMockingService(context, route.pointsJson, route.speedKmh)
    }

    fun deleteSavedRoute(route: SimulatedRoute) {
        viewModelScope.launch {
            repository.deleteRoute(route)
        }
    }

    // Service Management Intents
    fun startStaticMocking(context: Context) {
        val intent = Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_START
            putExtra(MockLocationService.EXTRA_LATITUDE, activeLatitude.value)
            putExtra(MockLocationService.EXTRA_LONGITUDE, activeLongitude.value)
            putExtra(MockLocationService.EXTRA_MODE, "STATIC")
        }
        context.startService(intent)
    }

    private fun startRouteMockingService(context: Context, routePointsStr: String, speedKmh: Double) {
        val intent = Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_START
            putExtra(MockLocationService.EXTRA_MODE, "ROUTE")
            putExtra(MockLocationService.EXTRA_ROUTE_POINTS, routePointsStr)
            putExtra(MockLocationService.EXTRA_SPEED_KMH, speedKmh)
        }
        context.startService(intent)
    }

    fun updateStaticMockLocation(context: Context, lat: Double, lng: Double) {
        activeLatitude.value = lat
        activeLongitude.value = lng
        
        if (isMockActive.value && liveMode.value == "STATIC") {
            val intent = Intent(context, MockLocationService::class.java).apply {
                action = MockLocationService.ACTION_UPDATE
                putExtra(MockLocationService.EXTRA_LATITUDE, lat)
                putExtra(MockLocationService.EXTRA_LONGITUDE, lng)
                putExtra(MockLocationService.EXTRA_MODE, "STATIC")
            }
            context.startService(intent)
        }
    }

    fun stopMocking(context: Context) {
        val intent = Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun clearError() {
        MockLocationService.errorFlow.value = null
    }
}
