package net.gschimmel.cryptomako

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import net.gschimmel.cryptomako.ui.CryptoMakoApp
import net.gschimmel.cryptomako.ui.theme.CryptoMakoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CryptoMakoTheme {
                CryptoMakoApp()
            }
        }
    }
}
