package nl.klrnbk.minecraft.plugins.whitelist.common.providers.toggle

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

class StatusFileToggleProvider {
    private var contents: String? = null

    fun load(
        dataDirectory: Path,
        fileName: String,
    ) {
        val file = dataDirectory.resolve(fileName)
        if (!file.exists()) return write(dataDirectory, fileName, false)

        contents = file.readText()
    }

    fun write(
        dataDirectory: Path,
        fileName: String,
        value: Boolean,
    ) {
        contents = value.toString()

        val filePath = dataDirectory.resolve(fileName)
        Files.write(filePath, value.toString().toByteArray(Charsets.UTF_8))
    }

    fun parse(): Boolean = contents?.trim()?.lowercase() == "true"
}
