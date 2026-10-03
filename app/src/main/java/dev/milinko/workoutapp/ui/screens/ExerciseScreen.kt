package dev.milinko.workoutapp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Cameraswitch
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
import kotlin.math.roundToInt
import androidx.hilt.navigation.compose.hiltViewModel
import dev.milinko.workoutapp.exercise.ExerciseType
import androidx.camera.core.CameraSelector
import dev.milinko.workoutapp.ui.components.CameraPreview
import dev.milinko.workoutapp.viewmodel.ExerciseViewModel
import dev.milinko.workoutapp.ui.components.PoseOverlay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseScreen(onBack: () -> Unit, viewModel: ExerciseViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val landmarks by viewModel.landmarks.collectAsState()
    val frameSize by viewModel.frameSize.collectAsState()
    val isSessionActive by viewModel.isSessionActive.collectAsState()
    val showSummary by viewModel.showSummary.collectAsState()

    val exerciseType by viewModel.currentExerciseType.collectAsState()

    var showExitDialog by remember { mutableStateOf(false) }
    var isFrontCamera by remember { mutableStateOf(true) }

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
                    Text("Vežba: ${exerciseType.displayName}")
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
            // Gornja polovina: live kamera sa skeletom iscrtanim preko nje (jedan jedinstveni prikaz)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(2f)
                .background(Color.Black)
        ) {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                lensFacing = if (isFrontCamera) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK,
                onFrame = { viewModel.onFrame(it) }
            )

            // Skelet se crta PREKO kamere - PoseOverlay sam mapira sirove landmark koordinate
            // (u koordinatnom sistemu analizirane slike, frameSize) na stvarnu veličinu ovog
            // Box-a, istom FILL_CENTER logikom koju PreviewView koristi za samu kameru, plus
            // mirror za prednju kameru (PreviewView ogleda prednju kameru, sirovi landmarci ne).
            PoseOverlay(
                landmarks = landmarks,
                sourceWidth = frameSize.first,
                sourceHeight = frameSize.second,
                mirror = isFrontCamera,
                modifier = Modifier.fillMaxSize()
            )

            // Prednja/zadnja kamera toggle
            IconButton(
                onClick = { isFrontCamera = !isFrontCamera },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(
                    Icons.Default.Cameraswitch,
                    contentDescription = "Switch camera",
                    tint = Color.White
                )
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

        // Donja polovina: sad eksplicitno weight(1f) naspram kamere weight(2f) iznad - kamera
        // dobija otprilike 2/3 visine, ovaj deo otprilike 1/3. Sadržaj promenljive visine
        // (birač vežbe, brojač, forma/ugao, pull-up banner) je u unutrašnjem Column-u koji
        // skroluje AKO ne stane u tu 1/3 (npr. manji telefon + banner istovremeno) - dugme za
        // start/kraj je namerno VAN tog scroll-a, uvek fiksno vidljivo na dnu, isto kao ranije
        // dokazano rešenje za "dugme nestalo van ekrana" bag.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Exercise picker - only before starting, switching mid-session would reset the count
                if (!isSessionActive) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ExerciseType.entries.forEach { type ->
                            FilterChip(
                                selected = exerciseType == type,
                                onClick = { viewModel.setExerciseType(type) },
                                label = { Text(type.displayName) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Main counter - i dalje krupno (80sp), samo malo manje nego pre (100sp) da
                // udobno stane zajedno sa ostatkom u ~1/3 ekrana.
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = exerciseType.displayName.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${state.count}",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 80.sp
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

                    // Angle - ugao se i dalje prati/smoothuje po stepenu kao i do sad (ništa u
                    // brojanju ponavljanja nije dirano), ali se PRIKAZ zaokružuje na najbliži
                    // petak (5°) da ne treperi/menja se svaki frejm za po 1° - samo kozmetika.
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val displayedAngle = (state.currentAngle / 5.0).roundToInt() * 5
                        Text(
                            text = "${displayedAngle}°",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (exerciseType == ExerciseType.PUSH_UPS) MaterialTheme.colorScheme.primary else Color.Cyan
                        )
                        Text(
                            text = "ELBOW ANGLE",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                }

                // Additional info about hands stability for pull-ups
                if (exerciseType == ExerciseType.PULL_UPS && isSessionActive) {
                    Surface(
                        color = if (state.visibilityMessage?.contains("STABILIZATION") == true || state.visibilityMessage?.contains("STEADY") == true)
                            Color.Yellow.copy(alpha = 0.2f) else Color.Green.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
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
            }

            Spacer(Modifier.height(10.dp))

            // Controls - pinned OUTSIDE the scrollable Column above, always visible no matter
            // how much (or how little) space the info above needs.
            if (!isSessionActive) {
                Button(
                    onClick = { viewModel.startSession() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
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
                        .height(56.dp),
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
}
