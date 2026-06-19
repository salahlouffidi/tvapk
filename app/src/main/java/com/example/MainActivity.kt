package com.example

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.MyApplicationTheme

private const val TARGET_URL = "https://salahlouffidi.github.io/tv.html"

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        WebContainerScreen()
      }
    }
  }
}

@Composable
fun WebContainerScreen() {
  val context = LocalContext.current
  val isNetworkConnected by rememberIsNetworkAvailable()
  
  var webViewRef by remember { mutableStateOf<WebView?>(null) }
  var isLoading by remember { mutableStateOf(true) }
  var progressValue by remember { mutableIntStateOf(0) }
  var canGoBackState by remember { mutableStateOf(false) }
  var canGoForwardState by remember { mutableStateOf(false) }
  var loadErrorOccurred by remember { mutableStateOf(false) }
  var showControlPanel by remember { mutableStateOf(true) }
  var currentUrlState by remember { mutableStateOf(TARGET_URL) }

  // Intercept the back press gestures
  BackHandler(enabled = canGoBackState) {
    webViewRef?.let {
      if (it.canGoBack()) {
        it.goBack()
      }
    }
  }

  // Periodic URL status check
  LaunchedEffect(webViewRef) {
    while (true) {
      kotlinx.coroutines.delay(800)
      webViewRef?.let {
        canGoBackState = it.canGoBack()
        canGoForwardState = it.canGoForward()
        it.url?.let { currentUrl ->
          currentUrlState = currentUrl
        }
      }
    }
  }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .background(Color(0xFF0F172A)) // Aesthetic Slate Dark Theme background
    ) {
      
      if (!isNetworkConnected) {
        // Offline Screen
        OfflineScreen(onRetry = {
          webViewRef?.reload()
        })
      } else if (loadErrorOccurred) {
        // Error / Disconnect Overlay Screen
        ErrorScreen(
          message = "We couldn't load the requested page. Please verify your connection or refresh.",
          onReload = {
            loadErrorOccurred = false
            isLoading = true
            webViewRef?.loadUrl(TARGET_URL)
          }
        )
      } else {
        // Main Container WebView View
        AndroidView(
          modifier = Modifier
            .fillMaxSize()
            .testTag("main_webview"),
          factory = { ctx ->
            WebView(ctx).apply {
              layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
              )
              
              webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                  isLoading = true
                  loadErrorOccurred = false
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                  isLoading = false
                  view?.let {
                    canGoBackState = it.canGoBack()
                    canGoForwardState = it.canGoForward()
                    it.url?.let { currentUrl ->
                      currentUrlState = currentUrl
                    }
                  }
                }

                override fun onReceivedError(
                  view: WebView?,
                  request: WebResourceRequest?,
                  error: WebResourceError?
                ) {
                  // Only report error for main frame load failures
                  if (request?.isForMainFrame == true) {
                    loadErrorOccurred = true
                    isLoading = false
                  }
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                  val url = request?.url ?: return false
                  val urlString = url.toString()
                  
                  // Handle system protocol intents seamlessly
                  if (urlString.startsWith("mailto:") ||
                    urlString.startsWith("tel:") ||
                    urlString.startsWith("sms:") ||
                    urlString.startsWith("geo:") ||
                    urlString.startsWith("market:") ||
                    urlString.startsWith("intent:") ||
                    !urlString.startsWith("http")
                  ) {
                    try {
                      val intent = Intent(Intent.ACTION_VIEW, url)
                      ctx.startActivity(intent)
                    } catch (e: Exception) {
                      // Silently catch if handoff app is not available
                    }
                    return true
                  }
                  return false
                }
              }

              webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                  progressValue = newProgress
                  if (newProgress >= 100) {
                    isLoading = false
                  }
                }
              }

              // Enable premium hybrid web configuration
              settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                builtInZoomControls = true
                displayZoomControls = false
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                mediaPlaybackRequiresUserGesture = false
              }

              loadUrl(TARGET_URL)
              webViewRef = this
            }
          },
          update = {
            // Unused as state transitions are handled dynamically inside custom clients
          }
        )

        // Loading Linear Indicator Line at top
        if (isLoading) {
          LinearProgressIndicator(
            progress = { progressValue / 100f },
            modifier = Modifier
              .fillMaxWidth()
              .statusBarsPadding()
              .height(3.dp)
              .testTag("progress_bar"),
            color = Color(0xFF6366F1), // Elegant Lavender Blue Indicator (M3 style)
            trackColor = Color(0x336366F1)
          )
        }

        // Beautiful Minimal Floating Island Action Controls
        AnimatedVisibility(
          visible = true,
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = 24.dp)
        ) {
          ControlIslandPill(
            canGoBack = canGoBackState,
            canGoForward = canGoForwardState,
            isPanelExpanded = showControlPanel,
            onBackClicked = { webViewRef?.goBack() },
            onForwardClicked = { webViewRef?.goForward() },
            onRefreshClicked = { webViewRef?.reload() },
            onHomeClicked = { webViewRef?.loadUrl(TARGET_URL) },
            onTogglePanel = { showControlPanel = !showControlPanel },
            onShareClicked = {
              sharePageUrl(context, currentUrlState)
            }
          )
        }
      }
    }
  }
}

