package com.example.geupjo_bus.map

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.example.geupjo_bus.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.content.Intent
import android.net.Uri


class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<*>
    private var busStopName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        // Intent에서 정류장 이름 가져오기
        busStopName = intent.getStringExtra("BUS_STOP_NAME")

        // 지도 프래그먼트 초기화
        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // BottomSheet 초기화
        val bottomSheet = findViewById<LinearLayout>(R.id.bottomSheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)

        // BottomSheet 내용 설정
        val busStopNameTextView = findViewById<TextView>(R.id.busStopNameTextView)
        busStopNameTextView.text = busStopName ?: "알 수 없는 정류장"

        // 경로 안내 버튼
        val directionButton = findViewById<Button>(R.id.directionButton)
        directionButton.setOnClickListener {
            // 경로 안내 로직 추가 (예: Intent로 외부 네비게이션 앱 호출)
            startDirections(busStopName)
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        // 예시 좌표 (서울 시청 위치) - 실제 좌표는 API에서 가져와야 함
        val exampleLocation = LatLng(37.5665, 126.9780)

        // 마커 추가
        map.addMarker(
            MarkerOptions()
                .position(exampleLocation)
                .title(busStopName)
        )

        // 카메라를 선택한 정류장 위치로 이동
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(exampleLocation, 15f))

        // 지도 옵션 설정 (예: 줌 버튼 활성화)
        map.uiSettings.isZoomControlsEnabled = true
    }

    private fun startDirections(busStopName: String?) {
        // 실제 경로 안내 로직 (외부 지도 앱 호출 예제)
        busStopName?.let {
            val gmmIntentUri = Uri.parse("google.navigation:q=$it")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            mapIntent.setPackage("com.google.android.apps.maps")
            startActivity(mapIntent)
        }
    }
}
