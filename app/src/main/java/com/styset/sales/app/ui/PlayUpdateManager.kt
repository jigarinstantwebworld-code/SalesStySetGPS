package com.styset.sales.app.ui

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.google.android.play.core.install.InstallState
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.styset.sales.app.R
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

class PlayUpdateManager(
    private val activity: Activity,
    private val lifecycleScope: CoroutineScope,
    private val updateCard: MaterialCardView
) {

    companion object {
        private const val REQUEST_CODE_UPDATE = 1001
        private const val UPDATE_CHECK_INTERVAL = 8 * 60 * 60 * 1000L // 8 hours
    }

    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(activity)
    private var updateJob: Job? = null
    private var currentUpdateInfo: AppUpdateInfo? = null
    private var isUpdateInProgress = false

    private val installStateListener = InstallStateUpdatedListener { state ->
        handleInstallState(state)
    }

    init {
        appUpdateManager.registerListener(installStateListener)
    }

    fun startPeriodicCheck() {
        updateJob = lifecycleScope.launch {
            while (isActive) {
                checkForUpdate()
                delay(UPDATE_CHECK_INTERVAL)
            }
        }
    }

    fun checkForUpdate() {
        lifecycleScope.launch {
            checkForUpdateAsync()
        }
    }

    private suspend fun checkForUpdateAsync() = withContext(Dispatchers.IO) {
        try {
            val appUpdateInfoTask = appUpdateManager.appUpdateInfo
            appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
                currentUpdateInfo = appUpdateInfo

                val hasUpdate = appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                        && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)

                if (hasUpdate) {
                    activity.runOnUiThread {
                        showUpdateAvailable(appUpdateInfo)
                    }
                }
            }.addOnFailureListener { exception ->
                exception.printStackTrace()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showUpdateAvailable(appUpdateInfo: AppUpdateInfo) {
        val updatePriority = appUpdateInfo.updatePriority()
        val isUpdateRequired = updatePriority >= 3 // High priority update

        updateCard.visibility = android.view.View.VISIBLE
        val tvTitle = updateCard.findViewById<TextView>(R.id.tvUpdateTitle)
        val tvMessage = updateCard.findViewById<TextView>(R.id.tvUpdateMessage)
        val btnCancel = updateCard.findViewById<MaterialButton>(R.id.btnCancelUpdate)
        val btnUpdate = updateCard.findViewById<MaterialButton>(R.id.btnUpdate)

        tvTitle.text = "Update Available"
        tvMessage.text = if (isUpdateRequired) {
            "Critical update available. Please update to continue using the app."
        } else {
            "A new version is available with improvements and bug fixes."
        }

        if (isUpdateRequired) {
            btnCancel.visibility = android.view.View.GONE
        } else {
            btnCancel.visibility = android.view.View.VISIBLE
            btnCancel.setOnClickListener { hideUpdateCard() }
        }

        btnUpdate.setOnClickListener { startUpdate(appUpdateInfo, isUpdateRequired) }
    }

    private fun startUpdate(appUpdateInfo: AppUpdateInfo, isRequired: Boolean) {
        if (isUpdateInProgress) return

        isUpdateInProgress = true
        showProgressUI()

        try {
            // Create AppUpdateOptions
            val options = AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE)
                .build()

            // Start update flow
            appUpdateManager.startUpdateFlowForResult(
                appUpdateInfo,
                activity,
                options,
                REQUEST_CODE_UPDATE
            )
        } catch (e: IntentSender.SendIntentException) {
            e.printStackTrace()
            isUpdateInProgress = false
            hideUpdateCard()
            showError("Update failed: ${e.message}")
        }
    }

    private fun handleInstallState(state: InstallState) {
        when (state.installStatus()) {
            InstallStatus.DOWNLOADING -> {
                val bytesDownloaded = state.bytesDownloaded()
                val totalBytes = state.totalBytesToDownload()
                val progress = if (totalBytes > 0) {
                    ((bytesDownloaded * 100) / totalBytes).toInt()
                } else {
                    0
                }
                updateProgress(progress)
            }

            InstallStatus.DOWNLOADED -> {
                isUpdateInProgress = false
                showRestartButton()
            }

            InstallStatus.INSTALLED -> {
                isUpdateInProgress = false
                hideUpdateCard()
            }

            InstallStatus.FAILED -> {
                isUpdateInProgress = false
                val errorCode = state.installErrorCode()
                showError("Update failed with error code: $errorCode")
                resetUI()
            }

            InstallStatus.CANCELED -> {
                isUpdateInProgress = false
                hideUpdateCard()
            }
        }
    }

    fun completeUpdate() {
        if (isUpdateInProgress) return

        val appUpdateInfoTask = appUpdateManager.appUpdateInfo
        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                appUpdateManager.completeUpdate()
            }
        }
    }

    fun restartAndInstall() {
        completeUpdate()
    }

    fun cancelUpdate() {
        isUpdateInProgress = false
        hideUpdateCard()
    }

    private fun showProgressUI() {
        val progressSection = updateCard.findViewById<LinearLayout>(R.id.progressSection)
        val btnUpdate = updateCard.findViewById<MaterialButton>(R.id.btnUpdate)
        val btnCancel = updateCard.findViewById<MaterialButton>(R.id.btnCancelUpdate)
        val tvMessage = updateCard.findViewById<TextView>(R.id.tvUpdateMessage)

        progressSection.visibility = android.view.View.VISIBLE
        btnUpdate.text = "Downloading..."
        btnUpdate.isEnabled = false
        btnCancel.text = "Cancel"
        btnCancel.setOnClickListener { cancelUpdate() }
        tvMessage.text = "Downloading update in background..."

        updateProgress(0)
    }

    private fun updateProgress(progress: Int) {
        val progressBar = updateCard.findViewById<ProgressBar>(R.id.updateProgressBar)
        val tvProgressPercent = updateCard.findViewById<TextView>(R.id.tvProgressPercent)

        progressBar.progress = progress
        tvProgressPercent.text = "Downloading: $progress%"
    }

    private fun showRestartButton() {
        val btnRestart = updateCard.findViewById<MaterialButton>(R.id.btnRestart)
        val btnUpdate = updateCard.findViewById<MaterialButton>(R.id.btnUpdate)
        val btnCancel = updateCard.findViewById<MaterialButton>(R.id.btnCancelUpdate)
        val progressSection = updateCard.findViewById<LinearLayout>(R.id.progressSection)
        val tvMessage = updateCard.findViewById<TextView>(R.id.tvUpdateMessage)

        progressSection.visibility = android.view.View.GONE
        btnUpdate.visibility = android.view.View.GONE
        btnRestart.visibility = android.view.View.VISIBLE
        btnRestart.setOnClickListener { restartAndInstall() }
        btnCancel.text = "Later"
        btnCancel.setOnClickListener { hideUpdateCard() }

        tvMessage.text = "Update downloaded! Tap Restart to install."
    }

    private fun resetUI() {
        val progressSection = updateCard.findViewById<LinearLayout>(R.id.progressSection)
        val btnUpdate = updateCard.findViewById<MaterialButton>(R.id.btnUpdate)
        val btnCancel = updateCard.findViewById<MaterialButton>(R.id.btnCancelUpdate)
        val tvError = updateCard.findViewById<TextView>(R.id.tvError)

        progressSection.visibility = android.view.View.GONE
        btnUpdate.isEnabled = true
        btnUpdate.text = "Retry Update"
        btnCancel.text = "Cancel"
        tvError.visibility = android.view.View.VISIBLE

        lifecycleScope.launch {
            delay(5000)
            tvError.visibility = android.view.View.GONE
        }
    }

    private fun showError(message: String) {
        val tvError = updateCard.findViewById<TextView>(R.id.tvError)
        tvError.text = message
        tvError.visibility = android.view.View.VISIBLE
    }

    private fun hideUpdateCard() {
        updateCard.visibility = android.view.View.GONE
        resetUI()
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_UPDATE) {
            if (resultCode != Activity.RESULT_OK) {
                isUpdateInProgress = false
                showError("Update was cancelled or failed")
                hideUpdateCard()
            }
        }
    }

    fun onResume() {
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo
        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                showRestartButton()
                updateCard.visibility = android.view.View.VISIBLE
            } else if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                isUpdateInProgress = true
                showProgressUI()
                updateCard.visibility = android.view.View.VISIBLE
            }
        }
    }

    fun onDestroy() {
        updateJob?.cancel()
        appUpdateManager.unregisterListener(installStateListener)
    }
}