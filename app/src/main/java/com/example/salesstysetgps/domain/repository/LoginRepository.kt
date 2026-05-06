package com.example.salesstysetgps.domain.repository


import com.example.salesstysetgps.data.api.ApiService
import com.example.salesstysetgps.models.LoginRequest
import com.example.salesstysetgps.models.LoginResponse
import com.example.salesstysetgps.models.Resource
import com.example.salesstysetgps.util.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.SocketTimeoutException
import retrofit2.HttpException

class LoginRepository(
    private val apiService: ApiService,
    private val preferenceManager: PreferenceManager
) {

    suspend fun login(login: String, password: String): Resource<LoginResponse> {
        return try {
            val request = LoginRequest(login, password)
            val response = withContext(Dispatchers.IO) {
                apiService.login(request)
            }

            if (response.status == "1") {
                // Save login data to preferences
                preferenceManager.saveLoginData(response)
                Resource.Success(response)
            } else {
                Resource.Error(response.message ?: "Login failed")
            }
        } catch (e: SocketTimeoutException) {
            Resource.Error("Connection timeout. Please try again.")
        } catch (e: HttpException) {
            Resource.Error(parseHttpError(e))
        } catch (e: IOException) {
            Resource.Error("Network error: ${e.message ?: "Please check your connection"}")
        } catch (e: Exception) {
            Resource.Error("Error: ${e.message ?: "Unknown error"}")
        }
    }

    private fun parseHttpError(exception: HttpException): String {
        return try {
            val errorBody = exception.response()?.errorBody()?.string()
            if (!errorBody.isNullOrEmpty()) {
                "Login failed: ${exception.code()}"
            } else {
                "Login failed: ${exception.message()}"
            }
        } catch (e: Exception) {
            "Network error occurred"
        }
    }
}