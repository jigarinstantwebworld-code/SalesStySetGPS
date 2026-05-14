package com.styset.sales.app.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.styset.sales.app.models.AttendanceResponse
import com.styset.sales.app.models.Resource
import com.styset.sales.app.repository.TripRepository
import com.styset.sales.app.util.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class AttendanceViewModel(
    app: Application, private val tripRepository: TripRepository,
    private val preferenceManager: PreferenceManager
) : AndroidViewModel(app) {

    private val _loginState = MutableStateFlow<Resource<AttendanceResponse>?>(null)
    val loginState: StateFlow<Resource<AttendanceResponse>?> = _loginState.asStateFlow()

    private val _breakInState = MutableStateFlow<Resource<AttendanceResponse>?>(null)
    val breakInState: StateFlow<Resource<AttendanceResponse>?> = _breakInState.asStateFlow()

    private val _breakOutState = MutableStateFlow<Resource<AttendanceResponse>?>(null)
    val breakOutState: StateFlow<Resource<AttendanceResponse>?> = _breakOutState.asStateFlow()

    private val _logoutState = MutableStateFlow<Resource<AttendanceResponse>?>(null)
    val logoutState: StateFlow<Resource<AttendanceResponse>?> = _logoutState.asStateFlow()
    fun loginWithApi(
        latitude: Double,
        longitude: Double,
        createdBy: String = "system",
        onSuccess: (message: String) -> Unit = {},
        onError: (errorMessage: String) -> Unit = {}
    ) {
        viewModelScope.launch {
            tripRepository.manageAttendance("LOGIN", latitude, longitude, createdBy)
                .catch { exception ->
                    val errorMsg = exception.message ?: "Unknown error"
                    _loginState.value = Resource.Error(errorMsg)
                    onError(errorMsg)
                }
                .collect { resource ->
                    _loginState.value = resource

                    when (resource) {
                        is Resource.Success -> {
                            if (resource.data?.success == 1) {
                                // Save login info if needed
                                val msg = resource.data?.message ?: "Login Successfully...."
                                onSuccess(msg)
                            } else {
                                val errorMsg = resource.data?.message ?: "Failed to login"
                                onError(errorMsg)
                            }
                        }

                        is Resource.Error -> {
                            onError(resource.message ?: "An error occurred")
                        }

                        else -> {
                            /* Loading state */
                        }
                    }
                }
        }
    }

    fun breakInWithApi(
        latitude: Double,
        longitude: Double,
        onSuccess: (message: String) -> Unit = {},
        onError: (errorMessage: String) -> Unit = {}
    ) {
        viewModelScope.launch {
            tripRepository.manageAttendance("BREAK_IN", latitude, longitude)
                .catch { exception ->
                    val errorMsg = exception.message ?: "Unknown error"
                    _breakInState.value = Resource.Error(errorMsg)
                    onError(errorMsg)
                }
                .collect { resource ->
                    _breakInState.value = resource

                    when (resource) {
                        is Resource.Success -> {
                            if (resource.data?.success == 1) {
                                val msg = resource.data?.message ?: "Break In Successfullyy...."
                                onSuccess(msg)
                            } else {
                                val errorMsg = resource.data?.message ?: "Failed to start break"
                                onError(errorMsg)
                            }
                        }

                        is Resource.Error -> {
                            onError(resource.message ?: "An error occurred")
                        }

                        else -> { /* Loading state */
                        }
                    }
                }
        }
    }

    fun breakOutWithApi(
        latitude: Double,
        longitude: Double,
        onSuccess: (message: String) -> Unit = {},
        onError: (errorMessage: String) -> Unit = {}
    ) {
        viewModelScope.launch {
            tripRepository.manageAttendance("BREAK_OUT", latitude, longitude)
                .catch { exception ->
                    val errorMsg = exception.message ?: "Unknown error"
                    _breakOutState.value = Resource.Error(errorMsg)
                    onError(errorMsg)
                }
                .collect { resource ->
                    _breakOutState.value = resource

                    when (resource) {
                        is Resource.Success -> {
                            if (resource.data?.success == 1) {
                                val msg = resource.data?.message ?: "Break out successfully..."
                                onSuccess(msg)
                            } else {
                                val errorMsg = resource.data?.message ?: "Failed to end break"
                                onError(errorMsg)
                            }
                        }

                        is Resource.Error -> {
                            onError(resource.message ?: "An error occurred")
                        }

                        else -> { /* Loading state */
                        }
                    }
                }
        }
    }

    fun logoutWithApi(
        latitude: Double,
        longitude: Double,
        onSuccess: (message: String) -> Unit = {},
        onError: (errorMessage: String) -> Unit = {}
    ) {
        viewModelScope.launch {
            tripRepository.manageAttendance("LOGOUT", latitude, longitude)
                .catch { exception ->
                    val errorMsg = exception.message ?: "Unknown error"
                    _logoutState.value = Resource.Error(errorMsg)
                    onError(errorMsg)
                }
                .collect { resource ->
                    _logoutState.value = resource

                    when (resource) {
                        is Resource.Success -> {
                            if (resource.data?.success == 1) {
                                val apiMessage = resource.data.message ?: "Logout successful"
                                onSuccess(apiMessage)
                            } else {
                                val errorMsg = resource.data?.message ?: "Failed to logout"
                                onError(errorMsg)
                            }
                        }

                        is Resource.Error -> {
                            onError(resource.message ?: "An error occurred")
                        }

                        else -> { /* Loading state */
                        }
                    }
                }
        }
    }

    fun clearLoginState() {
        _loginState.value = null
    }

    fun clearBreakInState() {
        _breakInState.value = null
    }

    fun clearBreakOutState() {
        _breakOutState.value = null
    }

    fun clearLogoutState() {
        _logoutState.value = null
    }
}