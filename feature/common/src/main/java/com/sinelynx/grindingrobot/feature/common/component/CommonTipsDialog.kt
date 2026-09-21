package com.sinelynx.grindingrobot.feature.common.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 全局覆盖弹窗容器（公共组件）。
 *
 * - 实现方式对齐 ConfirmOverlayDialog：Dialog + 全屏覆盖
 * - 面板样式对齐 Figma：白底 + 48dp 圆角
 */
@Composable
fun CommonTipsDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dialogWidth: Dp = 480.dp,
    dialogHeight: Dp = 400.dp,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    dimColor: Color = Color(0x4D000000),
    content: @Composable BoxScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = dismissOnBackPress,
            dismissOnClickOutside = dismissOnClickOutside,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(dimColor)
                .then(modifier),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(width = dialogWidth, height = dialogHeight)
                    .clip(RoundedCornerShape(48.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center,
                content = content
            )
        }
    }
}

/**
 * 连接失败弹窗（Figma node: 0:110）。
 *
 * 设计特征：
 * - 白底 48dp 圆角面板（480x368）
 * - 红色失败图标 + “连接失败”
 * - 底部蓝色主按钮（384x64）
 */
@Composable
fun ConnectFailedOverlayDialog(
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit = onConfirm
) {
    CommonTipsDialog(
        onDismissRequest = onDismissRequest,
        dialogWidth = 480.dp,
        dialogHeight = 368.dp,
        dimColor = Color.Transparent
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 56.dp)
                    .size(80.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(Color(0xFFD82B2A)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "×",
                    color = Color.White,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "连接失败",
                color = Color(0xFF202937),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp)
            )

            Box(
                modifier = Modifier
                    .padding(top = 78.dp)
                    .width(384.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(1000.dp))
                    .background(Color(0xFF00A0E9))
                    .clickable(onClick = onConfirm),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "确 定",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
