package com.bullshitman.bullshituberclone.ui.home

import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.bullshitman.bullshituberclone.Common
import com.bullshitman.bullshituberclone.R
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener

class HomeFragment : Fragment(), OnMapReadyCallback {

  private lateinit var mMap: GoogleMap
  private lateinit var homeViewModel: HomeViewModel
  private lateinit var mapFragment: SupportMapFragment

  //Location
  private lateinit var locationRequest: LocationRequest
  private lateinit var locationCallback: LocationCallback
  private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

  //Online system
  private lateinit var onlineRef: DatabaseReference
  private lateinit var currentUserRef: DatabaseReference
  private lateinit var driversLocationRef: DatabaseReference
  private lateinit var geoFire: GeoFire

  private val onlineValueEventListener = object:ValueEventListener {
    override fun onCancelled(p0: DatabaseError) {
      Snackbar.make(mapFragment.requireView(), p0.message, Snackbar.LENGTH_LONG).show()
    }

    override fun onDataChange(p0: DataSnapshot) {
      if (p0.exists()) {
        currentUserRef.onDisconnect().removeValue()
      }
    }
  }


  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)
    val root = inflater.inflate(R.layout.fragment_home, container, false)
    init()
    mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
    mapFragment.getMapAsync(this)
    return root
  }

  override fun onDestroy() {
//    geoFire.removeLocation(FirebaseAuth.getInstance().currentUser!!.uid)
    onlineRef.removeEventListener(onlineValueEventListener)
    fusedLocationProviderClient.removeLocationUpdates(locationCallback)
    super.onDestroy()
  }

  override fun onResume() {
    super.onResume()
    registerOnlineSystem()
  }

  private fun init() {
    onlineRef = FirebaseDatabase.getInstance().reference.child(".info/connected")
    driversLocationRef = FirebaseDatabase.getInstance().getReference(Common.DRIVER_LOCATION_REFERENCE)
    currentUserRef = FirebaseDatabase.getInstance().getReference(Common.DRIVER_LOCATION_REFERENCE).child(FirebaseAuth.getInstance().currentUser!!.uid)
    geoFire = GeoFire(driversLocationRef)
    registerOnlineSystem()

    locationRequest = LocationRequest()
    locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    locationRequest.fastestInterval = 3000
    locationRequest.interval = 5000
    locationRequest.smallestDisplacement = 10f

    locationCallback = object: LocationCallback() {
      override fun onLocationResult(locationResult: LocationResult?) {
        super.onLocationResult(locationResult)

        val newPos = LatLng(locationResult!!.lastLocation.latitude, locationResult!!.lastLocation.longitude)
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(newPos, 18f))

        //Location update
        geoFire.setLocation(
          FirebaseAuth.getInstance().currentUser!!.uid,
          GeoLocation(locationResult.lastLocation.latitude, locationResult.lastLocation.longitude)
        ) {key: String?, error: DatabaseError? ->
            if (error != null) {
              Snackbar.make(mapFragment.requireView(), error.message, Snackbar.LENGTH_LONG).show()
            } else {
              Snackbar.make(mapFragment.requireView(), "ONLINE!", Snackbar.LENGTH_LONG).show()
            }
        }
      }
    }
    fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireContext())
    fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper())
  }

  private fun registerOnlineSystem() {
    onlineRef.addValueEventListener(onlineValueEventListener)
  }

  override fun onMapReady(googleMap: GoogleMap?) {
    mMap = googleMap!!
    //Requesting permissions

    Dexter.withContext(context!!)
      .withPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
      .withListener(object:PermissionListener {
        override fun onPermissionGranted(p0: PermissionGrantedResponse?) {
          //Enabling buttons
          mMap.isMyLocationEnabled = true
          mMap.uiSettings.isZoomControlsEnabled = true
          mMap.setOnMyLocationClickListener {
            fusedLocationProviderClient.lastLocation
              .addOnFailureListener { e ->
                Toast.makeText(context!!, e.message, Toast.LENGTH_SHORT).show()
              }
              .addOnSuccessListener { location ->
                val userLatLng = LatLng(location.latitude, location.longitude)
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 18f))
              }
          }
          //Layout changes
          val view = mapFragment.view!!.findViewById<View>("1".toInt()).parent as View
          val locationButton = view.findViewById<View>("2".toInt())
          val layoutParams = locationButton.layoutParams as RelativeLayout.LayoutParams
          layoutParams.addRule(RelativeLayout.ALIGN_TOP, 0)
          layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE)
          layoutParams.bottomMargin = 50
        }

        override fun onPermissionRationaleShouldBeShown(
          p0: PermissionRequest?,
          p1: PermissionToken?
        ) {
        }

        override fun onPermissionDenied(p0: PermissionDeniedResponse?) {
          Toast.makeText(
            context!!,
            "Permission ${p0!!.permissionName} was denied.",
            Toast.LENGTH_SHORT
          ).show()
        }
      }).check()

    try {
        val success = googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(context, R.raw.uber_maps_style))
      if (!success) {
        Log.e("BullShitError", "Cannot parse style")
      }
    } catch (e: Exception) {
      Log.e("BullShitError", "${e.message}")
    }
  }
}