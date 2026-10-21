package com.maccms.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.maccms.mobile.ui.theme.BrandRed
import com.maccms.mobile.ui.theme.TextSecondary

@Composable
fun LoadingState(modifier: Modifier = Modifier, text: String = "加载中…") {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = BrandRed, strokeWidth = 3.dp)
            Spacer(Modifier.height(14.dp))
            Text(text, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.CloudOff,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(44.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text("加载失败", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                message.ifBlank { "网络异常，请稍后重试" },
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            if (onRetry != null) {
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                ) {
                    Text("重试", color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    subtitle: String = "",
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Outlined.Inbox,
                contentDescription = null,
                tint = TextSecondary.copy(alpha = 0.7f),
                modifier = Modifier.size(44.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(title, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleMedium)
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    subtitle,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun InlineLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = BrandRed, strokeWidth = 2.5.dp, modifier = Modifier.size(26.dp))
    }
}
