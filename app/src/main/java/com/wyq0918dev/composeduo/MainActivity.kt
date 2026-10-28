package com.wyq0918dev.composeduo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyq0918dev.composeduo.ui.theme.ComposeDuoTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * TODO: 双卡信号、电量数字显示
 */

/**
 * iPhone Duo 外屏「三合一」状态栏图标。
 *
 * 依据（联网核实）：
 *  - 状态标记 46×46pt，核心为 240° 电池圆弧（stroke 3、Round Cap）
 *  - 底部 120° 缺口内放置 4 个蜂窝信号圆点
 *  - 环心 = Wi-Fi 扇形 / 无 Wi-Fi 时显示 5G/4G 制式文字 / 充电时显示闪电
 *  - 单色为主（fg/strong-950），语义色：充电绿、省电黄、低于 20% 红
 */

/** 一组可推导的状态。全部语义由它派生，UI 只负责渲染。 */
data class DuoStatusState(
    val batteryLevel: Float = 0.8f,   // 0..1
    val isCharging: Boolean = false,
    val isFastCharging: Boolean = false,
    val isPowerSaving: Boolean = false,
    val wifiLevel: Int = 3,           // 0=无, 1..3 强度
    val cellularBars: Int = 4,        // 0..4 信号格
    val networkLabel: String? = null, // 无 Wi-Fi 时的制式，如 "5G"
)

/** 随深浅模式切换的配色。 */
data class DuoStatusColors(
    val foreground: Color,   // 主色（弧线 / 图标 / 文字）
    val track: Color,        // 未填充轨道 + 未点亮圆点
    val charging: Color,     // iOS 绿
    val powerSaving: Color,  // iOS 黄
    val lowBattery: Color,   // iOS 红
    val fastCharging: Color, // 快充闪电黄
) {
    companion object {
        val Light = DuoStatusColors(
            foreground = Color(0xFF0E0E0F),
            track = Color(0x330E0E0F),
            charging = Color(0xFF34C759),
            powerSaving = Color(0xFFFFCC00),
            lowBattery = Color(0xFFFF3B30),
            fastCharging = Color(0xFFFFCC00),
        )
        val Dark = DuoStatusColors(
            foreground = Color(0xFFFFFFFF),
            track = Color(0x40FFFFFF),
            charging = Color(0xFF30D158),
            powerSaving = Color(0xFFFFD60A),
            lowBattery = Color(0xFFFF453A),
            fastCharging = Color(0xFFFFD60A),
        )
    }
}

/** 圆环与 Wi-Fi 图标共用的线宽系数（相对 3/46 基准），保证整体粗细统一。 */
private const val DUO_STROKE_FACTOR = 1.15f

