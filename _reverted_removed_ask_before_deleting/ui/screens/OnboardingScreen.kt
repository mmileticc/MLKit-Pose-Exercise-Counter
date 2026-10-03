package dev.milinko.workoutapp.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class OnboardingPage(val title: String, val body: String)

private val ONBOARDING_PAGES = listOf(
    OnboardingPage(
        title = "Welcome to PoseTrack",
        body = "Log your street workout sessions two ways: point the camera at yourself for " +
            "automatic rep counting, or log any exercise manually. Either way it lands in the " +
            "same history."
    ),
    OnboardingPage(
        title = "Camera counting",
        body = "Automatic counting currently works for Push Ups and Pull Ups. Position your " +
            "phone so your body is clearly in frame - you can always check the Help tab for " +
            "exercise-specific tips before you start."
    ),
    OnboardingPage(
        title = "Track everything",
        body = "The Stats tab shows your accumulated totals per exercise across your entire " +
            "history, plus a 7-day chart once you pick a specific exercise. Manual entries and " +
            "camera sessions both count towards it."
    )
)

/**
 * Shown once on first launch (gated by a SharedPreferences flag in MainActivity). The same
 * explanations live permanently on the Help tab, so this is just a friendly introduction, not
 * the only place to find this information.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    var pageIndex by remember { mutableStateOf(0) }
    val page = ONBOARDING_PAGES[pageIndex]
    val isLastPage = pageIndex == ONBOARDING_PAGES.lastIndex

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (!isLastPage) {
                TextButton(onClick = onFinish) {
                    Text("Skip")
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = page.title,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = page.body,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PageIndicator(total = ONBOARDING_PAGES.size, current = pageIndex)
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    if (isLastPage) onFinish() else pageIndex++
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(if (isLastPage) "Get Started" else "Next")
            }
        }
    }
}

@Composable
private fun PageIndicator(total: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(total) { index ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .then(
                        Modifier.background(
                            if (index == current) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
            )
        }
    }
}
