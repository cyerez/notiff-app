package com.rez.notif.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.net.Uri
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File

/** Which languages to look for in the picture. */
enum class OcrLang(val label: String, val code: String) {
    BOTH("فارسی و انگلیسی", "fas+eng"),
    FA("فقط فارسی", "fas"),
    EN("فقط انگلیسی", "eng"),
}

/**
 * Picture to text, fully offline (Tesseract). The language files ship inside the app
 * (assets/tessdata, downloaded by the build) and are copied out once on first use.
 */
object Ocr {

    private fun dataDir(context: Context): File {
        val dir = File(context.filesDir, "ocr")
        val tessdata = File(dir, "tessdata").apply { mkdirs() }
        for (name in listOf("fas", "eng")) {
            val f = File(tessdata, "$name.traineddata")
            if (f.exists() && f.length() > 0) continue
            val tmp = File(tessdata, "$name.tmp")
            context.assets.open("tessdata/$name.traineddata").use { input -> tmp.outputStream().use { input.copyTo(it) } }
            tmp.renameTo(f)
        }
        return dir
    }

    /** Loads a shared or picked image, small enough to work with. */
    fun load(context: Context, uri: Uri): Bitmap {
        val src = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > 4000) {
                val k = 4000f / longest
                decoder.setTargetSize((info.size.width * k).toInt(), (info.size.height * k).toInt())
            }
        }
    }

    /** Reads the text. Slow-ish (a second or two): call it off the main thread. */
    fun read(context: Context, bitmap: Bitmap, lang: OcrLang): String {
        val api = TessBaseAPI()
        try {
            check(api.init(dataDir(context).absolutePath, lang.code)) { "OCR could not start" }
            api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
            api.setImage(prepare(bitmap))
            return api.getUTF8Text()?.trim().orEmpty()
        } finally {
            api.recycle()
        }
    }

    /**
     * Small screenshots (a Smart select crop) are scaled up and turned gray: Tesseract reads
     * letters best when they are 30+ pixels tall and the colors don't distract it.
     */
    private fun prepare(b: Bitmap): Bitmap {
        val shortest = minOf(b.width, b.height)
        val scale = when {
            shortest < 300 -> 3f
            shortest < 700 -> 2f
            else -> 1f
        }
        val out = Bitmap.createBitmap((b.width * scale).toInt(), (b.height * scale).toInt(), Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        }
        Canvas(out).apply {
            drawColor(android.graphics.Color.WHITE)
            scale(scale, scale)
            drawBitmap(b, 0f, 0f, paint)
        }
        return out
    }
}
