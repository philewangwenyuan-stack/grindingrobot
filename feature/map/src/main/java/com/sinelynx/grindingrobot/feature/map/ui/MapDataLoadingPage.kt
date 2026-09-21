package com.sinelynx.grindingrobot.feature.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MapDataLoadingPage(
    modifier: Modifier = Modifier,
    text: String = "地图加载中..."
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFFE7EBF2), Color(0xFFADB6C2)),
                    radius = 900f
                )
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(40.dp),
            color = Color(0xFF00A0E9),
            strokeWidth = 4.dp
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = text,
            color = Color(0xFF202937),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
