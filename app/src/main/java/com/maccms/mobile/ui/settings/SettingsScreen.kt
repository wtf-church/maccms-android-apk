package com.maccms.mobile.ui.settings

import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.maccms.mobile.BuildConfig
import com.maccms.mobile.ui.theme.AppBlack
import com.maccms.mobile.ui.theme.AppSurface
import com.maccms.mobile.ui.theme.AppSurfaceHigh
import com.maccms.mobile.ui.theme.BrandRed
import com.maccms.mobile.ui.theme.TextPrimary
import com.maccms.mobile.ui.theme.TextSecondary
import com.maccms.mobile.util.BaseUrlManager

/** 设置：API 服务器地址、缓存清理与关于信息 */
@OptIn(ExperimentalCoilApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    var url by remember { mutableStateOf(BaseUrlManager.getBaseUrl(context)) }
    val scroll = rememberScrollState()

    fun save() {
        val normalized = url.trim().trimEnd('/')
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            Toast.makeText(context, "地址需以 http:// 或 https:// 开头", Toast.LENGTH_SHORT).show()
            return
        }
        BaseUrlManager.setBaseUrl(context, normalized)
        url = normalized
        keyboard?.hide()
        Toast.makeText(context, "已保存，新的请求将使用该地址", Toast.LENGTH_SHORT).show()
    }

    Column(Modifier.fillMaxSize().background(AppBlack).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(end = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回", tint = TextPrimary)
            }
            Text("设置", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SettingsSection(title = "数据源", icon = Icons.Outlined.Language) {
                Text(
                    "服务器地址（API Base URL）",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = TextPrimary),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = AppSurfaceHigh,
                        unfocusedContainerColor = AppSurfaceHigh,
                        focusedBorderColor = BrandRed,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = BrandRed
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { save() })
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { save() },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                    ) {
                        Text("保存", color = Color.White, fontSize = 14.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            url = BaseUrlManager.DEFAULT_BASE_URL
                            BaseUrlManager.setBaseUrl(context, BaseUrlManager.DEFAULT_BASE_URL)
                            Toast.makeText(context, "已恢复默认地址", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3A3A42))
                    ) {
                        Text("恢复默认", color = TextPrimary, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "默认 ${BaseUrlManager.DEFAULT_BASE_URL}\n修改后立即生效，请确保手机可以访问该站点。",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }

            Spacer(Modifier.height(14.dp))

            SettingsSection(title = "存储与缓存", icon = Icons.Outlined.CleaningServices) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppSurfaceHigh)
                        .clickable {
                            val loader = context.imageLoader
                            loader.memoryCache?.clear()
                            loader.diskCache?.clear()
                            Toast.makeText(context, "图片缓存已清理", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("清理图片缓存", color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text("释放海报占用的空间", color = TextSecondary, fontSize = 11.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "收藏、观看历史与播放进度保存在本机，卸载应用后会被清除。",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }

            Spacer(Modifier.height(14.dp))

            SettingsSection(title = "关于", icon = Icons.Outlined.Info) {
                InfoRow("应用版本", BuildConfig.VERSION_NAME)
                InfoRow("数据接口", "maccms V10 · api.php/provide/vod")
                InfoRow("播放器", "Media3 ExoPlayer")
                Spacer(Modifier.height(8.dp))
                Text(
                    "本应用为 maccms 站点的通用客户端，所有影片数据与播放地址均来自所配置的站点。",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppSurface)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = BrandRed, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.width(78.dp))
        Text(value, color = TextPrimary, fontSize = 12.sp, maxLines = 2)
    }
}
