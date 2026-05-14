package com.styset.sales.app.ui

import android.view.View
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.styset.sales.app.PostAdapter
import com.styset.sales.app.data.network.NetworkClient
import com.styset.sales.app.databinding.ActivityApiBinding
import com.styset.sales.app.domain.repository.PostRepository
import com.styset.sales.app.models.Post
import com.styset.sales.app.models.Resource
import com.styset.sales.app.presentation.viewmodel.PostViewModel
import com.styset.sales.app.presentation.viewmodel.base.BaseActivity

class ApiActivity : BaseActivity<ActivityApiBinding>() {

    private val viewModel: PostViewModel by lazy {
        val apiService = NetworkClient.apiService
        val repository = PostRepository(apiService)
        PostViewModel(repository)
    }

    private lateinit var adapter: PostAdapter

    override fun getViewBinding(): ActivityApiBinding {
        return ActivityApiBinding.inflate(layoutInflater)
    }

    // Remove this entire override - BaseActivity already handles setting content view
    // override fun onCreate(savedInstanceState: Bundle?) {
    //     super.onCreate(savedInstanceState)
    //     enableEdgeToEdge()
    //     setContentView(R.layout.activity_api)  // ← THIS IS THE PROBLEM
    // }

    // Change order: setupViews should be called before setupObservers
    override fun setupViews() {
        // Initialize RecyclerView FIRST
        adapter = PostAdapter { post ->
            Toast.makeText(this, "Clicked: ${post.title}", Toast.LENGTH_SHORT).show()
        }

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@ApiActivity)
            adapter = this@ApiActivity.adapter
        }
    }

    override fun setupObservers() {
        // Now observers will run after adapter is initialized
        observeStateFlow(viewModel.postsState) { resource ->
            handlePostsResource(resource)
        }

        observeStateFlow(viewModel.createPostState) { resource ->
            handleCreatePostResource(resource)
        }
    }

    override fun setupListeners() {
        binding.btnFetchPosts.setOnClickListener {
            viewModel.loadPosts()
        }

        binding.btnRefresh.setOnClickListener {
            viewModel.refreshPosts()
        }

        binding.btnCreatePost.setOnClickListener {
            val title = binding.etTitle.text.toString()
            val body = binding.etBody.text.toString()

            if (title.isNotBlank() && body.isNotBlank()) {
                viewModel.createPost(title, body)
            } else {
                Toast.makeText(this, "Please enter title and body", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun loadData() {
        // Auto-load posts when activity opens
        viewModel.loadPosts()
    }

    private fun handlePostsResource(resource: Resource<List<Post>>?) {
        when (resource) {
            is Resource.Loading -> {
                showLoading(true)
                binding.tvErrorMessage.visibility = View.GONE
            }
            is Resource.Success -> {
                showLoading(false)
                resource.data?.let { posts ->
                    if (posts.isEmpty()) {
                        showEmptyState()
                    } else {
                        // Adapter is now guaranteed to be initialized
                        if (::adapter.isInitialized) {
                            adapter.submitList(posts)
                            binding.recyclerView.visibility = View.VISIBLE
                            binding.tvErrorMessage.visibility = View.GONE
                        }
                    }
                }
            }
            is Resource.Error -> {
                showLoading(false)
                showError(resource.message ?: "An error occurred")
            }
            null -> {
                // Initial state - do nothing
            }

            else -> {}
        }
    }

    private fun handleCreatePostResource(resource: Resource<Post>?) {
//        when (resource) {
//            is Resource.Loading -> {
//                binding.progressBarCreate.visibility = android.view.View.VISIBLE
//                binding.btnCreatePost.isEnabled = false
//            }
//            is Resource.Success -> {
//                binding.progressBarCreate.visibility = android.view.View.GONE
//                binding.btnCreatePost.isEnabled = true
//                Toast.makeText(this, "Post created: ${resource.data?.title}", Toast.LENGTH_SHORT).show()
//                binding.etTitle.text?.clear()
//                binding.etBody.text?.clear()
//                viewModel.refreshPosts()
//            }
//            is Resource.Error -> {
//                binding.progressBarCreate.visibility = android.view.View.GONE
//                binding.btnCreatePost.isEnabled = true
//                Toast.makeText(this, resource.message, Toast.LENGTH_LONG).show()
//            }
//            null -> {
//                // Initial state
//            }
//        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun showError(message: String) {
        binding.tvErrorMessage.text = message
        binding.tvErrorMessage.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.GONE
    }

    private fun showEmptyState() {
        binding.tvErrorMessage.text = "No posts available"
        binding.tvErrorMessage.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.GONE
    }
}