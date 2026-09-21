package com.sinelynx.grindingrobot.feature.connect.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.sinelynx.grindingrobot.core.designsystem.theme.LogoIcon
import com.sinelynx.grindingrobot.core.util.log.LogUtils
import com.sinelynx.grindingrobot.core.util.toast.ToastUtils
import com.sinelynx.grindingrobot.feature.common.component.rememberCurrentTimeText
import com.sinelynx.grindingrobot.feature.connect.R
import com.sinelynx.grindingrobot.feature.connect.viewmodel.ConnectWifiViewModel
import com.sinelynx.grindingrobot.feature.common.R as CommonR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val TEST_WIFI_PASSWORD = "12345678"

@Composable
fun ConnectWifiRoute(
    onBackHomeClick: () -> Unit = {},
    onWifiConnected: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ConnectWifiViewModel = hiltViewModel()
) {
    val currentTcpEndpoint by viewModel.currentTcpEndpoint.collectAsState()
    val isTcpConnecting by viewModel.isTcpConnecting.collectAsState()
    val isTcpConnected by viewModel.isTcpConnected.collectAsState()
    ConnectWifiScreen(
        onBackHomeClick = onBackHomeClick,
        onWifiConnected = onWifiConnected,
        currentTcpEndpoint = currentTcpEndpoint,
        isTcpConnecting = isTcpConnecting,
        isTcpConnected = isTcpConnected,
        onTcpEndpointSelected = viewModel::switchTcpEndpoint,
        modifier = modifier
    )
}

