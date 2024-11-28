import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun App() {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .size(200.dp, 200.dp)
        ) {
            drawLine(
                color = Color.Black,
                Offset(0f, 0f),
                Offset(10f, 0f),
                13f,
            )
        }
    }
}

@Composable
fun Scope(child: @Composable () -> Unit) {
    child()
}


fun strokeWidth(): Float = 130f

fun color() = Color.Red