@Composable
fun ControlIslandPill(
  canGoBack: Boolean,
  canGoForward: Boolean,
  isPanelExpanded: Boolean,
  onBackClicked: () -> Unit,
  onForwardClicked: () -> Unit,
  onRefreshClicked: () -> Unit,
  onHomeClicked: () -> Unit,
  onTogglePanel: () -> Unit,
  onShareClicked: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(28.dp),
    colors = CardDefaults.cardColors(
      containerColor = Color(0xE61E293B) // Frosted Slate Card color
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    modifier = Modifier
      .padding(horizontal = 16.dp)
      .testTag("control_island")
  ) {
    if (isPanelExpanded) {
      Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        // Back Navigation Icon
        IconButton(
          onClick = onBackClicked,
          enabled = canGoBack,
          modifier = Modifier.testTag("nav_back_button")
        ) {
          Icon(
            imageVector = Icons.Default.ArrowBack,
            contentDescription = "Go Back",
            tint = if (canGoBack) Color.White else Color(0xFF64748B)
          )
        }

        // Forward Navigation Icon
        IconButton(
          onClick = onForwardClicked,
          enabled = canGoForward,
          modifier = Modifier.testTag("nav_forward_button")
        ) {
          Icon(
            imageVector = Icons.Default.ArrowForward,
            contentDescription = "Go Forward",
            tint = if (canGoForward) Color.White else Color(0xFF64748B)
          )
        }

        // Full Page Reload
        IconButton(
          onClick = onRefreshClicked,
          modifier = Modifier.testTag("nav_refresh_button")
        ) {
          Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = "Reload Page",
            tint = Color(0xFF818CF8)
          )
        }

        // Return Home
        IconButton(
          onClick = onHomeClicked,
          modifier = Modifier.testTag("nav_home_button")
        ) {
          Icon(
            imageVector = Icons.Default.Home,
            contentDescription = "Return Home",
            tint = Color(0xFF38BDF8)
          )
        }

        // Share current URI link
        IconButton(
          onClick = onShareClicked,
          modifier = Modifier.testTag("nav_share_button")
        ) {
          Icon(
            imageVector = Icons.Default.Share,
            contentDescription = "Share Link",
            tint = Color(0xFF34D399)
          )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Minimize Card Button
        IconButton(
          onClick = onTogglePanel,
          modifier = Modifier
            .size(36.dp)
            .background(Color(0xFF334155), CircleShape)
            .clip(CircleShape)
            .testTag("nav_collapse_button")
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Minimize Panel",
            tint = Color.White,
            modifier = Modifier.size(16.dp)
          )
        }
      }
    } else {
      // Small minimalist trigger floating knob
      Row(
        modifier = Modifier
          .clickable { onTogglePanel() }
          .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
      ) {
        Icon(
          imageVector = Icons.Default.Menu,
          contentDescription = "Expand Controls",
          tint = Color.White,
          modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Controls",
          color = Color.White,
          fontSize = 13.sp,
          fontWeight = FontWeight.SemiBold,
          fontFamily = FontFamily.SansSerif
        )
      }
    }
  }
}

