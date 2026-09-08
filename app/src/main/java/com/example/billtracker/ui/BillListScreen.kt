package com.example.billtracker.ui

import android.app.DatePickerDialog as AndroidDatePickerDialog
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.billtracker.data.Biller
import com.example.billtracker.data.Recurrence
import com.example.billtracker.data.SortMode
import com.example.billtracker.ui.theme.AccentGreen
import com.example.billtracker.ui.theme.DangerRed
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillListScreen(viewModel: BillerViewModel = viewModel()) {
    val billers by viewModel.billers.collectAsState()
    val income by viewModel.income.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Biller?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val unpaid = billers.filter { !it.isPaid }
    val totalDue = unpaid.mapNotNull { it.amount }.sum()
    val remaining = income - totalDue
    val today = LocalDate.now()
    val monthLabel = today.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(monthLabel, fontWeight = FontWeight.Bold) },
                    actions = { IconButton({ showSettings = true }) { Icon(Icons.Default.Settings, "Settings") } }
                )
                Text(
                    "${billers.count { it.isPaid }} of ${billers.size} paid",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        },
        floatingActionButton = {
            FloatingActionButton({ showAdd = true }) { Icon(Icons.Default.Add, "Add bill") }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { SummaryBar(totalDue, income, remaining) }
    ) { padding ->
        if (billers.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No bills yet. Tap + to add one.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                itemsIndexed(billers, key = { _, b -> b.id }) { _, bill ->
                    BillRow(
                        bill = bill,
                        onToggle = { viewModel.togglePaid(bill) },
                        onEdit = { editing = bill },
                        onDelete = {
                            viewModel.deleteBiller(bill)
                            scope.launch { snackbar.showSnackbar("Deleted ${bill.name}") }
                        }
                    )
                }
            }
        }
    }

    if (showAdd) {
        BillerFormDialog("Add a bill", "Add", onDismiss = { showAdd = false }) { name, amount, date, recurrence ->
            viewModel.addBiller(name, amount, date, recurrence)
            showAdd = false
        }
    }

    editing?.let { bill ->
        BillerFormDialog(
            title = "Edit bill", confirmLabel = "Save",
            initialName = bill.name, initialAmount = bill.amount,
            initialDate = parseDate(bill.dueDate) ?: today,
            initialRecurrence = runCatching { Recurrence.valueOf(bill.recurrence) }.getOrDefault(Recurrence.MONTHLY),
            onDismiss = { editing = null }
        ) { name, amount, date, recurrence ->
            viewModel.updateBiller(bill, name, amount, date, recurrence)
            editing = null
        }
    }

    if (showSettings) SettingsDialog(viewModel, sortMode, income) { showSettings = false }
}

@Composable
private fun SummaryBar(totalDue: Double, income: Double, remaining: Double) {
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(Modifier.fillMaxWidth()) {
            SummaryItem("Income", income, Modifier.weight(1f))
            SummaryItem("Due", totalDue, Modifier.weight(1f))
            SummaryItem("Remaining", remaining, Modifier.weight(1f),
                if (remaining < 0) DangerRed else AccentGreen)
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: Double, modifier: Modifier, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatMoney(value), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = color)
    }
}

