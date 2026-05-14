package com.styset.sales.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.styset.sales.app.domain.repository.IPostRepository
import com.styset.sales.app.models.Post
import com.styset.sales.app.models.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class PostViewModel(
    private val repository: IPostRepository
) : ViewModel() {

    private val _postsState = MutableStateFlow<Resource<List<Post>>?>(null)
    val postsState: StateFlow<Resource<List<Post>>?> = _postsState.asStateFlow()

    private val _createPostState = MutableStateFlow<Resource<Post>?>(null)
    val createPostState: StateFlow<Resource<Post>?> = _createPostState.asStateFlow()

    // Track if initial load has been done
    private var isInitialLoadDone = false

    fun loadPosts(forceRefresh: Boolean = false) {
        // Only load if not loaded yet or force refresh
        if (!isInitialLoadDone || forceRefresh) {
            isInitialLoadDone = true

            viewModelScope.launch {
                repository.getPosts()
                    .catch { exception ->
                        _postsState.value = Resource.Error(
                            exception.message ?: "Unknown error occurred"
                        )
                    }
                    .collect { resource ->
                        _postsState.value = resource
                    }
            }
        }
    }

    fun createPost(title: String, body: String) {
        viewModelScope.launch {
            repository.createPost(title, body, 1)
                .catch { exception ->
                    _createPostState.value = Resource.Error(
                        exception.message ?: "Failed to create post"
                    )
                }
                .collect { resource ->
                    _createPostState.value = resource
                }
        }
    }

    fun refreshPosts() {
        loadPosts(forceRefresh = true)
    }

    fun resetCreatePostState() {
        _createPostState.value = null
    }
}