package com.example.data

import android.content.Context
import android.location.Geocoder
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class LocationSearchItem(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String,
    val description: String = ""
)

object GeocodingHelper {

    // Precompiled offline landmarks prioritizing Sri Lanka and global favorites
    val PRESETS = listOf(
        LocationSearchItem("Parakaduwa", 6.8494, 80.2974, "Sri Lanka", "Tranquil town in Sabaragamuwa Province, Ratnapura District"),
        LocationSearchItem("Colombo (Commercial Capital)", 6.9271, 79.8612, "Sri Lanka", "Main port, Galle Face Green to mock locs"),
        LocationSearchItem("Kandy (Sacred City)", 7.2906, 80.6337, "Sri Lanka", "Temple of the Tooth Relic, scenic lake"),
        LocationSearchItem("Galle Fort", 6.0535, 80.2210, "Sri Lanka", "Historic Dutch fortress & pristine beach coast"),
        LocationSearchItem("Sigiriya Rock Fortress", 7.9570, 80.7600, "Sri Lanka", "Ancient palace in the sky world heritage site"),
        LocationSearchItem("Jaffna Peninsula", 9.6615, 80.0255, "Sri Lanka", "Nallur Kandaswamy Kovil, northern culture"),
        LocationSearchItem("Negombo Beach", 7.2008, 79.8737, "Sri Lanka", "Close to Bandaranaike international airport"),
        LocationSearchItem("Ella (Scenic Highlands)", 6.8722, 81.0475, "Sri Lanka", "Nine Arch Bridge, mountain peaks & tea estates"),
        LocationSearchItem("Ratnapura (City of Gems)", 6.6828, 80.3992, "Sri Lanka", "Gem industry hub surrounded by mountains"),
        LocationSearchItem("Maharagama Town", 6.8482, 79.9265, "Sri Lanka", "Busy commercial center and textile market"),
        LocationSearchItem("Gampaha", 7.0897, 79.9925, "Sri Lanka", "Western Province city with famous Botanical Gardens"),
        LocationSearchItem("Kurunegala", 7.4863, 80.3647, "Sri Lanka", "Historic elephant-rock citadel city"),
        LocationSearchItem("Eiffel Tower, Paris", 48.8584, 2.2945, "France", "Global landmark standard coordinate"),
        LocationSearchItem("Colosseum, Rome", 41.8902, 12.4922, "Italy", "Ancient Roman amphitheater"),
        LocationSearchItem("Statue of Liberty", 40.6892, -74.0445, "United States", "Liberty Island, New York Harbor"),
        LocationSearchItem("Sydney Opera House", -33.8568, 151.2153, "Australia", "Multi-venue performing arts centre in Sydney Harbour"),
        LocationSearchItem("Tokyo District (Shibuya)", 35.6580, 139.7016, "Japan", "Shibuya Crossing, busy Tokyo downtown"),
        LocationSearchItem("Taj Mahal, India", 27.1751, 78.0421, "India", "White marble mausoleum monument of love"),
        LocationSearchItem("Burj Khalifa, Dubai", 25.1972, 55.2744, "United Arab Emirates", "The world's tallest building structure"),
        LocationSearchItem("Marina Bay Sands", 1.2834, 103.8607, "Singapore", "Marina Bay skyline iconic resort & infinity pool")
    )

    suspend fun searchLocations(context: Context, query: String): List<LocationSearchItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val cleanQuery = query.trim()

        // 1. Check exact/near-exact preset match first to quickly load standard offline favorites
        val exactPreset = PRESETS.firstOrNull { it.name.equals(cleanQuery, ignoreCase = true) }
        if (exactPreset != null) {
            return@withContext listOf(exactPreset)
        }

