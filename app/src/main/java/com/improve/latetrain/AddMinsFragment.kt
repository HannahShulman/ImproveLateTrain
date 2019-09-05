package com.improve.latetrain

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.location.LocationProvider
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Spinner
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.airbnb.lottie.LottieAnimationView
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import com.google.firebase.database.*
import kotlinx.android.synthetic.main.fragment_add_mins.*
import java.io.File
import java.security.Provider
import kotlin.math.absoluteValue


class AddMinsFragment : Fragment() {
    private val TAG = "ADD_MIN_FRAG_TAG"
    private val LAST_CLICK = "LAST_CLICK"
    private val LOCATION_PERMISSION_GIVEN = "LOCATION_PERMISSION_GIVEN"
    private val REQUEST_CHECK_SETTINGS = 0x1
    private val MY_PERMISSIONS_REQUEST_LOCATION = 0x2
    private val IS_PERMISSION_REQUEST_GRANTED = "IS_PERMISSION_REQUEST_GRANTED"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?):
            View? = inflater.inflate(R.layout.fragment_add_mins, container, false)



    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (animation_layout as LottieAnimationView).playAnimation()

        activity?.let {
            sharedPreferences = it.getSharedPreferences("com.improve.latetrain", Context.MODE_PRIVATE)
        }

        addMinBtn.setOnClickListener {
            val lastTime = sharedPreferences.getLong(LAST_CLICK, 0)
            Log.d(TAG, lastTime.toString())
            var is30MinPass = true
            if (lastTime + 1800 > System.currentTimeMillis() / 1000) {
                is30MinPass = false
                val builder = AlertDialog.Builder(context)
                builder.setTitle(getString(R.string.wait_addmins))
                builder.setMessage(getString(R.string.once_in_30_addmins))
                builder.setPositiveButton(getString(R.string.got_it_addmins)) { _, _ -> }
                val dialog: AlertDialog = builder.create()
                dialog.show()
                return@setOnClickListener
            }

            var minutes = 0
            if (minLateEt.selectedItem.toString() != "") {
                minutes = minLateEt.selectedItem.toString().toInt()
            }
            val isDestinationSelected: Boolean =
                !(destinationStationSp.selectedItem.toString().contains(getString(R.string.where_are_you_going_addmins)))
            var isCurrentStationSelected: Boolean =
                !(currentStationSp.selectedItem.toString().contains(getString(R.string.wich_station_addmins)))
            var isNotSame = !destinationStationSp.selectedItem.toString().equals(currentStationSp.selectedItem.toString())

            if (current_station_location_fam.visibility==View.VISIBLE) {
                isNotSame = !destinationStationSp.selectedItem.toString()
                        .equals(current_station_location_fam.text.toString())
                isCurrentStationSelected = true
            }

            if (minutes > 0 && isDestinationSelected && isCurrentStationSelected && is30MinPass && isNotSame) {
                totalWaitingPath.runTransaction(object : Transaction.Handler {
                    override fun onComplete(p0: DatabaseError?, p1: Boolean, p2: DataSnapshot?) {}

                    override fun doTransaction(mutableData: MutableData): Transaction.Result {
                        sharedPreferences.edit()?.putLong(LAST_CLICK, System.currentTimeMillis() / 1000)?.apply()
                        if (mutableData.getValue(Int::class.java) == 0) {
                            mutableData.value = minutes
                            return Transaction.success(mutableData)
                        }
                        val data = mutableData.getValue(Int::class.java)
                        mutableData.value = data?.plus(minutes)
                        return Transaction.success(mutableData)
                    }
                })
            } else {
                Toast.makeText(context, "נא למלא את המידע הנדרש!", Toast.LENGTH_SHORT).show()
            }
        }
        //Spinners
        val adapterCurrentLocations = SpinnerAdapter(
            context ?: return,
            R.layout.spinner_dropdown_layout,
            resources.getStringArray(R.array.stations).toList()
        )
        val listOfStations = adapterCurrentLocations.items as ArrayList<String>
        listOfStations[0] = "באיזו תחנה הינך?"
        adapterCurrentLocations.items = listOfStations
        currentStationSp.adapter = adapterCurrentLocations

        val adapterLocations = SpinnerAdapter(
            context ?: return,
            R.layout.spinner_dropdown_layout,
            resources.getStringArray(R.array.stations).toList()
        )
        destinationStationSp.adapter = adapterLocations

        val minutesList = arrayListOf<String>().also {
            for (i in 0..60)
                it.add("$i")
        }

        val adapterMinutes = SpinnerAdapter(
            context ?: return,
            R.layout.spinner_dropdown_layout,
            minutesList
        )
        minLateEt.adapter = adapterMinutes
        try {
            val popup = Spinner::class.java.getDeclaredField("mPopup")
            popup.isAccessible = true
            val popupWindow = popup.get(minLateEt) as android.widget.ListPopupWindow
            popupWindow.height = 500
        } catch (e: NoClassDefFoundError) {
        } catch (e: ClassCastException) {
        } catch (e: NoSuchFieldException) {
        } catch (e: IllegalAccessException) { }

        stationsList = mutableMapOf()
        val textStations = activity?.assets?.open("trainstationscoordinates.txt")?.bufferedReader()
        textStations?.forEachLine { line ->
            val lineList = line.split(":")
            val longiNlatti = lineList[1].split(",")
            val location = Location(LocationManager.GPS_PROVIDER)
            location.longitude = longiNlatti[1].toDouble()
            location.latitude = longiNlatti[0].toDouble()
            stationsList.put(lineList[0], location)
        }

        activity?.let {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(it.baseContext)
            if(sharedPreferences.getBoolean(IS_PERMISSION_REQUEST_GRANTED, true))
            {
                if (ContextCompat.checkSelfPermission(it.baseContext, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED)
                {
                    requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION),
                        MY_PERMISSIONS_REQUEST_LOCATION)
                }
                else
                    setLocationRequests()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun setLocationRequests() {
        val locationRequest = LocationRequest.create().apply {
            interval = 1000
            fastestInterval = 1000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val client: SettingsClient = LocationServices.getSettingsClient(context?:return)
        val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())
        task.addOnSuccessListener {
            sharedPreferences.edit()?.putInt(LOCATION_PERMISSION_GIVEN, 1)?.apply()
        }
        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try {
                    exception.startResolutionForResult(activity, REQUEST_CHECK_SETTINGS)
                }
                catch (sendEx: IntentSender.SendIntentException) { }
            }
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                if (locationResult != null) {
                    val lastLocationRecieved = locationResult.lastLocation
                    val result = FloatArray(1)
                    var isInStation = false
                    for (i in stationsList.keys) {
                        Location.distanceBetween(
                            lastLocationRecieved.latitude, lastLocationRecieved.longitude,
                            stationsList[i]?.latitude ?: 0.0, stationsList[i]?.longitude ?: 0.0, result
                        )
                        if (result[0].absoluteValue < 500) {
                            current_station_location_fam.visibility = View.VISIBLE
                            currentStationSp.visibility = View.GONE
                            current_station_location_fam.text = i
                            isInStation = true
                            break
                        }
                    }
                    if (!isInStation)
                    {
                        current_station_location_fam.visibility = View.GONE
                        currentStationSp.visibility = View.VISIBLE
                    }
                }
            }
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)

    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        Log.d(TAG, "5")
        when(requestCode)
        {
            MY_PERMISSIONS_REQUEST_LOCATION -> {
                if (grantResults.isNotEmpty() && grantResults[0]==PackageManager.PERMISSION_GRANTED)
                {
                    setLocationRequests()
                    sharedPreferences.edit().putBoolean(IS_PERMISSION_REQUEST_GRANTED, true).apply()
                }
                else
                    sharedPreferences.edit().putBoolean(IS_PERMISSION_REQUEST_GRANTED, false).apply()
            }
        }
    }

    lateinit var stationsList: MutableMap<String, Location>
    lateinit var sharedPreferences: SharedPreferences
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val instance = FirebaseDatabase.getInstance()
    private val totalWaitingPath = instance.getReference(FirebaseInfo.TOTAL_TIME_PATH)
    private lateinit var locationCallback: LocationCallback

    companion object {
        @JvmStatic
        fun newInstance() = AddMinsFragment()
    }
}
