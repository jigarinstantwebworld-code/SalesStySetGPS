package com.example.salesstysetgps.domain.repository

import com.example.salesstysetgps.models.Post
import com.example.salesstysetgps.models.Resource
import kotlinx.coroutines.flow.Flow

interface IPostRepository {
    fun getPosts(): Flow<Resource<List<Post>>>
    fun createPost(title: String, body: String, userId: Int): Flow<Resource<Post>>
}