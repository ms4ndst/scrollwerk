package dev.scrollwerk.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.scrollwerk.launcher.assistant.AssistantClient
import dev.scrollwerk.launcher.assistant.ChatMessage
import dev.scrollwerk.launcher.assistant.ChatProtocol
import dev.scrollwerk.launcher.config.LauncherConfig
import dev.scrollwerk.launcher.ui.theme.NIcons
import dev.scrollwerk.launcher.ui.theme.Noctalia
import dev.scrollwerk.launcher.ui.theme.NoctaliaStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * One conversation with the local AI, kept in memory only: it survives closing the panel and is
 * gone when the launcher restarts or "New chat" is tapped.
 */
class AssistantConversation {
    /** User turns and finished answers; answers are kept raw (a reasoning model's <think> included). */
    val messages = mutableStateListOf<ChatMessage>()
    /** The answer being written, raw; null when idle. */
    var streaming by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var job: Job? = null

    val busy get() = streaming != null

    fun send(scope: CoroutineScope, config: LauncherConfig.Assistant, text: String) {
        val question = text.trim()
        if (question.isEmpty() || busy) return
        error = null
        messages += ChatMessage(ChatMessage.Role.User, question)
        streaming = ""
        job = scope.launch {
            var answer = ""
            try {
                // Ollama and LM Studio need a model name; llama.cpp's server ignores it.
                val model = config.model ?: AssistantClient.models(config.endpoint).getOrThrow().firstOrNull()
                val history = listOf(ChatMessage(ChatMessage.Role.System, config.systemPrompt)) +
                    messages.map { if (it.role == ChatMessage.Role.Assistant) it.copy(content = ChatProtocol.visibleText(it.content)) else it }
                AssistantClient.chat(config.endpoint, model, history).collect { piece ->
                    answer += piece
                    streaming = answer
                }
                messages += ChatMessage(ChatMessage.Role.Assistant, answer)
            } catch (e: CancellationException) {
                if (ChatProtocol.visibleText(answer).isNotBlank()) messages += ChatMessage(ChatMessage.Role.Assistant, answer)
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong."
            } finally {
                streaming = null
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    fun clear() {
        stop()
        messages.clear()
        error = null
    }
}

/**
 * The assistant: a Noctalia panel over the home screen, like the launcher. Answers stream in as
 * they're written; Send (or the keyboard's Enter) asks, the square stops an answer, and "New chat"
 * starts over. Text can be selected and copied.
 */
@Composable
fun AssistantPanel(
    visible: Boolean,
    config: LauncherConfig.Assistant,
    conversation: AssistantConversation,
    scope: CoroutineScope,
    onDismiss: () -> Unit,
) {
    val style = Noctalia.style
    val c = style.colors

    // On open: is anything answering, and which model will answer?
    var status by remember { mutableStateOf<Result<List<String>>?>(null) }
    LaunchedEffect(visible, config.endpoint) {
        if (visible) status = AssistantClient.models(config.endpoint)
    }

    AnimatedVisibility(visible, enter = fadeIn(tween(NoctaliaStyle.ANIM_NORMAL)), exit = fadeOut(tween(NoctaliaStyle.ANIM_FAST))) {
        Box(
            Modifier.fillMaxSize().background(c.scrim.copy(alpha = 0.45f))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
    }
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .padding(12.dp)
            .padding(top = NoctaliaStyle.barThickness + 12.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible,
            enter = fadeIn(tween(NoctaliaStyle.ANIM_NORMAL)) + slideInVertically(tween(NoctaliaStyle.ANIM_NORMAL)) { it / 8 },
            exit = fadeOut(tween(NoctaliaStyle.ANIM_FAST)) + slideOutVertically(tween(NoctaliaStyle.ANIM_FAST)) { it / 16 },
        ) {
            val shape = RoundedCornerShape(style.radiusXl)
            Column(
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .heightIn(max = 620.dp)
                    .clip(shape)
                    .background(c.surface)
                    .border(NoctaliaStyle.borderWidth, c.outlineVariant, shape)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .padding(NoctaliaStyle.panelPadding),
                verticalArrangement = Arrangement.spacedBy(NoctaliaStyle.spaceSm),
            ) {
                Header(config, status, conversation)
                Conversation(conversation, status, config, Modifier.weight(1f, fill = false))
                Prompt(visible, conversation, onSend = { conversation.send(scope, config, it) })
            }
        }
    }
}

@Composable
private fun Header(config: LauncherConfig.Assistant, status: Result<List<String>>?, conversation: AssistantConversation) {
    val c = Noctalia.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(NIcons.Sparkles, null, tint = c.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Assistant", color = c.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        val model = config.model ?: status?.getOrNull()?.firstOrNull()
        Text(
            model ?: if (status?.isFailure == true) "not connected" else "",
            color = if (status?.isFailure == true) c.error else c.onSurfaceVariant,
            fontSize = 12.sp, maxLines = 1, modifier = Modifier.weight(1f),
        )
        if (conversation.messages.isNotEmpty()) {
            Text(
                "New chat", color = c.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(CircleShape).clickable { conversation.clear() }.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun Conversation(conversation: AssistantConversation, status: Result<List<String>>?, config: LauncherConfig.Assistant, modifier: Modifier) {
    val c = Noctalia.colors
    val scroll = rememberScrollState()
    LaunchedEffect(conversation.messages.size, conversation.streaming, conversation.error) { scroll.animateScrollTo(scroll.maxValue) }
    SelectionContainer(modifier) {
        Column(Modifier.fillMaxWidth().verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (conversation.messages.isEmpty() && conversation.streaming == null) {
                val failure = status?.exceptionOrNull()
                Text(
                    if (failure != null) failure.message.orEmpty()
                    else "Ask anything. The answer comes from the AI app on your phone; nothing is sent anywhere else.",
                    color = if (failure != null) c.error else c.onSurfaceVariant,
                    fontSize = 13.sp,
                )
                if (failure != null) {
                    Text("Address: ${config.endpoint}. Setup steps are in the README, under \"Assistant\".", color = c.onSurfaceVariant, fontSize = 12.sp)
                }
            }
            conversation.messages.forEach { m ->
                if (m.role == ChatMessage.Role.User) UserBubble(m.content) else AnswerText(ChatProtocol.visibleText(m.content))
            }
            conversation.streaming?.let { raw ->
                val shown = ChatProtocol.visibleText(raw)
                if (shown.isBlank()) {
                    Text(if (ChatProtocol.isThinking(raw)) "Thinking…" else "…", color = c.onSurfaceVariant, fontSize = 14.sp, fontStyle = FontStyle.Italic)
                } else {
                    AnswerText(shown)
                }
            }
            conversation.error?.let { Text(it, color = c.error, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    val style = Noctalia.style
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            text, color = style.colors.onSurface, fontSize = 14.sp,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(style.radiusLg))
                .background(style.colors.surfaceContainerHigh)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun AnswerText(text: String) {
    Text(text, color = Noctalia.colors.onSurface, fontSize = 14.sp, lineHeight = 20.sp)
}

@Composable
private fun Prompt(visible: Boolean, conversation: AssistantConversation, onSend: (String) -> Unit) {
    val style = Noctalia.style
    val c = style.colors
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(visible) {
        if (visible) {
            runCatching { focus.requestFocus() }
            keyboard?.show()
        }
    }
    fun send() {
        if (text.isNotBlank() && !conversation.busy) { onSend(text); text = "" }
    }
    val shape = RoundedCornerShape(style.radiusLg)
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            maxLines = 4,
            textStyle = LocalTextStyle.current.merge(TextStyle(color = c.onSurface, fontSize = 16.sp)),
            cursorBrush = SolidColor(c.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
            modifier = Modifier.weight(1f).focusRequester(focus),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = NoctaliaStyle.controlHeightLg)
                        .clip(shape)
                        .background(c.surfaceContainer)
                        .border(NoctaliaStyle.borderWidth, c.primary, shape)
                        .padding(horizontal = NoctaliaStyle.spaceMd, vertical = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (text.isEmpty()) Text("Ask anything…", color = c.onSurfaceVariant, fontSize = 16.sp)
                    inner()
                }
            },
        )
        val busy = conversation.busy
        Box(
            Modifier
                .size(NoctaliaStyle.controlHeightLg)
                .clip(CircleShape)
                .background(if (busy || text.isNotBlank()) c.primary else c.surfaceContainer)
                .clickable { if (busy) conversation.stop() else send() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (busy) NIcons.Stop else NIcons.ArrowUp,
                if (busy) "Stop" else "Send",
                tint = if (busy || text.isNotBlank()) c.onPrimary else c.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** The home screen's second pill, beside "Search apps…". */
@Composable
fun AskPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Noctalia.colors
    Row(
        modifier
            .height(NoctaliaStyle.controlHeightLg)
            .clip(CircleShape)
            .background(c.surface.copy(alpha = 0.94f))
            .border(NoctaliaStyle.borderWidth, c.outlineVariant, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = NoctaliaStyle.spaceLg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NoctaliaStyle.spaceSm),
    ) {
        Icon(NIcons.Sparkles, contentDescription = null, tint = c.primary, modifier = Modifier.size(18.dp))
        Text("Ask…", color = c.onSurfaceVariant, fontSize = 15.sp)
    }
}