@Composable
private fun BillRow(bill: Biller, onToggle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(bill.isPaid, { onToggle() }, colors = CheckboxDefaults.colors(checkedColor = AccentGreen))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                bill.name,
                style = MaterialTheme.typography.bodyLarge.copy(
                    textDecoration = if (bill.isPaid) TextDecoration.LineThrough else TextDecoration.None
                ),
                color = if (bill.isPaid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
            val date = parseDate(bill.dueDate)
            if (date != null) {
                val days = ChronoUnit.DAYS.between(LocalDate.now(), date).toInt()
                Text(
                    "${date.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))} • ${daysLabel(days)} • ${recurrenceLabel(bill)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        val days = parseDate(bill.dueDate)?.let { ChronoUnit.DAYS.between(LocalDate.now(), it).toInt() }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(72.dp)) {
            if (days != null) {
                Text(
                    daysLabel(days),
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        !bill.isPaid && days < 0 -> MaterialTheme.colorScheme.error
                        !bill.isPaid && days <= 3 -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            if (bill.amount != null) {
                Text(formatMoney(bill.amount), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(2.dp))
        IconButton(onEdit) { Icon(Icons.Default.Edit, "Edit") }
        IconButton(onDelete) { Icon(Icons.Default.Delete, "Delete") }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BillerFormDialog(
    title: String,
    confirmLabel: String,
    initialName: String = "",
    initialAmount: Double? = null,
    initialDate: LocalDate = LocalDate.now(),
    initialRecurrence: Recurrence = Recurrence.MONTHLY,
    onDismiss: () -> Unit,
    onConfirm: (String, Double?, LocalDate, Recurrence) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var amount by remember { mutableStateOf(initialAmount?.let(::formatPlainNumber) ?: "") }
    var date by remember { mutableStateOf(initialDate) }
    var recurrence by remember { mutableStateOf(initialRecurrence) }
    var recurrenceMenu by remember { mutableStateOf(false) }
    var showCalendar by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("Biller") },
                    placeholder = { Text("e.g. Electric, Amex, Rent") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount") }, placeholder = { Text("e.g. 120.00") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                OutlinedButton({ showCalendar = true }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.CalendarMonth, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Due date: ${date.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))}")
                }
                Spacer(Modifier.height(10.dp))
                Box {
                    OutlinedButton({ recurrenceMenu = true }, Modifier.fillMaxWidth()) {
                        Text("Repeats: ${recurrence.label}")
                    }
                    DropdownMenu(expanded = recurrenceMenu, onDismissRequest = { recurrenceMenu = false }) {
                        Recurrence.values().forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = { recurrence = option; recurrenceMenu = false },
                                leadingIcon = { if (option == recurrence) Icon(Icons.Default.Check, null) }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "The date is the next occurrence. Repeating bills automatically move to their next due date after they are paid.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), amount.toDoubleOrNull(), date, recurrence) },
                enabled = name.isNotBlank()
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } }
    )

    if (showCalendar) {
        AndroidDatePickerDialog(
            androidx.compose.ui.platform.LocalContext.current,
            { _, y, m, d -> date = LocalDate.of(y, m + 1, d); showCalendar = false },
            date.year, date.monthValue - 1, date.dayOfMonth
        ).apply {
            setOnCancelListener { showCalendar = false }
            show()
        }
    }
}

@Composable
private fun SettingsDialog(viewModel: BillerViewModel, sortMode: SortMode, income: Double, onDismiss: () -> Unit) {
    var incomeText by remember { mutableStateOf(if (income == 0.0) "" else formatPlainNumber(income)) }
    var sort by remember { mutableStateOf(sortMode) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column {
                OutlinedTextField(
                    incomeText, { incomeText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Monthly income") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
                )
                Spacer(Modifier.height(12.dp))
                Text("Sort bills by", fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(sort == SortMode.DUE_DATE, { sort = SortMode.DUE_DATE })
                    Text("Due date")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(sort == SortMode.MANUAL, { sort = SortMode.MANUAL })
                    Text("Manual")
                }
            }
        },
        confirmButton = {
            TextButton({
                viewModel.setIncome(incomeText.toDoubleOrNull() ?: 0.0)
                viewModel.setSortMode(sort)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } }
    )
}

private fun parseDate(s: String?): LocalDate? = try { if (s == null) null else LocalDate.parse(s) } catch (_: DateTimeParseException) { null }

private fun recurrenceLabel(b: Biller): String =
    runCatching { Recurrence.valueOf(b.recurrence).label }.getOrDefault("Monthly")

private fun daysLabel(days: Int): String = when {
    days < 0 -> "Overdue ${-days}d"
    days == 0 -> "Due today"
    days == 1 -> "Due tomorrow"
    else -> "$days days"
}

private fun formatMoney(v: Double) = String.format(Locale.US, "$%,.2f", v)
private fun formatPlainNumber(v: Double) = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
