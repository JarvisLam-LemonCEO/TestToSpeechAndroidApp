package com.example.texttospeechapp

import android.media.AudioFormat
import com.github.axet.lamejni.Config
import com.github.axet.lamejni.Lame
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Converts the PCM bytes supplied by Android TextToSpeech into a real MP3 stream.
 *
 * This implementation uses the Android/JNI build of libmp3lame. The encoder uses
 * a VBR header frame, so the final LAME tag returned by close() must replace the
 * placeholder frame at the start of the encoded stream before the MP3 is saved.
 */
object Mp3Encoder {

    @Volatile
    private var nativeLibrariesLoaded = false

    fun encode(
        pcmBytes: ByteArray,
        sampleRate: Int,
        audioFormat: Int,
        channelCount: Int,
        output: OutputStream
    ) {
        require(sampleRate > 0) { "Invalid sample rate: $sampleRate" }
        require(channelCount in 1..2) { "Only mono and stereo TTS audio is supported." }

        val samples = pcmToSigned16Bit(pcmBytes, audioFormat)
        require(samples.isNotEmpty()) { "The TTS engine returned no audio samples." }
        require(samples.size % channelCount == 0) {
            "The TTS audio does not contain complete PCM frames."
        }

        ensureNativeLibrariesLoaded()

        val lame = Lame()
        var encoderClosed = false

        try {
            lame.open(
                channelCount,
                sampleRate,
                recommendedBitRate(sampleRate),
                4
            )

            val encoded = ByteArrayOutputStream()
            val valuesPerChunk = 1152 * channelCount * 8
            var offset = 0

            while (offset < samples.size) {
                var length = minOf(valuesPerChunk, samples.size - offset)

                // Stereo input must contain whole interleaved L/R frames.
                if (channelCount == 2 && length % 2 != 0) {
                    length -= 1
                }
                if (length <= 0) break

                val chunk = lame.encode(samples, offset, length)
                if (chunk.isNotEmpty()) {
                    encoded.write(chunk)
                }
                offset += length
            }

            // Flush buffered MP3 frames before obtaining the final LAME/Xing tag.
            val flushed = lame.encode(ShortArray(0), 0, 0)
            if (flushed.isNotEmpty()) {
                encoded.write(flushed)
            }

            val finalTag = lame.close()
            encoderClosed = true

            val mp3Bytes = encoded.toByteArray()
            require(mp3Bytes.isNotEmpty()) { "The MP3 encoder returned no data." }

            // libmp3lame initially writes a placeholder VBR frame. The JNI wrapper
            // returns the completed LAME/Xing frame from close(), which must replace
            // that placeholder. Without this step some players report a corrupt MP3.
            if (finalTag.isNotEmpty()) {
                require(finalTag.size <= mp3Bytes.size) {
                    "The MP3 encoder returned an invalid final header."
                }
                System.arraycopy(finalTag, 0, mp3Bytes, 0, finalTag.size)
            }

            require(containsValidMp3Frame(mp3Bytes)) {
                "The MP3 encoder did not produce a valid MP3 frame."
            }

            output.write(mp3Bytes)
            output.flush()
        } finally {
            if (!encoderClosed) {
                try {
                    lame.close()
                } catch (_: Throwable) {
                    // Best-effort release if encoding failed before normal close().
                }
            }
        }
    }

    @Synchronized
    private fun ensureNativeLibrariesLoaded() {
        if (nativeLibrariesLoaded) return

        // The lamejni library depends on liblame, so load the dependency first.
        // Config.natives=false prevents Lame's static initializer from trying to
        // load lamejni a second time.
        System.loadLibrary("lame")
        System.loadLibrary("lamejni")
        Config.natives = false
        nativeLibrariesLoaded = true
    }

    private fun recommendedBitRate(sampleRate: Int): Int = when {
        sampleRate <= 12_000 -> 32
        sampleRate <= 24_000 -> 64
        else -> 96
    }

    private fun pcmToSigned16Bit(pcmBytes: ByteArray, audioFormat: Int): ShortArray {
        return when (audioFormat) {
            AudioFormat.ENCODING_PCM_8BIT -> ShortArray(pcmBytes.size) { index ->
                (((pcmBytes[index].toInt() and 0xFF) - 128) shl 8).toShort()
            }

            AudioFormat.ENCODING_PCM_16BIT -> {
                require(pcmBytes.size % 2 == 0) { "Incomplete 16-bit PCM sample." }
                val sampleCount = pcmBytes.size / 2
                val buffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.nativeOrder())
                ShortArray(sampleCount) { buffer.short }
            }

            AudioFormat.ENCODING_PCM_FLOAT -> {
                require(pcmBytes.size % 4 == 0) { "Incomplete float PCM sample." }
                val sampleCount = pcmBytes.size / 4
                val buffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.nativeOrder())
                ShortArray(sampleCount) {
                    val raw = buffer.float
                    val value = if (raw.isFinite()) raw.coerceIn(-1.0f, 1.0f) else 0.0f
                    (value * 32767.0f).roundToInt().toShort()
                }
            }

            else -> error("Unsupported TTS PCM format: $audioFormat")
        }
    }

    private fun containsValidMp3Frame(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false

        val scanLimit = minOf(bytes.size - 3, 16 * 1024)
        for (i in 0 until scanLimit) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = bytes[i + 1].toInt() and 0xFF
            val b2 = bytes[i + 2].toInt() and 0xFF

            if (b0 != 0xFF || (b1 and 0xE0) != 0xE0) continue

            val versionBits = b1 and 0x18
            val layerBits = b1 and 0x06
            val bitrateIndex = (b2 ushr 4) and 0x0F
            val sampleRateIndex = (b2 ushr 2) and 0x03

            if (versionBits == 0x08) continue // reserved MPEG version
            if (layerBits == 0x00) continue   // reserved layer
            if (bitrateIndex == 0x00 || bitrateIndex == 0x0F) continue
            if (sampleRateIndex == 0x03) continue

            return true
        }

        return false
    }
}
