package com.wyq0918dev.composeduo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyq0918dev.composeduo.ui.theme.ComposeDuoTheme
import kotlin.math.roundToInt


// ---------------------------------------------------------------------------
// 演示页面
// ---------------------------------------------------------------------------

@Composable
private fun DuoDemo() {
    val colors = if (isSystemInDarkTheme()) DuoStatusColors.Dark else DuoStatusColors.Light

    var level by remember { mutableStateOf(0.64f) }
    var charging by remember { mutableStateOf(false) }
    var powerSaving by remember { mutableStateOf(false) }
    var wifiLevel by remember { mutableStateOf(3) }
    var bars by remember { mutableStateOf(3) }

    val live = DuoStatusState(
        batteryLevel = level,
        isCharging = charging,
        isPowerSaving = powerSaving,
        wifiLevel = wifiLevel,
        cellularBars = bars,
        networkLabel = if (wifiLevel == 0) "5G" else null,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = colors.foreground.copy(alpha = 0.05f),
        ) {
            Column(Modifier.padding(20.dp)) {
                DuoStatusMark(
                    state = live,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    colors = colors,
                )
                Spacer(Modifier.height(20.dp))
                LabeledSlider("电量", value = level) {
                    level = it
                }
                LabeledSlider("信号格", value = bars / 4f) {
                    bars = (it * 4f).roundToInt()
                }
                LabeledSlider("Wi-Fi 强度", value = wifiLevel / 3f) {
                    wifiLevel = (it * 3f).roundToInt()
                }
                ToggleRow("充电中", charging) {
                    charging = it
                }
                ToggleRow("省电模式", powerSaving) {
                    powerSaving = it
                }
            }
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(72.dp), fontSize = 14.sp)
        Slider(value = value, onValueChange = onChange)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ComposeDuoTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                ) {
                    DuoDemo()
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 420, heightDp = 900)
@Composable
fun DuoStatusPreview() {
    ComposeDuoTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            DuoDemo()
        }
    }
}
