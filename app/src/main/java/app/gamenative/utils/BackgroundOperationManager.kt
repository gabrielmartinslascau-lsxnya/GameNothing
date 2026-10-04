package app.gamenative.utils

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

enum class OperationType {
    DOWNLOAD,
    TRANSFER,
    INSTALL,
    VERIFY
}

data class BackgroundOperation(
    val id: String,
    val type: OperationType,
    val displayName: String,
    val progress: Float = 0f,
    val statusMessage: String = "",
    val speedText: String = "",
    val timeLeftText: String = "",
    val totalBytes: Long = 0L,
    val processedBytes: Long = 0L,
    val isCancellable: Boolean = true,
    val onCancel: () -> Unit = {}
)

object BackgroundOperationManager {
    private val _operations = mutableStateListOf<BackgroundOperation>()
    val operations: List<BackgroundOperation> = _operations

    private val _activeOperationsCount = MutableStateFlow(0)
    val activeOperationsCount: StateFlow<Int> = _activeOperationsCount

    fun startOperation(operation: BackgroundOperation) {
        _operations.add(operation)
        _activeOperationsCount.value = _operations.size
        Timber.tag("BackgroundOp").d("Started operation: ${operation.displayName} (${operation.id})")
    }

    fun updateOperation(id: String, update: (BackgroundOperation) -> BackgroundOperation) {
        val index = _operations.indexOfFirst { it.id == id }
        if (index != -1) {
            _operations[index] = update(_operations[index])
        }
    }

    fun finishOperation(id: String) {
        val removed = _operations.removeIf { it.id == id }
        if (removed) {
            _activeOperationsCount.value = _operations.size
            Timber.tag("BackgroundOp").d("Finished operation: $id")
        }
    }

    fun getOperation(id: String): BackgroundOperation? {
        return _operations.find { it.id == id }
    }
}
