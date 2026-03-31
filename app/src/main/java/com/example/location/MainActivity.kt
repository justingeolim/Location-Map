package com.example.location

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.location.ui.theme.LocationTheme

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LocationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    LocationMapScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun LocationMapScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    // ask for permission on launch
    LaunchedEffect(Unit) {
        if (!hasPermission) {
            launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    if (hasPermission) {
        MapContent(context = context, modifier = modifier)
    } else {
        Text("Location permission is required.", modifier = modifier)
    }
}

@Composable
fun MapContent(context: Context, modifier: Modifier = Modifier) {
    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var userAddress by remember { mutableStateOf("Loading address...") }
    val customMarkers = remember { mutableStateListOf<LatLng>() }

    // get the last known location
    LaunchedEffect(Unit) {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    userLocation = LatLng(it.latitude, it.longitude)
                }
            }
        } catch (e: SecurityException) {
            Log.e("Location", "Security Exception: ${e.message}")
        }
    }

    // reverse geocode whenever location changes
    LaunchedEffect(userLocation) {
        userLocation?.let {
            userAddress = reverseGeocode(context, it.latitude, it.longitude)
        }
    }

    // fallback to BU when not loaded
    val mapCenter = userLocation ?: LatLng(42.3505, -71.1054)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(mapCenter, 15f)
    }

    // move camera when we get the real location
    LaunchedEffect(userLocation) {
        userLocation?.let {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(it, 15f)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // show address info at the top
        Text(
            text = userAddress,
            fontSize = 14.sp,
            modifier = Modifier.padding(8.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            onMapClick = { latLng ->
                // place a custom marker on tap
                customMarkers.add(latLng)
            }
        ) {
            // user location marker
            userLocation?.let {
                Marker(
                    state = MarkerState(position = it),
                    title = "My Location"
                )
            }

            // custom markers from tapping
            customMarkers.forEach { pos ->
                Marker(
                    state = MarkerState(position = pos),
                    title = "Custom Marker"
                )
            }
        }
    }
}

// reverse geocode to get address string
suspend fun reverseGeocode(context: Context, lat: Double, lng: Double): String =
    withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context)
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (!addresses.isNullOrEmpty()) {
                val a = addresses[0]
                (0..a.maxAddressLineIndex).mapNotNull { a.getAddressLine(it) }
                    .joinToString(", ")
            } else {
                "Address not found"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Address not found"
        }
    }

@Preview(showBackground = true)
@Composable
fun LocationMapScreenPreview() {
    LocationTheme {
        Text("Location Map Preview")
    }
}