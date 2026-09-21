package com.sinelynx.grindingrobot.core.ui.component.loading

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.sinelynx.grindingrobot.core.designsystem.theme.AppTheme
import com.sinelynx.grindingrobot.core.designsystem.theme.SpaceVerticalSmall
import com.sinelynx.grindingrobot.core.ui.R
import com.sinelynx.grindingrobot.core.ui.component.text.AppText
import com.sinelynx.grindingrobot.core.ui.component.text.TextSize

/**
 * 页面加载中
 *
 * @param modifier 可选修饰符
 * @author Dreamj
 */
@Composable
fun PageLoading(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        MiLoadingMobile(
            borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        SpaceVerticalSmall()
        AppText(text = stringResource(R.string.loading), size = TextSize.BODY_MEDIUM)
    }
}

@Preview(showBackground = true)
@Composable
fun PageLoadingPreview() {
    AppTheme{
        PageLoading()
    }
}
