package com.yang.player.data

import com.yang.player.model.Song

fun sameSongFile(first: Song, second: Song): Boolean = !first.isOnline && !second.isOnline &&
    (first.mediaUri == second.mediaUri || (first.dataPath.isNotBlank() && first.dataPath == second.dataPath))
