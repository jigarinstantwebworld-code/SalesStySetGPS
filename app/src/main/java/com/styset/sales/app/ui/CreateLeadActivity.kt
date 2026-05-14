package com.styset.sales.app.ui


import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import android.Manifest
import android.app.AlertDialog
import android.location.Address
import android.location.Geocoder
import android.os.Build

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.styset.sales.app.R
import com.styset.sales.app.data.api.ApiService
import com.styset.sales.app.data.network.NetworkClient
import com.styset.sales.app.domain.repository.LeadRepository
import com.styset.sales.app.models.CreateLeadRequest
import com.styset.sales.app.presentation.viewmodel.LeadViewModel
import com.styset.sales.app.presentation.viewmodel.LeadViewModelFactory
import com.styset.sales.app.presentation.viewmodel.base.BaseActivity
import com.styset.sales.app.util.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import com.styset.sales.app.BuildConfig
import com.styset.sales.app.databinding.ActivityCreateLeadBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
import kotlin.getValue

class CreateLeadActivity : BaseActivity<ActivityCreateLeadBinding>() {

    private lateinit var preferenceManager: PreferenceManager
    private var currentLatitude: Double = 0.0
    private var currentLongitude: Double = 0.0


    private var uploadCall: Call? = null

    lateinit var fileMain: File

    private lateinit var currentPhotoPath: String
    private var currentCompressedFile: File? = null

    private var capturedImageUri: Uri? = null
    private var compressedImageFile: File? = null
    private lateinit var apiService: ApiService
    private var selectedSellingType: String = "a1"
    private var sellerImage: String = ""

