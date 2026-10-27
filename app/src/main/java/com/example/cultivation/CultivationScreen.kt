package com.example.cultivation

/*
 * Gradle (module build.gradle.kts), versions via the Compose BOM:
 *
 *   implementation(platform("androidx.compose:compose-bom:2024.09.00"))
 *   implementation("androidx.compose.ui:ui")
 *   implementation("androidx.compose.foundation:foundation")
 *   implementation("androidx.compose.material3:material3")
 *   implementation("androidx.activity:activity-compose:1.9.2")
 *   implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
 *   implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
 *
 * Use it:   setContent { CultivationScreen() }       (call enableEdgeToEdge() first)
 * From the reader: call viewModel.onChapterRead(qiGain = ..., aiGain = ..., minutes = ...)
 */

import android.content.Context
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.sin

/**
 * Fonts. These fall back to system fonts so the project compiles out of the box.
 * For the full look, download Cinzel, Manrope and Ma Shan Zheng from Google Fonts,
 * put them in res/font/ and swap in the lines marked "→".
 */
object XianFonts {
    val Heading: FontFamily = FontFamily.Serif        // → FontFamily(Font(R.font.cinzel, FontWeight.Medium))
    val Body: FontFamily = FontFamily.SansSerif       // → FontFamily(Font(R.font.manrope))
    val Calligraphy: FontFamily = FontFamily.Serif    // → FontFamily(Font(R.font.ma_shan_zheng))

    /** Used for the glyphs drawn on the canvas (rune ring, realm badge). */
    @Suppress("UNUSED_PARAMETER")
    fun calligraphyTypeface(context: Context): Typeface =
        Typeface.SERIF                                // → ResourcesCompat.getFont(context, R.font.ma_shan_zheng)!!
}

@Composable
fun CultivationScreen(vm: CultivationViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    CultivationContent(
        state = state,
        events = vm.events,
        onRead = { vm.onChapterRead() },
        onBreakthrough = vm::beginBreakthrough,
        onToastShown = vm::dismissToast,
    )
}

@Composable
fun CultivationContent(
    state: CultivationUiState,
    events: Flow<DialEvent>,
    onRead: () -> Unit,
    onBreakthrough: () -> Unit,
    onToastShown: (Long) -> Unit,
) {
    val context = LocalContext.current
    val typeface = remember { XianFonts.calligraphyTypeface(context) }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val boost = remember { Animatable(0f) }          // speeds up the drifting motes

    LaunchedEffect(events) {
        events.collect { e ->
            when (e) {
                DialEvent.ChapterRead -> scope.launch { boost.snapTo(1f); boost.animateTo(0f, tween(1500)) }
                is DialEvent.LayerReached -> haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                DialEvent.Burst -> {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    scope.launch { boost.snapTo(3f); boost.animateTo(0f, tween(2500)) }
                }
            }
        }
    }

    // toast: keep the text around while it fades out
    var shownToast by remember { mutableStateOf("") }
    LaunchedEffect(state.toast) {
        state.toast?.let {
            shownToast = it.text
            delay(2600)
            onToastShown(it.id)
        }
    }
    val flashAlpha by animateFloatAsState(
        if (state.flash) .85f else 0f, tween(if (state.flash) 120 else 900), label = "flash",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Xian.Ink)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0xFF17463D), Color.Transparent),
                        center = Offset(size.width * .5f, size.height * .24f), radius = size.width * .95f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0x992A1B10), Color.Transparent),
                        center = Offset(size.width * .9f, size.height * .92f), radius = size.width * .7f,
                    ),
                )
            },
    ) {
        QiMotes(speed = 1f + boost.value * 2f, modifier = Modifier.fillMaxSize())

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 16.dp)
                .padding(top = 18.dp, bottom = 44.dp),
        ) {
            Header()

            DantianDial(
                state = state,
                events = events,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                calligraphy = typeface,
                calligraphyFamily = XianFonts.Calligraphy,
            )

            RealmInfo(state)
            QiBar(state)

            if (state.readyToBreak) {
                ActionButton("BEGIN BREAKTHROUGH", gold = true, enabled = !state.breakingThrough, onClick = onBreakthrough)
            } else {
                ActionButton("READ A CHAPTER", gold = false, enabled = !state.breakingThrough, onClick = onRead)
            }

            Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Chip("✧ Ai · wisdom from reading", "%,d".format(state.ai), min(1f, state.ai / 2000f), gold = true, Modifier.weight(1f))
                    Chip("☯ Focus", "${state.focus}%", state.focus / 100f, gold = false, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Chip("◈ Spirit", "${state.spirit}%", state.spirit / 100f, gold = false, Modifier.weight(1f))
                    Chip("☼ Dao Insight", "${state.daoInsight}", min(1f, state.daoInsight * 3 / 100f), gold = true, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat("${state.chapters}", "chapters", Modifier.weight(1f))
                    Stat("${state.streakDays}", "day streak", Modifier.weight(1f))
                    Stat("%.1f".format(state.minutesRead / 60f), "hours read", Modifier.weight(1f))
                }
            }
        }

        // breakthrough flash
        if (flashAlpha > .01f) {
            Box(Modifier.fillMaxSize().background(Color(0xFFFFF8E0).copy(alpha = flashAlpha)))
        }

        AnimatedVisibility(
            visible = state.toast != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 24.dp, vertical = 22.dp),
            enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 2 },
            exit = fadeOut(tween(300)),
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF10251F))
                    .border(1.dp, Color(0x477CF2C3), RoundedCornerShape(22.dp))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                Text(shownToast, color = Xian.Jade, fontSize = 12.sp, textAlign = TextAlign.Center, fontFamily = XianFonts.Body)
            }
        }
    }
}

