package com.styset.sales.app.presentation.viewmodel

// LoginViewModel.kt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.styset.sales.app.domain.repository.LoginRepository
import com.styset.sales.app.models.LoginResponse
import com.styset.sales.app.models.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(
    private val loginRepository: LoginRepository
) : ViewModel() {

    private val _loginState = MutableStateFlow<Resource<LoginResponse>>(Resource.Loading())
    val loginState: StateFlow<Resource<LoginResponse>> = _loginState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun login(
        email: String,
        password: String,
        onSuccess: (LoginResponse) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _loginState.value = Resource.Loading()

            val result = loginRepository.login(email, password)

            _loginState.value = result
            _isLoading.value = false

            when (result) {
                is Resource.Success -> {
                    result.data?.let { response ->
                        if (response.status == "1") {
                            onSuccess(response)
                        } else {
                            onError(response.message ?: "Login failed")
                        }
                    } ?: onError("No data received")
                }
                is Resource.Error -> {
                    onError(result.message ?: "An error occurred")
                }
                else -> {}
            }
        }
    }
}

class LoginViewModelFactory(
    private val loginRepository: LoginRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LoginViewModel(loginRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}