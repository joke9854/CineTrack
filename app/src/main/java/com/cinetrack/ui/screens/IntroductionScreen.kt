package com.cinetrack.ui.screens

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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cinetrack.R
import com.cinetrack.ui.components.AdaptiveBackground
import com.cinetrack.ui.components.BrandMark
import com.cinetrack.ui.components.PrimaryAction
import com.cinetrack.ui.components.glass
import com.cinetrack.ui.components.rememberLightHapticAction
import com.cinetrack.ui.theme.Accent
import com.cinetrack.ui.theme.AccentLight
import com.cinetrack.ui.theme.TextPrimary
import com.cinetrack.ui.theme.TextSecondary

private data class IntroductionPage(
    val title: Int,
    val body: Int,
    val icon: ImageVector,
)

@Composable
fun IntroductionScreen(
    onOpenSettings: () -> Unit,
    onFinish: () -> Unit,
) {
    val pages = listOf(
        IntroductionPage(R.string.intro_welcome_title, R.string.intro_welcome_body, Icons.Filled.Explore),
        IntroductionPage(R.string.intro_progress_title, R.string.intro_progress_body, Icons.Filled.QueryStats),
        IntroductionPage(R.string.intro_api_title, R.string.intro_api_body, Icons.Filled.Link),
    )
    var pageIndex by remember { mutableIntStateOf(0) }
    val page = pages[pageIndex]
    AdaptiveBackground {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandMark(62.dp)
            Text("CineTrack", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 12.dp))
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(78.dp).glass(RoundedCornerShape(24.dp)).background(Accent.copy(alpha = .20f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(page.icon, null, tint = AccentLight, modifier = Modifier.size(36.dp))
            }
            Text(
                stringResource(page.title),
                color = TextPrimary,
                fontSize = 27.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 24.dp),
            )
            Text(
                stringResource(page.body),
                color = TextSecondary,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 20.dp)) {
                pages.indices.forEach { index ->
                    Box(
                        Modifier.size(if (index == pageIndex) 20.dp else 8.dp, 8.dp)
                            .background(if (index == pageIndex) AccentLight else Color.White.copy(alpha = .20f), CircleShape),
                    )
                }
            }
            if (pageIndex < pages.lastIndex) {
                PrimaryAction(
                    text = stringResource(R.string.intro_next),
                    icon = Icons.Filled.ArrowForward,
                    modifier = Modifier.fillMaxWidth(),
                ) { pageIndex++ }
            } else {
                PrimaryAction(
                    text = stringResource(R.string.intro_open_settings),
                    icon = Icons.Filled.Link,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenSettings,
                )
                val later = rememberLightHapticAction(onFinish)
                Text(
                    stringResource(R.string.intro_later),
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().height(48.dp).clickable(onClick = later).padding(top = 15.dp),
                )
            }
        }
    }
}