@Composable
fun ConnectWifiScreen(
    onBackHomeClick: () -> Unit,
    onWifiConnected: (String) -> Unit,
    currentTcpEndpoint: String,
    isTcpConnecting: Boolean,
    isTcpConnected: Boolean,
    onTcpEndpointSelected: (String) -> Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentTime = rememberCurrentTimeText()
    val lifecycleOwner = LocalLifecycleOwner.current
    val wifiManager = remember(context) {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf(ConnectPage.Entry) }
    var uiState by remember { mutableStateOf(WifiSearchUiState()) }
    var scanJob by remember { mutableStateOf<Job?>(null) }
    var pendingScanAfterPermission by remember { mutableStateOf(false) }
    var pendingSystemWifiCheck by remember { mutableStateOf(false) }
    var connectMode by remember { mutableStateOf(ConnectMode.NearbyDevice) }

    fun handleConnectedFgWifi(ssid: String) {
        pendingSystemWifiCheck = false
        page = ConnectPage.Search
        connectMode = ConnectMode.NearbyDevice
        uiState = uiState.copy(
            isSearching = false,
            hasTimedOut = false,
            wifiList = (uiState.wifiList + ssid).distinct().sorted(),
            connectingSsid = null,
            connectedSsid = ssid
        )
        onWifiConnectedInternal(ssid)
        onWifiConnected(ssid)
    }

    fun performSearch() {
        scanJob?.cancel()
        page = ConnectPage.Search
        connectMode = ConnectMode.NearbyDevice
        uiState = WifiSearchUiState(isSearching = true)
        scanJob = scope.launch {
            val discovered = discoverSswifi(
                context = context,
                wifiManager = wifiManager,
                timeoutMs = 30_000L
            )
            uiState = if (discovered.isEmpty()) {
                WifiSearchUiState(isSearching = false, hasTimedOut = true)
            } else {
                WifiSearchUiState(isSearching = false, wifiList = discovered)
            }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val granted = hasWifiScanPermission(context)
        if (granted && pendingScanAfterPermission) {
            performSearch()
        }
        pendingScanAfterPermission = false
    }
    fun startSearch() {
        if (!hasWifiScanPermission(context)) {
            pendingScanAfterPermission = true
            permissionLauncher.launch(requiredWifiPermissions())
            return
        }
        performSearch()
    }

    DisposableEffect(lifecycleOwner, pendingSystemWifiCheck) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && pendingSystemWifiCheck) {
                getConnectedFgSsid(wifiManager)?.let { ssid ->
                    handleConnectedFgWifi(ssid)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFD1DBE8), Color(0xFFEBEEF2))
                )
            )
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        if (page == ConnectPage.Entry) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    LogoIcon(size = 24.dp, res = CommonR.drawable.ic_topabr_logo)
                }
                Text(
                    text = stringResource(R.string.connect_app_title),
                    color = Color(0xFF202937),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }

            Text(
                text = currentTime,
                color = Color(0xFF202937),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopEnd)
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RobotIllustration()
                Spacer(modifier = Modifier.height(26.dp))
                Text(
                    text = stringResource(R.string.connect_robot_title),
                    color = Color(0xFF202937),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.Both
                        )
                    )
                )
            }

            Column(
                modifier = Modifier
                    .width(700.dp)
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 22.dp)
            ) {
                Text(
                    text = stringResource(R.string.connect_add_device),
                    color = Color(0xFF202937),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(98.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFFFCFDFE))
                        .border(2.dp, Color.White, RoundedCornerShape(24.dp))
                        .clickable { startSearch() },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+",
                            color = Color(0xFFE5E7EB),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            WifiSearchContent(
                uiState = uiState,
                currentTcpEndpoint = currentTcpEndpoint,
                isTcpConnecting = isTcpConnecting,
                isTcpConnected = isTcpConnected,
                connectMode = connectMode,
                onBackHomeClick = onBackHomeClick,
                onRefresh = { startSearch() },
                onConnectModeSelected = { connectMode = it },
                onTcpEndpointSelected = onTcpEndpointSelected,
                onConnect = { ssid ->
                    scope.launch {
                        if (shouldGuideToSystemWifiSettings(context)) {
                            pendingSystemWifiCheck = true
                            openWifiSettings(context, ssid)
                            return@launch
                        }
                        uiState = uiState.copy(connectingSsid = ssid)
                        val success = connectToWifi(context, wifiManager, ssid)
                        uiState = uiState.copy(
                            connectingSsid = null,
                            connectedSsid = if (success) ssid else uiState.connectedSsid
                        )
                        if (success) {
                            onWifiConnectedInternal(ssid)
                            onWifiConnected(ssid)
                        } else {
                            LogUtils.w("ConnectWifiScreen", "WiFi连接失败: ssid=$ssid")
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun WifiSearchContent(
    uiState: WifiSearchUiState,
    currentTcpEndpoint: String,
    isTcpConnecting: Boolean,
    isTcpConnected: Boolean,
    connectMode: ConnectMode,
    onBackHomeClick: () -> Unit,
    onRefresh: () -> Unit,
    onConnectModeSelected: (ConnectMode) -> Unit,
    onTcpEndpointSelected: (String) -> Boolean,
    onConnect: (String) -> Unit
) {
    var tcpEndpointInput by remember(currentTcpEndpoint) {
        mutableStateOf(currentTcpEndpoint)
    }
    val fixedIpScrollState = rememberScrollState()
    val density = LocalDensity.current
    val isImeVisible = WindowInsets.ime.getBottom(density) > 0
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(isImeVisible, connectMode) {
        if (!isImeVisible || connectMode != ConnectMode.FixedIp) {
            fixedIpScrollState.scrollTo(0)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(onClick = onBackHomeClick).padding(top = 20.dp)
        ) {
            Icon(
                painter = painterResource(CommonR.drawable.ic_back),
                contentDescription = "返回图标",
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = stringResource(R.string.connect_back_home),
                color = Color(0xFF202937),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.height(42.dp))
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(80.dp)
                .clip(CircleShape)
                .background(if (uiState.hasTimedOut) Color(0xFFD82B2A) else Color(0xFF00A0E9)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(CommonR.drawable.ic_search_find), contentDescription = "发现设备图标",
                modifier = Modifier.size(32.dp),
                tint = Color.White
                )
        }

        Spacer(modifier = Modifier.height(16.dp))
        ConnectModeTabs(
            selectedMode = connectMode,
            onModeSelected = onConnectModeSelected,
            modifier = Modifier
                .width(573.dp)
                .align(Alignment.CenterHorizontally)
        )

        when (connectMode) {
            ConnectMode.NearbyDevice -> {
                if (uiState.hasTimedOut) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = stringResource(R.string.connect_not_found_device),
                        color = Color(0xFF202937),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = stringResource(R.string.connect_not_found_tips),
                        color = Color(0x80202937),
                        fontSize = 20.sp,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(500.dp),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    PrimaryActionButton(
                        text = stringResource(R.string.connect_refresh),
                        onClick = onRefresh,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Spacer(modifier = Modifier.height(64.dp))
                } else {
                    Spacer(modifier = Modifier.height(28.dp))
                    LazyColumn(
                        modifier = Modifier
                            .width(573.dp)
                            .align(Alignment.CenterHorizontally)
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(uiState.wifiList) { ssid ->
                            WifiItemRow(
                                ssid = ssid,
                                connecting = uiState.connectingSsid == ssid,
                                connected = uiState.connectedSsid == ssid,
                                onConnect = { onConnect(ssid) }
                            )
                        }
                    }
                }
            }

            ConnectMode.FixedIp -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(fixedIpScrollState)
                        .imePadding(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(28.dp))
                    FixedIpConnectionRow(
                        endpoint = tcpEndpointInput,
                        connecting = isTcpConnecting,
                        connected = isTcpConnected &&
                            tcpEndpointInput.trim() == currentTcpEndpoint.trim(),
                        onEndpointChange = { tcpEndpointInput = it },
                        onConnect = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            onTcpEndpointSelected(tcpEndpointInput)
                        },
                        modifier = Modifier.width(573.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    if (uiState.isSearching && connectMode == ConnectMode.NearbyDevice) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0x80000000)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 5.dp,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}

@Composable
private fun ConnectModeTabs(
    selectedMode: ConnectMode,
    onModeSelected: (ConnectMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier) {
        ConnectModeTab(
            text = stringResource(R.string.connect_nearby_device),
            selected = selectedMode == ConnectMode.NearbyDevice,
            onClick = { onModeSelected(ConnectMode.NearbyDevice) },
            modifier = Modifier.width(96.dp)
        )
        Spacer(modifier = Modifier.width(48.dp))
        ConnectModeTab(
            text = stringResource(R.string.connect_fixed_ip),
            selected = selectedMode == ConnectMode.FixedIp,
            onClick = { onModeSelected(ConnectMode.FixedIp) },
            modifier = Modifier.width(144.dp)
        )
    }
}

@Composable
private fun ConnectModeTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            color = Color(0xFF202937).copy(alpha = if (selected) 1f else 0.5f),
            fontSize = 24.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 5.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(if (selected) Color(0xFF202937) else Color.Transparent)
        )
    }
}

@Composable
private fun FixedIpConnectionRow(
    endpoint: String,
    connecting: Boolean,
    connected: Boolean,
    onEndpointChange: (String) -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(80.dp)
            .shadow(12.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = endpoint,
            onValueChange = onEndpointChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            textStyle = TextStyle(
                color = Color(0xFF202937),
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium
            ),
            cursorBrush = SolidColor(Color(0xFF00A0E9)),
            modifier = Modifier
                .weight(1f)
                .padding(end = 24.dp),
            decorationBox = { innerTextField ->
                if (endpoint.isEmpty()) {
                    Text(
                        text = stringResource(R.string.connect_ip_hint),
                        color = Color(0xFFCCCCCC),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                innerTextField()
            }
        )
        PrimaryActionButton(
            text = when {
                connecting -> stringResource(R.string.connect_connecting)
                connected -> stringResource(R.string.connect_connected)
                else -> stringResource(R.string.connect_connect)
            },
            onClick = onConnect,
            enabled = !connecting && !connected
        )
    }
}

@Composable
private fun WifiItemRow(
    ssid: String,
    connecting: Boolean,
    connected: Boolean,
    onConnect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .shadow(12.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = ssid,
            color = Color(0xFF202937),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        val buttonText = when {
            connected -> stringResource(R.string.connect_connected)
            connecting -> stringResource(R.string.connect_connecting)
            else -> stringResource(R.string.connect_connect)
        }
        PrimaryActionButton(
            text = buttonText,
            onClick = onConnect,
            enabled = !connecting && !connected
        )
    }
}

@Composable
private fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(width = 120.dp, height = 48.dp)
            .clip(RoundedCornerShape(1000.dp))
            .background(
                if (enabled) Color(0xFF00A0E9) else Color(0xFF9DA3AF)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RobotIllustration() {
    Image(
        painter = painterResource(id = R.drawable.ic_robot_big_connect),
        contentDescription = stringResource(R.string.connect_robot_illustration),
        modifier = Modifier.size(width = 220.dp, height = 260.dp)
    )
}

private enum class ConnectPage {
    Entry,
    Search
}

private enum class ConnectMode {
    NearbyDevice,
    FixedIp
}

private data class WifiSearchUiState(
    val isSearching: Boolean = false,
    val hasTimedOut: Boolean = false,
    val wifiList: List<String> = emptyList(),
    val connectingSsid: String? = null,
    val connectedSsid: String? = null
)

private suspend fun discoverSswifi(
    context: Context,
    wifiManager: WifiManager,
    timeoutMs: Long
): List<String> = withContext(Dispatchers.IO) {
    if (!isLocationEnabled(context)) return@withContext emptyList()
    runCatching { wifiManager.isWifiEnabled = true }
    val endAt = SystemClock.elapsedRealtime() + timeoutMs
    val found = linkedSetOf<String>()
    while (SystemClock.elapsedRealtime() < endAt && found.isEmpty()) {
        runCatching { wifiManager.startScan() }
        delay(1300L)
        val candidates = runCatching { wifiManager.scanResults }.getOrDefault(emptyList())
            .map { result ->
                result.SSID.trim().ifEmpty { result.BSSID?.trim().orEmpty() }
            }
            .filter { it.startsWith("FG", ignoreCase = true) }
            .sorted()
        found.addAll(candidates)
        if (found.isNotEmpty()) break
        delay(1200L)
    }
    found.toList()
}

@Suppress("DEPRECATION")
private suspend fun connectToWifi(
    context: Context,
    wifiManager: WifiManager,
    ssid: String
): Boolean = withContext(Dispatchers.IO) {
    val targetSsid = ssid.trim()
    if (targetSsid.isEmpty()) return@withContext false
    runCatching { wifiManager.isWifiEnabled = true }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !isSystemApp(context)) {
        return@withContext connectToWifiBySpecifier(context, wifiManager, targetSsid)
    }

    val scanResult = runCatching {
        wifiManager.scanResults.firstOrNull { it.SSID?.trim() == targetSsid }
    }.getOrNull()
    val secure = isSecureWifi(scanResult)
    val config = WifiConfiguration().apply {
        SSID = "\"$targetSsid\""
        if (secure) {
            preSharedKey = "\"$TEST_WIFI_PASSWORD\""
            allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
        } else {
            allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
        }
    }

    val networkId = runCatching { wifiManager.addNetwork(config) }.getOrDefault(-1)
    LogUtils.d("ConnectWifiScreen", "legacy addNetwork result=$networkId, ssid=$targetSsid, secure=$secure")
    val finalNetworkId = if (networkId != -1) {
        networkId
    } else {
        runCatching {
            wifiManager.configuredNetworks
                ?.firstOrNull { stripQuotes(it.SSID) == targetSsid }
                ?.networkId
        }.getOrNull() ?: -1
    }
    if (finalNetworkId == -1) return@withContext false
    val enabled = runCatching { wifiManager.enableNetwork(finalNetworkId, true) }.getOrDefault(false)
    LogUtils.d("ConnectWifiScreen", "legacy enableNetwork result=$enabled, networkId=$finalNetworkId")
    if (!enabled) return@withContext false
    runCatching { wifiManager.disconnect() }
    runCatching { wifiManager.reconnect() }
    runCatching { wifiManager.saveConfiguration() }
    true
}

private fun getConnectedFgSsid(wifiManager: WifiManager): String? {
    val ssid = runCatching {
        stripQuotes(wifiManager.connectionInfo?.ssid)
    }.getOrDefault("")
    return ssid.takeIf { it.startsWith("FG", ignoreCase = true) }
}

private fun stripQuotes(ssid: String?): String = ssid?.trim()?.trim('"').orEmpty()

private fun shouldGuideToSystemWifiSettings(context: Context): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !isSystemApp(context)
}

private fun openWifiSettings(context: Context, ssid: String) {
    ToastUtils.showWarning("请在系统 WiFi 设置中连接 $ssid")
    val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
        .onFailure { error ->
            LogUtils.w("ConnectWifiScreen", "打开WiFi设置失败: ssid=$ssid, error=${error.message}")
        }
}

private suspend fun connectToWifiBySpecifier(
    context: Context,
    wifiManager: WifiManager,
    ssid: String
): Boolean {
    val scanResult = runCatching {
        wifiManager.scanResults.firstOrNull { it.SSID?.trim() == ssid }
    }.getOrNull()
    val secure = isSecureWifi(scanResult)
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false

    val specifierBuilder = WifiNetworkSpecifier.Builder()
        .setSsid(ssid)
    if (secure) {
        specifierBuilder.setWpa2Passphrase(TEST_WIFI_PASSWORD)
    }
    val specifier = specifierBuilder.build()
    val request = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .setNetworkSpecifier(specifier)
        .build()

    val connected = runCatching {
        withTimeoutOrNull(15_000L) {
            suspendCancellableCoroutine { continuation ->
                var resumed = false
                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        runCatching { connectivityManager.unregisterNetworkCallback(this) }
                        connectivityManager.bindProcessToNetwork(network)
                        if (!resumed) {
                            resumed = true
                            continuation.resume(true)
                        }
                    }

                    override fun onUnavailable() {
                        runCatching { connectivityManager.unregisterNetworkCallback(this) }
                        if (!resumed) {
                            resumed = true
                            continuation.resume(false)
                        }
                    }
                }
                runCatching { connectivityManager.requestNetwork(request, callback) }
                    .onFailure {
                        if (!resumed) {
                            resumed = true
                            continuation.resume(false)
                        }
                    }
                continuation.invokeOnCancellation {
                    runCatching { connectivityManager.unregisterNetworkCallback(callback) }
                }
            }
        } ?: false
    }.getOrDefault(false)

    LogUtils.d("ConnectWifiScreen", "specifier connect result=$connected, ssid=$ssid, secure=$secure")
    return connected
}

private fun isSecureWifi(scanResult: ScanResult?): Boolean {
    val caps = scanResult?.capabilities?.uppercase().orEmpty()
    return caps.contains("WEP") || caps.contains("WPA")
}

private fun isSystemApp(context: Context): Boolean {
    val flags = context.applicationInfo.flags
    return (flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
        (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
}

private fun requiredWifiPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    arrayOf(
        Manifest.permission.NEARBY_WIFI_DEVICES,
        Manifest.permission.ACCESS_FINE_LOCATION
    )
} else {
    arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
}

private fun hasWifiScanPermission(context: Context): Boolean {
    val hasFine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val hasCoarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val hasLocation = hasFine || hasCoarse
    if (!hasLocation) return false
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    val hasNearbyWifi = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.NEARBY_WIFI_DEVICES
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    return hasNearbyWifi
}

private fun isLocationEnabled(context: Context): Boolean {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return false
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        locationManager.isLocationEnabled
    } else {
        true
    }
}

private fun onWifiConnectedInternal(ssid: String) {
    // TODO: 在这里补充 WiFi 连接成功后的后续业务逻辑，例如保存状态、上报或自动跳转。
}
