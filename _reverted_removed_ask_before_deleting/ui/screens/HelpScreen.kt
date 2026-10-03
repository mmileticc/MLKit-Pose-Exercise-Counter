package dev.milinko.workoutapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.milinko.workoutapp.exercise.ExerciseType

private data class MessageExplanation(val message: String, val meaning: String)

// Plain-language explanation of every visibilityMessage/status string the analyzers can surface,
// so a confused user has somewhere to look it up instead of guessing. Keep in sync with the
// messages produced by PhaseBasedRepCounter, PushUpAnalyzer and PullUpAnalyzer.
private val MESSAGE_GLOSSARY = listOf(
    MessageExplanation("NO POSE DETECTED / GET IN FRAME", "The camera can't see you at all - step into frame."),
    MessageExplanation("STEP BACK — CAN'T SEE ARMS", "Your shoulder, elbow or wrist isn't clearly visible - move back or adjust the phone."),
    MessageExplanation("HOLD STILL (X%)", "Calibration in progress - hold your resting position (arms extended) until it reaches 100%."),
    MessageExplanation("READY / SETTLING...", "Calibrated and waiting for you to start the next rep."),
    MessageExplanation("GO! / WORKING / RETURNING", "A rep is currently in progress."),
    MessageExplanation("FORM OK / BAD FORM", "Whether your last completed rep met the required range of motion."),
    MessageExplanation("GO FURTHER / FULL RANGE NEEDED", "That rep didn't reach a deep enough bend or a big enough range of motion to count."),
    MessageExplanation("TOO SLOW - TRY AGAIN", "A rep was started but took too long to finish, so it timed out and wasn't counted."),
    MessageExplanation("HANDS MUST BE ABOVE HEAD", "Pull-ups only: your hands need to stay above your head/shoulders (i.e. on the bar)."),
    MessageExplanation("KEEP HANDS STILL ON BAR", "Pull-ups only: your grip moved too much during the pull - that rep looked like a swing, not a clean pull, so it was rejected."),
    MessageExplanation("Elbow angle (the number in degrees)", "A live reading of how bent your arm is - it's what the app uses to detect the phases of each rep.")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen() {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Help") })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "How PoseTrack works",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            item { TwoWaysToLogCard() }

            items(ExerciseType.entries.toList()) { type ->
                ExerciseTipsCard(type)
            }

            item { MessageGlossaryCard() }

            item { StatsExplanationCard() }
        }
    }
}

@Composable
private fun TwoWaysToLogCard() {
    HelpCard(title = "Two ways to log a workout") {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Icon(Icons.Default.CameraAlt, contentDescription = null)
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text("Camera training", fontWeight = FontWeight.SemiBold)
                Text(
                    "Automatic rep counting, currently available for: " +
                        ExerciseType.entries.joinToString(", ") { it.displayName } +
                        ". More exercises will get their own automatic counting over time."
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Icon(Icons.Default.Edit, contentDescription = null)
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text("Manual log", fontWeight = FontWeight.SemiBold)
                Text(
                    "Log any exercise by name and rep count - dips, squats, L-sits, anything " +
                        "that doesn't have a camera analyzer yet. It goes into the same history " +
                        "and the same Stats totals as camera sessions."
                )
            }
        }
    }
}

@Composable
private fun ExerciseTipsCard(type: ExerciseType) {
    HelpCard(title = "${type.displayName} - camera tips") {
        Column(modifier = Modifier.padding(top = 4.dp)) {
            type.tips.forEach { tip ->
                TipRow(tip)
            }
        }
    }
}

@Composable
private fun MessageGlossaryCard() {
    HelpCard(title = "What the on-screen messages mean") {
        Column(modifier = Modifier.padding(top = 4.dp)) {
            MESSAGE_GLOSSARY.forEach { entry ->
                Column(modifier = Modifier.padding(bottom = 12.dp)) {
                    Text(entry.message, fontWeight = FontWeight.SemiBold)
                    Text(entry.meaning, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StatsExplanationCard() {
    HelpCard(title = "Stats") {
        Text(
            "The totals at the top of the Stats tab are accumulated across your ENTIRE history " +
                "for each exercise, no matter what filter is currently selected below. The list " +
                "and 7-day chart underneath respect whatever exercise/date filter you pick, so you " +
                "can zoom into a specific period without losing sight of your all-time numbers.",
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun TipRow(text: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text("•  ", fontWeight = FontWeight.Bold)
        Text(text)
    }
}

@Composable
private fun HelpCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            content()
        }
    }
}
