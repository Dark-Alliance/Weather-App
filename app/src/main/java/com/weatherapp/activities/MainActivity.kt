package com.weatherapp.activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.location.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.weatherapp.R
import com.weatherapp.models.WeatherResponse
import com.weatherapp.network.WeatherService
import com.weatherapp.utils.Constants
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory

// OpenWeather Link : https://openweathermap.org/api
/**
 * The useful link or some more explanation for this app you can checkout this link :
 * https://medium.com/@sasude9/basic-android-weather-app-6a7c0855caf4
 */
class MainActivity : AppCompatActivity() {

    // A fused location client variable which is further user to get the user's current location
    private lateinit var mFusedLocationClient: FusedLocationProviderClient

    // TODO (STEP 4: Create a global variable for ProgressDialog.)
    // A global variable for the Progress Dialog
    private var mProgressDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize the Fused location variable
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (!isLocationEnabled()) {
            Toast.makeText(
                this,
                "Your location provider is turned off. Please turn it on.",
                Toast.LENGTH_SHORT
            ).show()

            // This will redirect you to settings from where you need to turn on the location provider.
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } else {
            Dexter.withActivity(this)
                .withPermissions(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                .withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if (report!!.areAllPermissionsGranted()) {
                            requestLocationData()
                        }

                        if (report.isAnyPermissionPermanentlyDenied) {
                            Toast.makeText(
                                this@MainActivity,
                                "You have denied location permission. Please allow it is mandatory.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: MutableList<PermissionRequest>?,
                        token: PermissionToken?
                    ) {
                        showRationalDialogForPermissions()
                    }
                }).onSameThread()
                .check()
        }
    }

    /**
     * A function which is used to verify that the location or GPS is enable or not of the user's device.
     */
    private fun isLocationEnabled(): Boolean {

        // This provides access to the system location services.
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    /**
     * A function used to show the alert dialog when the permissions are denied and need to allow it from settings app info.
     */
    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this)
            .setMessage("It Looks like you have turned off permissions required for this feature. It can be enabled under Application Settings")
            .setPositiveButton(
                "GO TO SETTINGS"
            ) { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel") { dialog,
                                           _ ->
                dialog.dismiss()
            }.show()
    }

    /**
     * A function to request the current location. Using the fused location provider client.
     */
    @SuppressLint("MissingPermission")
    private fun requestLocationData() {

        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest, mLocationCallBack,
            Looper.myLooper()
        )
    }

    /**
     * A location callback object of fused location provider client where we will get the current location details.
     */
    private val mLocationCallBack = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location? = locationResult.lastLocation
            val latitude = mLastLocation?.latitude
            Log.i("Current Latitude", "$latitude")

            val longitude = mLastLocation?.longitude
            Log.i("Current Longitude", "$longitude")

            getLocationWeatherDetails(latitude!!, longitude!!)

        }


        /**
         * Function is used to get the weather details of the current location based on the latitude longitude
         */
        private fun getLocationWeatherDetails(latitude: Double, longitude: Double) {

            if (Constants.isNetworkAvailable(this@MainActivity)) {

                /**
                 * Add the built-in converter factory first. This prevents overriding its
                 * behavior but also ensures correct behavior when using converters that consume all types.
                 */
                val retrofit: Retrofit = Retrofit.Builder()
                    // API base URL.
                    .baseUrl(Constants.BASE_URL)
                    /** Add converter factory for serialization and deserialization of objects. */
                    /**
                     * Create an instance using a default {@link Gson} instance for conversion. Encoding to JSON and
                     * decoding from JSON (when no charset is specified by a header) will use UTF-8.
                     */
                    .addConverterFactory(GsonConverterFactory.create())
                    /** Create the Retrofit instances. */
                    .build()

                /**
                 * Here we map the service interface in which we declares the end point and the API type
                 *i.e GET, POST and so on along with the request parameter which are required.
                 */
                val service: WeatherService =
                    retrofit.create(WeatherService::class.java)

                /** An invocation of a Retrofit method that sends a request to a web-server and returns a response.
                 * Here we pass the required param in the service
                 */
                val listCall: Call<WeatherResponse> = service.getWeather(
                    latitude, longitude, Constants.METRIC_UNIT, Constants.APP_ID
                )

                // TODO (STEP 6: Show the progress dialog)
                // START
                showCustomProgressDialog() // Used to show the progress dialog
                // END

                // Callback methods are executed using the Retrofit callback executor.
                listCall.enqueue(object : Callback<WeatherResponse> {
                    @SuppressLint("SetTextI18n")
                    override fun onResponse(
                        call: Call<WeatherResponse>,
                        response: Response<WeatherResponse>
                    ) {
                        // Check weather the response is success or not.
                        if (response.isSuccessful) {

                            // TODO (STEP 7: Hide the progress dialog)
                            // START
                            hideProgressDialog() // Hides the progress dialog
                            // END

                            /** The de-serialized response body of a successful response. */
                            val weatherList: WeatherResponse? = response.body()
                            setupUI(weatherList!!)
                            Log.i("Response Result", "$weatherList")
                        } else {
                            // If the response is not "success" then we check the response code.
                            when (response.code()) {
                                400 -> {
                                    Log.e("Error 400", "Bad Request")
                                }
                                404 -> {
                                    Log.e("Error 404", "Not Found")
                                }
                                else -> {
                                    Log.e("Error", "Generic Error")
                                }
                            }
                        }

                    }

                    override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                        hideProgressDialog() // Hides the progress dialog
                        Log.e("Error", t.message.toString())

                    }
                })
            }

        }
    }

    fun showCustomProgressDialog() {
        mProgressDialog = Dialog(this)

        /*Set the screen content from a layout resource.
        The resource will be inflated, adding all top-level views to the screen.*/
        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)

        //Start the dialog and display it on screen.
        mProgressDialog!!.show()
    }

    private fun hideProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog!!.dismiss()
        }
    }

    private fun setupUI(weatherList: WeatherResponse){
        for(i in weatherList.weather.indices){
            Log.i("Weather Name", weatherList.weather.toString())
        }
    }
}
// END
