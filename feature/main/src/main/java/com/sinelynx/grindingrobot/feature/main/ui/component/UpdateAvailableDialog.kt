package com.sinelynx.grindingrobot.feature.main.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sinelynx.grindingrobot.feature.main.R

/**
 * 系统更新弹框 - 发现新版本后提示用户更新
 *
 * 设计稿布局：
 * - 右上角关闭按钮
 * - 居中火箭图标
 * - 「发现新版本」标题 + 版本号
 * - 底部蓝色「更 新」按钮
 */
@Composable
fun UpdateAvailableDialog(
    versionName: String,
    releaseNotes: String?,
    updateRequired: Boolean,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x4D000000))
                .clickable(
                    indication = null,
                    interactionSource = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(width = 540.dp, height = 390.dp)
                    .clickable(
                        indication = null,
                        interactionSource = null,
                        onClick = {} // 阻止穿透点击
                    )
            ) {
                // 弹框主体卡片
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color.White)
                        .padding(horizontal = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(20.dp))

                    // 火箭图标 - 悬浮在卡片顶部
                    Image(
                        painter = painterResource(id = R.drawable.ic_upgrade_rocket),
                        contentDescription = "升级",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(120.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // 标题
                    Text(
                        text = if (updateRequired) "发现新版本，建议尽快更新" else "发现新版本",
                        color = Color(0xFF202937),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 版本号
                    Text(
                        text = versionName,
                        color = Color(0xFF9DA3AF),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Normal
                    )

                    if (!releaseNotes.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = releaseNotes,
                            color = Color(0xFF596579),
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // 更新按钮
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(1000.dp))
                            .background(Color(0xFF00A0E9))
                            .clickable(onClick = onUpdate),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "更 新",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 4.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }

                // 关闭按钮 - 右上角
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 20.dp, end = 20.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFD6D9DE))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = com.sinelynx.grindingrobot.core.designsystem.R.drawable.ic_close),
                        contentDescription = "关闭",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
