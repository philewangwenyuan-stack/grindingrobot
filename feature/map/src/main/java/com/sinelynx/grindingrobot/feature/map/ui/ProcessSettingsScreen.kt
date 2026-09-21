package com.sinelynx.grindingrobot.feature.map.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sinelynx.grindingrobot.core.designsystem.theme.ArrowLeftIcon
import com.sinelynx.grindingrobot.feature.map.R
import com.sinelynx.grindingrobot.feature.map.viewmodel.ConcreteStrength
import com.sinelynx.grindingrobot.feature.map.viewmodel.GrindingParameter
import com.sinelynx.grindingrobot.feature.map.viewmodel.ProcessType
import com.sinelynx.grindingrobot.feature.map.viewmodel.WorkspaceDraft

@Composable
fun ProcessSettingsScreen(
    draft: WorkspaceDraft,
    onBack: () -> Unit,
    onSelectProcessType: (ProcessType) -> Unit,
    onSelectGrindingParameter: (GrindingParameter) -> Unit,
    onSelectConcreteStrength: (ConcreteStrength) -> Unit,
    onDecreaseSpeed: () -> Unit,
    onIncreaseSpeed: () -> Unit,
    onSpeedChange: (String) -> Unit,
    onDecreasePressure: () -> Unit,
    onIncreasePressure: () -> Unit,
    onPressureChange: (String) -> Unit,
    onDecreaseDiscSpeed: () -> Unit,
    onIncreaseDiscSpeed: () -> Unit,
    onDiscSpeedChange: (String) -> Unit,
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
                text = "工艺设置",
                fontSize = 18.sp,
                color = Color(0xFF202937),
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        PanelDivider()
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "工艺参数",
            fontSize = 12.sp,
            color = Color(0xFF9DA3AF),
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(10.dp))

        SelectionRow(
            label = "类型",
            options = ProcessType.entries,
            selected = draft.processType,
            optionLabel = {
                when (it) {
                    ProcessType.ROUGH -> "粗磨"
                    ProcessType.FINE -> "精磨"
                    ProcessType.POLISH -> "抛光"
                }
            },
            onSelect = onSelectProcessType
        )
        Spacer(modifier = Modifier.height(8.dp))
        SelectionRow(
            label = "参数",
            options = GrindingParameter.optionsFor(draft.processType),
            selected = draft.grindingParameter,
            optionLabel = GrindingParameter::label,
            onSelect = onSelectGrindingParameter
        )
        Spacer(modifier = Modifier.height(8.dp))
        SelectionRow(
            label = "混凝土强度",
            options = ConcreteStrength.entries,
            selected = draft.concreteStrength,
            optionLabel = ConcreteStrength::name,
            onSelect = onSelectConcreteStrength
        )
        Spacer(modifier = Modifier.height(8.dp))
        ProcessValueRow(
            label = "速度（m/s）",
            value = formatSpeed(draft.processSpeed),
            keyboardType = KeyboardType.Decimal,
            canDecrease = draft.processSpeed > 0.0,
            canIncrease = draft.processSpeed < 0.2,
            onDecrease = onDecreaseSpeed,
            onIncrease = onIncreaseSpeed,
            onValueChange = onSpeedChange
        )
        Spacer(modifier = Modifier.height(8.dp))
        ProcessValueRow(
            label = "压力（kg）",
            value = formatPressure(draft.processPressure),
            keyboardType = KeyboardType.Number,
            canDecrease = draft.processPressure > 1.0,
            canIncrease = draft.processPressure < 477.0,
            onDecrease = onDecreasePressure,
            onIncrease = onIncreasePressure,
            onValueChange = onPressureChange
        )
        Spacer(modifier = Modifier.height(8.dp))
        ProcessValueRow(
            label = "磨盘转速（rpm）",
            value = draft.discSpeedRpm.toString(),
            keyboardType = KeyboardType.Number,
            canDecrease = draft.discSpeedRpm > 0,
            canIncrease = draft.discSpeedRpm < 1500,
            onDecrease = onDecreaseDiscSpeed,
            onIncrease = onIncreaseDiscSpeed,
            onValueChange = onDiscSpeedChange
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
                text = "保存",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun <T> SelectionRow(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit
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
        Row(
            modifier = Modifier
                .width(200.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFF3F4F6))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            options.forEach { option ->
                SelectionChip(
                    text = optionLabel(option),
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SelectionChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color.White else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            color = if (selected) Color(0xFF202937) else Color(0xFF9DA3AF),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ProcessValueRow(
    label: String,
    value: String,
    keyboardType: KeyboardType,
    canDecrease: Boolean,
    canIncrease: Boolean,
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
                enabled = canDecrease,
                onClick = onDecrease
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
                enabled = canIncrease,
                onClick = onIncrease
            )
        }
    }
}

@Composable
private fun StepButton(iconRes: Int, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .alpha(if (enabled) 1f else 0.4f)
            .background(Color(0xFFF3F4F6))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = androidx.compose.ui.res.painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
    }
}

private fun formatSpeed(value: Double): String = String.format("%.2f", value)

private fun formatPressure(value: Double): String = String.format("%.0f", value)
