package com.lnu.tclhdmilauncher.launcher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text


/**
 * TV 介面用的短時間倒數線性進度條。
 *
 * 元件在首次進入組合時開始倒數；[restartKey] 改變時會重設並重新開始。
 *
 * @param durationMillis 倒數總時間，必須大於 0。
 * @param restartKey 改變此值即可重啟倒數，例如遞增的執行序號。
 * @param thickness 進度條高度。TV 遠距觀看建議使用較粗的進度條。
 * @param indicatorColor 已完成進度的顏色。
 * @param trackColor 尚未完成軌道的顏色，建議與背景保持足夠對比。
 * @param onFinished 倒數正常結束時呼叫一次；若倒數被重啟或離開畫面則不呼叫。
 */
@Composable
fun ElegantCountdownProgress(
    durationMillis: Int = 10_000,
    restartKey: Any? = Unit,

    thickness: Dp = 8.dp,
    indicatorColor: Color = TvMaterialTheme.colorScheme.primary,
    trackColor: Color = TvMaterialTheme.colorScheme.surfaceVariant,
    modifier: Modifier = Modifier,
    onFinished: () -> Unit,
) {
    require(durationMillis > 0) { "durationMillis 必須大於 0" }
    val progressAnimation = remember { Animatable(1f) }
    val currentOnFinished by rememberUpdatedState(onFinished)

    // Animatable 插值進度；lambda 讀值避免每個動畫影格都重組整個 TV 畫面。
    LaunchedEffect(durationMillis, restartKey) {
        progressAnimation.snapTo(1f)
        progressAnimation.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = durationMillis,
                easing = LinearEasing,
            ),
        )
        currentOnFinished()
    }

    val progressProvider = remember(progressAnimation) { { progressAnimation.value } }

    LinearProgressIndicator(
        progress = progressProvider,
        modifier = modifier.fillMaxWidth().height(thickness),
        color = indicatorColor,
        trackColor = trackColor,
    )
}

/**
 * 顯示由 Activity 既有秒數計時器驅動的線性進度。每次收到新的目標進度時，
 * 以線性動畫平滑補間到目標，避免直接逐秒跳格。
 */
@Composable
internal fun ActivityCountdownProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    animationDurationMillis: Int = 1_000,
    thickness: Dp = 8.dp,
) {
    require(animationDurationMillis >= 0) { "animationDurationMillis 不可小於 0" }

    val progressAnimation = remember { Animatable(0f) }
    val targetProgress = progress.coerceIn(0f, 1f)

    LaunchedEffect(targetProgress) {
        progressAnimation.animateTo(
            targetValue = targetProgress,
            animationSpec = tween(
                durationMillis = animationDurationMillis,
                easing = LinearEasing,
            ),
        )
    }

    val progressProvider = remember(progressAnimation) { { progressAnimation.value } }

    LinearProgressIndicator(
        progress = progressProvider,
        modifier = modifier.fillMaxWidth().height(thickness),
        color = TvMaterialTheme.colorScheme.primary,
        trackColor = TvMaterialTheme.colorScheme.surfaceVariant,
    )
}

/**
 * 可直接放入 TV Compose 畫面的使用範例。按下遙控器確認鍵即可重新啟動倒數。
 */
@Composable
fun ElegantCountdownProgressExample(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var restartKey by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier.padding(horizontal = 48.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            text = "即將進入待機",
            style = TvMaterialTheme.typography.headlineSmall,
        )
        ElegantCountdownProgress(
            durationMillis = 10_000,
            restartKey = restartKey,
            modifier = Modifier.fillMaxWidth(),
            onFinished = onFinished,
        )
        Button(onClick = { restartKey += 1 }) {
            Text("重新開始")
        }
    }
}
