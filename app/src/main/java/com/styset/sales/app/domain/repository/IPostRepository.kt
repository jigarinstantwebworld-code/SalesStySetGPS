package com.styset.sales.app.domain.repository

import com.styset.sales.app.models.Post
import com.styset.sales.app.models.Resource
import kotlinx.coroutines.flow.Flow

interface IPostRepository {
    fun getPosts(): Flow<Resource<List<Post>>>
    fun createPost(title: String, body: String, userId: Int): Flow<Resource<Post>>
}