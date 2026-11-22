package com.kyant.backdrop.catalog.utils

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.util.fastFirstOrNull
import kotlin.math.abs

suspend fun PointerInputScope.inspectDragGestures(
    horizontalSlopConsume: Boolean = false,
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit
) {
    val touchSlop = viewConfiguration.touchSlop
    awaitEachGesture {
        val initialDown = awaitFirstDown(false, PointerEventPass.Initial)
        val down = awaitFirstDown(false)
        val drag = initialDown

        if (!horizontalSlopConsume) {
            onDragStart(down)
            onDrag(drag, Offset.Zero)
            val upEvent =
                drag(
                    pointerId = drag.id,
                    consume = false,
                    onDrag = { onDrag(it, it.positionChange()) }
                )
            if (upEvent == null) {
                onDragCancel()
            } else {
                onDragEnd(upEvent)
            }
            return@awaitEachGesture
        }

        // For horizontal sliders/toggles inside vertically scrollable containers:
        // Wait briefly to see whether the user is scrolling vertically or dragging horizontally,
        // then lock & consume horizontal move events so parent verticalScroll cannot steal the drag.
        onDragStart(down)
        var lockedHorizontal = false
        var totalDx = 0f
        var totalDy = 0f
        var pointer = drag.id
        var finishedUp: PointerInputChange? = null
        var cancelled = false

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.fastFirstOrNull { it.id == pointer }
            if (change == null) {
                cancelled = true
                break
            }
            if (change.changedToUpIgnoreConsumed()) {
                val otherDown = event.changes.fastFirstOrNull { it.pressed }
                if (otherDown == null) {
                    finishedUp = change
                    break
                } else {
                    pointer = otherDown.id
                    continue
                }
            }
            if (change.isConsumed) {
                cancelled = true
                break
            }

            val delta = change.positionChange()
            if (!lockedHorizontal) {
                totalDx += delta.x
                totalDy += delta.y
                if (abs(totalDy) > touchSlop && abs(totalDy) > abs(totalDx) * 1.25f) {
                    cancelled = true
                    break
                }
                if (abs(totalDx) >= touchSlop * 0.4f && abs(totalDx) >= abs(totalDy)) {
                    lockedHorizontal = true
                    change.consume()
                    onDrag(change, Offset(totalDx, 0f))
                }
            } else if (delta != Offset.Zero) {
                change.consume()
                onDrag(change, delta)
            }
        }

        if (cancelled || finishedUp == null) {
            onDragCancel()
        } else {
            finishedUp.consume()
            onDragEnd(finishedUp)
        }
    }
}

private suspend inline fun AwaitPointerEventScope.drag(
    pointerId: PointerId,
    consume: Boolean,
    onDrag: (PointerInputChange) -> Unit
): PointerInputChange? {
    val isPointerUp = currentEvent.changes.fastFirstOrNull { it.id == pointerId }?.pressed != true
    if (isPointerUp) {
        return null
    }
    var pointer = pointerId
    while (true) {
        val change = awaitDragOrUp(pointer) ?: return null
        if (change.isConsumed) {
            return null
        }
        if (change.changedToUpIgnoreConsumed()) {
            return change
        }
        if (consume) {
            change.consume()
        }
        onDrag(change)
        pointer = change.id
    }
}

private suspend inline fun AwaitPointerEventScope.awaitDragOrUp(
    pointerId: PointerId
): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val dragEvent = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
        if (dragEvent.changedToUpIgnoreConsumed()) {
            val otherDown = event.changes.fastFirstOrNull { it.pressed }
            if (otherDown == null) {
                return dragEvent
            } else {
                pointer = otherDown.id
            }
        } else {
            val hasDragged = dragEvent.previousPosition != dragEvent.position
            if (hasDragged) {
                return dragEvent
            }
        }
    }
}
