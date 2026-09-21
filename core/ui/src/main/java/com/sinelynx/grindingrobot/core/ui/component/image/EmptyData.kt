package com.sinelynx.grindingrobot.core.ui.component.image

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.sinelynx.grindingrobot.core.designsystem.theme.AppTheme
import com.sinelynx.grindingrobot.core.ui.R

/**
 * 暂无数据状态视图
 *
 * @param modifier 修饰符
 * @param onRetryClick 重试点击回调
 * @author Dreamj
 */
@Composable
fun EmptyData(
    modifier: Modifier = Modifier,
    onRetryClick: (() -> Unit)? = null
) {
    Empty(
        modifier = modifier,
        message = R.string.empty_data,
        subtitle = R.string.empty_data_subtitle,
        icon = R.drawable.ic_empty_data,
        retryButtonText = R.string.click_retry,
        onRetryClick = onRetryClick
    )
}

/**
 * 暂无数据状态预览
 *
 * @author Dreamj
 */
@Preview(showBackground = true)
@Composable
fun EmptyDataPreview() {
    AppTheme {
        Empty(
            message = R.string.empty_data,
            icon = R.drawable.ic_empty_data,
            retryButtonText = R.string.click_retry,
        )
    }
}