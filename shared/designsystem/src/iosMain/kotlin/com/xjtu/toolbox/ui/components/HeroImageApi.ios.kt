@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.xjtu.toolbox.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import com.xjtu.toolbox.platform.iosTopViewController
import com.xjtu.toolbox.platform.onIosMain
import com.xjtu.toolbox.platform.showBriefMessage
import kotlinx.cinterop.readBytes
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.darwin.NSObject

@Composable
actual fun rememberHeroImagePicker(onPicked: (ByteArray) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onPicked)
    val picker = remember { IosHeroPicker { callback.value(it) } }
    DisposableEffect(picker) { onDispose { picker.dispose() } }
    return remember(picker) { { picker.launch() } }
}

private class IosHeroPicker(private val onPicked: (ByteArray) -> Unit) : NSObject(), PHPickerViewControllerDelegateProtocol {
    private var controller: PHPickerViewController? = null
    private var active = true

    fun launch() = onIosMain {
        if (!active || controller != null) return@onIosMain
        val presenter = iosTopViewController() ?: return@onIosMain
        val configuration = PHPickerConfiguration().apply {
            selectionLimit = 1
            filter = PHPickerFilter.imagesFilter
        }
        val picker = PHPickerViewController(configuration).also { it.delegate = this }
        controller = picker
        presenter.presentViewController(picker, true, null)
    }

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, null)
        controller = null
        val item = didFinishPicking.firstOrNull() as? PHPickerResult ?: return
        item.itemProvider.loadDataRepresentationForTypeIdentifier("public.image") { data, _ ->
            if (data == null || data.length == 0uL || data.length > 32uL * 1024uL * 1024uL) {
                onIosMain { if (active) showBriefMessage("无法读取图片，或图片超过 32 MB") }
                return@loadDataRepresentationForTypeIdentifier
            }
            val bytes = data.bytes?.readBytes(data.length.toInt()) ?: return@loadDataRepresentationForTypeIdentifier
            onIosMain { if (active) onPicked(bytes) }
        }
    }

    fun dispose() = onIosMain {
        active = false
        controller?.dismissViewControllerAnimated(false, null)
        controller = null
    }
}

actual fun encodeHeroImage(bitmap: ImageBitmap): ByteArray? = runCatching {
    val image = Image.makeFromBitmap(bitmap.asSkiaBitmap())
    try {
        val data = image.encodeToData(EncodedImageFormat.WEBP, 86) ?: return@runCatching null
        try { data.bytes } finally { data.close() }
    } finally {
        image.close()
    }
}.getOrNull()
