package pl.usypianka

import android.app.Application
import kotlinx.coroutines.flow.MutableStateFlow
import pl.usypianka.domain.SessionState
import pl.usypianka.playback.PlaybackClient

class UsypiankaApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

/** Manual DI: service and Activity live in one process and share this state. */
class AppContainer(app: Application) {
    val sessionState = MutableStateFlow<SessionState>(SessionState.Idle)
    val playbackClient = PlaybackClient(app)
}
