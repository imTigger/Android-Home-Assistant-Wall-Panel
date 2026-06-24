package com.tigerworkshop.homepanel.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow

/** Lifecycle-aware StateFlow collection for Compose. */
@Composable
fun <T> StateFlow<T>.collectAsStateLifecycleSafe(): State<T> =
    collectAsStateWithLifecycle()
