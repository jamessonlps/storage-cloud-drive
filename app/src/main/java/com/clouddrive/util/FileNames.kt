package com.clouddrive.util

import java.io.File

object FileNames {

    /**
     * Reduces an untrusted name (a content provider's DISPLAY_NAME, an S3 key segment...) to a
     * plain leaf file name. Returns null when nothing usable is left.
     *
     * Removes any directory part (both `/` and `\`), control characters and the special
     * names "." and "..", so the result can neither escape a target directory nor inject
     * extra S3 key prefixes.
     */
    fun sanitize(raw: String?): String? {
        if (raw == null) return null
        val leaf = raw.replace('\\', '/').substringAfterLast('/')
        val cleaned = leaf.filter { !it.isISOControl() }.trim()
        if (cleaned.isEmpty() || cleaned == "." || cleaned == "..") return null
        return cleaned
    }

    /**
     * Resolves [name] inside [dir] and guarantees the resulting file really lives in [dir].
     * Throws [IllegalArgumentException] otherwise (not an IOException, so the transfer retry
     * policy does not keep retrying a name that can never be valid).
     */
    fun resolveInside(dir: File, name: String): File {
        val safeName = sanitize(name) ?: throw IllegalArgumentException("Nome de arquivo invalido")
        val target = File(dir, safeName)
        val dirPath = dir.canonicalFile.path + File.separator
        if (!target.canonicalFile.path.startsWith(dirPath)) {
            throw IllegalArgumentException("Caminho de destino invalido")
        }
        return target
    }
}