@Composable
fun OfflineScreen(onRetry: () -> Unit) {
  Surface(
    modifier = Modifier.fillMaxSize(),
    color = Color(0xFF0F172A)
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(32.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Box(
        modifier = Modifier
          .size(100.dp)
          .background(Color(0x1FEF4444), CircleShape),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.Warning,
          contentDescription = "Offline Symbol",
          tint = Color(0xFFEF4444),
          modifier = Modifier.size(48.dp)
        )
      }
      
      Spacer(modifier = Modifier.height(28.dp))
      
      Text(
        text = "No Internet Connection",
        color = Color.White,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
      )
      
      Spacer(modifier = Modifier.height(12.dp))
      
      Text(
        text = "Please verify your active mobile data or Wi-Fi network settings to view the E web service application.",
        color = Color(0xFF94A3B8),
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        lineHeight = 20.sp
      )
      
      Spacer(modifier = Modifier.height(36.dp))
      
      Button(
        onClick = onRetry,
        modifier = Modifier
          .fillMaxWidth(0.7f)
          .height(48.dp)
          .testTag("offline_retry_button"),
        colors = ButtonDefaults.buttonColors(
          containerColor = Color(0xFF6366F1),
          contentColor = Color.White
        ),
        shape = RoundedCornerShape(24.dp)
      ) {
        Text("Try Again", fontWeight = FontWeight.Bold)
      }
    }
  }
}

@Composable
fun ErrorScreen(message: String, onReload: () -> Unit) {
  Surface(
    modifier = Modifier.fillMaxSize(),
    color = Color(0xFF0F172A)
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(32.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Box(
        modifier = Modifier
          .size(100.dp)
          .background(Color(0x1FF59E0B), CircleShape),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.Warning,
          contentDescription = "Error Symbol",
          tint = Color(0xFFF59E0B),
          modifier = Modifier.size(48.dp)
        )
      }

      Spacer(modifier = Modifier.height(28.dp))

      Text(
        text = "Could Not Load Page",
        color = Color.White,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
      )

      Spacer(modifier = Modifier.height(12.dp))

      Text(
        text = message,
        color = Color(0xFF94A3B8),
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        lineHeight = 20.sp
      )

      Spacer(modifier = Modifier.height(36.dp))

      Button(
        onClick = onReload,
        modifier = Modifier
          .fillMaxWidth(0.7f)
          .height(48.dp)
          .testTag("error_reload_button"),
        colors = ButtonDefaults.buttonColors(
          containerColor = Color(0xFF6366F1),
          contentColor = Color.White
        ),
        shape = RoundedCornerShape(24.dp)
      ) {
        Text("Refresh Page", fontWeight = FontWeight.Bold)
      }
    }
  }
}

fun sharePageUrl(context: Context, url: String) {
  try {
    val shareIntent = Intent().apply {
      action = Intent.ACTION_SEND
      putExtra(Intent.EXTRA_TEXT, "Check out the E Web App: $url")
      type = "text/plain"
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share URL"))
  } catch (e: Exception) {
    // Graceful error catcher
  }
}

@Composable
fun rememberIsNetworkAvailable(): State<Boolean> {
  val context = LocalContext.current
  return produceState(initialValue = isNetworkAvailable(context)) {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val callback = object : ConnectivityManager.NetworkCallback() {
      override fun onAvailable(network: Network) {
        value = true
      }
      override fun onLost(network: Network) {
        value = isNetworkAvailable(context)
      }
    }
    val request = NetworkRequest.Builder()
      .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
      .build()
    
    try {
      connectivityManager.registerNetworkCallback(request, callback)
    } catch (e: Exception) {
      // Fallback
    }

    awaitDispose {
      try {
        connectivityManager.unregisterNetworkCallback(callback)
      } catch (e: Exception) {
        // Fallback
      }
    }
  }
}

fun isNetworkAvailable(context: Context): Boolean {
  val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    val nw = connectivityManager.activeNetwork ?: return false
    val actNw = connectivityManager.getNetworkCapabilities(nw) ?: return false
    return when {
      actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
      actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
      actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
      actNw.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> true
      else -> false
    }
  } else {
    @Suppress("DEPRECATION")
    val nwInfo = connectivityManager.activeNetworkInfo ?: return false
    @Suppress("DEPRECATION")
    return nwInfo.isConnected
  }
}

