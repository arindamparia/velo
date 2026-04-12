package com.velo.app.ui.accounts

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.velo.app.engine.CookieTranspiler
import com.velo.app.ui.theme.veloColors
import java.io.File

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AccountsScreen(
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val colors = veloColors

    // Store cookies in internal private storage (filesDir) instead of external storage.
    // External storage is readable by other apps; internal storage is sandboxed to this app.
    val facebookCookieFile = remember { File(context.filesDir, "velo_cookies_facebook.txt") }
    val instagramCookieFile = remember { File(context.filesDir, "velo_cookies_instagram.txt") }

    var facebookAuthenticated by remember { mutableStateOf(facebookCookieFile.exists()) }
    var instagramAuthenticated by remember { mutableStateOf(instagramCookieFile.exists()) }

    // null = show landing, "facebook" / "instagram" = show WebView
    var activeLogin by remember { mutableStateOf<String?>(null) }

    // On Samsung / Android 14+, composing a BackHandler (even disabled) can 
    // kill the predictive back gesture. Only compose it when actively needed.
    if (activeLogin != null) {
        BackHandler {
            activeLogin = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // ── Top bar ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { if (activeLogin != null) activeLogin = null else onNavigateBack() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "back", tint = colors.text)
            }
            Text(
                text = if (activeLogin != null) "log in to $activeLogin" else "accounts",
                style = MaterialTheme.typography.titleMedium,
                color = colors.text,
            )
        }

        HorizontalDivider(color = colors.border, thickness = 0.5.dp)

        if (activeLogin == null) {
            // ── Landing page ──────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "unlock private downloads",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.text
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Log in to access friends-only and private account content.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(32.dp))

                // ── Facebook button ───────────────────────────────────────────
                PlatformLoginButton(
                    label = "facebook",
                    isAuthenticated = facebookAuthenticated,
                    onClick = { activeLogin = "facebook" },
                    onLogout = {
                        facebookCookieFile.delete()
                        facebookAuthenticated = false
                    }
                )

                Spacer(Modifier.height(12.dp))

                // ── Instagram button ──────────────────────────────────────────
                PlatformLoginButton(
                    label = "instagram",
                    isAuthenticated = instagramAuthenticated,
                    onClick = { activeLogin = "instagram" },
                    onLogout = {
                        instagramCookieFile.delete()
                        instagramAuthenticated = false
                    }
                )
            }
        } else {
            // ── WebView ───────────────────────────────────────────────────────
            val loginUrl = when (activeLogin) {
                "facebook" -> "https://www.facebook.com/login"
                "instagram" -> "https://www.instagram.com/accounts/login/"
                else -> ""
            }
            val targetCookieFile = if (activeLogin == "instagram") instagramCookieFile else facebookCookieFile

            var isLoading by remember { mutableStateOf(true) }

            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadsImagesAutomatically = true
                            settings.setSupportZoom(true)
                            settings.builtInZoomControls = false
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            @Suppress("DEPRECATION")
                            settings.saveFormData = true

                            // Desktop Chrome UA — Meta blocks WebView UAs
                            settings.userAgentString =
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                            // Do NOT set MIXED_CONTENT_ALWAYS_ALLOW — Facebook and Instagram
                            // are HTTPS-only, so mixed content is unnecessary and would expose
                            // cookies to downgrade attacks.

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    isLoading = true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isLoading = false
                                    CookieManager.getInstance().flush()

                                    val currentUrl = url ?: return
                                    val raw = CookieManager.getInstance().getCookie(currentUrl) ?: return

                                    val loggedIn = when (activeLogin) {
                                        "facebook" -> raw.contains("c_user=")
                                        "instagram" -> raw.contains("sessionid=")
                                        else -> false
                                    }

                                    if (loggedIn) {
                                        val domain = if (activeLogin == "instagram") "instagram.com" else "facebook.com"
                                        CookieTranspiler.saveToNetscapeFormat(raw, domain, targetCookieFile)

                                        // Update only the specific platform's state
                                        if (activeLogin == "facebook") facebookAuthenticated = true
                                        else instagramAuthenticated = true

                                        activeLogin = null
                                    }
                                }
                            }
                            webChromeClient = WebChromeClient()
                            loadUrl(loginUrl)
                        }
                    }
                )

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colors.bg),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = colors.accent,
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "loading $activeLogin…",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlatformLoginButton(
    label: String,
    isAuthenticated: Boolean,
    onClick: () -> Unit,
    onLogout: () -> Unit,
) {
    val colors = veloColors

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isAuthenticated) colors.success.copy(alpha = 0.15f) else colors.elevated
            ),
            contentPadding = PaddingValues(16.dp)
        ) {
            if (isAuthenticated) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = colors.success,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "$label · logged in",
                    color = colors.success,
                    style = MaterialTheme.typography.labelLarge
                )
            } else {
                Text(
                    "continue with $label",
                    color = colors.text,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        // Log out button only shown when authenticated
        if (isAuthenticated) {
            TextButton(
                onClick = onLogout,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("logout", color = colors.error, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
