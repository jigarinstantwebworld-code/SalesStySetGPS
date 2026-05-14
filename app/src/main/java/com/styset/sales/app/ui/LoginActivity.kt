package com.styset.sales.app.ui

// LoginActivity.kt

import android.content.Intent
import android.util.Patterns
import android.view.View
import androidx.lifecycle.ViewModelProvider
import com.styset.sales.app.data.network.NetworkClient
import com.styset.sales.app.databinding.ActivityLoginBinding
import com.styset.sales.app.domain.repository.LoginRepository
import com.styset.sales.app.models.Resource
import com.styset.sales.app.presentation.viewmodel.LoginViewModel
import com.styset.sales.app.presentation.viewmodel.LoginViewModelFactory
import com.styset.sales.app.presentation.viewmodel.base.BaseActivity
import com.styset.sales.app.util.PreferenceManager
import com.styset.sales.app.util.SnackbarUtils
import java.security.MessageDigest

class LoginActivity : BaseActivity<ActivityLoginBinding>() {

    private lateinit var viewModel: LoginViewModel
    private lateinit var preferenceManager: PreferenceManager

    override fun getViewBinding(): ActivityLoginBinding =
        ActivityLoginBinding.inflate(layoutInflater)


    override fun setupViews() {
        // Any additional view setup if needed
        val apiService = NetworkClient.apiService
        preferenceManager = PreferenceManager(this)
        val loginRepository = LoginRepository(apiService, preferenceManager)
        val viewModelFactory = LoginViewModelFactory(loginRepository)
        viewModel = ViewModelProvider(this, viewModelFactory)[LoginViewModel::class.java]
        if (preferenceManager.isLoggedInMain()) {
            navigateToMainActivity()
            return
        }

    }

    override fun setupObservers() {
        observeStateFlow(viewModel.isLoading) { isLoading ->
            if (isLoading) {
                binding.progressBar.visibility = View.VISIBLE
                binding.btnLogin.isEnabled = false
            } else {
                binding.progressBar.visibility = View.GONE
                binding.btnLogin.isEnabled = true
            }
        }

        observeStateFlow(viewModel.loginState) { resource ->
            when (resource) {
                is Resource.Success -> {
                    resource.data?.let { response ->
                        if (response.status == "1") {
                            showSuccessSnackbar(response.message)
                            navigateToMainActivity()
                        } else {
                            showErrorSnackbar(response.message ?: "Login failed")
                        }
                    }
                }

                is Resource.Error -> {
                    showErrorSnackbar(resource.message ?: "An error occurred")
                }

                else -> {}
            }
        }
    }

    override fun setupListeners() {
        binding.btnLogin.setOnClickListener {
            performLogin()
//            navigateToMainActivity()
        }
    }

    private fun setupViewModel() {
        val apiService = NetworkClient.apiService
        val loginRepository = LoginRepository(apiService, preferenceManager)
        val viewModelFactory = LoginViewModelFactory(loginRepository)
        viewModel = ViewModelProvider(this, viewModelFactory)[LoginViewModel::class.java]
    }

    private fun performLogin() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        // Validate inputs
        if (!validateInputs(email, password)) {
            return
        }

        // Encrypt password using SHA-1
        val encryptedPassword = getSha1Hex(password)

        if (encryptedPassword == null) {
            showErrorSnackbar("Failed to encrypt password")
            return
        }

        // Call login API
        viewModel.login(
            email = email,
            password = encryptedPassword,
            onSuccess = { response ->
                // Already handled in observer
            },
            onError = { error ->
                showErrorSnackbar(error)
            }
        )
    }

    private fun validateInputs(email: String, password: String): Boolean {
        if (email.isEmpty()) {
            binding.etEmail.requestFocus()
            showErrorSnackbar("Email is required")
            return false
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showErrorSnackbar("Please enter a valid email address")
            binding.etEmail.requestFocus()
            return false
        }

        if (password.isEmpty()) {
            showErrorSnackbar("Password is required")
            binding.etPassword.requestFocus()
            return false
        }

        if (password.length < 6) {
            showErrorSnackbar("Password must be at least 6 characters")
            binding.etPassword.requestFocus()
            return false
        }

        return true
    }

    private fun getSha1Hex(pwd: String): String? {
        return try {
            val messageDigest = MessageDigest.getInstance("SHA-1")
            messageDigest.update(pwd.toByteArray(charset("UTF-8")))
            val bytes = messageDigest.digest()
            val buffer = StringBuilder()
            for (b in bytes) {
                buffer.append(String.format("%02X", b))
            }
            buffer.toString().lowercase() // Convert to lowercase for consistency
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showSuccessSnackbar(message: String) {
        SnackbarUtils.showSuccess(binding.root, message, true)
    }

    private fun showErrorSnackbar(message: String) {
        SnackbarUtils.showError(binding.root, message, true)
    }


}