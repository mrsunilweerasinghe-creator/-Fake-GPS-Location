package com.example.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.*

class MockLocationService : Service() {

    private var serviceJob = Job()
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("MockLocationService", "Uncaught exception in serviceScope", throwable)
        errorFlow.value = "Simulation Error: ${throwable.message}"
    }
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob + exceptionHandler)
    private var mockJob: Job? = null

    companion object {
        const val CHANNEL_ID = "mock_location_service_channel"
        const val NOTIFICATION_ID = 8801
        
        const val ACTION_START = "com.example.service.START"
        const val ACTION_UPDATE = "com.example.service.UPDATE"
        const val ACTION_STOP = "com.example.service.STOP"

        const val EXTRA_LATITUDE = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"
        const val EXTRA_MODE = "extra_mode" // "STATIC" or "ROUTE"
        const val EXTRA_ROUTE_POINTS = "extra_route_points" // format: "lat,lng;lat,lng;..."
        const val EXTRA_SPEED_KMH = "extra_speed_kmh"

        // Exposed global state flows for immediate Composable binding
        val isServiceRunning = MutableStateFlow(false)
        val currentMockLatitude = MutableStateFlow(37.7749) // Default SF
        val currentMockLongitude = MutableStateFlow(-122.4194)
        val currentMockMode = MutableStateFlow("STATIC")
        val currentMockStateLabel = MutableStateFlow("Idle")
        val currentRouteIndex = MutableStateFlow(0)
        val routePointsCount = MutableStateFlow(0)
        val errorFlow = MutableStateFlow<String?>(null)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_STOP

        when (action) {
            ACTION_START -> {
                val lat = intent?.getDoubleExtra(EXTRA_LATITUDE, 37.7749) ?: 37.7749
                val lng = intent?.getDoubleExtra(EXTRA_LONGITUDE, -122.4194) ?: -122.4194
                val mode = intent?.getStringExtra(EXTRA_MODE) ?: "STATIC"
                val routePoints = intent?.getStringExtra(EXTRA_ROUTE_POINTS) ?: ""
                val speed = intent?.getDoubleExtra(EXTRA_SPEED_KMH, 15.0) ?: 15.0

                startRunningForeground(lat, lng, mode, routePoints, speed)
            }
            ACTION_UPDATE -> {
                val lat = intent?.getDoubleExtra(EXTRA_LATITUDE, 37.7749) ?: 37.7749
                val lng = intent?.getDoubleExtra(EXTRA_LONGITUDE, -122.4194) ?: -122.4194
                val mode = intent?.getStringExtra(EXTRA_MODE) ?: "STATIC"
                val routePoints = intent?.getStringExtra(EXTRA_ROUTE_POINTS) ?: ""
                val speed = intent?.getDoubleExtra(EXTRA_SPEED_KMH, 15.0) ?: 15.0

                updateMocking(lat, lng, mode, routePoints, speed)
            }
            ACTION_STOP -> {
                stopServiceGracefully()
            }
        }

        return START_NOT_STICKY
    }

    private fun startRunningForeground(
        lat: Double,
        lng: Double,
        mode: String,
        routePoints: String,
        speed: Double
    ) {
        // Build Notification
        val notification = createNotification(lat, lng, mode, "Starting simulation...")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            
            isServiceRunning.value = true
            updateMocking(lat, lng, mode, routePoints, speed)
        } catch (e: Exception) {
            Log.e("MockLocationService", "Failed to start foreground service", e)
            errorFlow.value = "Failed to start Foreground Service: ${e.message}"
            stopServiceGracefully()
        }
    }

    private fun updateMocking(
        lat: Double,
        lng: Double,
        mode: String,
        routePoints: String,
        speed: Double
    ) {
        mockJob?.cancel()

        currentMockMode.value = mode
        if (mode == "STATIC") {
            currentMockLatitude.value = lat
            currentMockLongitude.value = lng
            currentMockStateLabel.value = "Static Location Set"
            routePointsCount.value = 0
            currentRouteIndex.value = 0
        }

        mockJob = serviceScope.launch {
            val isSuccess = setupMockProviders()
            if (!isSuccess) {
                withContext(Dispatchers.Main) {
                    errorFlow.value = "Mock Location permission not enabled. Please enable this app in Developer Options."
                }
                stopServiceGracefully()
                return@launch
            }

            if (mode == "STATIC") {
                startStaticMockingLoop(lat, lng)
            } else {
                startRouteMockingLoop(routePoints, speed)
            }
        }
    }

    private suspend fun CoroutineScope.startStaticMockingLoop(lat: Double, lng: Double) {
        var driftCounter = 0
        while (isActive) {
            // Add tiny human drift to make location simulator realistic and bypass speed static check telemetry
            val driftLat = (sin(driftCounter.toDouble() * 0.1) * 0.000003)
            val driftLng = (cos(driftCounter.toDouble() * 0.1) * 0.000003)
            driftCounter++

            val activeLat = lat + driftLat
            val activeLng = lng + driftLng

            currentMockLatitude.value = activeLat
            currentMockLongitude.value = activeLng
            currentMockStateLabel.value = "Mocking: %.5f, %.5f".format(activeLat, activeLng)

            pushMockLocationToSystem(activeLat, activeLng)
            updateNotificationBuilder(activeLat, activeLng, "Mock active at selected spot")

            delay(1000)
        }
    }

    private suspend fun CoroutineScope.startRouteMockingLoop(routePointsStr: String, speedKmh: Double) {
        // Parse "lat,lng;lat,lng"
        val points = routePointsStr.split(";").mapNotNull {
            val parts = it.split(",")
            if (parts.size == 2) {
                val latitude = parts[0].toDoubleOrNull()
                val longitude = parts[1].toDoubleOrNull()
                if (latitude != null && longitude != null) {
                    Pair(latitude, longitude)
                } else null
            } else null
        }

        if (points.isEmpty()) {
            currentMockStateLabel.value = "Route calculation failed: No points"
            stopServiceGracefully()
            return
        }

        routePointsCount.value = points.size
        var currentIdx = 0
        currentRouteIndex.value = currentIdx

        // Speed in meters per second
        val speedMps = (speedKmh * 1000.0) / 3600.0
        val tickIntervalSec = 1.0

        var currentLat = points[0].first
        var currentLng = points[0].second

        while (isActive && currentIdx < points.size - 1) {
            val targetLat = points[currentIdx + 1].first
            val targetLng = points[currentIdx + 1].second

            // Compute distance and bearing
            val distAndBearing = computeDistanceAndBearing(currentLat, currentLng, targetLat, targetLng)
            val totalDistance = distAndBearing.first
            val bearingDegrees = distAndBearing.second

            // Step in distance
            val stepSize = speedMps * tickIntervalSec

            if (totalDistance <= stepSize) {
                // We reached the current target node, move to next
                currentLat = targetLat
                currentLng = targetLng
                currentIdx++
                withContext(Dispatchers.Main) {
                    currentRouteIndex.value = currentIdx
                }
            } else {
                // Interpolate coordinate
                val nextCoords = getNextCoordinate(currentLat, currentLng, stepSize, bearingDegrees)
                currentLat = nextCoords.first
                currentLng = nextCoords.second
            }

            // Push fake location
            currentMockLatitude.value = currentLat
            currentMockLongitude.value = currentLng
            currentMockStateLabel.value = "Simulating Route: node %d/%d (Speed: %.1f km/h)".format(
                currentIdx + 1, points.size, speedKmh
            )

            pushMockLocationToSystem(currentLat, currentLng)
            updateNotificationBuilder(
                currentLat,
                currentLng,
                "Route Simulating (Speed: %.0f km/h)".format(speedKmh)
            )

            delay((tickIntervalSec * 1000).toLong())
        }

        // Lock at final location static loop
        if (points.isNotEmpty()) {
            val finalPoint = points.last()
            startStaticMockingLoop(finalPoint.first, finalPoint.second)
        }
    }

    private fun setupMockProviders(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        var success = true

        for (provider in providers) {
            try {
                // Ensure no crash by removing it first if present
                try {
                    locationManager.removeTestProvider(provider)
                } catch (e: Exception) {}

                locationManager.addTestProvider(
                    provider,
                    false, // requiresNetwork
                    false, // requiresSatellite
                    false, // requiresCell
                    false, // hasMonetaryCost
                    true,  // supportsAltitude
                    true,  // supportsSpeed
                    true,  // supportsBearing
                    1,     // powerRequirement (POWER_LOW)
                    1      // accuracy (ACCURACY_FINE)
                )
                locationManager.setTestProviderEnabled(provider, true)
            } catch (e: SecurityException) {
                Log.e("MockLocationService", "SecurityException: Mock Location not enabled in dev settings", e)
                success = false
            } catch (e: Exception) {
                Log.e("MockLocationService", "Exception setting up provider: $provider", e)
            }
        }
        return success
    }

    private fun pushMockLocationToSystem(lat: Double, lng: Double) {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        val currentTime = System.currentTimeMillis()
        val elapsedNanos = SystemClock.elapsedRealtimeNanos()

        for (provider in providers) {
            try {
                val mockLocation = Location(provider).apply {
                    this.latitude = lat
                    this.longitude = lng
                    this.altitude = 50.0
                    this.time = currentTime
                    this.elapsedRealtimeNanos = elapsedNanos
                    this.accuracy = 1.0f
                    this.speed = 0.0f
                    this.bearing = 0.0f
                }
                locationManager.setTestProviderLocation(provider, mockLocation)
            } catch (e: Exception) {
                // Ignored or handled gracefully
            }
        }
    }

    private fun removeMockProviders() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        for (provider in providers) {
            try {
                locationManager.removeTestProvider(provider)
            } catch (e: Exception) {}
        }
    }

    private fun stopServiceGracefully() {
        removeMockProviders()
        mockJob?.cancel()
        isServiceRunning.value = false
        currentMockStateLabel.value = "Idle"
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        serviceJob.cancel()
        removeMockProviders()
        mockJob?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // Create App Notification Channel
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Fake GPS Active Mocking Context",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows notifications while user GPS simulation is running."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(lat: Double, lng: Double, mode: String, msg: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Stop Action button inside Notification Shade
        val stopIntent = Intent(this, MockLocationService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val modeLabel = if (mode == "STATIC") "Fixed Point" else "Simulated Route"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GPS Mock Location Active")
            .setContentText("%.5f , %.5f (%s)".format(lat, lng, modeLabel))
            .setSubText(msg)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Mocking", stopPendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotificationBuilder(lat: Double, lng: Double, details: String) {
        val notification = createNotification(lat, lng, currentMockMode.value, details)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    // Helper functions for GPS route interpolation
    private fun computeDistanceAndBearing(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Pair<Double, Double> {
        val r = 6371000.0 // Earth's radius in meters
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2).pow(2) + cos(lat1Rad) * cos(lat2Rad) * sin(dLon / 2).pow(2)
        val clampedA = a.coerceIn(0.0, 1.0)
        val c = 2 * atan2(sqrt(clampedA), sqrt(1.0 - clampedA))
        val distance = r * c

        val y = sin(dLon) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon)
        val bearing = (Math.toDegrees(atan2(y, x)) + 360) % 360

        return Pair(distance, bearing)
    }

    private fun getNextCoordinate(
        lat: Double, lon: Double,
        distanceMeters: Double, bearingDegrees: Double
    ): Pair<Double, Double> {
        val r = 6371000.0 // Earth's radius
        val angularDist = distanceMeters / r
        val bearingRad = Math.toRadians(bearingDegrees)

        val latRad = Math.toRadians(lat)
        val lonRad = Math.toRadians(lon)

        val nextLatRad = asin(
            sin(latRad) * cos(angularDist) +
                    cos(latRad) * sin(angularDist) * cos(bearingRad)
        )
        val nextLonRad = lonRad + atan2(
            sin(bearingRad) * sin(angularDist) * cos(latRad),
            cos(angularDist) - sin(latRad) * sin(nextLatRad)
        )

        return Pair(Math.toDegrees(nextLatRad), Math.toDegrees(nextLonRad))
    }
}
