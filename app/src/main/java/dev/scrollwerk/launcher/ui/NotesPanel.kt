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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.scrollwerk.launcher.data.Notes
import dev.scrollwerk.launcher.data.NotesStore
import dev.scrollwerk.launcher.ui.theme.NIcons
import dev.scrollwerk.launcher.ui.theme.Noctalia
import dev.scrollwerk.launcher.ui.theme.NoctaliaStyle

/**
 * The notes editor: a Noctalia panel above the keyboard, like the assistant's. Saves as you type;
 * Done, Back or a tap outside closes it.
 */
@Composable
fun NotesPanel(visible: Boolean, store: NotesStore, onDismiss: () -> Unit) {
    val style = Noctalia.style
    val c = style.colors
    // Whatever is still waiting to be written goes now.
    LaunchedEffect(visible) { if (!visible) store.save() }

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(NIcons.Notes, null, tint = c.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Notes", color = c.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(
                        "Done", color = c.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(CircleShape).clickable(onClick = onDismiss).padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                val text by store.text.collectAsStateWithLifecycle()
                // Opens with the cursor at the end, where new lines usually go.
                var value by remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
                LaunchedEffect(text) { if (text != value.text) value = TextFieldValue(text, TextRange(text.length)) }
                LaunchedEffect(visible) { if (visible) value = TextFieldValue(text, TextRange(text.length)) }
                fun apply(next: TextFieldValue) {
                    value = next
                    if (next.text != text) store.edit(next.text)
                }
                NotesEditor(visible, value, Modifier.weight(1f, fill = false)) { typed ->
                    // Enter in a list starts the next item; Enter on an empty item ends the list;
                    // backspace into a ☐ removes the whole checkbox.
                    val typedEdit = Notes.Edit(typed.text, typed.selection.start, typed.selection.end)
                    val e = Notes.backspaceMarker(value.text, Notes.continueList(value.text, typedEdit))
                    apply(if (e.text == typed.text) typed else TextFieldValue(e.text, TextRange(e.start, e.end)))
                }
                FormatBar { kind ->
                    val e = Notes.format(Notes.Edit(value.text, value.selection.start, value.selection.end), kind)
                    // A fresh value without a composition: the keyboard's half-typed word ends here instead of
                    // being written again over the old position.
                    apply(TextFieldValue(e.text, TextRange(e.start, e.end)))
                }
            }
        }
    }
}

/** Checkbox, list and heading for the cursor's line (or every selected line); press again to undo. */
@Composable
private fun FormatBar(onFormat: (Notes.Kind) -> Unit) {
    val style = Noctalia.style
    val c = style.colors
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(
            Triple(NIcons.Checkbox, "Checkbox", Notes.Kind.Task),
            Triple(NIcons.List, "List", Notes.Kind.Bullet),
            Triple(NIcons.Heading, "Heading", Notes.Kind.Heading),
        ).forEach { (icon, label, kind) ->
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(c.surfaceContainer)
                    .border(NoctaliaStyle.borderWidth, c.outlineVariant, CircleShape)
                    // Not focusable: the text field keeps the keyboard and the cursor.
                    .clickable { onFormat(kind) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(icon, null, tint = c.primary, modifier = Modifier.size(16.dp))
                Text(label, color = c.onSurface, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun NotesEditor(visible: Boolean, value: TextFieldValue, modifier: Modifier, onChange: (TextFieldValue) -> Unit) {
    val style = Noctalia.style
    val c = style.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(visible) { if (visible) runCatching { focus.requestFocus() } }
    val shape = RoundedCornerShape(style.radiusLg)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = LocalTextStyle.current.merge(TextStyle(color = c.onSurface, fontSize = 15.sp, lineHeight = 22.sp)),
        cursorBrush = SolidColor(c.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        visualTransformation = remember(c.primary, c.onSurfaceVariant) { MarkerTransformation(c.primary, c.onSurfaceVariant) },
        modifier = modifier.fillMaxWidth().focusRequester(focus),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp)
                    .clip(shape)
                    .background(c.surfaceContainer)
                    .border(NoctaliaStyle.borderWidth, c.primary, shape)
                    .padding(12.dp),
            ) {
                if (value.text.isEmpty()) {
                    Text("Write anything. The buttons below make checkboxes, lists and headings.", color = c.onSurfaceVariant, fontSize = 15.sp)
                }
                inner()
            }
        },
    )
}

/**
 * Draws the file's markers the way the card does: "- [ ] " as ☐, "- [x] " as ☑ (and the line
 * struck through), "- " as •, and "# " hidden with the line in bold. The text itself, and so the
 * file, keeps the plain markers.
 */
private class MarkerTransformation(private val accent: Color, private val muted: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val markers = Notes.markers(raw)
        if (markers.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        // Where each marker went and what it became, to map cursor positions both ways.
        class Swap(val start: Int, val length: Int, val newStart: Int, val newLength: Int)
        val swaps = ArrayList<Swap>()
        val out = buildAnnotatedString {
            var pos = 0
            markers.forEach { m ->
                append(raw.substring(pos, m.start))
                val lineEnd = raw.indexOf('\n', m.start).let { if (it < 0) raw.length else it }
                val symbol = when (m.kind) {
                    Notes.Kind.Task -> if (m.checked) "☑ " else "☐ "
                    Notes.Kind.Bullet -> "• "
                    Notes.Kind.Heading -> ""
                }
                val at = length
                withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) { append(symbol) }
                swaps += Swap(m.start, m.length, at, symbol.length)
                val rest = raw.substring(m.start + m.length, lineEnd)
                when {
                    m.kind == Notes.Kind.Heading -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp)) { append(rest) }
                    m.checked -> withStyle(SpanStyle(color = muted, textDecoration = TextDecoration.LineThrough)) { append(rest) }
                    else -> append(rest)
                }
                pos = lineEnd
            }
            append(raw.substring(pos))
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                var shift = 0
                for (s in swaps) {
                    if (offset <= s.start) break
                    if (offset < s.start + s.length) return s.newStart + s.newLength
                    shift += s.newLength - s.length
                }
                return (offset + shift).coerceIn(0, out.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                var shift = 0
                for (s in swaps) {
                    if (offset <= s.newStart) break
                    if (offset < s.newStart + s.newLength) return s.start + s.length
                    shift += s.length - s.newLength
                }
                return (offset + shift).coerceIn(0, raw.length)
            }
        }
        return TransformedText(out, mapping)
    }
}
