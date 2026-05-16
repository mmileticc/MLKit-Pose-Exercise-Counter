package dev.milinko.workoutapp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.milinko.workoutapp.ui.components.CameraPreview
import dev.milinko.workoutapp.viewmodel.ExerciseViewModel
import dev.milinko.workoutapp.db.entitys.Exercise
import dev.milinko.workoutapp.ui.components.PoseOverlay
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseScreen(onBack: () -> Unit, viewModel: ExerciseViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val landmarks by viewModel.landmarks.collectAsState()
    val isSessionActive by viewModel.isSessionActive.collectAsState()
    val showSummary by viewModel.showSummary.collectAsState()

    val exerciseType by viewModel.currentExerciseType.collectAsState()

    var showExitDialog by remember { mutableStateOf(false) }

    // Keep screen on during training
    val view = LocalView.current
    DisposableEffect(isSessionActive) {
        if (isSessionActive) {
            view.keepScreenOn = true
        }
        onDispose {
            view.keepScreenOn = false
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Exit Training?") },
            text = { Text("Are you sure you want to stop the training session? Progress will not be saved.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.discardSession()
                        showExitDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("EXIT")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("CANCEL")
                }
            }
        )
    }

    if (showSummary) {
        AlertDialog(
            onDismissRequest = { /* Prevent dismissal by clicking outside */ },
            title = { Text("Rezime treninga") },
            text = {
                Column {
                    Text("Odličan posao!")
                    Spacer(Modifier.height(8.dp))
                    Text("Ukupno ponavljanja: ${state.count}", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("Vežba: $exerciseType")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveSession()
                        onBack()
                    }
                ) {
                    Text("SAČUVAJ")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.discardSession()
                        onBack()
                    }
                ) {
                    Text("ODBACI")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Training Session") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSessionActive) {
                            showExitDialog = true
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Gornja polovina: Vizuelni prikaz
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black)
        ) {
            // Desni ćošak: Live prikaz sa kamere
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxWidth(0.45f)
                    .fillMaxHeight(0.9f)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(2.dp, Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            ) {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    onFrame = { viewModel.onFrame(it) }
                )
            }

            // Levi ćošak: Skenirani skelet
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth(0.45f)
                    .fillMaxHeight(0.9f)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.DarkGray.copy(alpha = 0.3f))
                    .border(2.dp, Color.Cyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            ) {
                PoseOverlay(landmarks = landmarks, modifier = Modifier.fillMaxSize())
            }

            // Warning if user is not in frame or full body not visible
            if (isSessionActive && state.visibilityMessage != null && !state.areHandsFixed) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = if (!state.isUserInFrame) 0.6f else 0.0f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                            .padding(24.dp)
                            .border(2.dp, if (!state.isUserInFrame) Color.Red else Color.Black, RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (!state.isUserInFrame) Color.Red else Color.DarkGray,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = state.visibilityMessage ?: "GET IN FRAME",
                            color = Color.Black,
                            fontWeight = FontWeight.Black,
                            fontSize = 28.sp,
                            lineHeight = 34.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Lower half: Info and controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Main counter
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (exerciseType == "Push Ups") "PUSH UPS" else "PULL UPS",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${state.count}",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 100.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Form status and angle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Form
                Surface(
                    color = if (state.isCorrectForm) Color.Green.copy(alpha = 0.1f) else Color.Red.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        2.dp,
                        if (state.isCorrectForm) Color.Green else Color.Red
                    )
                ) {
                    Text(
                        text = if (state.isCorrectForm) "FORM OK" else "BAD FORM",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        fontWeight = FontWeight.Bold,
                        color = if (state.isCorrectForm) Color.Green else Color.Red
                    )
                }

                // Angle
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${state.currentAngle.toInt()}°",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (exerciseType == "Push Ups") MaterialTheme.colorScheme.primary else Color.Cyan
                    )
                    Text(
                        text = "ELBOW ANGLE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }

            // Additional info about hands stability for pull-ups
            if (exerciseType == "Pull Ups" && isSessionActive) {
                Surface(
                    color = if (state.visibilityMessage?.contains("STABILIZATION") == true || state.visibilityMessage?.contains("STEADY") == true)
                        Color.Yellow.copy(alpha = 0.2f) else Color.Green.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    val isStabilizing = state.visibilityMessage?.contains("STABILIZATION") == true || state.visibilityMessage?.contains("STEADY") == true
                    Text(
                        text = if (isStabilizing)
                            "HAND STABILIZATION IN PROGRESS${state.visibilityMessage?.substringAfter("%)")?.let { "" } ?: state.visibilityMessage?.substringAfter("HANDS") ?: ""}"
                        else "HANDS FIXED",
                        color = if (isStabilizing)
                            Color(0xFF8B8000) else Color(0xFF006400),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Controls
            if (!isSessionActive) {
                Button(
                    onClick = { viewModel.startSession() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("START TRAINING", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = { viewModel.stopSession() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("FINISH TRAINING", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun HistoryRow(exercise: Exercise) {
    val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = exercise.name,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = dateFormat.format(exercise.date),
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.labelSmall
            )
        }
        Text(
            text = "${exercise.numOf}",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black
        )
    }
}
}
