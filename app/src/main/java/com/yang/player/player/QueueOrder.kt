package com.yang.player.player

import com.yang.player.model.PlayerState

object QueueOrder {
    fun move(state: PlayerState, from: Int, to: Int): PlayerState {
        if (from !in state.queue.indices || to !in state.queue.indices || from == to) return state
        val queue = state.queue.toMutableList().apply { add(to, removeAt(from)) }
        val index = when {
            state.currentIndex == from -> to
            from < state.currentIndex && to >= state.currentIndex -> state.currentIndex - 1
            from > state.currentIndex && to <= state.currentIndex -> state.currentIndex + 1
            else -> state.currentIndex
        }
        return state.copy(queue = queue, currentIndex = index)
    }
}
