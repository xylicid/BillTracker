package com.example.billtracker.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Minimal drag-to-reorder support for a LazyColumn — press and hold an item, drag it up or
 * down, and [onMove] fires whenever the dragged item crosses the midpoint of a neighbor.
 * No external dependency; adapted from the standard Compose reorderable-list pattern.
 *
 * Note: this does not auto-scroll the list when you drag near the top/bottom edge of the
 * screen — fine for short lists, but worth adding if the bill list gets long enough to scroll.
 */
class DragDropState internal constructor(
    private val state: LazyListState,
    private val canDrag: (LazyListItemInfo) -> Boolean,
    private val onMove: (from: Int, to: Int) -> Unit
) {
    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set

    private var draggedDistance by mutableStateOf(0f)
    private var draggingItemInitialOffset by mutableStateOf(0)

    internal val draggingItemOffset: Float
        get() = draggingItemLayoutInfo?.let { item ->
            draggingItemInitialOffset + draggedDistance - item.offset
        } ?: 0f

    private val draggingItemLayoutInfo: LazyListItemInfo?
        get() = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == draggingItemIndex }

    fun onDragStart(offset: Offset) {
        state.layoutInfo.visibleItemsInfo
            .firstOrNull { item -> offset.y.toInt() in item.offset..(item.offset + item.size) && canDrag(item) }
            ?.also {
                draggingItemIndex = it.index
                draggingItemInitialOffset = it.offset
            }
    }

    fun onDragInterrupted() {
        draggingItemIndex = null
        draggedDistance = 0f
        draggingItemInitialOffset = 0
    }

    fun onDrag(offset: Offset) {
        draggedDistance += offset.y
        val draggingItem = draggingItemLayoutInfo ?: return

        val startOffset = draggingItem.offset + draggingItemOffset
        val endOffset = startOffset + draggingItem.size
        val middleOffset = startOffset + (endOffset - startOffset) / 2f

        val targetItem = state.layoutInfo.visibleItemsInfo.find { item ->
            middleOffset.toInt() in item.offset..(item.offset + item.size) &&
                draggingItem.index != item.index &&
                canDrag(item)
        }

        if (targetItem != null) {
            onMove(draggingItem.index, targetItem.index)
            draggingItemIndex = targetItem.index
        }
    }
}

@Composable
fun rememberDragDropState(
    lazyListState: LazyListState,
    canDrag: (LazyListItemInfo) -> Boolean,
    onMove: (from: Int, to: Int) -> Unit
): DragDropState {
    return remember(lazyListState) {
        DragDropState(state = lazyListState, canDrag = canDrag, onMove = onMove)
    }
}

fun Modifier.dragContainer(dragDropState: DragDropState, onDragEnd: () -> Unit): Modifier {
    return pointerInput(dragDropState) {
        detectDragGesturesAfterLongPress(
            onDrag = { change, offset ->
                change.consume()
                dragDropState.onDrag(offset = offset)
            },
            onDragStart = { offset -> dragDropState.onDragStart(offset) },
            onDragEnd = {
                dragDropState.onDragInterrupted()
                onDragEnd()
            },
            onDragCancel = { dragDropState.onDragInterrupted() }
        )
    }
}
