package com.sinelynx.grindingrobot.feature.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sinelynx.grindingrobot.feature.common.component.CommonTipsDialog

/** Map-module confirmation content using the existing shared white overlay shell. */
@Composable
internal fun DrawingConfirmDialog(title:String,message:String,confirmText:String,onConfirm:()->Unit,onCancel:()->Unit,
    cancelText:String="取消",onDismiss:()->Unit=onCancel) {
    val config=LocalConfiguration.current
    CommonTipsDialog(onDismissRequest=onDismiss,dialogWidth=minOf(480,config.screenWidthDp-24).dp,
        dialogHeight=minOf(400,config.screenHeightDp-24).dp) {
        Column(Modifier.fillMaxSize().padding(horizontal=28.dp,vertical=20.dp),horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(64.dp).background(Color(0xFFFFE2E2),CircleShape),contentAlignment=Alignment.Center) {
                Text("!",fontSize=32.sp,color=Color(0xFFD82B2A),fontWeight=FontWeight.Bold)
            }
            Text(title,color=Color(0xFF202937),fontSize=24.sp,lineHeight=30.sp,fontWeight=FontWeight.Bold,
                fontFamily=FontFamily.Default,textAlign=TextAlign.Center)
            Text(message,Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),color=Color(0xFF9DA3AF),
                fontSize=20.sp,lineHeight=28.sp,fontWeight=FontWeight.Normal,fontFamily=FontFamily.Default,textAlign=TextAlign.Center)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                listOf(cancelText to onCancel,confirmText to onConfirm).forEachIndexed { index,(label,action)->
                    Surface(onClick=action,modifier=Modifier.weight(1f).height(56.dp),shape=RoundedCornerShape(1000.dp),
                        color=if(index==0)Color(0xFFE5E7EB)else Color(0xFFD82B2A)) {
                        Box(contentAlignment=Alignment.Center){Text(label,color=if(index==0)Color(0xFF647080)else Color.White,
                            fontSize=18.sp,lineHeight=24.sp,fontFamily=FontFamily.Default,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)}
                    }
                }
            }
        }
    }
}
