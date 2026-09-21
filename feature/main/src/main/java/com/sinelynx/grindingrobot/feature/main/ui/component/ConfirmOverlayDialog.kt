package com.sinelynx.grindingrobot.feature.main.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sinelynx.grindingrobot.feature.map.R

@Composable
fun ConfirmOverlayDialog(
    title: String,
    message: String,
    confirmText: String,
    cancelText: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x4D000000)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .size(width = 480.dp, height = 400.dp)
                    .clip(RoundedCornerShape(48.dp))
                    .background(Color.White)
                    .padding(horizontal = 40.dp, vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(Color(0xFFFFE2E2)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_warning),
                        contentDescription = "警告",
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = title,
                    color = Color(0xFF202937),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = message,
                    textAlign = TextAlign.Center,
                    color = Color(0x809DA3AF),
                    fontSize = 20.sp
                )

                Spacer(modifier = Modifier.weight(1f))

                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    ConfirmDialogActionButton(
                        text = cancelText,
                        background = Color(0xFFE5E7EB),
                        textColor = Color(0x80202937),
                        onClick = onCancel
                    )
                    ConfirmDialogActionButton(
                        text = confirmText,
                        background = Color(0xFFD82B2A),
                        textColor = Color.White,
                        onClick = onConfirm
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfirmDialogActionButton(
    text: String,
    background: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = 180.dp, height = 64.dp)
            .clip(RoundedCornerShape(1000.dp))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