@Composable
private fun Header() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text("CULTIVATOR LIN YUE", color = Xian.Gold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp, fontFamily = XianFonts.Heading)
            Text("Path of the Immortal", color = Color(0xFFEAF3F0), fontSize = 22.sp, letterSpacing = .3.sp,
                fontFamily = XianFonts.Heading)
        }
        val seal = RoundedCornerShape(6.dp)
        Box(
            Modifier
                .size(42.dp)
                .rotate(-6f)
                .shadow(12.dp, seal, ambientColor = Xian.Cinnabar, spotColor = Xian.Cinnabar)
                .background(Xian.Cinnabar, seal),
            contentAlignment = Alignment.Center,
        ) {
            Text("道", color = Color(0xFFFFF1E6), fontSize = 26.sp, fontFamily = XianFonts.Calligraphy)
        }
    }
}

@Composable
private fun RealmInfo(state: CultivationUiState) {
    val realm = state.realm
    val layer = when {
        realm.isFinal -> "Beyond the heavens"
        state.layersLit >= LAYERS_PER_REALM -> "Layer 9 · Peak"
        else -> "Layer ${state.layersLit + 1} of $LAYERS_PER_REALM"
    }
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            realm.glyph, color = Xian.Gold, fontSize = 58.sp, fontFamily = XianFonts.Calligraphy,
            style = TextStyle(shadow = Shadow(Color(0x66F3CB83), blurRadius = 40f)),
        )
        Column {
            Text(realm.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .8.sp,
                color = Color(0xFFEAF3F0), fontFamily = XianFonts.Heading)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Xian.Gold, fontWeight = FontWeight.SemiBold)) { append(layer) }
                    append(" · ${realm.coreName}")
                },
                color = Xian.Mist, fontSize = 12.sp, fontFamily = XianFonts.Body,
            )
        }
    }
}

@Composable
private fun QiBar(state: CultivationUiState) {
    val p by animateFloatAsState(state.progress, tween(1000, easing = CubicBezierEasing(.2f, .8f, .2f, 1f)), label = "qi")
    val full = state.readyToBreak
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Text("Qi", color = Xian.Mist, fontSize = 12.sp, fontFamily = XianFonts.Body)
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Xian.Jade, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, fontFamily = XianFonts.Heading)) { append("${state.qi}") }
                append(" / ${if (state.realm.isFinal) "∞" else state.realm.qiNeeded.toString()}")
            },
            color = Xian.Mist, fontSize = 12.sp, fontFamily = XianFonts.Body,
        )
    }
    val track = RoundedCornerShape(6.dp)
    Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(8.dp).clip(track).background(Color.White.copy(alpha = .08f))) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(p)
                .clip(track)
                .background(
                    Brush.horizontalGradient(
                        if (full) listOf(Color(0xFFAA7640), Xian.Gold) else listOf(Color(0xFF4CAE8D), Color(0xFF9AF6C9)),
                    ),
                ),
        )
    }
}

