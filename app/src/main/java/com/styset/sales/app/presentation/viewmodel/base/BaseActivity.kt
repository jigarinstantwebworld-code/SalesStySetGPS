package com.styset.sales.app.presentation.viewmodel.base

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewbinding.ViewBinding
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch



abstract class BaseActivity<VB : ViewBinding> : AppCompatActivity() {

    private var _binding: VB? = null
    protected val binding get() = _binding!!

    abstract fun getViewBinding(): VB

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = getViewBinding()
        setContentView(binding.root)

        setupViews()
        setupObservers()
        setupListeners()
        loadData()
    }

    protected open fun setupViews() {}
    protected open fun setupObservers() {}
    protected open fun setupListeners() {}
    protected open fun loadData() {}

    protected fun <T> observeStateFlow(
        flow: StateFlow<T>,
        minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
        onCollect: (T) -> Unit
    ) {
        lifecycleScope.launch {
            repeatOnLifecycle(minActiveState) {
                flow.collect { value ->
                    onCollect(value)
                }
            }
        }
    }

    protected fun <T> observeSharedFlow(
        flow: SharedFlow<T>,
        minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
        onCollect: (T) -> Unit
    ) {
        lifecycleScope.launch {
            repeatOnLifecycle(minActiveState) {
                flow.collect { value ->
                    onCollect(value)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}