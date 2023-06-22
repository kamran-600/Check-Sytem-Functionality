package com.example.secquraise

import android.Manifest.permission.*
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.core.ImageCapture.OnImageSavedCallback
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import com.example.secquraise.databinding.ActivityMainBinding
import com.google.android.gms.location.*
import com.google.android.material.snackbar.Snackbar
import com.google.common.util.concurrent.ListenableFuture
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*


@SuppressLint("MissingPermission")
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var processCameraFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var processCameraProvider: ProcessCameraProvider
    private lateinit var imageCapture: ImageCapture
    private lateinit var preview: Preview
    private lateinit var cameraSelector: CameraSelector
    private lateinit var contentValues: ContentValues


    private lateinit var chargingReceiver: BroadcastReceiver
    private val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)

    private val requiredPermissions = arrayOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, CAMERA)

    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    private var locationHandler = Handler(Looper.getMainLooper())

    private var frequencyScheduler = Handler(Looper.getMainLooper())

    private lateinit var snackBar: Snackbar
    private var captureCount = 0
    private lateinit var imageName: String

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { map ->
            val allPermissionGranted = map.values.all { it }
            if (allPermissionGranted) {
                getCapturedImage()
                getInternetConnectivity()
                getBatteryChargingInfo()
                getLocationCoordinate()
                getTimeStamp()

            } else if (map[CAMERA] == true && (map[ACCESS_FINE_LOCATION] == false || map[ACCESS_COARSE_LOCATION] == false)) {
                getCapturedImage()
                getInternetConnectivity()
                getBatteryChargingInfo()
                binding.location.text = "null, null"
                getTimeStamp()

            } else if (map[CAMERA] == false && (map[ACCESS_FINE_LOCATION] == true || map[ACCESS_COARSE_LOCATION] == true)) {
                getInternetConnectivity()
                getBatteryChargingInfo()
                getLocationCoordinate()
                getTimeStamp()
            } else {
                getInternetConnectivity()
                getBatteryChargingInfo()
                binding.location.text = "null, null"
                getTimeStamp()
            }

        }

    private fun getTimeStamp() {

        val dateFormat = SimpleDateFormat(
            "dd/MM/yyyy hh:mm:ss",
            Locale.ENGLISH
        ).format(System.currentTimeMillis())
        binding.timeStamp.text = dateFormat
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Deprecated("")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        }

        binding.frequencyTitle.setOnClickListener{

            // gain focus
            binding.frequency.requestFocus()
            binding.frequency.text?.let { it1 -> binding.frequency.setSelection(it1.length) }

            // show the keyboard
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.frequency, InputMethodManager.SHOW_IMPLICIT)
        }

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        // it will access all required functionality
        getAllFunctionality()


        // manual refresh

        binding.refresh.setOnClickListener {

            binding.refresh.isEnabled = false
            binding.imageX.setImageURI(null)
            binding.connectivityStatus.text = ""
            binding.batteryStatus.text = ""
            binding.batteryPercentage.text = ""
            binding.location.text = ""

            frequencyScheduler.removeCallbacksAndMessages(null)
            if(binding.frequency.text?.isNotEmpty() == true && binding.frequency.text.toString().toInt() != 0){
                getAllFunctionality(binding.frequency.text.toString().toInt())
            }
            else {
                binding.frequency.setText("15")
                getAllFunctionality()
            }

        }



    }


    private fun getAllFunctionality(minutes: Int = 15) {
        val permissionsToRequest = mutableListOf<String>()
        for (permission in requiredPermissions) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(permission)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {

            getCapturedImage()

            getLocationCoordinate()

            getTimeStamp()

            getInternetConnectivity()

            getBatteryChargingInfo()

        }


        frequencyScheduler.postDelayed({

            getAllFunctionality(minutes)

        }, minutes*60*1000L )


    }

    // fetch location  167-216
    private fun getLocationCoordinate() {

        val permissionsToRequest = mutableListOf<String>()
        val locationPermissions = arrayOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)

        for (permission in locationPermissions) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(permission)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            locationPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {

            fusedLocationProviderClient.lastLocation.addOnSuccessListener { location ->
                binding.location.text = "${location?.latitude}, ${location?.longitude}"
            }

            requestLocationAfterMinutes(15)

        }


    }

    private fun requestLocationAfterMinutes(minutes: Int) {

        locationHandler.postDelayed({
            fusedLocationProviderClient.lastLocation.addOnSuccessListener { location ->
                binding.location.text = "${location?.latitude}, ${location?.longitude}"
            }
            requestLocationAfterMinutes(minutes)
        }, (minutes * 60 * 1000).toLong())
    }

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { map ->
            val allPermissionsGranted = map.values.all { it }
            if (allPermissionsGranted) {
                fusedLocationProviderClient.lastLocation.addOnSuccessListener { location ->
                    binding.location.text = "${location?.latitude}, ${location?.longitude}"
                }
                requestLocationAfterMinutes(15)
            } else binding.location.text = "null, null"
        }


    // ********INTERNET CONNECTIVITY***** 220-239
    private fun getInternetConnectivity() {
        if (isInternetConnected()) {
            binding.connectivityStatus.text = "ON"
        } else binding.connectivityStatus.text = "OFF"
    }

    private fun isInternetConnected(): Boolean {

        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = connectivityManager.activeNetwork ?: return false
        val networkCapabilities =
            connectivityManager.getNetworkCapabilities(network) ?: return false

        return networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)

    }


    // image capture 242 - 398
    private fun getCapturedImage() {
        if (ActivityCompat.checkSelfPermission(this, CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(CAMERA)
        } else {
            startCameraX()

            showTimerAndTakePhoto()
        }
    }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
            if (result) {
                startCameraX()

                showTimerAndTakePhoto()

            }
        }


    private fun startCameraX() {

        binding.preview.visibility = View.VISIBLE
        processCameraFuture = ProcessCameraProvider.getInstance(this)
        cameraSelector =
            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()


        preview = Preview.Builder().build()
        preview.setSurfaceProvider(binding.preview.surfaceProvider)

        imageCapture =
            ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()



        processCameraFuture.addListener({
            try {
                processCameraProvider = processCameraFuture.get()
                processCameraProvider.unbindAll()
                processCameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (e: Exception) {
                Toast.makeText(applicationContext, "${e.message}", Toast.LENGTH_SHORT).show()
            }


        }, mainExecutor)


        imageName =
            SimpleDateFormat("ddMMyyyy_hhmmss", Locale.ENGLISH).format(System.currentTimeMillis())
        contentValues = ContentValues()
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, imageName)
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/CameraX_Image")
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun takePhoto() {

        // Create output options object which contains file + metadata
        val outPutFileOptions = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        // Set up image capture listener, which is triggered after photo has been taken
        imageCapture.takePicture(outPutFileOptions, mainExecutor, object : OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {

                binding.refresh.isEnabled = true
                binding.preview.visibility = View.GONE
                processCameraProvider.unbindAll()
                binding.imageX.setImageURI(outputFileResults.savedUri)

                captureCount++
                binding.captureCount.text = captureCount.toString()


                val map = HashMap<String, Any>()
                map["Capture Count"] = binding.captureCount.text
                map["Frequency"] = binding.frequency.text.toString()
                map["Battery Charging"] = binding.batteryStatus.text
                map["Battery Percentage"] = binding.batteryPercentage.text
                map["Connectivity"] = binding.connectivityStatus.text
                map["Time Stamp"] = binding.timeStamp.text
                map["Longitude,Latitude"] = binding.location.text


                GlobalScope.launch {
                    val downloadUrl = uploadImageToStorage(outputFileResults.savedUri, imageName)
                    if (downloadUrl != null) {
                        map["capturedImageUrl"] = downloadUrl

                        Firebase.firestore.collection(getString(R.string.app_name))
                            .document(contentValues[MediaStore.Images.ImageColumns.DISPLAY_NAME].toString())
                            .set(map, SetOptions.merge()).await()


                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                applicationContext,
                                "Data ${map["Capture Count"]} is saved ",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(applicationContext, "Url is null", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Toast.makeText(applicationContext, exception.localizedMessage, Toast.LENGTH_SHORT)
                    .show()
            }
        })


    }

    private suspend fun uploadImageToStorage(savedUri: Uri?, imageName: String): String? {

        return withContext(Dispatchers.IO) {

            try {
                val storageReference =
                    Firebase.storage.reference.child("Captured Images/${imageName}.jpg")

                val uploadTask = savedUri?.let { storageReference.putFile(it) }
                uploadTask?.await()

                val downloadUrl = storageReference.downloadUrl.await()
                downloadUrl.toString()
            } catch (e: Exception) {
                null
            }


        }
    }

    private fun showTimerAndTakePhoto() {

        snackBar = Snackbar.make(this, binding.root, "", Snackbar.LENGTH_INDEFINITE)

        // create a countdown timer
        val timer = object : CountDownTimer(4000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                // update the snackBar text
                snackBar.setText("Image will be Captured in ${millisUntilFinished / 1000} sec")
            }

            override fun onFinish() {
                snackBar.dismiss()
                takePhoto()
            }
        }

        snackBar.show()
        timer.start()
    }


    // battery charging info (Status / batteryPercentage)
    private fun getBatteryChargingInfo() {

        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        /*
         val batteryStatus = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
         if(batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING){
             Toast.makeText(context, "is Charging", Toast.LENGTH_SHORT).show()
         }
         else if (batteryStatus == BatteryManager.BATTERY_STATUS_NOT_CHARGING) Toast.makeText(context, "is not charging", Toast.LENGTH_SHORT).show()

         val batteryPercentage = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
         binding.textView.text = "$batteryPercentage"
         */

        chargingReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING

                if (isCharging) {
                    binding.batteryStatus.text = "ON"
                } else binding.batteryStatus.text = "OFF"

                val batteryPercentage =
                    batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

                binding.batteryPercentage.text = "$batteryPercentage"

            }
        }
        registerReceiver(chargingReceiver, filter)
    }

    override fun onResume() {
        super.onResume()
        processCameraFuture = ProcessCameraProvider.getInstance(this)
        val isPreviewActive = processCameraFuture.get().isBound(preview)
        if(isPreviewActive && !binding.refresh.isEnabled){
            showTimerAndTakePhoto()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationHandler.removeCallbacksAndMessages(null)
        unregisterReceiver(chargingReceiver)
        processCameraProvider.unbindAll()
        frequencyScheduler.removeCallbacksAndMessages(null)
    }

}