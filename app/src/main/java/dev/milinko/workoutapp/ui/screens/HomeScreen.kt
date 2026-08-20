package dev.milinko.workoutapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.milinko.workoutapp.exercise.ExerciseType
import dev.milinko.workoutapp.ui.components.ExerciseHistoryRow
import dev.milinko.workoutapp.viewmodel.ExerciseViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onStartTraining: () -> Unit, viewModel: ExerciseViewModel = hiltViewModel()) {
    val history by viewModel.history.collectAsState()
    val manualNameSuggestions by viewModel.manualNameSuggestions.collectAsState()
    var showManualDialog by remember { mutableStateOf(false) }
    var manualName by remember { mutableStateOf("") }
    var manualReps by remember { mutableStateOf("") }

    val exerciseType by viewModel.currentExerciseType.collectAsState()

    if (showManualDialog) {
        AlertDialog(
            onDismissRequest = { showManualDialog = false },
            title = { Text("Manual log") },
            text = {
                Column {
                    Text("Exercise name and number of repetitions completed:")
                    Spacer(Modifier.height(8.dp))
                    ExerciseNameField(
                        value = manualName,
                        onValueChange = { manualName = it },
                        suggestions = manualNameSuggestions
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = manualReps,
                        onValueChange = { if (it.all { char -> char.isDigit() }) manualReps = it },
                        label = { Text("Repetitions") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val reps = manualReps.toIntOrNull() ?: 0
                        if (reps > 0 && manualName.isNotBlank()) {
                            viewModel.logManualExercise(manualName, reps)
                            showManualDialog = false
                            manualName = ""
                            manualReps = ""
                        }
                    },
                    enabled = manualReps.isNotEmpty() && manualName.isNotBlank()
                ) {
                    Text("SAVE")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualDialog = false }) {
                    Text("CANCEL")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("FitVision") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Welcome back!",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Text(
                text = "Choose an exercise:",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExerciseType.entries.forEach { type ->
                    val isSelected = exerciseType == type
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setExerciseType(type) },
                        label = { Text(type.displayName) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onStartTraining,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("START CAMERA TRAINING", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { showManualDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("LOG REPS MANUALLY", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Recent workouts",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Start)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(history.take(5)) { exercise ->
                    ExerciseHistoryRow(
                        exercise = exercise,
                        onDelete = { viewModel.deleteExercise(it) }
                    )
                }
            }
        }
    }
}

/**
 * Free-text field with a lightweight autocomplete list (built-in exercises + names already
 * logged before), so manual entries can cover any street workout exercise while still steering
 * the user towards reusing an existing name (avoids stats getting split by typos).
 */
@Composable
private fun ExerciseNameField(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val filteredSuggestions = remember(value, suggestions) {
        if (value.isBlank()) suggestions else suggestions.filter { it.contains(value, ignoreCase = true) }
    }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("Exercise name") },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused }
        )
        if (isFocused && filteredSuggestions.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp)) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    filteredSuggestions.forEach { suggestion ->
                        Text(
                            text = suggestion,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onValueChange(suggestion) }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }
            }
        }
    }
}