/** 核心：46dp 的圆环状态标记。 */
@Composable
fun DuoStatusMark(
    state: DuoStatusState,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    colors: DuoStatusColors? = null,
) {
    val c = colors ?: if (isSystemInDarkTheme()) DuoStatusColors.Dark else DuoStatusColors.Light

    val level = state.batteryLevel.coerceIn(0f, 1f)
    val isCharging = state.isCharging
    val isLow = level < 0.2f && !isCharging
    val wifiLevel = state.wifiLevel.coerceIn(0, 3)
    val bars = state.cellularBars.coerceIn(0, 4)

    // 电量弧平滑过渡
    val animatedLevel by animateFloatAsState(
        targetValue = level,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
    )
    // 语义色平滑过渡
    val accent by animateColorAsState(
        targetValue = when {
            isCharging -> c.charging
            state.isPowerSaving -> c.powerSaving
            isLow -> c.lowBattery
            else -> c.foreground
        },
        animationSpec = tween(durationMillis = 500),
    )
    // 充电 / 快充闪电颜色平滑过渡
    val boltColor by animateColorAsState(
        targetValue = if (state.isFastCharging) c.fastCharging else c.foreground,
        animationSpec = tween(durationMillis = 400),
    )
    // 充电 / 低电量脉冲
    val infinite = rememberInfiniteTransition()
    val pulse by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 850), RepeatMode.Reverse),
    )

    // 无 Wi-Fi 且非充电时，环心显示制式文字（统一用 Canvas 绘制）
    val label = if (!isCharging && wifiLevel == 0) {
        state.networkLabel ?: "5G"
    } else {
        null
    }
    val textMeasurer = rememberTextMeasurer()
    val labelLayout = remember(label, size, c.foreground) {
        label?.let {
            textMeasurer.measure(
                text = AnnotatedString(it),
                style = TextStyle(
                    color = c.foreground,
                    fontSize = (size.value * 0.30f).sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
    }

    // 环心内容类型 + 切换过渡（单 Canvas 内做淡入淡出）
    val kind = when {
        isCharging -> CenterKind.Charging
        wifiLevel > 0 -> CenterKind.Wifi
        else -> CenterKind.Label
    }
    var fromKind by remember { mutableStateOf(kind) }
    var toKind by remember { mutableStateOf(kind) }
    val crossfade = remember { Animatable(1f) }
    LaunchedEffect(kind) {
        if (toKind != kind) {
            fromKind = toKind
            toKind = kind
            crossfade.snapTo(0f)
            crossfade.animateTo(1f, tween(durationMillis = 320))
        }
    }

    Canvas(modifier = modifier.size(size)) {
        drawDuoMark(
            colors = c,
            accent = accent,
            level = animatedLevel,
            boltColor = boltColor,
            isLow = isLow,
            pulse = pulse,
            wifiLevel = wifiLevel,
            bars = bars,
            label = labelLayout,
            fromKind = fromKind,
            toKind = toKind,
            crossfade = crossfade.value,
        )
    }
}

private fun DrawScope.drawDuoMark(
    colors: DuoStatusColors,
    accent: Color,
    level: Float,
    boltColor: Color,
    isLow: Boolean,
    pulse: Float,
    wifiLevel: Int,
    bars: Int,
    label: TextLayoutResult?,
    fromKind: CenterKind,
    toKind: CenterKind,
    crossfade: Float,
) {
    val s = size.minDimension
    val stroke = s * (3f / 46f) * DUO_STROKE_FACTOR
    val center = Offset(s / 2f, s / 2f)
    val radius = s / 2f - stroke / 2f

    // 底部开口：点距与「弧端-点」间距分开控制，后者略大，保证「弧 + 点」读作一个完整圆
    val dotSpacing = 16f                          // 点与点的角距
    val capGap = 22f                              // 弧端与最近点的角距（比点距稍大）
    val opening = capGap * 2f + dotSpacing * 3f   // 底部开口 96°
    val arcStart = 90f + opening / 2f             // 138°
    val arcSweep = 360f - opening                 // 264°

    // 低电量时进度弧做呼吸脉冲
    val arcColor = if (isLow) accent.copy(alpha = 0.35f + 0.65f * pulse) else accent

    // 轨道
    drawArc(
        color = colors.track,
        startAngle = arcStart,
        sweepAngle = arcSweep,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )

    // 进度弧
    val sweep = arcSweep * level
    if (sweep > 0f) {
        drawArc(
            color = arcColor,
            startAngle = arcStart,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }

    // 底部缺口内的 4 个信号圆点：与圆环同轨道、同粗细，补全成完整圆环
    val dotRadius = stroke / 2f
    val dotOrbit = radius
    for (i in 0 until 4) {
        val a = Math.toRadians((90f + (i - 1.5f) * dotSpacing).toDouble())
        drawCircle(
            color = if (i < bars) colors.foreground else colors.track,
            radius = dotRadius,
            center = Offset(
                center.x + dotOrbit * cos(a).toFloat(),
                center.y + dotOrbit * sin(a).toFloat()
            ),
        )
    }

    // 环心内容：from 淡出、to 淡入，切换时平滑过渡
    drawCenterContent(fromKind, center, s, colors, boltColor, wifiLevel, label, 1f - crossfade)
    drawCenterContent(toKind, center, s, colors, boltColor, wifiLevel, label, crossfade)
}

private enum class CenterKind { Charging, Wifi, Label }

private fun DrawScope.drawCenterContent(
    kind: CenterKind,
    center: Offset,
    s: Float,
    colors: DuoStatusColors,
    boltColor: Color,
    wifiLevel: Int,
    label: TextLayoutResult?,
    alpha: Float,
) {
    when (kind) {
        CenterKind.Charging -> drawLightning(center, s, boltColor, alpha)
        CenterKind.Wifi -> drawWifi(center, s, colors.foreground, colors.track, wifiLevel, alpha)
        CenterKind.Label -> label?.let {
            drawText(
                textLayoutResult = it,
                topLeft = Offset(center.x - it.size.width / 2f, center.y - it.size.height / 2f),
                alpha = alpha,
            )
        }
    }
}

private fun DrawScope.drawLightning(center: Offset, s: Float, color: Color, alpha: Float) {
    val bolt = s * 0.46f
    val pts = listOf(
        Offset(0.62f, 0.00f),
        Offset(0.18f, 0.55f),
        Offset(0.40f, 0.55f),
        Offset(0.28f, 1.00f),
        Offset(0.72f, 0.42f),
        Offset(0.52f, 0.42f),
    )
    val path = Path()
    pts.forEachIndexed { i, p ->
        val x = center.x + (p.x - 0.5f) * bolt
        val y = center.y + (p.y - 0.5f) * bolt
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color, alpha = alpha)
    // 圆角描边柔化闪电的尖角，使其更圆润
    drawPath(
        path,
        color,
        alpha = alpha,
        style = Stroke(
            width = s * 0.05f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
    )
}

private fun DrawScope.drawWifi(
    center: Offset,
    s: Float,
    color: Color,
    trackColor: Color,
    level: Int,
    alpha: Float,
) {
    // 官方样式：圆角扇形（第 1 格）+ 2 条弧（第 2、3 格），同一圆心向上辐射
    // 弧线加粗、间距收紧，让三格视觉重量平衡（扇形径向厚度 ≈ 1.3 倍弧线宽）
    val iconStroke = s * (3f / 46f) * DUO_STROKE_FACTOR
    val gap = iconStroke * 0.7f                  // 各元素间统一的径向留白
    val innerArc = 0.19f * s                     // 第 2 格圆心线半径
    val outerArc = innerArc + iconStroke + gap   // 第 3 格圆心线半径
    // 扇形圆角描边：越大越圆润；绘制半径相应扣除，保持扇形外缘不变
    val cornerRound = s * 0.06f
    val wedgeRadius = innerArc - iconStroke / 2f - gap - cornerRound / 2f
    // 图标从顶点(下，含圆角外扩)延伸到外弧外缘(上)，下移使其在环内上下对称
    val fan = Offset(center.x, center.y + (outerArc + iconStroke / 2f - cornerRound / 2f) / 2f)
    // 扇形张开角：对称于正上方 270°，扇形与两条弧共用同一角度
    val halfSweep = 40f
    val startAngle = 270f - halfSweep
    val sweepAngle = halfSweep * 2f
    // 两条弧：不满格用灰色占位，与信号/电量一致
    for (i in 0 until 2) {
        val r = if (i == 0) innerArc else outerArc
        drawArc(
            color = if (level >= i + 2) color else trackColor,
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(fan.x - r, fan.y - r),
            size = Size(r * 2f, r * 2f),
            alpha = alpha,
            style = Stroke(width = iconStroke, cap = StrokeCap.Round),
        )
    }
    // 圆角扇形（第 1 格）：实心 + 圆角描边，与两条弧同圆心、同角度范围（朝上张开，顶点在下）
    drawArc(
        color = color,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = true,
        topLeft = Offset(fan.x - wedgeRadius, fan.y - wedgeRadius),
        size = Size(wedgeRadius * 2f, wedgeRadius * 2f),
        alpha = alpha,
    )
    drawArc(
        color = color,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = true,
        topLeft = Offset(fan.x - wedgeRadius, fan.y - wedgeRadius),
        size = Size(wedgeRadius * 2f, wedgeRadius * 2f),
        alpha = alpha,
        style = Stroke(width = cornerRound, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

// ---------------------------------------------------------------------------
// 演示页面
// ---------------------------------------------------------------------------

@Composable
private fun DuoDemo() {
    val colors = if (isSystemInDarkTheme()) DuoStatusColors.Dark else DuoStatusColors.Light

    var level by remember { mutableStateOf(0.64f) }
    var charging by remember { mutableStateOf(false) }
    var fastCharging by remember { mutableStateOf(false) }
    var powerSaving by remember { mutableStateOf(false) }
    var wifiLevel by remember { mutableStateOf(3) }
    var bars by remember { mutableStateOf(3) }

    val live = DuoStatusState(
        batteryLevel = level,
        isCharging = charging,
        isFastCharging = fastCharging,
        isPowerSaving = powerSaving,
        wifiLevel = wifiLevel,
        cellularBars = bars,
        networkLabel = if (wifiLevel == 0) "5G" else null,
    )

    val samples = listOf(
        "充电" to DuoStatusState(0.80f, isCharging = true, wifiLevel = 3, cellularBars = 4),
        "快充" to DuoStatusState(
            0.80f,
            isCharging = true,
            isFastCharging = true,
            wifiLevel = 3,
            cellularBars = 4
        ),
        "Wi-Fi" to DuoStatusState(0.64f, wifiLevel = 3, cellularBars = 3),
        "5G" to DuoStatusState(0.79f, wifiLevel = 0, cellularBars = 4, networkLabel = "5G"),
        "省电" to DuoStatusState(0.24f, isPowerSaving = true, wifiLevel = 2, cellularBars = 2),
        "低电量" to DuoStatusState(0.12f, wifiLevel = 1, cellularBars = 1),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        // 顶部：时间 + 实时状态标记
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                style = MaterialTheme.typography.titleLarge,
                color = colors.foreground,
            )
            Spacer(modifier = Modifier.weight(1f))
            DuoStatusMark(state = live, size = 46.dp, colors = colors)
        }

        Spacer(Modifier.height(28.dp))

        // 六种状态预览
        Text("状态预览", style = MaterialTheme.typography.titleMedium, color = colors.foreground)
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            samples.forEach { (label, s) ->
                StateSample(label, s, colors)
            }
        }

        Spacer(Modifier.height(28.dp))

        // 交互控制
        Text("交互演示", style = MaterialTheme.typography.titleMedium, color = colors.foreground)
        Spacer(Modifier.height(16.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = colors.foreground.copy(alpha = 0.05f),
        ) {
            Column(Modifier.padding(20.dp)) {
                DuoStatusMark(
                    state = live,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
//                    size = 88.dp,
                    colors = colors,
                )
                Spacer(Modifier.height(20.dp))

                LabeledSlider("电量", value = level) { level = it }
                LabeledSlider("信号格", value = bars / 4f) { bars = (it * 4f).roundToInt() }
                LabeledSlider("Wi-Fi 强度", value = wifiLevel / 3f) {
                    wifiLevel = (it * 3f).roundToInt()
                }

                ToggleRow("充电中", charging) { charging = it }
                ToggleRow("快充", fastCharging) { fastCharging = it }
                ToggleRow("省电模式", powerSaving) { powerSaving = it }
            }
        }
    }
}

@Composable
private fun StateSample(label: String, state: DuoStatusState, colors: DuoStatusColors) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        DuoStatusMark(state = state, size = 46.dp, colors = colors)
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            color = colors.foreground.copy(alpha = 0.7f),
            fontSize = 12.sp,
        )
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
