package com.cinetrack.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/** A factory that always builds the ViewModel returned by [create]. */
internal inline fun <VM : ViewModel> viewModelFactoryOf(crossinline create: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }
