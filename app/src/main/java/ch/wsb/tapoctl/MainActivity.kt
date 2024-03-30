package ch.wsb.tapoctl

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ch.wsb.tapoctl.ui.theme.TapoctlTheme
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.runBlocking
import tapo.TapoGrpcKt
import tapo.TapoOuterClass

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val channel = ManagedChannelBuilder.forAddress("192.168.1.173", 19191).usePlaintext().build()
        val stub = TapoGrpcKt.TapoCoroutineStub(channel)

        setContent {
            TapoctlTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Row {
                        Trigger {
                            runBlocking {
                                val request = TapoOuterClass.Empty.newBuilder().build()
                                val response = stub.devices(request)
                                response.devicesList
                                Log.i("result", response.toString())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Trigger(onClick: () -> Unit) {
    Button(
        onClick = onClick
    ) {
        Text(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .padding(16.dp)
                .background(MaterialTheme.colorScheme.background, shape = CircleShape),
            text = "Click me"
        )
    }
}
