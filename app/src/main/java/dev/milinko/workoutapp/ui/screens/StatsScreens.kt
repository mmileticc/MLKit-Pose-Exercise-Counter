package dev.milinko.workoutapp.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.milinko.workoutapp.db.entitys.Exercise
import dev.milinko.workoutapp.viewmodel.ExerciseViewModel
import java.text.SimpleDateFormat
import java.util.*



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(viewModel: ExerciseViewModel = hiltViewModel()) {
    val filteredHistory by viewModel.filteredHistory.collectAsState()
    val currentExerciseFilter by viewModel.statsExerciseFilter.collectAsState()
    val currentDateFilter by viewModel.statsDateFilter.collectAsState()

    val totalReps = filteredHistory.sumOf { it.numOf }
    val totalSessions = filteredHistory.size
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Statistics") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.FilterList, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = "Filters",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExerciseFilterDropdown(
                    selectedFilter = currentExerciseFilter,
                    onFilterSelected = { viewModel.setStatsExerciseFilter(it) },
                    modifier = Modifier.weight(1f)
                )

                DateFilterDropdown(
                    selectedFilter = currentDateFilter,
                    onFilterSelected = { viewModel.setStatsDateFilter(it) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stat Summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatCard(
                    title = "Total Reps",
                    value = totalReps.toString(),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Workouts",
                    value = totalSessions.toString(),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Graph
            if (filteredHistory.isNotEmpty()) {
                Text(
                    text = "Activity Graph (Last 7 Days)",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                WorkoutBarChart(
                    exercises = filteredHistory,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // History Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "History",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "$totalSessions sessions",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredHistory) { exercise ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { 
                                Text(
                                    text = "${exercise.numOf} reps",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                ) 
                            },
                            supportingContent = { Text(dateFormat.format(exercise.date)) },
                            trailingContent = { 
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = exercise.name,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExerciseFilterDropdown(
    selectedFilter: String,
    onFilterSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf("All", "Push Ups", "Pull Ups")

    Box(modifier = modifier) {
        OutlinedCard(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = selectedFilter, fontSize = 14.sp)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.45f)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onFilterSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun DateFilterDropdown(
    selectedFilter: ExerciseViewModel.DateFilter,
    onFilterSelected: (ExerciseViewModel.DateFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedCard(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = selectedFilter.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 14.sp)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.45f)
        ) {
            ExerciseViewModel.DateFilter.values().forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    onClick = {
                        onFilterSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun WorkoutBarChart(
    exercises: List<Exercise>,
    modifier: Modifier = Modifier
) {
    // Group by date and sum reps
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val displayFormat = SimpleDateFormat("dd.MM", Locale.getDefault())
    val groupedData = exercises.groupBy { dateFormat.format(it.date) }
        .mapValues { entry -> entry.value.sumOf { it.numOf } }
        .toList()
        .sortedBy { it.first } // Sort by ISO date string
        .takeLast(7)
        .map { (dateStr, count) -> 
            val date = dateFormat.parse(dateStr) ?: Date()
            displayFormat.format(date) to count
        }

    if (groupedData.isEmpty()) return

    val maxReps = (groupedData.maxOfOrNull { it.second } ?: 1).toFloat()
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    Canvas(modifier = modifier.padding(start = 8.dp, end = 8.dp, top = 24.dp, bottom = 32.dp)) {
        val width = size.width
        val height = size.height
        val barWidth = (width / (groupedData.size * 2))
        val spaceBetween = barWidth

        val textPaint = Paint().apply {
            color = onSurfaceColor.toArgb()
            textSize = 10.sp.toPx()
            textAlign = Paint.Align.CENTER
        }

        groupedData.forEachIndexed { index, data ->
            val barHeight = (data.second / maxReps) * height
            val x = index * (barWidth + spaceBetween) + (spaceBetween / 2)
            val y = height - barHeight

            // Draw Bar
            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(4.dp.toPx())
            )

            // Draw Value label above bar
            drawContext.canvas.nativeCanvas.drawText(
                data.second.toString(),
                x + barWidth / 2,
                y - 8.dp.toPx(),
                textPaint
            )

            // Draw Date label below bar
            drawContext.canvas.nativeCanvas.drawText(
                data.first,
                x + barWidth / 2,
                height + 20.dp.toPx(),
                textPaint
            )
        }
    }
}

@Composable
fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, fontSize = 12.sp)
            Text(text = value, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}
