package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.annotation.SuppressLint
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.ConsoleMessage
import android.webkit.WebSettings
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.FavoriteLocation
import com.example.data.GeocodingHelper
import com.example.data.LocationSearchItem
import com.example.data.SimulatedRoute
import com.example.ui.LocationViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.abs


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = false, dynamicColor = false) {
                MainAppScreen()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainAppScreen() {
    val context = LocalContext.current
    val viewModel: LocationViewModel = viewModel()

    // Permissions check state
    var permissionsGranted by remember {
        mutableStateOf(
            hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) &&
                    hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val fineOk = results[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseOk = results[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        permissionsGranted = fineOk && coarseOk
        if (!fineOk) {
            Toast.makeText(context, "Location permission is needed to select and lock mock GPS points.", Toast.LENGTH_LONG).show()
        }
    }

    // Launch permission request once if not granted
    LaunchedEffect(Unit) {
        if (!permissionsGranted) {
            val permissions = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            // Request Android 14 location foreground permission if available
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                permissions.add(Manifest.permission.FOREGROUND_SERVICE_LOCATION)
            }
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    // Handle incoming backend errors as Snackbars
    val liveError by viewModel.liveError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(liveError) {
        liveError?.let {
            snackbarHostState.showSnackbar(
                message = it,
                actionLabel = "OK",
                duration = SnackbarDuration.Long
            )
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing,
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavyBG)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(DarkNavyBG)
        ) {
            if (!permissionsGranted) {
                PermissionRequestBanner {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            }

            // High Fidelity Responsive Layout Layout selection
            val configuration = LocalConfiguration.current
            val isWideScreen = configuration.screenWidthDp > 600

            if (isWideScreen) {
                WideScreenLayout(viewModel)
            } else {
                CompactScreenLayout(viewModel)
            }
        }
    }
}

// Permission missing block
@Composable
fun PermissionRequestBanner(onRequest: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = WarningOrange.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Warning",
                tint = WarningOrange,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "GPS Permissions Required",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextLight,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Please grant location permissions to choose custom pins.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(containerColor = WarningOrange),
                modifier = Modifier.testTag("grant_permissions_button")
            ) {
                Text("Grant", color = DarkNavyBG, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// Tablet/Foldable Horizontal Split
@Composable
fun WideScreenLayout(viewModel: LocationViewModel) {
    Row(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1.2f)
                .fillMaxHeight()
        ) {
            GridComplexMapCanvas(viewModel)
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(CardNavy)
        ) {
            MobileDashboardTabs(viewModel)
        }
    }
}

// Compact Mobile Screen Vertical
@Composable
fun CompactScreenLayout(viewModel: LocationViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxWidth()
        ) {
            GridComplexMapCanvas(viewModel)
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(CardNavy)
        ) {
            MobileDashboardTabs(viewModel)
        }
    }
}

// Futuristic GPS Matrix Grid canvas mapper with OpenStreetMap (Leaflet) and Tactical Sonar Grid modes
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OpenStreetMapWebView(
    latitude: Double,
    longitude: Double,
    zoom: Int,
    isMockActive: Boolean,
    liveLat: Double,
    liveLng: Double,
    onLocationSelected: (Double, Double) -> Unit
) {
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.databaseEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            webViewClient = WebViewClient()
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    Log.d("MapWebViewConsole", "${consoleMessage?.message()} -- From line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}")
                    return true
                }
            }
            
            addJavascriptInterface(object {
                @JavascriptInterface
                fun onMapClicked(lat: Double, lng: Double) {
                    post {
                        onLocationSelected(lat, lng)
                    }
                }
                @JavascriptInterface
                fun onMarkerDragged(lat: Double, lng: Double) {
                    post {
                        onLocationSelected(lat, lng)
                    }
                }
            }, "AndroidBridge")
        }
    }

    // Load initial map setup with stable Cloudflare cdnjs links
    val mapHtml = remember {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.css" />
            <style>
                html, body {
                    height: 100%;
                    margin: 0;
                    padding: 0;
                    background-color: #0d1527;
                }
                #map {
                    height: 100%;
                    width: 100%;
                }
                .leaflet-bar a {
                    background-color: #1e293b !important;
                    color: #38bdf8 !important;
                    border-bottom: 1px solid #334155 !important;
                }
                /* Hide default Leaflet watermarks/links info to keep space clean */
                .leaflet-control-attribution {
                    display: none !important;
                }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script src="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.js"></script>
            <script>
                var map = L.map('map', { zoomControl: false }).setView([$latitude, $longitude], $zoom);
                
                L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                    maxZoom: 19
                }).addTo(map);

                // Setup Active location pin marker
                var activeMarker = L.marker([$latitude, $longitude], { draggable: true }).addTo(map);
                var liveCircle = null;

                // Bind click/drag to Android Kotlin ViewModel updates
                activeMarker.on('dragend', function(e) {
                    var pos = activeMarker.getLatLng();
                    AndroidBridge.onMarkerDragged(pos.lat, pos.lng);
                });

                map.on('click', function(e) {
                    activeMarker.setLatLng(e.latlng);
                    AndroidBridge.onMapClicked(e.latlng.lat, e.latlng.lng);
                });

                // Functions invoked dynamically from Kotlin flow emitters
                function updateActiveLocation(lat, lng, zoomLvl) {
                    var latLng = L.latLng(lat, lng);
                    activeMarker.setLatLng(latLng);
                    map.setView(latLng, zoomLvl || map.getZoom());
                }

                function updateLiveLocation(lat, lng, active) {
                    if (active) {
                        if (!liveCircle) {
                            liveCircle = L.circle([lat, lng], {
                                color: '#10B981',
                                fillColor: '#10B981',
                                fillOpacity: 0.4,
                                radius: 100
                            }).addTo(map);
                        } else {
                            liveCircle.setLatLng([lat, lng]);
                        }
                    } else {
                        if (liveCircle) {
                            map.removeLayer(liveCircle);
                            liveCircle = null;
                        }
                    }
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    // Keep active coordinates in sync when search presets or route list triggers changes
    LaunchedEffect(latitude, longitude, zoom) {
        if (!latitude.isNaN() && !longitude.isNaN()) {
            webView.evaluateJavascript("javascript:updateActiveLocation($latitude, $longitude, $zoom)", null)
        }
    }

    LaunchedEffect(liveLat, liveLng, isMockActive) {
        if (!liveLat.isNaN() && !liveLng.isNaN()) {
            webView.evaluateJavascript("javascript:updateLiveLocation($liveLat, $liveLng, $isMockActive)", null)
        }
    }

    DisposableEffect(Unit) {
        webView.loadDataWithBaseURL("https://openstreetmap.org", mapHtml, "text/html", "UTF-8", null)
        onDispose {
            webView.destroy()
        }
    }

    AndroidView(
        factory = { webView },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun GridComplexMapCanvas(viewModel: LocationViewModel) {
    val context = LocalContext.current
    val isMockActive by viewModel.isMockActive.collectAsState()
    val liveLat by viewModel.liveLatitude.collectAsState()
    val liveLng by viewModel.liveLongitude.collectAsState()
    val liveLabel by viewModel.liveStateLabel.collectAsState()

    var dragActive by remember { mutableStateOf(false) }
    var mapTypeByState by remember { mutableStateOf("real") } // Default to "real" OpenStreetMap worldview

    // Pulsing animation for mock marker
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 10f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseRadius"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFFE2E8F0)) // Sleek interface slate map BG
            .border(1.dp, Color(0xFFC2C7CF), RoundedCornerShape(24.dp))
    ) {
        if (mapTypeByState == "real") {
            // Render actual map (Sri Lanka / Colombo / Global interactively via OSM web helper)
            OpenStreetMapWebView(
                latitude = viewModel.activeLatitude.value,
                longitude = viewModel.activeLongitude.value,
                zoom = viewModel.mapZoom.value,
                isMockActive = isMockActive,
                liveLat = liveLat,
                liveLng = liveLng,
                onLocationSelected = { lat, lng ->
                    viewModel.selectLocation(lat, lng, "Selected Coordinates")
                }
            )
        } else {
            // Master Canvas drawing standard geographic mapping grid
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { dragActive = true },
                            onDragEnd = { dragActive = false },
                            onDragCancel = { dragActive = false },
                            onDrag = { _, dragAmount ->
                                // Map drag scale offset relative to virtual zoom
                                val degreesPerPixel = 1.0 / (viewModel.mapZoom.value * 250.0)
                                val cleanLng = viewModel.activeLongitude.value - (dragAmount.x * degreesPerPixel)
                                val cleanLat = viewModel.activeLatitude.value + (dragAmount.y * degreesPerPixel)

                                // Boundaries limiting standard latitude longitude limits
                                viewModel.selectLocation(
                                    latitude = cleanLat.coerceIn(-85.0, 85.0),
                                    longitude = cleanLng.coerceIn(-180.0, 180.0),
                                    name = "Selected Coordinates"
                                )
                            }
                        )
                    }
            ) {
                val width = size.width
                val height = size.height
                val center = Offset(width / 2f, height / 2f)

                // Drawing futuristic tactical sonar radar lines
                val degreesPerPixel = 1.0 / (viewModel.mapZoom.value * 250.0)

                // Calculate current visible grid steps
                val gridSpacingDp = 60.dp.toPx()
                val gridLinesColor = DividerGray.copy(alpha = 0.4f)
                val subGridColor = DividerGray.copy(alpha = 0.15f)

                // Draw matrix background grids
                var x = center.x % gridSpacingDp
                while (x < width) {
                    drawLine(
                        color = gridLinesColor,
                        start = Offset(x, 0f),
                        end = Offset(x, height),
                        strokeWidth = 1f
                    )
                    x += gridSpacingDp
                }
                var y = center.y % gridSpacingDp
                while (y < height) {
                    drawLine(
                        color = gridLinesColor,
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1f
                    )
                    y += gridSpacingDp
                }

                // Draw outer compass circular bounds
                drawCircle(
                    color = CyberTeal.copy(alpha = 0.08f),
                    center = center,
                    radius = minOf(width, height) / 2.3f,
                    style = Stroke(3f)
                )

                drawCircle(
                    color = CyberTeal.copy(alpha = 0.03f),
                    center = center,
                    radius = minOf(width, height) / 4f,
                    style = Stroke(1.5f)
                )

                // Draw historical drafted points paths connects
                if (viewModel.draftRoutePoints.isNotEmpty()) {
                    var prevOffset: Offset? = null
                    for (pt in viewModel.draftRoutePoints) {
                        val dx = ((pt.second - viewModel.activeLongitude.value) / degreesPerPixel).toFloat()
                        val dy = -((pt.first - viewModel.activeLatitude.value) / degreesPerPixel).toFloat()
                        val targetOffset = Offset(center.x + dx, center.y + dy)

                        // Draw route point marker circle
                        drawCircle(
                            color = WarningOrange,
                            center = targetOffset,
                            radius = 8f
                        )

                        // Draw connection line
                        if (prevOffset != null) {
                            drawLine(
                                color = WarningOrange.copy(alpha = 0.7f),
                                start = prevOffset,
                                end = targetOffset,
                                strokeWidth = 4f
                            )
                        }
                        prevOffset = targetOffset
                    }
                }

                // Render matching presets/landmarks around current view if close
                GeocodingHelper.PRESETS.forEach { preset ->
                    val dx = ((preset.longitude - viewModel.activeLongitude.value) / degreesPerPixel).toFloat()
                    val dy = -((preset.latitude - viewModel.activeLatitude.value) / degreesPerPixel).toFloat()

                    // Check distance within clip envelope
                    if (abs(dx) < width / 2f && abs(dy) < height / 2f) {
                        val ptOffset = Offset(center.x + dx, center.y + dy)
                        drawCircle(
                            color = CyberTeal,
                            radius = 6f,
                            center = ptOffset
                        )
                        drawCircle(
                            color = CyberTeal.copy(alpha = 0.3f),
                            radius = 16f,
                            center = ptOffset,
                            style = Stroke(1f)
                        )
                    }
                }

                // Draw background pulsing active overlay when mocking is active
                if (isMockActive) {
                    val dx = ((liveLng - viewModel.activeLongitude.value) / degreesPerPixel).toFloat()
                    val dy = -((liveLat - viewModel.activeLatitude.value) / degreesPerPixel).toFloat()
                    val liveOffset = Offset(center.x + dx, center.y + dy)

                    if (abs(dx) < width / 2f && abs(dy) < height / 2f) {
                        // Pulsing green circle
                        drawCircle(
                            color = ActiveGreen.copy(alpha = pulseAlpha),
                            center = liveOffset,
                            radius = pulseRadius,
                            style = Stroke(2f)
                        )
                        drawCircle(
                            color = ActiveGreen,
                            center = liveOffset,
                            radius = 8f
                        )
                    }
                }

                // Draw absolute center focused marker target
                drawCircle(
                    color = if (dragActive) WarningOrange else CyberTeal,
                    center = center,
                    radius = 14f,
                    style = Stroke(3f)
                )
                drawCircle(
                    color = if (dragActive) WarningOrange else CyberTeal,
                    center = center,
                    radius = 4f
                )

                // Drawing compass dynamic HUD indicators
                drawLine(
                    color = CyberTeal.copy(alpha = 0.4f),
                    start = Offset(center.x, center.y - 25f),
                    end = Offset(center.x, center.y - 10f),
                    strokeWidth = 2f
                )
                drawLine(
                    color = CyberTeal.copy(alpha = 0.4f),
                    start = Offset(center.x, center.y + 10f),
                    end = Offset(center.x, center.y + 25f),
                    strokeWidth = 2f
                )
                drawLine(
                    color = CyberTeal.copy(alpha = 0.4f),
                    start = Offset(center.x - 25f, center.y),
                    end = Offset(center.x - 10f, center.y),
                    strokeWidth = 2f
                )
                drawLine(
                    color = CyberTeal.copy(alpha = 0.4f),
                    start = Offset(center.x + 10f, center.y),
                    end = Offset(center.x + 25f, center.y),
                    strokeWidth = 2f
                )
            }

            // Mini cardinal headings indicators overlay
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-110).dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("NORTH", style = MaterialTheme.typography.labelSmall, color = CyberTeal.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
                Icon(Icons.Default.KeyboardArrowUp, null, tint = CyberTeal.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
            }
        }

        // Floating Map action control buttons
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Toggle map mode view (Real Street view vs Sonar Grid Matrix)
            FloatingActionButton(
                onClick = { mapTypeByState = if (mapTypeByState == "real") "radar" else "real" },
                containerColor = CardNavy,
                contentColor = if (mapTypeByState == "real") CyberTeal else WarningOrange,
                modifier = Modifier.size(44.dp).testTag("toggle_map_mode_button")
            ) {
                Icon(
                    imageVector = if (mapTypeByState == "real") Icons.Default.Layers else Icons.Default.Map,
                    contentDescription = "Toggle Map Type",
                    tint = if (mapTypeByState == "real") CyberTeal else WarningOrange
                )
            }

            // Zoom In
            FloatingActionButton(
                onClick = { viewModel.mapZoom.value = (viewModel.mapZoom.value + 2).coerceAtMost(30) },
                containerColor = CardNavy,
                contentColor = CyberTeal,
                modifier = Modifier.size(44.dp).testTag("zoom_in_button")
            ) {
                Icon(Icons.Default.Add, "Zoom In")
            }

            // Zoom Out
            FloatingActionButton(
                onClick = { viewModel.mapZoom.value = (viewModel.mapZoom.value - 2).coerceAtLeast(4) },
                containerColor = CardNavy,
                contentColor = CyberTeal,
                modifier = Modifier.size(44.dp).testTag("zoom_out_button")
            ) {
                Icon(Icons.Default.Remove, "Zoom Out")
            }

            // Snap back to Active Mock position
            if (isMockActive) {
                FloatingActionButton(
                    onClick = { viewModel.selectLocation(liveLat, liveLng, "Live GPS Simulator Location") },
                    containerColor = CardNavy,
                    contentColor = ActiveGreen,
                    modifier = Modifier.size(44.dp).testTag("snap_live_button")
                ) {
                    Icon(Icons.Default.MyLocation, "Snap to Mock")
                }
            }
        }

        // Live HUD Indicator Banner at top
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .background(CardNavy.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                .drawBehind {
                    // Accent indicator colors
                    val clr = if (isMockActive) ActiveGreen else CyberTeal.copy(alpha = 0.5f)
                    drawRoundRect(
                        brush = Brush.horizontalGradient(listOf(clr, Color.Transparent)),
                        size = size.copy(height = 4.dp.toPx())
                    )
                }
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                color = if (isMockActive) ActiveGreen else Color.Gray,
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isMockActive) "MOCKING LIVE" else "READY / OFFLINE GRID",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isMockActive) ActiveGreen else TextMuted,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text(
                    text = "Zoom: x${viewModel.mapZoom.value}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Target Lat: %.6f, Lng: %.6f".format(viewModel.activeLatitude.value, viewModel.activeLongitude.value),
                color = TextLight,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )

            if (isMockActive) {
                Text(
                    text = "Active system spoofer: $liveLabel",
                    color = ActiveGreen,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// Bottom Dashboard controllers
@Composable
fun MobileDashboardTabs(viewModel: LocationViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Control Hub", "Presets & Favs", "Custom Route", "Dev Options")

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = CardNavy,
            contentColor = CyberTeal,
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = CyberTeal
                )
            }
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    modifier = Modifier.testTag("tab_button_$index")
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val icon = when (index) {
                            0 -> Icons.Default.CompassCalibration
                            1 -> Icons.Default.FavoriteBorder
                            2 -> Icons.Default.AltRoute
                            else -> Icons.Default.QueryStats
                        }
                        Icon(icon, null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkNavyBG)
        ) {
            when (selectedTab) {
                0 -> SimulatedControlTab(viewModel)
                1 -> PresetsAndFavoritesTab(viewModel)
                2 -> RouteSimulatorTab(viewModel)
                3 -> DeveloperGuideTab(viewModel)
            }
        }
    }
}

