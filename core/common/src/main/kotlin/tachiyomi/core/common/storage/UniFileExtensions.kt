package tachiyomi.core.common.storage

import com.hippo.unifile.UniFile

val UniFile.extension: String?
    get() = name?.substringAfterLast('.')

val UniFile.nameWithoutExtension: String?
    get() = name?.substringBeforeLast('.')

val UniFile.displayablePath: String
    get() = filePath ?: uri.toString()

/**
 * Total size in bytes of this file, or of all files under it if it is a directory.
 */
fun UniFile.recursiveSize(): Long =
    if (isDirectory) listFiles().orEmpty().sumOf { it.recursiveSize() } else length()
