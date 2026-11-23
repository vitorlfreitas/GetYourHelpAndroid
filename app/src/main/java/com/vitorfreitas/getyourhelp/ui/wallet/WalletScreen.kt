package com.vitorfreitas.getyourhelp.ui.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorfreitas.getyourhelp.data.PaymentRecord
import com.vitorfreitas.getyourhelp.data.PaymentStatus
import com.vitorfreitas.getyourhelp.ui.components.EmptyState
import com.vitorfreitas.getyourhelp.ui.formatEuro

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/** Payment records for the user's paid tasks. Record keeping only; no money moves through the app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(payments: List<PaymentRecord>, busy: Boolean, onMarkPaid: (PaymentRecord) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Wallet") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Text(
                "Payment history. Record keeping only: payments happen outside GetYourHelp.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))
            if (payments.isEmpty()) {
                EmptyState("No payment records", "A record is created when a requester accepts an application for a paid task.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(payments, key = { it.id }) { payment ->
                        PaymentCard(payment, busy, onMarkPaid = { onMarkPaid(payment) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentCard(payment: PaymentRecord, busy: Boolean, onMarkPaid: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(payment.taskTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("${formatEuro(payment.amount)} · ${payment.method?.label ?: "Not specified"}")
            Text("Participants: ${payment.otherPartyName}", style = MaterialTheme.typography.bodySmall)
            Text("Status: ${payment.status.label}", style = MaterialTheme.typography.bodySmall)
            if (payment.status != PaymentStatus.MARKED_PAID) {
                OutlinedButton(onClick = onMarkPaid, enabled = !busy) { Text("Mark as paid (record only)") }
            }
        }
    }
}
