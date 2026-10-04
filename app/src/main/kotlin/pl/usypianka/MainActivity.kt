package pl.usypianka

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.usypianka.ui.PlayerScreen
import pl.usypianka.ui.PlayerViewModel
import pl.usypianka.ui.theme.UsypiankaTheme

class MainActivity : ComponentActivity() {

    private val container get() = (application as UsypiankaApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            UsypiankaTheme {
                PlayerScreen(viewModel(factory = PlayerViewModel.Factory))
            }
        }
    }

    // Controllers must unbind so the service can stop (connect in onStart, release in onStop).
    override fun onStart() {
        super.onStart()
        container.playbackClient.connect()
    }

    override fun onStop() {
        container.playbackClient.release()
        super.onStop()
    }
}
