package com.example.geupjo_bus

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.geupjo_bus.ui.rememberMapViewWithLifecycle
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import androidx.compose.material.icons.Icons


@Composable
fun RouteSearchScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {}
) {
    var departure by remember { mutableStateOf(TextFieldValue("")) }
    var destination by remember { mutableStateOf(TextFieldValue("")) }
    var routeResults by remember { mutableStateOf(listOf<String>()) }
    var isSearching by remember { mutableStateOf(false) }
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var destinationLocation by remember { mutableStateOf<LatLng?>(null) } // 도착지 위치
    var destinationMarker by remember { mutableStateOf<Marker?>(null) } // 도착지 마커

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 위치 가져오기
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            currentLocation = getCurrentLocation(context)
            currentLocation?.let {
                // 현재 위치의 위도, 경도를 주소로 변환하여 출발지 텍스트 필드에 자동 입력
                val address = getAddressFromLocation(context, it.latitude, it.longitude)
                departure = TextFieldValue(address)
            }
        }
    }

    // 도착지 주소를 위도, 경도로 변환하는 함수
    fun getDestinationLocation(address: String) {
        coroutineScope.launch {
            destinationLocation = geocodeAddress(context, address)
        }
    }

    // 맵 관련 상태
    val mapView = rememberMapViewWithLifecycle(context)
    var googleMap by remember { mutableStateOf<GoogleMap?>(null) }

    // 도착지 주소 입력시 변환 호출
    LaunchedEffect(destination.text) {
        if (destination.text.isNotEmpty()) {
            getDestinationLocation(destination.text)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Button(onClick = onBackClick, modifier = Modifier.align(Alignment.Start)) {
            Text("뒤로 가기")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 현재 위치 표시
        currentLocation?.let { location ->
            val address = getAddressFromLocation(context, location.latitude, location.longitude)
            Text(
                text = "현재 위치: $address",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 출발지 입력
        Text(text = "출발지", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = departure,
            onValueChange = { departure = it },
            label = { Text("출발지를 입력하세요") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 도착지 입력
        Text(text = "도착지", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = destination,
            onValueChange = { destination = it },
            label = { Text("도착지를 입력하세요") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    isSearching = true
                    coroutineScope.launch {
                        routeResults = fetchDirections(departure.text, destination.text)
                        isSearching = false
                    }
                }
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                isSearching = true
                coroutineScope.launch {
                    routeResults = fetchDirections(departure.text, destination.text)
                    isSearching = false
                }
            },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(text = "경로 검색")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isSearching) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (routeResults.isNotEmpty()) {
            Text(text = "검색 결과:", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            for (result in routeResults) {
                RouteSearchResultItem(route = result)
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 검색 결과 아래에 구글 맵 표시
            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxHeight(0.8f)
            ) { map ->
                map.getMapAsync { gMap ->
                    googleMap = gMap
                    googleMap?.let { map ->
                        if (currentLocation != null) {
                            val currentLatLng = LatLng(currentLocation!!.latitude, currentLocation!!.longitude)
                            map.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 16f))
                            map.addMarker(MarkerOptions().position(currentLatLng).title("현재 위치"))
                        }

                        // 기존 도착지 마커가 있다면 제거
                        destinationMarker?.remove()

                        // 도착지 마커 추가
                        destinationLocation?.let {
                            destinationMarker = map.addMarker(MarkerOptions().position(it).title("도착지"))
                        }
                    }
                }
            }
        } else if (!isSearching) {
            Text(text = "검색 결과가 없습니다.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// 위치 정보를 가져오는 suspend 함수
@SuppressLint("MissingPermission")
suspend fun getCurrentLocation(context: Context): Location? {
    val fusedLocationProviderClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    return suspendCoroutine { continuation ->
        fusedLocationProviderClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                continuation.resume(location)
            }
            .addOnFailureListener { exception ->
                continuation.resumeWithException(exception)
            }
    }
}

// 현재 위치의 위도, 경도로 주소를 변환하는 함수
fun getAddressFromLocation(context: Context, latitude: Double, longitude: Double): String {
    val geocoder = Geocoder(context)
    val addresses = geocoder.getFromLocation(latitude, longitude, 1)

    // addresses가 null이 아니고 비어있지 않으면
    return if (addresses?.isNotEmpty() == true) {
        addresses[0]?.getAddressLine(0) ?: "주소를 찾을 수 없습니다."
    } else {
        "주소를 찾을 수 없습니다."
    }
}

// Directions API를 호출하여 경로 정보를 가져오는 함수
suspend fun fetchDirections(departure: String, destination: String): List<String> {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val url = "https://maps.googleapis.com/maps/api/directions/json?" +
                    "origin=$departure&destination=$destination&mode=transit&transit_mode=bus&language=ko&key=AIzaSyA-XxR0OPZoPTA9-TxDyqQVqaRt9EOa-Eg"
            Log.d("Google Directions API", "URL: $url")

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val jsonData = response.body?.string()
            Log.d("Google Directions API", "Response: $jsonData")

            val routeList = mutableListOf<String>()

            if (jsonData != null) {
                val jsonObject = JSONObject(jsonData)
                val routes = jsonObject.getJSONArray("routes")

                if (routes.length() > 0) {
                    val legs = routes.getJSONObject(0).getJSONArray("legs")
                    val steps = legs.getJSONObject(0).getJSONArray("steps")

                    for (i in 0 until steps.length()) {
                        val step = steps.getJSONObject(i)
                        val instruction = step.getString("html_instructions")
                        val distance = step.getJSONObject("distance").getString("text")

                        if (step.has("transit_details")) {
                            val transitDetails = step.getJSONObject("transit_details")
                            val line = transitDetails.getJSONObject("line")
                            val busNumber = line.getString("short_name")
                            val departureStop = transitDetails.getJSONObject("departure_stop").getString("name")
                            val arrivalStop = transitDetails.getJSONObject("arrival_stop").getString("name")

                            routeList.add("$instruction - $distance\n버스: $busNumber, 출발 정류장: $departureStop, 도착 정류장: $arrivalStop")
                        } else {
                            routeList.add("$instruction - $distance")
                        }
                    }
                } else {
                    Log.e("Google Directions API", "No routes found.")
                }
            }
            routeList
        } catch (e: Exception) {
            Log.e("Google Directions API", "Error fetching directions: ${e.message}")
            emptyList()
        }
    }
}

// 주소를 위도, 경도로 변환하는 함수
suspend fun geocodeAddress(context: Context, address: String): LatLng? {
    val geocoder = Geocoder(context)
    return try {
        val addresses = geocoder.getFromLocationName(address, 1)
        if (addresses != null && addresses.isNotEmpty()) {
            val location = addresses[0]
            LatLng(location.latitude, location.longitude)
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}

fun parseRouteDetails(route: String): Triple<String, String, String?> {
    val lines = route.split("\n")
    val instruction = lines.getOrNull(0) ?: "경로 설명 없음"
    val distance = lines.getOrNull(1) ?: "거리 정보 없음"
    val busDetails = lines.getOrNull(2)

    return Triple(instruction, distance, busDetails)
}

@Composable
fun RouteSearchResultItem(route: String) {
    val (instruction, distance, busDetails) = parseRouteDetails(route)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 경로 지침
            Text(
                text = instruction,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 거리 정보
            Text(
                text = "거리: $distance",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // 버스 정보 (있으면 표시)
            if (!busDetails.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "버스 정보: $busDetails",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
