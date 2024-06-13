package com.example.weather

import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.karumi.dexter.Dexter
import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.location.Location
import android.net.Uri
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AlertDialog
import com.example.weather.models.WeatherResponse
import com.example.weather.network.WeatherService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import retrofit.*


class MainActivity : AppCompatActivity() {

    private lateinit var mFusedLocationClient : FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (!isLocationEnabled()) {
            Toast.makeText(this, "Please enable location services", Toast.LENGTH_SHORT).show()

            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }else{
            Toast.makeText(this, "Location services enabled", Toast.LENGTH_SHORT).show()

            Dexter.withActivity(this)
                .withPermissions(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                .withListener(object : MultiplePermissionsListener{
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if (report!!.areAllPermissionsGranted()){
                            requestLocationData()
                        }

                        if(report.isAnyPermissionPermanentlyDenied){
                            Toast.makeText(
                                this@MainActivity,
                                "You have denied location permission. Please enable them as it is mandatory for the app to work",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permission: MutableList<PermissionRequest>?,
                        token: PermissionToken?
                    ){
                        showRationalDialogForPermissions()
                    }
                }).onSameThread()
                .check()
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData(){
        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest, mLocationCallback,
            Looper.myLooper(),

        )
    }

    private val mLocationCallback = object : LocationCallback(){
        override fun onLocationResult(locationResultIt: LocationResult){
            val mLastLocation: Location = locationResultIt.lastLocation
            val latitude = mLastLocation.latitude
            Log.i("Current Latitude", "$latitude")

            val longitude = mLastLocation.longitude
            Log.i("Current Longitude", "$longitude")
            getLocationWeatherDetails(latitude, longitude)
        }
    }

    private fun getLocationWeatherDetails(latitude: Double, longitude: Double){
        if(Constants.isNetworkAvailable(this)){
            Toast.makeText(
                this@MainActivity,
                "You are connected to the internet",
                Toast.LENGTH_SHORT
                ).show()

            val retrofit : Retrofit = Retrofit.Builder().baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create()).build()

            val service : WeatherService = retrofit.create<WeatherService>(WeatherService::class.java)

            val listCall: Call<WeatherResponse> = service.getWeather(
                latitude, longitude, Constants.METRIC_UNIT, Constants.APP_ID
            )

            listCall.enqueue(object : Callback<WeatherResponse>{
                override fun onFailure(t: Throwable?) {

                    Log.e("Error", t!!.message.toString())

                }

                override fun onResponse(response: Response<WeatherResponse>?, retrofit: Retrofit) {

                    if(response!!.isSuccess){
                        val weatherList: WeatherResponse = response.body()
                        Log.i("Response Result", "$weatherList")
                    }else{
                        val rc = response.code()
                        when(rc){
                            400 -> {
                                Log.e("Error 400", "Bad Connection")
                            }
                            404 -> {
                                Log.e("Error 404", "Not Found")
                        }else ->{
                            Log.e("Error", "Generic Error")

                        }                        }
                    }
                }

            })
        }else{
            Toast.makeText(
                this@MainActivity,
                "No internet connection available",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    private fun showRationalDialogForPermissions(){
        AlertDialog.Builder(this)
            .setMessage("It looks like you have turned off permission")
            .setPositiveButton("Go to Settings"){
                _,_ ->
                try{
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                }catch (e: ActivityNotFoundException){
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel"){dialog,
                                       _ ->
                dialog.dismiss()
            }.show()
    }

    private fun isLocationEnabled(): Boolean {

        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)  ||
        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
}