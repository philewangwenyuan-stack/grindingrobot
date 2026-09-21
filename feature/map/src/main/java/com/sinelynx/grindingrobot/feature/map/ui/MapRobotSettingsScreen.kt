package com.sinelynx.grindingrobot.feature.map.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sinelynx.grindingrobot.core.designsystem.theme.ArrowLeftIcon
import com.sinelynx.grindingrobot.feature.map.R
import com.sinelynx.grindingrobot.feature.map.viewmodel.WorkspaceDraft
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val STEP_BUTTON_REPEAT_DELAY_MS = 400L
private const val STEP_BUTTON_REPEAT_INTERVAL_MS = 100L

@Composable
fun MapRobotSettingsScreen(
    draft: WorkspaceDraft,
    onBack: () -> Unit,
    onDecreaseWidth: () -> Unit,
    onIncreaseWidth: () -> Unit,
    onWidthChange: (String) -> Unit,
    onDecreaseLength: () -> Unit,
    onIncreaseLength: () -> Unit,
    onLengthChange: (String) -> Unit,
    onDecreasePathSpacing: () -> Unit,
    onIncreasePathSpacing: () -> Unit,
    onPathSpacingChange: (String) -> Unit,
    onDecreaseCoverage: () -> Unit,
    onIncreaseCoverage: () -> Unit,
    onCoverageChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArrowLeftIcon(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .clickable(onClick = onBack)
            )
            Text(
                text = "机器人设置",
                fontSize = 18.sp,
                color = Color(0xFF202937),
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        PanelDivider()
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "机器人参数",
            fontSize = 12.sp,
            color = Color(0xFF9DA3AF),
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(10.dp))

        RobotValueRow(
            label = "机器人宽度",
            value = formatOneDecimal(draft.robotWidth),
            keyboardType = KeyboardType.Decimal,
            onDecrease = onDecreaseWidth,
            onIncrease = onIncreaseWidth,
            onValueChange = onWidthChange
        )
        Spacer(modifier = Modifier.height(8.dp))
        RobotValueRow(
            label = "机器人长度",
            value = formatOneDecimal(draft.robotLength),
            keyboardType = KeyboardType.Decimal,
            onDecrease = onDecreaseLength,
            onIncrease = onIncreaseLength,
            onValueChange = onLengthChange
        )
        Spacer(modifier = Modifier.height(8.dp))
        RobotValueRow(
            label = "规划路径间隔",
            value = formatOneDecimal(draft.robotPathSpacing),
            keyboardType = KeyboardType.Decimal,
            onDecrease = onDecreasePathSpacing,
            onIncrease = onIncreasePathSpacing,
            onValueChange = onPathSpacingChange
        )
        Spacer(modifier = Modifier.height(8.dp))
        RobotValueRow(
            label = "研磨覆盖率",
            value = draft.robotCoverage.toString(),
            keyboardType = KeyboardType.Number,
            onDecrease = onDecreaseCoverage,
            onIncrease = onIncreaseCoverage,
            onValueChange = onCoverageChange
        )

        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(1000.dp))
                .background(Color(0xFF00A0E9))
                .clickable(onClick = onSave),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "保 存",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun RobotValueRow(
    label: String,
    value: String,
    keyboardType: KeyboardType,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onValueChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color(0xFF6D737D),
            fontWeight = FontWeight.Medium
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton(
                iconRes = R.drawable.ic_change_left,
                accessibilityLabel = "减少$label",
                onAction = onDecrease
            )
            Spacer(modifier = Modifier.width(12.dp))
            val textFieldValue = remember(value) {
                TextFieldValue(text = value, selection = TextRange(value.length))
            }
            BasicTextField(
                value = textFieldValue,
                onValueChange = { changed -> onValueChange(changed.text) },
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                singleLine = true,
                textStyle = TextStyle(
                    color = Color(0xFF202937),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(Color(0xFF00A0E9)),
                modifier = Modifier
                    .width(80.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .border(1.dp, Color(0xFFE6E6E6), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            StepButton(
                iconRes = R.drawable.ic_change_right,
                accessibilityLabel = "增加$label",
                onAction = onIncrease
            )
        }
    }
}

@Composable
private fun StepButton(
    iconRes: Int,
    accessibilityLabel: String,
    onAction: () -> Unit
) {
    val currentOnAction = rememberUpdatedState(onAction)
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF3F4F6))
            .semantics {
                role = Role.Button
                contentDescription = accessibilityLabel
                onClick {
                    currentOnAction.value()
                    true
                }
            }
            .pointerInput(Unit) {
                coroutineScope {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        currentOnAction.value()
                        val repeatJob = launch {
                            delay(STEP_BUTTON_REPEAT_DELAY_MS)
                            while (true) {
                                currentOnAction.value()
                                delay(STEP_BUTTON_REPEAT_INTERVAL_MS)
                            }
                        }
                        try {
                            waitForUpOrCancellation()
                        } finally {
                            repeatJob.cancel()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = androidx.compose.ui.res.painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
    }
}

private fun formatOneDecimal(value: Double): String = String.format("%.1f", value)