data class SearchSuggestion(
    val name: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double,
    val isFavorite: Boolean
)

// Tab 1: Static Simulator Controller
@Composable
fun SimulatedControlTab(viewModel: LocationViewModel) {
    val context = LocalContext.current
    val isMockActive by viewModel.isMockActive.collectAsState()
    val liveLat by viewModel.liveLatitude.collectAsState()
    val liveLng by viewModel.liveLongitude.collectAsState()
    val favorites by viewModel.favoriteLocations.collectAsState()

    var customLatInput by remember { mutableStateOf("") }
    var customLngInput by remember { mutableStateOf("") }
    var showSuggestions by remember { mutableStateOf(true) }

    val query = viewModel.searchQuery.value.trim()
    val suggestions = remember(query, favorites) {
        if (query.isEmpty()) {
            emptyList()
        } else {
            val list = mutableListOf<SearchSuggestion>()
            
            // Add matching favorites
            favorites.forEach { fav ->
                if (fav.name.contains(query, ignoreCase = true)) {
                    list.add(
                        SearchSuggestion(
                            name = fav.name,
                            subtitle = "Saved Favorite (${fav.latitude}, ${fav.longitude})",
                            latitude = fav.latitude,
                            longitude = fav.longitude,
                            isFavorite = true
                        )
                    )
                }
            }
            
            // Add matching preset landmarks
            GeocodingHelper.PRESETS.forEach { preset ->
                if (preset.name.contains(query, ignoreCase = true) || 
                    preset.country.contains(query, ignoreCase = true) ||
                    preset.description.contains(query, ignoreCase = true)) {
                    
                    if (list.none { abs(it.latitude - preset.latitude) < 0.0001 && abs(it.longitude - preset.longitude) < 0.0001 }) {
                        list.add(
                            SearchSuggestion(
                                name = preset.name,
                                subtitle = "${preset.country} • ${preset.description}",
                                latitude = preset.latitude,
                                longitude = preset.longitude,
                                isFavorite = false
                            )
                        )
                    }
                }
            }
            
            list.take(5)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Search Bar header styled as a sleek rounded-full capsule
            OutlinedTextField(
                value = viewModel.searchQuery.value,
                onValueChange = {
                    viewModel.searchQuery.value = it
                    showSuggestions = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("location_search_input"),
                placeholder = { Text("Search city, landmark or country...", color = TextMuted) },
                leadingIcon = { Icon(Icons.Default.Search, "Search", tint = CyberTeal) },
                trailingIcon = {
                    if (viewModel.searchQuery.value.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearSearch() }) {
                            Icon(Icons.Default.Clear, "Clear", tint = TextMuted)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberTeal,
                    unfocusedBorderColor = DividerGray,
                    focusedTextColor = TextLight,
                    unfocusedTextColor = TextLight,
                    unfocusedContainerColor = CardNavy,
                    focusedContainerColor = CardNavy
                )
            )

            // Dynamic search suggestions matching user query input
            if (showSuggestions && suggestions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = CardNavy),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(DividerGray, DividerGray)))
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        suggestions.forEach { suggestion ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectLocation(
                                            suggestion.latitude,
                                            suggestion.longitude,
                                            suggestion.name
                                        )
                                        viewModel.searchQuery.value = suggestion.name
                                        showSuggestions = false
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (suggestion.isFavorite) Icons.Default.Favorite else Icons.Default.Place,
                                    contentDescription = null,
                                    tint = if (suggestion.isFavorite) ErrorRed else CyberTeal,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = suggestion.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextLight,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = suggestion.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.performSearch() },
                colors = ButtonDefaults.buttonColors(containerColor = CyberTeal, contentColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("search_action_button")
            ) {
                if (viewModel.isSearching.value) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                } else {
                    Text("Search Globally With AI Geocoder", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (viewModel.searchResults.value.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "AI GEOCONNECT SEARCH RESULTS",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberTeal,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Clear x",
                        style = MaterialTheme.typography.labelSmall,
                        color = ErrorRed,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable { viewModel.clearSearch() }
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
            items(viewModel.searchResults.value) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp, horizontal = 4.dp)
                        .background(CardNavy, RoundedCornerShape(16.dp))
                        .border(1.dp, DividerGray, RoundedCornerShape(16.dp))
                        .clickable { viewModel.selectLocation(item.latitude, item.longitude, item.name) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(DarkNavyBG, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            tint = CyberTeal,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextLight,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${item.country} • ${item.description}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Active Spot Detail Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardNavy),
                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(DividerGray, DividerGray))),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "CURRENT SELECTED SPOT",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyberTeal,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = viewModel.activeLocationName.value,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextLight,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Elegantly styled side-by-side coordinate telemetry grid from Sleek theme spec
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Latitude display box
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(DarkNavyBG, RoundedCornerShape(16.dp))
                                .border(1.dp, DividerGray, RoundedCornerShape(16.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = "LATITUDE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextMuted,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "%.6f".format(viewModel.activeLatitude.value),
                                    fontSize = 14.sp,
                                    color = TextLight,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Longitude display box
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(DarkNavyBG, RoundedCornerShape(16.dp))
                                .border(1.dp, DividerGray, RoundedCornerShape(16.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = "LONGITUDE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextMuted,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "%.6f".format(viewModel.activeLongitude.value),
                                    fontSize = 14.sp,
                                    color = TextLight,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.toggleFavoriteCurrent() },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkNavyBG),
                            shape = RoundedCornerShape(20.dp),
                            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(DividerGray, DividerGray))),
                            modifier = Modifier
                                .weight(1.1f)
                                .height(40.dp)
                                .testTag("favorite_toggle_button")
                        ) {
                            Icon(Icons.Default.Favorite, "Fav", tint = ErrorRed, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Favorite", color = TextLight, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }

                        Button(
                            onClick = { viewModel.addPointToDraftRoute() },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkNavyBG),
                            shape = RoundedCornerShape(20.dp),
                            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(DividerGray, DividerGray))),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(40.dp)
                                .testTag("add_to_route_button")
                        ) {
                            Icon(Icons.Default.AddLocation, "Add to Route", tint = CyberTeal, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Pt to Route", color = TextLight, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        // Action Trigger Hub
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardNavy),
                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(DividerGray, DividerGray))),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "ACTIVE SIMULATOR SWITCH",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (!isMockActive) {
                        Button(
                            onClick = { viewModel.startStaticMocking(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberTeal, contentColor = Color.White),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("start_mock_location_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Start Mocking", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Update Live Spot Button
                            Button(
                                onClick = { 
                                    viewModel.updateStaticMockLocation(
                                        context, 
                                        viewModel.activeLatitude.value, 
                                        viewModel.activeLongitude.value
                                    ) 
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberTeal, contentColor = Color.White),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("update_mock_location_button")
                            ) {
                                Icon(Icons.Default.SyncAlt, null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("UPDATE SYSTEM TO SPOT", color = Color.White, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { viewModel.stopMocking(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("stop_mock_location_button")
                            ) {
                                Icon(Icons.Default.Stop, null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("RELEASE SYSTEM LOCATION", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Live Joystick simulator Controls
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardNavy),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "LIVE SHIFT CONTROLLER (JOYSTICK)",
                        style = MaterialTheme.typography.titleSmall,
                        color = CyberTeal,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Nudges your active latitude/longitude smoothly by small distances.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Joystick grid
                    val stepsNudge = 0.0003 // Approx ~35 meters
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                viewModel.updateStaticMockLocation(
                                    context,
                                    viewModel.activeLatitude.value + stepsNudge,
                                    viewModel.activeLongitude.value
                                )
                            },
                            modifier = Modifier
                                .background(DividerGray, CircleShape)
                                .size(40.dp)
                                .testTag("nudge_north")
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, "Nudge North", tint = CyberTeal)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                            IconButton(
                                onClick = {
                                    viewModel.updateStaticMockLocation(
                                        context,
                                        viewModel.activeLatitude.value,
                                        viewModel.activeLongitude.value - stepsNudge
                                    )
                                },
                                modifier = Modifier
                                    .background(DividerGray, CircleShape)
                                    .size(40.dp)
                                    .testTag("nudge_west")
                            ) {
                                Icon(Icons.Default.KeyboardArrowLeft, "Nudge West", tint = CyberTeal)
                            }

                            // Center dot indicator
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(DividerGray.copy(alpha = 0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(modifier = Modifier.size(8.dp).background(WarningOrange, CircleShape))
                            }

                            IconButton(
                                onClick = {
                                    viewModel.updateStaticMockLocation(
                                        context,
                                        viewModel.activeLatitude.value,
                                        viewModel.activeLongitude.value + stepsNudge
                                    )
                                },
                                modifier = Modifier
                                    .background(DividerGray, CircleShape)
                                    .size(40.dp)
                                    .testTag("nudge_east")
                            ) {
                                Icon(Icons.Default.KeyboardArrowRight, "Nudge East", tint = CyberTeal)
                            }
                        }

                        IconButton(
                            onClick = {
                                viewModel.updateStaticMockLocation(
                                    context,
                                    viewModel.activeLatitude.value - stepsNudge,
                                    viewModel.activeLongitude.value
                                )
                            },
                            modifier = Modifier
                                .background(DividerGray, CircleShape)
                                .size(40.dp)
                                .testTag("nudge_south")
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, "Nudge South", tint = CyberTeal)
                        }
                    }
                }
            }
        }
    }
}

// Tab 2: Presets & Favorites Tab
@Composable
fun PresetsAndFavoritesTab(viewModel: LocationViewModel) {
    val favorites by viewModel.favoriteLocations.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Preset Landmarks List Section
        item {
            Text(
                text = "GLOBAL & SRI LANKAN PRESETS",
                style = MaterialTheme.typography.titleSmall,
                color = CyberTeal,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(GeocodingHelper.PRESETS) { item ->
            PresetRowItem(item) {
                viewModel.selectLocation(item.latitude, item.longitude, item.name)
            }
        }

        // Custom Favorites list from DB
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "YOUR SAVED FAVORITES (${favorites.size})",
                style = MaterialTheme.typography.titleSmall,
                color = WarningOrange,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (favorites.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No favorites saved yet. Zoom and tap points on Control Hub to save spots!",
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        items(favorites) { fav ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardNavy, RoundedCornerShape(16.dp))
                    .border(1.dp, DividerGray, RoundedCornerShape(16.dp))
                    .clickable { viewModel.selectLocation(fav.latitude, fav.longitude, fav.name) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Favorite, "Fav", tint = ErrorRed)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(fav.name, color = TextLight, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            "Lat: %.5f, Lng: %.5f".format(fav.latitude, fav.longitude),
                            color = TextMuted,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                IconButton(
                    onClick = { viewModel.deleteFavorite(fav) },
                    modifier = Modifier.testTag("delete_favorite_${fav.id}")
                ) {
                    Icon(Icons.Default.Delete, "Delete", tint = ErrorRed)
                }
            }
        }
    }
}

@Composable
fun PresetRowItem(item: LocationSearchItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardNavy, RoundedCornerShape(16.dp))
            .border(1.dp, DividerGray, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(DividerGray, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Place, "Location", tint = CyberTeal, modifier = Modifier.size(18.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = item.name,
                color = TextLight,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Text(
                text = "${item.country} • ${item.description}",
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// Tab 3: Route Simulation Interface
@Composable
fun RouteSimulatorTab(viewModel: LocationViewModel) {
    val context = LocalContext.current
    val savedRoutes by viewModel.savedRoutes.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "ROUTE SIMULATION SETUP",
                style = MaterialTheme.typography.titleSmall,
                color = WarningOrange,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Create complex simulated movements globally. Tap locations on the map and click 'Add Pt to Route' to construct your path.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }

        // Active Draft Route Builder Panel
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardNavy),
                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(DividerGray, DividerGray))),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "NEW SIMULATED ROUTE BUILDER",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberTeal,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Points in Route: ${viewModel.draftRoutePoints.size}",
                        color = TextLight,
                        fontWeight = FontWeight.Bold
                    )

                    if (viewModel.draftRoutePoints.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        // Mini row displaying selected nodes
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            viewModel.draftRoutePoints.forEachIndexed { idx, pt ->
                                Box(
                                    modifier = Modifier
                                        .background(DividerGray, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                        .clickable { viewModel.removePointFromDraft(idx) }
                                ) {
                                    Text(
                                        "Node ${idx + 1}",
                                        color = WarningOrange,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = viewModel.draftRouteName.value,
                        onValueChange = { viewModel.draftRouteName.value = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("route_name_input"),
                        label = { Text("Route Descriptive Name", color = TextMuted) },
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberTeal,
                            unfocusedBorderColor = DividerGray,
                            focusedTextColor = TextLight,
                            unfocusedTextColor = TextLight,
                            unfocusedContainerColor = CardNavy,
                            focusedContainerColor = CardNavy
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Speed Slider walking vs driving
                    Text(
                        "Simulation Speed: ${viewModel.draftRouteSpeed.value.toInt()} km/h",
                        color = TextLight,
                        fontSize = 13.sp,
                    )
                    Slider(
                        value = viewModel.draftRouteSpeed.value.toFloat(),
                        onValueChange = { viewModel.draftRouteSpeed.value = it.toDouble() },
                        valueRange = 2f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = CyberTeal,
                            activeTrackColor = CyberTeal
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { viewModel.clearDraftRoute() },
                            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Text("Clear", color = ErrorRed, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.saveAndSimulateDraftRoute(context) },
                            enabled = viewModel.draftRoutePoints.size >= 2,
                            colors = ButtonDefaults.buttonColors(containerColor = CyberTeal, contentColor = Color.White),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .weight(1.5f)
                                .height(44.dp)
                                .testTag("save_and_run_route_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.DirectionsWalk, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save & Run", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Saved routes header
        item {
            Text(
                text = "SAVED SIMULATION RUNWAYS (${savedRoutes.size})",
                style = MaterialTheme.typography.titleSmall,
                color = CyberTeal,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        items(savedRoutes) { route ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardNavy, RoundedCornerShape(16.dp))
                    .border(1.dp, DividerGray, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.AltRoute, "Route", tint = CyberTeal)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(route.name, color = TextLight, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            "Speed: %.0f km/h • %d Nodes in runway".format(
                                route.speedKmh,
                                route.pointsJson.split(";").size
                            ),
                            color = TextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Row {
                    IconButton(onClick = { viewModel.playSavedRoute(context, route) }) {
                        Icon(Icons.Default.PlayArrow, "Play", tint = ActiveGreen)
                    }
                    IconButton(onClick = { viewModel.deleteSavedRoute(route) }) {
                        Icon(Icons.Default.Delete, "Delete", tint = ErrorRed)
                    }
                }
            }
        }
    }
}

// Tab 4: Comprehensive Developer guidelines Setup
@Composable
fun DeveloperGuideTab(viewModel: LocationViewModel) {
    val context = LocalContext.current
    val isMockActive by viewModel.isMockActive.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "ANDROID MOCK LOCATION SETUP GUIDE",
                style = MaterialTheme.typography.titleSmall,
                color = WarningOrange,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "By default, Android system restricts mocking location unless this App is set as the 'Select mock location app' under developer options.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextLight
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardNavy),
                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(DividerGray, DividerGray))),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Follow these 3 easy steps:",
                        style = MaterialTheme.typography.titleMedium,
                        color = CyberTeal,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    StepRow("1", "Go to your Android Settings -> About Phone, and tap 'Build number' 7 times continuously to enable developer options.")
                    Spacer(modifier = Modifier.height(8.dp))
                    StepRow("2", "Open 'Developer Options' and scroll down to locate the 'Select mock location app' preference item.")
                    Spacer(modifier = Modifier.height(8.dp))
                    StepRow("3", "Choose 'Fake GPS Location' as the mock provider app and launch coordinates spoofing inside this application!")
                }
            }
        }

        item {
            Button(
                onClick = {
                    try {
                        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Developer options not enabled on this device yet! Enable manually.", Toast.LENGTH_LONG).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyberTeal),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("open_developer_settings_button")
            ) {
                Icon(Icons.Default.Settings, null, tint = DarkNavyBG)
                Spacer(modifier = Modifier.width(8.dp))
                Text("OPEN DEVELOPER OPTIONS SHADE", color = DarkNavyBG, fontWeight = FontWeight.Bold)
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardNavy.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "SYSTEM SPOOF STATUS REPORT",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextLight,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Mock Service Connection: ${if (isMockActive) "ACTIVE (In Foreground)" else "IDLE"}",
                        color = if (isMockActive) ActiveGreen else TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "• GPS Hardware Provider Overrides: ENABLED",
                        color = TextLight,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "• Network Hardware Provider Overrides: ENABLED",
                        color = TextLight,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun StepRow(step: String, instructions: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(CyberTeal, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(step, color = DarkNavyBG, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(instructions, color = TextLight, fontSize = 13.sp)
    }
}

fun hasPermission(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