        // 2. Try Komoot Photon Geocoder (OSM-based, extremely fast, global, has all villages & cities)
        try {
            val encodedQuery = java.net.URLEncoder.encode(cleanQuery, "UTF-8")
            val urlConnection = URL("https://photon.komoot.io/api/?q=$encodedQuery&limit=10")
                .openConnection() as HttpURLConnection
            urlConnection.requestMethod = "GET"
            urlConnection.setRequestProperty("User-Agent", "Mozilla/5.0")
            urlConnection.connectTimeout = 8000
            urlConnection.readTimeout = 8000

            val responseCode = urlConnection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader(InputStreamReader(urlConnection.inputStream)).use { reader ->
                    val response = reader.readText()
                    val results = parsePhotonResponse(response)
                    if (results.isNotEmpty()) {
                        Log.d("GeocodingHelper", "Photon successfully returned ${results.size} matches for $cleanQuery")
                        return@withContext results
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GeocodingHelper", "Komoot Photon lookup failed, trying fallback indexers", e)
        }

        // 3. Try Android Platform Geocoder (System Service using Google Maps geocoding on device)
        if (Geocoder.isPresent()) {
            try {
                val geocoder = Geocoder(context)
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocationName(cleanQuery, 5)
                if (addresses != null && addresses.isNotEmpty()) {
                    val results = addresses.map { address ->
                        val name = address.featureName ?: address.thoroughfare ?: address.subLocality ?: cleanQuery
                        val city = address.locality ?: address.subAdminArea ?: ""
                        val state = address.adminArea ?: ""
                        val country = address.countryName ?: "World"
                        
                        val descriptionParts = listOfNotNull(
                            address.thoroughfare,
                            address.subLocality,
                            city,
                            state
                        ).distinct().take(3)
                        
                        val description = if (descriptionParts.isNotEmpty()) {
                            descriptionParts.joinToString(", ")
                        } else {
                            "Coordinates: ${address.latitude}, ${address.longitude}"
                        }

                        LocationSearchItem(
                            name = if (name.equals(cleanQuery, ignoreCase = true)) name else "$name, $city".trimEnd(',', ' '),
                            latitude = address.latitude,
                            longitude = address.longitude,
                            country = country,
                            description = description
                        )
                    }
                    if (results.isNotEmpty()) {
                        Log.d("GeocodingHelper", "Platform geocoder successfully returned ${results.size} matches.")
                        return@withContext results
                    }
                }
            } catch (e: Exception) {
                Log.e("GeocodingHelper", "Platform Geocoder failed, falling back to other protocols", e)
            }
        }

        // 4. Fallback to offline presets filter
        val matchingPresets = PRESETS.filter {
            it.name.contains(cleanQuery, ignoreCase = true) || it.country.contains(cleanQuery, ignoreCase = true)
        }
        if (matchingPresets.isNotEmpty()) {
            return@withContext matchingPresets
        }

        // 5. Try Osm Nominatim Geocoding protocol
        try {
            val encodedQuery = java.net.URLEncoder.encode(cleanQuery, "UTF-8")
            val urlConnection = URL("https://nominatim.openstreetmap.org/search?format=json&q=$encodedQuery&limit=5")
                .openConnection() as HttpURLConnection
            urlConnection.requestMethod = "GET"
            urlConnection.setRequestProperty("User-Agent", "MockGPSLocationApp/1.1 (mr.sunilweerasinghe@gmail.com)")
            urlConnection.connectTimeout = 8000
            urlConnection.readTimeout = 8000

            val responseCode = urlConnection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader(InputStreamReader(urlConnection.inputStream)).use { reader ->
                    val response = reader.readText()
                    val results = parseNominatimResponse(response)
                    if (results.isNotEmpty()) {
                        return@withContext results
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GeocodingHelper", "Osm Nominatim lookup failed, falling back to Gemini API", e)
        }

        // 6. Fetch using Gemini REST API if available and user enters query
        val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY" || apiKey.contains("placeholder", ignoreCase = true)) {
            // If offline/no API key, fallback to standard mock generator near Sri Lanka or worldwide
            return@withContext createMockSearchResults(query)
        }

        try {
            val urlConnection = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey")
                .openConnection() as HttpURLConnection
            urlConnection.requestMethod = "POST"
            urlConnection.setRequestProperty("Content-Type", "application/json")
            urlConnection.doOutput = true
            urlConnection.connectTimeout = 10000
            urlConnection.readTimeout = 10000

            val prompt = """
                You are a precise geographic search geocoder assistant.
                Resolve the user's geographic search query: "$query" into 1 to 4 precise physical matches in the world.
                You MUST return ONLY a valid raw JSON array of objects representing coordinates. Do not include any markdown markup like triple backticks or ```json.
                Each object MUST have these exact Keys: "name", "latitude", "longitude", "country", "description".
                Ensure latitude is between -90 and 90, and longitude is between -180 and 180.
                Example Output:
                [
                  {"name": "Colombo Fort", "latitude": 6.9319, "longitude": 79.8430, "country": "Sri Lanka", "description": "Busy economic center and harbor zone"}
                ]
            """.trimIndent()

            // Build request JSON
            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                })
            }

            OutputStreamWriter(urlConnection.outputStream).use { writer ->
                writer.write(requestJson.toString())
                writer.flush()
            }

            val responseCode = urlConnection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader(InputStreamReader(urlConnection.inputStream)).use { reader ->
                    val response = reader.readText()
                    return@withContext parseGeminiResponse(response)
                }
            } else {
                Log.e("GeocodingHelper", "Gemini HTTP Class error: $responseCode")
                return@withContext createMockSearchResults(query)
            }
        } catch (e: Exception) {
            Log.e("GeocodingHelper", "Failed query on Gemini", e)
            return@withContext createMockSearchResults(query)
        }
    }

    private fun parsePhotonResponse(rawJsonStr: String): List<LocationSearchItem> {
        val list = mutableListOf<LocationSearchItem>()
        try {
            val root = JSONObject(rawJsonStr)
            val features = root.optJSONArray("features") ?: return emptyList()
            for (i in 0 until features.length()) {
                val feature = features.getJSONObject(i)
                val geometry = feature.getJSONObject("geometry")
                val coordinates = geometry.getJSONArray("coordinates")
                val lon = coordinates.getDouble(0)
                val lat = coordinates.getDouble(1)

                val properties = feature.getJSONObject("properties")
                val name = properties.optString("name", "")
                val country = properties.optString("country", "World")
                val city = properties.optString("city", "")
                val state = properties.optString("state", "")
                val street = properties.optString("street", "")
                val postcode = properties.optString("postcode", "")

                // Construct a helpful localized description
                val detailsList = listOfNotNull(
                    street.takeIf { it.isNotBlank() },
                    city.takeIf { it.isNotBlank() },
                    state.takeIf { it.isNotBlank() },
                    postcode.takeIf { it.isNotBlank() }
                ).distinct()
                
                val details = if (detailsList.isNotEmpty()) {
                    detailsList.joinToString(", ")
                } else {
                    "Coordinates: $lat, $lon"
                }

                if (name.isNotBlank()) {
                    list.add(
                        LocationSearchItem(
                            name = name,
                            latitude = lat,
                            longitude = lon,
                            country = country,
                            description = details
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("GeocodingHelper", "Error parsing Photon response stream", e)
        }
        return list
    }

    private fun parseNominatimResponse(rawJsonStr: String): List<LocationSearchItem> {
        val list = mutableListOf<LocationSearchItem>()
        try {
            val jsonArray = JSONArray(rawJsonStr)
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val displayName = item.optString("display_name", "")
                val lat = item.optDouble("lat", 0.0)
                val lon = item.optDouble("lon", 0.0)

                // Split display name to extract localized descriptors
                val parts = displayName.split(",")
                val mainName = parts.firstOrNull()?.trim() ?: "Search Result"
                val country = parts.lastOrNull()?.trim() ?: "World"
                val remDesc = if (parts.size > 1) {
                    parts.subList(1, minOf(4, parts.size)).joinToString(", ").trim()
                } else {
                    ""
                }

                list.add(
                    LocationSearchItem(
                        name = mainName,
                        latitude = lat,
                        longitude = lon,
                        country = country,
                        description = remDesc
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("GeocodingHelper", "Error parsing Nominatim response stream", e)
        }
        return list
    }

    private fun parseGeminiResponse(rawJsonStr: String): List<LocationSearchItem> {
        val list = mutableListOf<LocationSearchItem>()
        try {
            val mainObj = JSONObject(rawJsonStr)
            val candidates = mainObj.getJSONArray("candidates")
            val parts = candidates.getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
            var text = parts.getJSONObject(0).getString("text")

            // Clean markdown blocks if Gemini included them despite instruction
            text = text.replace("```json", "").replace("```", "").trim()

            val jsonArray = JSONArray(text)
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                list.add(
                    LocationSearchItem(
                        name = item.optString("name", "Search Result"),
                        latitude = item.optDouble("latitude", 0.0),
                        longitude = item.optDouble("longitude", 0.0),
                        country = item.optString("country", "World"),
                        description = item.optString("description", "")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("GeocodingHelper", "Error parsing Gemini response stream", e)
        }
        return list
    }

    private fun createMockSearchResults(query: String): List<LocationSearchItem> {
        // Fallback generator to make the app functional even offline
        // Calculates a deterministic pseudo-random lat/lng seed based on the search string
        val seed = query.hashCode().toDouble()
        val offsetLat = (seed % 100) / 15.0
        val offsetLng = (seed % 150) / 10.0

        // Create virtual results anchored around general interesting locations
        return listOf(
            LocationSearchItem(
                name = "${query.replaceFirstChar { it.uppercase() }} North",
                latitude = 6.9271 + (offsetLat * 0.05),
                longitude = 79.8612 + (offsetLng * 0.05),
                country = "Mock Geo System",
                description = "Offline simulated coordinates loaded successfully"
            ),
            LocationSearchItem(
                name = "${query.replaceFirstChar { it.uppercase() }} Landmark",
                latitude = 6.9271 - (offsetLat * 0.03),
                longitude = 79.8612 - (offsetLng * 0.03),
                country = "Mock Geo System",
                description = "Accurate simulated center point"
            )
        )
    }
}
