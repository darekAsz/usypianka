package pl.usypianka.probe

import android.app.Activity
import android.content.ComponentName
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionToken

/**
 * CR-01 UAT probe. Runs under its own uid, connects to Usypianka's PlaybackService and tries every playback-changing
 * call. Expected: only read-only getters are available, each mutation is a no-op, custom commands are denied.
 * Start a session in Usypianka first, then launch this app. Results go to the screen and to logcat (tag "Probe").
 */
class ProbeActivity : Activity() {

    private val out = StringBuilder()
    private lateinit var text: TextView
    private val main = Handler(Looper.getMainLooper())
    private val mainThread = java.util.concurrent.Executor { main.post(it) }
    private var failures = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        text = TextView(this).apply { setPadding(32, 32, 32, 32); textSize = 13f }
        setContentView(ScrollView(this).apply { addView(text) })

        val token = SessionToken(this, ComponentName("pl.usypianka", "pl.usypianka.playback.PlaybackService"))
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener({
            val controller = try {
                future.get()
            } catch (e: Exception) {
                log("CONNECT FAILED: $e")
                return@addListener
            }
            probe(controller)
        }, mainThread)
    }

    private fun probe(c: MediaController) {
        log("connected, my uid=${android.os.Process.myUid()}")
        val before = snapshot(c)
        log("before: $before")

        val mutating = mapOf(
            "PLAY_PAUSE" to Player.COMMAND_PLAY_PAUSE,
            "PREPARE" to Player.COMMAND_PREPARE,
            "STOP" to Player.COMMAND_STOP,
            "SEEK_TO_DEFAULT_POSITION" to Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
            "SEEK_IN_CURRENT_MEDIA_ITEM" to Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            "SET_SPEED_AND_PITCH" to Player.COMMAND_SET_SPEED_AND_PITCH,
            "SET_MEDIA_ITEM" to Player.COMMAND_SET_MEDIA_ITEM,
            "CHANGE_MEDIA_ITEMS" to Player.COMMAND_CHANGE_MEDIA_ITEMS,
            "SET_VOLUME" to Player.COMMAND_SET_VOLUME,
            "SET_DEVICE_VOLUME" to Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS,
            "SET_REPEAT_MODE" to Player.COMMAND_SET_REPEAT_MODE,
            "RELEASE" to Player.COMMAND_RELEASE,
        )
        for ((name, cmd) in mutating) {
            check("command $name unavailable", !c.isCommandAvailable(cmd))
        }
        check("GET_CURRENT_MEDIA_ITEM available", c.isCommandAvailable(Player.COMMAND_GET_CURRENT_MEDIA_ITEM))
        check("GET_METADATA available", c.isCommandAvailable(Player.COMMAND_GET_METADATA))
        check("no session commands granted", c.availableSessionCommands.commands.isEmpty())

        // Try the calls anyway; MediaController drops unavailable commands and the session rejects them server-side.
        c.setPlaybackSpeed(2f)
        c.setVolume(0f)
        c.setRepeatMode(Player.REPEAT_MODE_OFF)
        c.seekTo(0)
        c.setMediaItem(MediaItem.fromUri("asset:///nope.flac"))
        c.clearMediaItems()
        c.pause()
        c.stop()
        c.play()

        val stop = c.sendCustomCommand(SessionCommand("pl.usypianka.STOP", Bundle.EMPTY), Bundle.EMPTY)
        stop.addListener({
            val code = stop.get().resultCode
            check("custom STOP denied (code=$code)", code == SessionError.ERROR_PERMISSION_DENIED)
            main.postDelayed({ finish(c, before) }, 2_000)
        }, mainThread)
    }

    private fun finish(c: MediaController, before: String) {
        val after = snapshot(c)
        log("after:  $after")
        check("player state unchanged after all attempts", after == before)
        log(if (failures == 0) "RESULT: PASS" else "RESULT: FAIL ($failures)")
        c.release()
    }

    private fun snapshot(c: MediaController): String =
        "title=${c.mediaMetadata.title} item=${c.currentMediaItem?.mediaId != null} " +
            "playing=${c.isPlaying} playWhenReady=${c.playWhenReady} state=${c.playbackState} " +
            "speed=${c.playbackParameters.speed} repeat=${c.repeatMode}"

    private fun check(name: String, ok: Boolean) {
        if (!ok) failures++
        log((if (ok) "OK   " else "FAIL ") + name)
    }

    private fun log(line: String) {
        Log.i("Probe", line)
        out.appendLine(line)
        text.text = out
    }
}
