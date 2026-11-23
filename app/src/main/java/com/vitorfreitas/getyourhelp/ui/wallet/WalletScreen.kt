package com.vitorfreitas.getyourhelp.ui.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorfreitas.getyourhelp.data.PaymentRecord
import com.vitorfreitas.getyourhelp.data.PaymentStatus
import com.vitorfreitas.getyourhelp.ui.components.ConfirmDialog
import com.vitorfreitas.getyourhelp.ui.components.EmptyState
import com.vitorfreitas.getyourhelp.ui.components.PaymentStatusChip
import com.vitorfreitas.getyourhelp.ui.formatEuro
import com.vitorfreitas.getyourhelp.ui.theme.AppTheme

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/** Payment records for the user's paid tasks. Record keeping only, no money moves through the app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(payments: List<PaymentRecord>, userId: String, busy: Boolean, onMarkPaid: (PaymentRecord) -> Unit) {
    var paymentToConfirm by remember { mutableStateOf<PaymentRecord?>(null) }
    Scaffold(topBar = { TopAppBar(title = { Text("Wallet") }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Payment history. Record keeping only: payments happen outside GetYourHelp.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (payments.isEmpty()) {
                item {
                    EmptyState(
                        title = "No payment records",
                        message = "A record is created when a requester accepts an application for a paid task.",
                        icon = Icons.Outlined.AccountBalanceWallet
                    )
                }
            } else {
                items(payments, key = { it.id }) { payment ->
                    PaymentCard(
                        payment = payment,
                        isIncoming = payment.payeeId == userId,
                        busy = busy,
                        onMarkPaid = { paymentToConfirm = payment }
                    )
                }
            }
        }
    }
    paymentToConfirm?.let { payment ->
        ConfirmDialog(
            title = "Mark as paid?",
            message = "Only do this once the money has changed hands. The app keeps a record, it does not move money.",
            confirmLabel = "Mark as paid",
            onConfirm = { onMarkPaid(payment) },
            onDismiss = { paymentToConfirm = null }
        )
    }
}

/** One payment: money in (helper, green) or money out (requester), its method and status. */
@Composable
private fun PaymentCard(payment: PaymentRecord, isIncoming: Boolean, busy: Boolean, onMarkPaid: () -> Unit) {
    val extended = AppTheme.extendedColors
    val colors = MaterialTheme.colorScheme
    Card(colors = CardDefaults.cardColors(containerColor = extended.card), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = if (isIncoming) extended.successContainer else colors.tertiaryContainer,
                    contentColor = if (isIncoming) extended.onSuccessContainer else colors.onTertiaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (isIncoming) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward,
                            contentDescription = if (isIncoming) "Money in" else "Money out",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(payment.taskTitle, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (isIncoming) "You receive" else "You pay",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
                Text(
                    (if (isIncoming) "+" else "−") + formatEuro(payment.amount),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isIncoming) extended.success else colors.onSurface
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "${payment.method?.label ?: "Method not specified"} · ${payment.otherPartyName}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PaymentStatusChip(payment.status)
                Spacer(Modifier.weight(1f))
                if (payment.status != PaymentStatus.MARKED_PAID) {
                    TextButton(onClick = onMarkPaid, enabled = !busy) { Text("Mark as paid") }
                }
            }
        }
    }
}
