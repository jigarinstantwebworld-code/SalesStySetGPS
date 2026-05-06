package com.example.salesstysetgps.domain.repository

import com.example.salesstysetgps.data.api.ApiService
import com.example.salesstysetgps.models.CreatePostRequest
import com.example.salesstysetgps.models.Post
import com.example.salesstysetgps.models.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

class PostRepository(
    private val apiService: ApiService
) : IPostRepository {

    override fun getPosts(): Flow<Resource<List<Post>>> = flow {
        emit(Resource.Loading())
        try {
            val posts = withContext(Dispatchers.IO) {
                apiService.getPosts()
            }
            emit(Resource.Success(posts))
        } catch (e: SocketTimeoutException) {
            emit(Resource.Error("Connection timeout. Please check your internet."))
        } catch (e: HttpException) {
            val errorMessage = when (e.code()) {
                400 -> "Bad request"
                401 -> "Unauthorized"
                403 -> "Forbidden"
                404 -> "Resource not found"
                500 -> "Server error"
                else -> "HTTP Error: ${e.code()}"
            }
            emit(Resource.Error(errorMessage))
        } catch (e: IOException) {
            emit(Resource.Error("Network error: ${e.message ?: "Please check your connection"}"))
        } catch (e: Exception) {
            emit(Resource.Error("Unexpected error: ${e.message ?: "Unknown error"}"))
        }
    }

    override fun createPost(title: String, body: String, userId: Int): Flow<Resource<Post>> = flow {
        emit(Resource.Loading())
        try {
            val request = CreatePostRequest(title, body, userId)
            val response = withContext(Dispatchers.IO) {
                apiService.createPost(request)
            }
            emit(Resource.Success(response))
        } catch (e: SocketTimeoutException) {
            emit(Resource.Error("Connection timeout. Please try again."))
        } catch (e: HttpException) {
            emit(Resource.Error("Server error: ${e.code()}"))
        } catch (e: IOException) {
            emit(Resource.Error("Network error: ${e.message}"))
        } catch (e: Exception) {
            emit(Resource.Error("Error: ${e.message}"))
        }
    }
}