    private val viewModel: LeadViewModel by viewModels {
        val apiService = NetworkClient.apiService
        val preferenceManager = PreferenceManager.Companion.getInstance(this)
        val leadRepository = LeadRepository(apiService, preferenceManager)
        LeadViewModelFactory(leadRepository)
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openCamera()
        } else {
            showErrorSnackbar("Camera permission is required to capture visiting card image")
        }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            capturedImageUri?.let { uri ->
                val file = File(currentPhotoPath)

                if (!file.exists()) {
                    Log.e("Camera", "File not found: ${file.absolutePath}")
                    showErrorSnackbar("Failed to capture image")
                    return@let
                }

                binding.progressBar.visibility = View.VISIBLE
                binding.ivImagePreview.visibility = View.VISIBLE
                binding.btnCreateLead.isEnabled = false
                showInfoSnackbar("Getting upload URL...")
                binding.ivImagePreview.setImageURI(uri)

                val originalFile = copyUriToTempFile(this, uri)

                val compressedFile = compressImageFromFile(
                    context = this,
                    inputFile = originalFile,
                    quality = 70
                )
                logImageSize("Original", file)

                fileMain = compressedFile

                // Compress image
                lifecycleScope.launch {
                    compressedImageFile = withContext(Dispatchers.IO) {
                        compressImageFromFile(this@CreateLeadActivity, file, 70)
                    }

                    val compressedFile = compressImageFromFile(
                        context = this@CreateLeadActivity,
                        inputFile = file,
                        quality = 70
                    )
                    fileMain = compressImageFromFile(this@CreateLeadActivity, file, 70)
                    val folderName = "input"
                    val objectKey = "$folderName/${preferenceManager.getSalesExecutiveId()}/${compressedFile.name}"

                    getPresignedUrlAndUpload(BuildConfig.BUCKET_NAME,compressedFile.nameWithoutExtension,objectKey)
                }
            }
        } else {
            showErrorSnackbar("Failed to capture image")
            binding.progressBar.visibility = View.GONE
        }
    }

    fun copyUriToTempFile(context: Context, uri: Uri): File {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Unable to open URI")

        val fileName = "original_${System.currentTimeMillis()}.jpg"
        val tempFile = File(context.cacheDir, fileName)

        FileOutputStream(tempFile).use { output ->
            inputStream.copyTo(output)
        }

        inputStream.close()
        return tempFile
    }

    private fun getPresignedUrlAndUpload(bucketName : String,fieldLabel : String,objectKey :String) {
        lifecycleScope.launch {
            try {
                viewModel.getPresignedUrl(bucketName,fieldLabel,objectKey,{ response ->
                    // This is PresignedUrlResponse object, not separate parameters
                    val presignedUrl = response.presignedUrl
                    val objectKey = response.objectKey

                    Log.d("TAG", "Presigned URL: $presignedUrl")
                    Log.d("TAG", "Object Key: $objectKey")
                    uploadFileToS3(response.presignedUrl, fileMain)
                })

            } catch (e: Exception) {
                Log.e("TAG", "Error getting presigned URL", e)
                showErrorSnackbar("Error: ${e.message}")
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    fun uploadFileToS3(presignedUrl: String, file: File) {
        val client = OkHttpClient()

        // Create a RequestBody with the file to upload
        val requestBody = file.asRequestBody("application/octet-stream".toMediaTypeOrNull())

        // Create the PUT request to the pre-signed URL
        val request = Request.Builder()
            .url(presignedUrl)
            .addHeader("Content-Type", "application/json")
            .put(requestBody)
            .build()


        uploadCall = client.newCall(request)

        // Execute the request
        uploadCall?.enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (call.isCanceled()) return // ✅ ignore cancel
                val responseBodyString = response.body?.string().orEmpty()
                this@CreateLeadActivity.runOnUiThread {
                    if (response.isSuccessful) {
                        binding.btnCreateLead.isEnabled = true
                        Log.i(
                            "Upload", """
                                                            File uploaded successfully.
                                                            Code: ${response.code}
                                                            Message: ${response.message}
                                                            Headers: ${response.headers}
                                                            Body: $responseBodyString
                                                        """.trimIndent()
                        )
                        val signedUrl = response.request.url.toString()

                        val fullUrl = signedUrl

                        val cleanUrl = fullUrl.substringBefore("?")
                        sellerImage = cleanUrl
                        println("Signed URL: $cleanUrl")
                        binding.progressBar.visibility = View.GONE
                        showSuccessSnackbar("Image uploaded successfully")
                    } else {
                        binding.progressBar.visibility = View.GONE
                        binding.btnCreateLead.isEnabled = true
                        Log.e("Upload", "File upload failed: ${response.message}")
                        Log.e("Upload", "File upload failed: ${response.code}")
                        Log.e("Upload", "File upload failed: ${response.body}")
                        showErrorSnackbar("Upload failed: ${response.code}")
                    }
                }

            }

            override fun onFailure(call: Call, e: IOException) {
                if (call.isCanceled()) {
                    Log.d("Upload", "Upload cancelled by user")
                    return // ✅ VERY IMPORTANT
                }

                Log.e("Upload", "Failed to upload file", e)
            }
        })

    }


    override fun getViewBinding(): ActivityCreateLeadBinding =
        ActivityCreateLeadBinding.inflate(layoutInflater)

    override fun setupViews() {
        currentLatitude = intent.getDoubleExtra("latitude", 0.0)
        currentLongitude = intent.getDoubleExtra("longitude", 0.0)

        setupToolbar()
        setupMobileNumberValidation()
        setupSellingTypeRadioGroup()
        setCurrentDate()
        getAddressFromLatLng(this,currentLatitude,currentLongitude)
        setupDatePicker()
        apiService = NetworkClient.apiService
        preferenceManager = PreferenceManager(this)
        setupVisitingCardImageClick()
    }

    private fun checkCameraPermissionAndOpen() {
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                openCamera()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun setupVisitingCardImageClick() {
        binding.etVisitingCardImage.setOnClickListener {
            checkCameraPermissionAndOpen()
        }

        // Make it non-editable
        binding.etVisitingCardImage.isFocusable = false
        binding.etVisitingCardImage.isCursorVisible = false
        binding.etVisitingCardImage.keyListener = null
    }

    private fun openCamera() {
        try {
            val photoFile = createImageFile()
            photoFile?.let {
                currentPhotoPath = it.absolutePath
                val uri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    it
                )
                capturedImageUri = uri
                takePictureLauncher.launch(uri)
            }
        } catch (e: Exception) {
            showErrorSnackbar("Error opening camera: ${e.message}")
        }
    }

    private fun createImageFile(): File? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "visiting_card_${timeStamp}_"
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            imageFileName,
            ".jpg",
            storageDir
        ).apply {
            currentPhotoPath = absolutePath
        }
    }

    private fun showImagePreview(imagePath: String) {
        val bitmap = BitmapFactory.decodeFile(imagePath)
        binding.ivImagePreview.visibility = View.VISIBLE
        binding.ivImagePreview.setImageBitmap(bitmap)

        // Optional: Add click to enlarge preview
        binding.ivImagePreview.setOnClickListener {
            // Show full screen image dialog
            showFullScreenImage(imagePath)
        }
    }

    private fun showFullScreenImage(imagePath: String) {
        // Simple dialog to show enlarged image
        val dialog = AlertDialog.Builder(this)
            .setTitle("Visiting Card")
            .setMessage("Tap outside to close")
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .create()

        val imageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setImageBitmap(BitmapFactory.decodeFile(imagePath))
            scaleType = ImageView.ScaleType.FIT_CENTER
            setOnClickListener { dialog.dismiss() }
        }

        dialog.setView(imageView)
        dialog.show()
    }

    fun compressImageFromFile(
        context: Context,
        inputFile: File,
        quality: Int
    ): File {
        val bitmap = BitmapFactory.decodeFile(inputFile.absolutePath)

        val compressedFile = File(
            context.cacheDir,
            "compressed_${System.currentTimeMillis()}.jpg"
        )

        FileOutputStream(compressedFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos)
        }

        return compressedFile
    }


    private fun logImageSize(tag: String, file: File) {
        val sizeKb = file.length() / 1024
        val sizeMb = file.length() / (1024f * 1024f)
        Log.d(
            "IMAGE_SIZE",
            "$tag -> ${file.name}, Size: ${sizeKb} KB (${String.format("%.2f", sizeMb)} MB)"
        )
    }


    private fun setCurrentDate() {
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        binding.etDate.setText(currentDate)
    }


    override fun setupListeners() {
        binding.btnCreateLead.setOnClickListener {
            createLead()
        }
    }

    private fun setupDatePicker() {
        binding.etDate.isFocusable = false
        binding.etDate.isCursorVisible = false
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Create New Lead"

        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupMobileNumberValidation() {
        binding.etMobileNumber.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Ensure +91 is always at the beginning
                if (!s.toString().startsWith("+91")) {
                    binding.etMobileNumber.removeTextChangedListener(this)
                    binding.etMobileNumber.setText("+91")
                    binding.etMobileNumber.text?.let { binding.etMobileNumber.setSelection(it.length) }
                    binding.etMobileNumber.addTextChangedListener(this)
                }
            }


            override fun afterTextChanged(s: Editable?) {
                // Validate 10 digits after +91
                val number = s.toString()
                if (number.length > 13) {
                    binding.etMobileNumber.setText(number.substring(0, 13))
                    binding.etMobileNumber.setSelection(13)
                }

                val digitsAfterCode = number.replace("+91", "")
                if (digitsAfterCode.length == 10) {
                    binding.etMobileNumber.error = null
                } else if (digitsAfterCode.isNotEmpty()) {
                    binding.etMobileNumber.error = "Please enter 10 digits after +91"
                }
            }
        })

        // Set initial +91
        if (binding.etMobileNumber.text.toString().isEmpty()) {
            binding.etMobileNumber.setText("+91")
        }
    }

    private fun setupSellingTypeRadioGroup() {
        binding.rgSellingType.setOnCheckedChangeListener { _, checkedId ->
            selectedSellingType = when (checkedId) {
                binding.rbA1.id -> "a1"
                binding.rbA2.id -> "a2"
                binding.rbA3.id -> "a3"
                binding.rbA4.id -> "a4"
                else -> ""
            }
        }
        // Set default selection
//        binding.rbA1.isChecked = true
    }

    private fun validateEmail(email: String): Boolean {
        // More strict email pattern
        val emailPattern = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
        )
        return emailPattern.matcher(email).matches()
    }

    private fun validateMobileNumber(mobileNumber: String): Boolean {
        val digitsOnly = mobileNumber.replace("+91", "")
        return digitsOnly.length == 10 && digitsOnly.all { it.isDigit() }
    }

    private fun createLead() {
        // Validate required fields
        if (!validateInputs()) {
            return
        }

//        val executiveName = preferenceManager.getSalesExecutiveName() ?: "Unknown"
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        if (preferenceManager.getSalesExecutiveId()== null){
            showErrorSnackbar("Sales Executive ID not found. Please login first.")
            return
        }
        val mobileNumber = binding.etMobileNumber.text.toString().replace("+91", "")

        val demoValue = when (binding.rgDemo.checkedRadioButtonId) {
            R.id.rbYes -> "YES"
            R.id.rbNo -> "NO"
            else -> "" // or handle validation
        }
        val request = CreateLeadRequest(
            currentLocation = binding.etCurrentLocation.text.toString().trim(),
            date = binding.etDate.text.toString().trim().ifEmpty { currentDate },
            executiveName = binding.etExecutiveName.text.toString().trim(),
            area = binding.etArea.text.toString().trim(),
            sellingType = selectedSellingType,
            demo = demoValue,
            nameOfShop = binding.etNameOfShop.text.toString().trim(),
            contactPerson = binding.etContactPerson.text.toString().trim(),
            mobileNumber = mobileNumber,
            emailId = binding.etEmailId.text.toString().trim(),
            remark = binding.etRemark.text.toString().trim(),
            sellerAddress = binding.etSellerAddress.text.toString().trim(),
            visitingCardImage = sellerImage,
            onboardingClientName = binding.etOnboardingClientName.text.toString().trim(),
            paidAmount = binding.etPaidAmount.text.toString().trim().toIntOrNull() ?: 0,
            notes = binding.etNotes.text.toString().trim(),
            status = "ACTIVE",
            createdBy = preferenceManager.getSalesExecutiveId().toString(),
            salesExecutiveId = preferenceManager.getSalesExecutiveId().toString()
        )

        // Show loading
        binding.progressBar.visibility = View.VISIBLE
        binding.btnCreateLead.isEnabled = false

        lifecycleScope.launch {
            try {
                val response = apiService.createSellLead(request)

                if (response.success == 1) {
                    showSuccessSnackbar(response.message)
                    setResult(RESULT_OK)
                    finish()
                } else {
                    Toast.makeText(this@CreateLeadActivity, response.message, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@CreateLeadActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.btnCreateLead.isEnabled = true
            }
        }
    }

    private fun validateInputs(): Boolean {
        var isValid = true
        var firstErrorField: View? = null

        // Validate Name of Shop
        if (binding.etNameOfShop.text.toString().trim().isEmpty()) {
//            binding.etNameOfShop.error = "Shop name is required"
            isValid = false
            firstErrorField = binding.etNameOfShop
        } else {
            binding.etNameOfShop.error = null
        }

        // Validate Contact Person
        if (binding.etContactPerson.text.toString().trim().isEmpty()) {
//            binding.etContactPerson.error = "Contact person is required"
            isValid = false
            if (firstErrorField == null) firstErrorField = binding.etContactPerson
        } else {
            binding.etContactPerson.error = null
        }

        // Validate Mobile Number
        val mobileNumber = binding.etMobileNumber.text.toString()
        if (!validateMobileNumber(mobileNumber)) {
//            binding.etMobileNumber.error = "Please enter valid 10-digit mobile number"
            isValid = false
            if (firstErrorField == null) firstErrorField = binding.etMobileNumber
        } else {
            binding.etMobileNumber.error = null
        }

        // Validate Email
        val email = binding.etEmailId.text.toString().trim()
        if (email.isNotEmpty() && !validateEmail(email)) {
//            binding.etEmailId.error = "Please enter valid email address"
            isValid = false
            if (firstErrorField == null) firstErrorField = binding.etEmailId
        } else {
            binding.etEmailId.error = null
        }

        // Validate Area
        if (binding.etArea.text.toString().trim().isEmpty()) {
//            binding.etArea.error = "Area is required"
            isValid = false
            if (firstErrorField == null) firstErrorField = binding.etArea
        } else {
            binding.etArea.error = null
        }

        // Validate Selling Type
        if (selectedSellingType.isEmpty()) {
            showErrorSnackbar("Please select selling type")
            isValid = false
        }

        if (binding.etExecutiveName.text.toString().trim().isEmpty()) {
            isValid = false
            if (firstErrorField == null) firstErrorField = binding.etExecutiveName
        }else {
            binding.etExecutiveName.error = null
        }

//        if (sellerImage.isEmpty()){
//            showErrorSnackbar("Please select seller Image")
//            isValid = false
//        }

        // Show error snackbar for first validation error
        if (!isValid && firstErrorField != null) {
            val errorMessage = when (firstErrorField) {
                binding.etNameOfShop -> "Shop name is required"
                binding.etContactPerson -> "Contact person is required"
                binding.etMobileNumber -> "Please enter valid 10-digit mobile number"
                binding.etEmailId -> "Please enter valid email address"
                binding.etArea -> "Area is required"
                binding.etExecutiveName -> "ExecutiveName is required"
//                binding.etArea -> "Seller Image is required"
                else -> "Please fill all required fields"
            }
            showErrorSnackbar(errorMessage)

            // Request focus on the first error field
            firstErrorField.requestFocus()
        }

        return isValid
    }




    fun getAddressFromLatLng(context: Context, latitude: Double, longitude: Double) {
        val geocoder = Geocoder(context, Locale.ENGLISH)

        if (Build.VERSION.SDK_INT >= 33) {
            // Asynchronous method for API 33+
            geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) {
                    if (addresses.isNotEmpty()) {
                        val address = addresses[0]
                        val fullAddress = address.getAddressLine(0)
                        runOnUiThread {
                            binding.etCurrentLocation.setText(fullAddress)
                        }
                        // Update UI on main thread
                        // runOnUiThread { updateUI(fullAddress) }
                    }
                }

                override fun onError(errorMessage: String?) {
                    // Handle error
                }
            })
        } else {
            // Fallback for older APIs
            Thread {
                try {
                    val addresses = geocoder.getFromLocation(latitude, longitude, 1)

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }.start()
        }
    }


    private fun showSnackbar(
        message: String,
        duration: Int = Snackbar.LENGTH_SHORT,
        type: SnackbarType = SnackbarType.INFO
    ) {
        try {
            val snackbar = Snackbar.make(binding.root, message, duration)

            when (type) {
                SnackbarType.SUCCESS -> {
                    snackbar.setBackgroundTint(ContextCompat.getColor(this, R.color.md_theme_light_primary))
                    snackbar.setTextColor(ContextCompat.getColor(this, R.color.white))
                }
                SnackbarType.ERROR -> {
                    snackbar.setBackgroundTint(ContextCompat.getColor(this, R.color.md_theme_light_error))
                    snackbar.setTextColor(ContextCompat.getColor(this, R.color.white))
                    snackbar.setAction("Dismiss") { snackbar.dismiss() }
                    snackbar.setActionTextColor(ContextCompat.getColor(this, R.color.white))
                }
                SnackbarType.WARNING -> {
                    snackbar.setBackgroundTint(ContextCompat.getColor(this, R.color.snackbar_warning_bg))
                    snackbar.setTextColor(ContextCompat.getColor(this, R.color.snackbar_text_color))
                }
                SnackbarType.INFO -> {
                    snackbar.setBackgroundTint(ContextCompat.getColor(this, R.color.snackbar_info_bg))
                    snackbar.setTextColor(ContextCompat.getColor(this, R.color.snackbar_text_color))
                }
            }

            snackbar.show()
        } catch (e: Exception) {
            // Fallback to Toast if CoordinatorLayout is not available
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSuccessSnackbar(message: String) {
        showSnackbar(message, Snackbar.LENGTH_SHORT, SnackbarType.SUCCESS)
    }

    private fun showErrorSnackbar(message: String) {
        showSnackbar(message, Snackbar.LENGTH_LONG, SnackbarType.ERROR)
    }

    private fun showWarningSnackbar(message: String) {
        showSnackbar(message, Snackbar.LENGTH_LONG, SnackbarType.WARNING)
    }

    private fun showInfoSnackbar(message: String) {
        showSnackbar(message, Snackbar.LENGTH_SHORT, SnackbarType.INFO)
    }

    enum class SnackbarType {
        SUCCESS, ERROR, WARNING, INFO
    }



    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}