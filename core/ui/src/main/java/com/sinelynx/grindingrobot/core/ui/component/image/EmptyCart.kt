package com.sinelynx.grindingrobot.core.ui.component.image

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.sinelynx.grindingrobot.core.designsystem.theme.AppTheme
import com.sinelynx.grindingrobot.core.ui.R

/**
 * 购物车为空状态视图
 *
 * @param modifier 修饰符
 * @param onRetryClick 重试点击回调
 * @author Dreamj
 */
@Composable
fun EmptyCart(
    modifier: Modifier = Modifier,
    onRetryClick: (() -> Unit)? = null
) {
    Empty(
        modifier = modifier,
        message = R.string.empty_cart,
        subtitle = R.string.empty_cart_subtitle,
        icon = R.drawable.ic_empty_cart,
        retryButtonText = R.string.empty_cart_btn,
        onRetryClick = onRetryClick
    )
}

/**
 * 购物车为空状态预览
 *
 * @author Dreamj
 */
@Preview(showBackground = true)
@Composable
fun EmptyCartPreview() {
    AppTheme {
        Empty(
            message = R.string.empty_cart,
            icon = R.drawable.ic_empty_cart,
            retryButtonText = R.string.click_retry,
        )
    }
}
