package io.github.gigpir.ritmo

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import io.github.gigpir.ritmo.core.Cue
import io.github.gigpir.ritmo.core.Tone
import java.util.Locale

/**
 * Beeps, voice and vibration, mixed over the user's music. While a cue plays, Ritmo asks
 * Android to lower the other apps' audio, and gives it back once the cue is over.
 */
class CuePlayer(context: Context, private val settings: Settings) : TextToSpeech.OnInitListener {
    var isMuted = false

    private val audio = context.getSystemService(AudioManager::class.java)
    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    // On the media volume, the one the user turns up for music.
    private val beepAudio = attributes(AudioAttributes.CONTENT_TYPE_SONIFICATION)
    private val speechAudio = attributes(AudioAttributes.CONTENT_TYPE_SPEECH)
    private val shortBeep = track(Tone.beep(frequency = 880.0, seconds = 0.12))
    private val longBeep = track(Tone.beep(frequency = 1320.0, seconds = 0.5))

    private val voice = TextToSpeech(context.applicationContext, this)
    private var hasVoice = false
    private var isSpeaking = false

    private val handler = Handler(Looper.getMainLooper())
    private val focus =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(speechAudio)
            .setOnAudioFocusChangeListener {}
            .build()
    private var isDucking = false
    private val unduck = Runnable {
        if (!isSpeaking) {
            audio.abandonAudioFocusRequest(focus)
            isDucking = false
        }
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val english = listOf(Locale.US, Locale.UK, Locale.ENGLISH)
            .firstOrNull { voice.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE } ?: return
        voice.language = english
        voice.setAudioAttributes(speechAudio)
        voice.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) = finishedSpeaking()

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finishedSpeaking()
        })
        hasVoice = true
    }

    fun play(cue: Cue) {
        if (cue != Cue.Beep && settings.vibration) {
            vibrator.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
        }
        if (isMuted) return
        duck()
        when (cue) {
            Cue.Beep -> {
                restart(shortBeep)
                unduckSoon(after = 1500)
            }
            is Cue.Announce -> say(cue.text)
            Cue.Finish -> say("Workout complete")
        }
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        voice.stop()
        voice.shutdown()
        shortBeep.release()
        longBeep.release()
        if (isDucking) audio.abandonAudioFocusRequest(focus)
        isDucking = false
    }

    /** Without the voice, or without an English one on the phone, a long beep marks the change. */
    private fun say(text: String) {
        if (!settings.voice || !hasVoice) {
            restart(longBeep)
            unduckSoon(after = 1000)
            return
        }
        isSpeaking = true
        voice.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cue")
    }

    private fun finishedSpeaking() {
        handler.post {
            isSpeaking = false
            unduckSoon(after = 300)
        }
    }

    // Lowering the music

    /** Lowers other apps' audio from the first beep to the end of the announcement. */
    private fun duck() {
        handler.removeCallbacks(unduck)
        if (!settings.duckMusic || isDucking) return
        isDucking = audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun unduckSoon(after: Long) {
        if (!isDucking) return
        handler.removeCallbacks(unduck)
        handler.postDelayed(unduck, after)
    }

    private fun restart(track: AudioTrack) {
        track.stop()
        track.reloadStaticData()
        track.play()
    }

    private fun attributes(contentType: Int): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(contentType)
            .build()

    private fun track(samples: ShortArray): AudioTrack =
        AudioTrack.Builder()
            .setAudioAttributes(beepAudio)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(Tone.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 2)
            .build()
            .apply { write(samples, 0, samples.size) }
}
