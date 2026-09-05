package com.example.customtitle

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import java.io.File
import java.io.FileInputStream

/**
 * One image found in the background folder, already registered as a live texture.
 * width/height are read straight from the file (via NativeImage) - not hardcoded.
 */
data class BackgroundImageEntry(
    val file: File,
    val displayName: String,
    val width: Int,
    val height: Int,
    val identifier: Identifier
)

/**
 * Scans <gameDirectory>/background/ for images, loads them as DynamicTextures, and remembers
 * the user's selection across restarts via a small selected.txt file in that same folder.
 *
 * Verified against the real 26.2 decompiled source:
 *   - Minecraft.gameDirectory is a public final File field (TitleScreen/Screen use it directly)
 *   - Minecraft.getInstance().getTextureManager() -> TextureManager.register(Identifier, AbstractTexture)
 *   - NativeImage.read(InputStream) -> NativeImage, with .getWidth()/.getHeight() (Kotlin: .width/.height)
 *   - DynamicTexture(Supplier<String> label, NativeImage image) uploads to the GPU immediately
 */
object CustomBackgroundManager {
    private const val MOD_ID = "customtitle"
    private val SUPPORTED_EXTENSIONS = setOf("png", "jpg", "jpeg")

    private var nextTexIndex = 0
    private val loaded = mutableListOf<BackgroundImageEntry>()
    private var scannedOnce = false

    var selected: BackgroundImageEntry? = null
        private set

    /** <gameDirectory>/background - created on first access if missing. */
    fun backgroundDir(): File {
        val dir = File(Minecraft.getInstance().gameDirectory, "background")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** Cached list for this session. Call [rescan] to pick up files added while the game is running. */
    fun entries(): List<BackgroundImageEntry> {
        if (!scannedOnce) rescan()
        return loaded
    }

    fun rescan() {
        scannedOnce = true
        val dir = backgroundDir()
        val files = dir.listFiles { f -> f.isFile && f.extension.lowercase() in SUPPORTED_EXTENSIONS }
            ?: emptyArray()

        for (file in files.sortedBy { it.name.lowercase() }) {
            if (loaded.any { it.file == file }) continue
            loadEntry(file)?.let { loaded.add(it) }
        }

        // Drop entries whose file disappeared since last scan.
        loaded.removeAll { entry -> files.none { it == entry.file } }

        if (selected == null) {
            readSelectedName()?.let { name -> selected = loaded.find { it.file.name == name } }
        }
    }

    private fun loadEntry(file: File): BackgroundImageEntry? {
        return try {
            FileInputStream(file).use { stream ->
                val image = NativeImage.read(stream)
                val id = Identifier.fromNamespaceAndPath(MOD_ID, "dynamic/background_${nextTexIndex++}")
                Minecraft.getInstance().textureManager.register(id, DynamicTexture({ file.name }, image))
                BackgroundImageEntry(file, file.name, image.width, image.height, id)
            }
        } catch (e: Exception) {
            // Corrupt/unsupported file - skip it rather than crashing the picker.
            null
        }
    }

    fun select(entry: BackgroundImageEntry) {
        selected = entry
        try {
            selectionFile().writeText(entry.file.name)
        } catch (e: Exception) {
            // Non-fatal - selection still applies for this session even if it can't persist.
        }
    }

    fun clearSelection() {
        selected = null
        try {
            selectionFile().delete()
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun selectionFile(): File = File(backgroundDir(), "selected.txt")

    private fun readSelectedName(): String? = try {
        selectionFile().takeIf { it.exists() }?.readText()?.trim()?.ifBlank { null }
    } catch (e: Exception) {
        null
    }
}
