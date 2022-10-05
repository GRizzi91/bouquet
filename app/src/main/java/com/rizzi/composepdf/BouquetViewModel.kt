package com.rizzi.composepdf

import androidx.lifecycle.ViewModel
import com.rizzi.bouquet.PdfReaderState
import com.rizzi.bouquet.ResourceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BouquetViewModel : ViewModel() {
    private val mStateFlow = MutableStateFlow<PdfReaderState?>(null)
    val stateFlow: StateFlow<PdfReaderState?> = mStateFlow

    fun openResource(resourceType: ResourceType) {
        mStateFlow.tryEmit(
            PdfReaderState(resourceType, true)
        )
    }

    fun clearResource() {
        mStateFlow.tryEmit(null)
    }
}