@Composable
private fun ActionButton(text: String, gold: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "btn").animateFloat(
        1f, if (gold) 1.02f else 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "pulse",
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (gold) Xian.Gold else Xian.Jade,
            contentColor = Color(0xFF082019),
            disabledContainerColor = Xian.Mist.copy(alpha = .4f),
            disabledContentColor = Color(0xFF082019),
        ),
        modifier = Modifier
            .padding(top = 14.dp)
            .fillMaxWidth()
            .height(52.dp)
            .graphicsLayer { scaleX = pulse; scaleY = pulse },
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp, fontFamily = XianFonts.Body)
    }
}

private fun Modifier.glass(radius: Dp = 14.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .clip(shape)
        .background(Brush.linearGradient(listOf(Color(0xD615272B), Color(0xC70D191F))))
        .border(1.dp, Xian.Line, shape)
}

@Composable
private fun Chip(label: String, value: String, progress: Float, gold: Boolean, modifier: Modifier = Modifier) {
    val p by animateFloatAsState(progress, tween(800), label = "chip")
    Column(modifier.glass().padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(label, color = Xian.Mist, fontSize = 11.sp, letterSpacing = .6.sp, fontFamily = XianFonts.Body)
        Text(value, color = Xian.Gold, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, fontFamily = XianFonts.Heading)
        val track = RoundedCornerShape(4.dp)
        Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(4.dp).clip(track).background(Color.White.copy(alpha = .08f))) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(p.coerceIn(0f, 1f)).clip(track).background(
                    Brush.horizontalGradient(
                        if (gold) listOf(Color(0xFFAA7640), Xian.Gold) else listOf(Color(0xFF4CAE8D), Color(0xFF9AF6C9)),
                    ),
                ),
            )
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.glass().padding(horizontal = 6.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Xian.Gold, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, fontFamily = XianFonts.Heading)
        Text(label, color = Xian.Mist, fontSize = 11.sp, letterSpacing = .6.sp, fontFamily = XianFonts.Body)
    }
}

// ---- drifting Qi motes in the background ----------------------------------
private class Mote(var x: Float, var y: Float, val r: Float, val v: Float, val phase: Float, val a: Float)

@Composable
private fun QiMotes(speed: Float, modifier: Modifier = Modifier) {
    val rnd = remember { kotlin.random.Random(7) }
    fun spawn(initial: Boolean) = Mote(
        x = rnd.nextFloat(), y = if (initial) rnd.nextFloat() else 1.02f,
        r = rnd.nextFloat() * 1.4f + .5f, v = rnd.nextFloat() * .5f + .2f,
        phase = rnd.nextFloat() * 6f, a = rnd.nextFloat() * .5f + .15f,
    )
    val motes = remember { Array(36) { spawn(true) } }
    val speedNow by rememberUpdatedState(speed)
    var frame by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { t ->
                val dt = if (last == 0L) 16f else (t - last) / 1_000_000f
                last = t
                for (i in motes.indices) {
                    val m = motes[i]
                    m.y -= m.v * speedNow * dt * 0.00005f
                    m.x += sin(t / 9e8f + m.phase) * 0.0003f
                    if (m.y < -0.02f) motes[i] = spawn(false)
                }
                frame = t
            }
        }
    }
    Canvas(modifier) {
        val tick = frame                       // reading this state redraws every frame
        if (tick < 0L) return@Canvas
        motes.forEach { m ->
            drawCircle(Xian.Jade.copy(alpha = m.a), m.r.dp.toPx(), Offset(m.x * size.width, m.y * size.height))
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF081114, widthDp = 390, heightDp = 900)
@Composable
private fun CultivationPreview() {
    CultivationContent(CultivationUiState(), emptyFlow(), {}, {}, {})
}
