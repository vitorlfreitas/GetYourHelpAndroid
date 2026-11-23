package com.vitorfreitas.getyourhelp.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorfreitas.getyourhelp.data.AppUser
import com.vitorfreitas.getyourhelp.data.ChatMessage
import com.vitorfreitas.getyourhelp.data.TaskItem
import com.vitorfreitas.getyourhelp.data.chatIsOpen
import com.vitorfreitas.getyourhelp.data.hasChatFor
import com.vitorfreitas.getyourhelp.ui.components.EmptyState
import com.vitorfreitas.getyourhelp.ui.components.InitialsAvatar
import com.vitorfreitas.getyourhelp.ui.components.LoadingIndicator
import com.vitorfreitas.getyourhelp.ui.components.Pill
import com.vitorfreitas.getyourhelp.ui.formatTime
import com.vitorfreitas.getyourhelp.validation.ChatValidator

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/**
 * Live chat between the requester and the accepted helper of a task. New messages appear on both
 * phones without refreshing. Once the task is completed the history stays visible, but the text
 * field is replaced by a note that the chat is read-only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: ChatState,
    user: AppUser,
    onSend: (String) -> Unit,
    onUnsentTextRestored: () -> Unit,
    onBack: () -> Unit
) {
    // The task, if this user may read its chat (the requester or the accepted helper)
    val task: TaskItem? = state.task?.takeIf { it.hasChatFor(user.uid) }
    val otherName = when {
        task == null -> ""
        task.ownerId == user.uid -> task.assignedHelperName
        else -> task.ownerName
    }
    // The draft is saved, so it survives rotation. It is only the user's own text, not a password.
    var draft by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state.unsentText) {
        state.unsentText?.let { unsent ->
            if (draft.isBlank()) draft = unsent
            onUnsentTextRestored()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (task != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            InitialsAvatar(otherName, size = 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(otherName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    task.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    } else {
                        Text("Chat")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        },
        bottomBar = {
            when {
                task == null -> Unit
                task.chatIsOpen -> MessageInput(
                    draft = draft,
                    onDraftChange = { draft = it },
                    onSend = {
                        onSend(draft)
                        draft = ""
                    }
                )
                else -> ReadOnlyNotice()
            }
        }
    ) { padding ->
        val contentModifier = Modifier.padding(padding).consumeWindowInsets(padding)
        when {
            state.loading || (task != null && !state.messagesLoaded) -> LoadingIndicator(contentModifier)
            task == null -> EmptyState(
                title = "Chat not available",
                message = "The chat opens when a helper is accepted. Only the requester and that helper can see it.",
                icon = Icons.AutoMirrored.Outlined.Chat,
                modifier = contentModifier
            )
            state.messages.isEmpty() && task.chatIsOpen -> EmptyState(
                title = "Say hello to ${otherName.substringBefore(' ')}",
                message = "Agree on the details here: the exact address, the time and how payment works. Only the two of you can see this chat.",
                icon = Icons.AutoMirrored.Outlined.Chat,
                modifier = contentModifier
            )
            state.messages.isEmpty() -> EmptyState(
                title = "No messages",
                message = "No messages were sent for this task.",
                icon = Icons.AutoMirrored.Outlined.Chat,
                modifier = contentModifier
            )
            else -> MessageList(state.messages, user.uid, contentModifier)
        }
    }
}

/**
 * The messages, newest at the bottom. The list is reversed (reverseLayout), so it starts at the
 * bottom and stays there when a new message arrives.
 */
@Composable
private fun MessageList(messages: List<ChatMessage>, myId: String, modifier: Modifier) {
    val rows = remember(messages, myId) { chatRows(messages, myId).asReversed() }
    val listState = rememberLazyListState()
    // Scroll down to a new message if it is mine, or if the user was already looking at the latest ones.
    val newest = messages.lastOrNull()
    LaunchedEffect(newest?.id) {
        if (newest != null && (newest.senderId == myId || listState.firstVisibleItemIndex <= 1)) {
            listState.animateScrollToItem(0)
        }
    }
    LazyColumn(
        state = listState,
        reverseLayout = true,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(rows, key = { it.key }) { row ->
            when (row) {
                is ChatRow.DayHeader -> DayHeader(row.label)
                is ChatRow.Bubble -> MessageBubble(row.message, row.isMine)
            }
        }
    }
}

@Composable
private fun DayHeader(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Pill(label, MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One message: mine on the right in the brand colour, theirs on the left. */
@Composable
private fun MessageBubble(message: ChatMessage, isMine: Boolean) {
    val colors = MaterialTheme.colorScheme
    val container = if (isMine) colors.primary else colors.surfaceContainerHigh
    val content = if (isMine) colors.onPrimary else colors.onSurface
    // The corner next to the sender is less rounded, like a speech bubble's tail
    val shape = if (isMine) RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start) {
        // At most 80% of the width, so it is clear which side a message is on
        Box(Modifier.fillMaxWidth(0.8f), contentAlignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart) {
            Surface(color = container, contentColor = content, shape = shape) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(message.text, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (message.pending) "Sending…" else formatTime(message.sentAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = content.copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
    }
}

/** The text field and the send button. It moves up with the keyboard. */
@Composable
private fun MessageInput(draft: String, onDraftChange: (String) -> Unit, onSend: () -> Unit) {
    val error = ChatValidator.message(draft).takeIf { draft.isNotBlank() }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                placeholder = { Text("Message") },
                isError = error != null,
                supportingText = if (draft.length > ChatValidator.MESSAGE_MAX - 100) {
                    { Text(error ?: "${draft.trim().length}/${ChatValidator.MESSAGE_MAX}") }
                } else null,
                maxLines = 5,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = onSend,
                enabled = ChatValidator.message(draft) == null,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

/** Shown instead of the text field once the task is completed. */
@Composable
private fun ReadOnlyNotice() {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "This task is completed, so the chat is read-only.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
