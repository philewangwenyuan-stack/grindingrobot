package com.sinelynx.grindingrobot.feature.main.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 软件下载与校验的 Loading 弹框
 *
 * 设计稿布局：
 * - 居中圆形 Loading，等待期间不展示无法稳定更新的百分比
 * - "正在准备软件升级..." 文本
 * - 底部多行提示词："请不要关机，断开网络，关闭应用\n保持屏幕亮起"
 */
@Composable
fun DownloadProgressDialog(
    onDismiss: () -> Unit = {}
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x4D000000))
                .clickable(
                    indication = null,
                    interactionSource = null,
                    onClick = {} // 不允许点击外部消失
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .size(width = 480.dp, height = 360.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color.White)
                    .padding(horizontal = 40.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.weight(1f))

                // 不定进度 Loading 覆盖下载、续传和 APK 校验等待时间。
                CircularProgressIndicator(
                    modifier = Modifier.size(100.dp),
                    color = Color(0xFF00A0E9),
                    trackColor = Color(0xFFEFF6FF),
                    strokeWidth = 8.dp,
                    strokeCap = StrokeCap.Round
                )

                Spacer(modifier = Modifier.height(32.dp))

                // 不显示百分比，避免已缓存文件或调试拦截器导致的 0% 假象。
                Text(
                    text = "正在准备软件升级...",
                    color = Color(0xFF202937),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 提示文字
                Text(
                    text = "请不要关机，断开网络，关闭应用\n保持屏幕亮起",
                    color = Color(0xFF9DA3AF),
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
