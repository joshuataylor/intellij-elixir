package org.elixir_lang.util

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile

/**
 * Loads [path], and a directory's children, into the VFS so that a watch on it produces events: watching loads nothing,
 * and a refresh reports changes only to loaded files and new children only of directories whose children are loaded.
 * Loading is one level deep.
 *
 * Reads the filesystem, so never call it under a lock.
 */
fun LocalFileSystem.loadForEvents(path: String): VirtualFile? =
    refreshAndFindFileByPath(path)?.also { if (it.isDirectory) it.children }
