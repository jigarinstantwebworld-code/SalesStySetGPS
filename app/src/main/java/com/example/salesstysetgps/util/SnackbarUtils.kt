package com.example.salesstysetgps.util

import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.example.salesstysetgps.R
import com.google.android.material.snackbar.Snackbar

object SnackbarUtils {

    enum class SnackbarType {
        SUCCESS, ERROR, WARNING, INFO
    }

    private fun getSnackbarColor(context: android.content.Context, type: SnackbarType): Int {
        return when (type) {
            SnackbarType.SUCCESS -> ContextCompat.getColor(context, R.color.md_theme_light_primary)
            SnackbarType.ERROR -> ContextCompat.getColor(context, R.color.md_theme_light_error)
            SnackbarType.WARNING -> ContextCompat.getColor(context, R.color.snackbar_warning_bg)
            SnackbarType.INFO -> ContextCompat.getColor(context, R.color.snackbar_info_bg)
        }
    }

    fun showSnackbar(
        view: View,
        message: String,
        duration: Int = Snackbar.LENGTH_SHORT,
        type: SnackbarType = SnackbarType.INFO,
        withAction: Boolean = false,
        actionText: String = "Dismiss"
    ) {
        try {
            val snackbar = Snackbar.make(view, message, duration)

            snackbar.setBackgroundTint(getSnackbarColor(view.context, type))
            snackbar.setTextColor(ContextCompat.getColor(view.context, R.color.white))

            if (withAction || type == SnackbarType.ERROR) {
                snackbar.setAction(actionText) { snackbar.dismiss() }
                snackbar.setActionTextColor(ContextCompat.getColor(view.context, R.color.white))
            }

            snackbar.show()
        } catch (e: Exception) {
            Toast.makeText(view.context, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun showSuccess(view: View, message: String, withAction: Boolean = false) {
        showSnackbar(view, message, Snackbar.LENGTH_SHORT, SnackbarType.SUCCESS, withAction)
    }

    fun showError(view: View, message: String, withAction: Boolean = true) {
        showSnackbar(view, message, Snackbar.LENGTH_LONG, SnackbarType.ERROR, withAction)
    }

    fun showWarning(view: View, message: String, withAction: Boolean = false) {
        showSnackbar(view, message, Snackbar.LENGTH_LONG, SnackbarType.WARNING, withAction)
    }

    fun showInfo(view: View, message: String, withAction: Boolean = false) {
        showSnackbar(view, message, Snackbar.LENGTH_SHORT, SnackbarType.INFO, withAction)
    }

    // With String resource
    fun showSuccess(view: View, @StringRes messageRes: Int, withAction: Boolean = false) {
        showSuccess(view, view.context.getString(messageRes), withAction)
    }

    fun showError(view: View, @StringRes messageRes: Int, withAction: Boolean = true) {
        showError(view, view.context.getString(messageRes), withAction)
    }

    fun showWarning(view: View, @StringRes messageRes: Int, withAction: Boolean = false) {
        showWarning(view, view.context.getString(messageRes), withAction)
    }

    fun showInfo(view: View, @StringRes messageRes: Int, withAction: Boolean = false) {
        showInfo(view, view.context.getString(messageRes), withAction)
    }

    // Indefinite snackbar (stays until dismissed)
    fun showIndefinite(view: View, message: String, type: SnackbarType = SnackbarType.INFO) {
        try {
            val snackbar = Snackbar.make(view, message, Snackbar.LENGTH_INDEFINITE)
            snackbar.setBackgroundTint(getSnackbarColor(view.context, type))
            snackbar.setTextColor(ContextCompat.getColor(view.context, R.color.white))
            snackbar.setAction("Dismiss") { snackbar.dismiss() }
            snackbar.setActionTextColor(ContextCompat.getColor(view.context, R.color.white))
            snackbar.show()
        } catch (e: Exception) {
            Toast.makeText(view.context, message, Toast.LENGTH_LONG).show()
        }
    }
}