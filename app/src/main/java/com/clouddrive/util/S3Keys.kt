package com.clouddrive.util

object S3Keys {

    /**
     * Returns [baseKey], or a variant of it that is not in [taken], and records the result in
     * [taken].
     *
     * Gallery sync derives the key from "<prefix>/<leaf folder name>/<file name>", so two
     * different photos (say IMG_0001.jpg after a phone reset, or the same name in two folders
     * that share a leaf name) used to map to the same object and the second upload silently
     * overwrote the backup of the first. The variant adds [discriminator] (the MediaStore id)
     * before the extension: "a/b/IMG_0001_42.jpg".
     */
    fun uniqueKey(baseKey: String, discriminator: Long, taken: MutableSet<String>): String {
        if (taken.add(baseKey)) return baseKey

        val slash = baseKey.lastIndexOf('/')
        val dot = baseKey.lastIndexOf('.')
        // A dot at the very start of the name (".nomedia") is not an extension.
        val hasExtension = dot > slash + 1
        val stem = if (hasExtension) baseKey.substring(0, dot) else baseKey
        val extension = if (hasExtension) baseKey.substring(dot) else ""

        var candidate = "${stem}_$discriminator$extension"
        var attempt = 2
        while (!taken.add(candidate)) {
            candidate = "${stem}_${discriminator}_$attempt$extension"
            attempt++
        }
        return candidate
    }
}
