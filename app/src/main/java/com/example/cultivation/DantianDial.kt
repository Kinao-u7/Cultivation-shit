package com.example.cultivation

import android.graphics.Typeface
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

// The whole dial is drawn in a 450 x 450 coordinate space centred on (0,0),
// then scaled to fit, so every number below matches the web prototype.
private const val RING_R = 190f
private const val RUNES = "天地玄黃宇宙洪荒日月盈昃辰宿列張寒來暑往秋收冬藏"
private val Plate = Color(0xFF0B1A1D)
private val NodeEdge = Color(0xFF3B6B5F)

private class Ripple(
    val color: Color,
    val center: Offset,
    val startR: Float,
    val endR: Float,
    val width: Float,
    val delayMs: Long,
    val durationMs: Int = 1500,
) {
    val progress = Animatable(0f)
}

private fun nodePos(k: Int): Offset {
    val a = Math.toRadians(-90.0 + k * 40.0)
    return Offset((RING_R * cos(a)).toFloat(), (RING_R * sin(a)).toFloat())
}

@Composable
fun DantianDial(
    state: CultivationUiState,
    events: Flow<DialEvent>,
    modifier: Modifier = Modifier,
    calligraphy: Typeface = Typeface.SERIF,
    calligraphyFamily: FontFamily = FontFamily.Serif,
) {
    // ---- endless motion -------------------------------------------------
    val inf = rememberInfiniteTransition(label = "dial")
    val runeRot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(90_000, easing = LinearEasing)), label = "rune")
    val baguaRot by inf.animateFloat(0f, -360f, infiniteRepeatable(tween(50_000, easing = LinearEasing)), label = "bagua")
    val orbitA by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(22_000, easing = LinearEasing)), label = "orbA")
    val orbitB by inf.animateFloat(0f, -360f, infiniteRepeatable(tween(16_000, easing = LinearEasing)), label = "orbB")
    val comet by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(14_000, easing = LinearEasing)), label = "comet")

    // ---- progress ring: animate within a realm, snap when the realm changes
    val progress = remember { Animatable(state.progress) }
    LaunchedEffect(state.realmIndex) { progress.snapTo(state.progress) }
    LaunchedEffect(state.progress) {
        progress.animateTo(state.progress, tween(1000, easing = CubicBezierEasing(.2f, .8f, .2f, 1f)))
    }

    // ---- new ring fades in after a breakthrough
    val swap = remember { Animatable(1f) }
    LaunchedEffect(state.realmIndex) {
        swap.snapTo(0f)
        swap.animateTo(1f, tween(900))
    }

    // ---- ripples ----------------------------------------------------------
    val ripples = remember { mutableStateListOf<Ripple>() }
    val scope = rememberCoroutineScope()
    fun spawn(r: Ripple) {
        ripples.add(r)
        scope.launch {
            delay(r.delayMs)
            r.progress.animateTo(1f, tween(r.durationMs, easing = LinearOutSlowInEasing))
            ripples.remove(r)
        }
    }
    LaunchedEffect(events) {
        events.collect { e ->
            when (e) {
                DialEvent.ChapterRead -> spawn(Ripple(Xian.Jade, Offset.Zero, 70f, 217f, 3f, 0))
                is DialEvent.LayerReached -> {
                    e.layers.forEach { i -> spawn(Ripple(Xian.Gold, nodePos(i + 1), 4f, 28f, 1.5f, 0, 1000)) }
                    spawn(Ripple(Xian.Gold, Offset.Zero, 70f, 217f, 3f, 100))
                }
                DialEvent.Burst -> {
                    spawn(Ripple(Xian.Gold, Offset.Zero, 70f, 217f, 3f, 0))
                    spawn(Ripple(Xian.GoldPale, Offset.Zero, 70f, 217f, 3f, 250))
                    spawn(Ripple(Xian.Gold, Offset.Zero, 70f, 217f, 3f, 500))
                }
            }
        }
    }

    // ---- native paints for the CJK glyphs ----------------------------------
    val runePaint = remember(calligraphy) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = calligraphy
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 19f
            color = Xian.Jade.copy(alpha = .85f).toArgb()
        }
    }
    val badgePaint = remember(calligraphy) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = calligraphy
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 22f
        }
    }

    val ready = state.readyToBreak
    val peak = state.layersLit >= LAYERS_PER_REALM && !state.realm.isFinal
    val sweepBrush = remember {
        Brush.sweepGradient(listOf(Xian.JadeDeep, Xian.Jade, Xian.Gold), center = Offset.Zero)
    }

    BoxWithConstraints(modifier.aspectRatio(1f)) {
        val orbSize = maxWidth * 0.31f

        BeamLayer(ready = ready, modifier = Modifier.fillMaxSize())

        Canvas(Modifier.fillMaxSize()) {
            val s = size.minDimension / 450f
            withTransform({
                translate(size.width / 2f, size.height / 2f)
                scale(s, s, Offset.Zero)
            }) {
                // 1. rune characters, slowly turning
                rotate(runeRot, Offset.Zero) {
                    drawIntoNative { c ->
                        RUNES.forEachIndexed { i, ch ->
                            c.save()
                            c.rotate(i * 15f)
                            c.drawText(ch.toString(), 0f, -146f, runePaint)
                            c.restore()
                        }
                    }
                }

                // 2. octagon + 8 trigrams, turning the other way
                rotate(baguaRot, Offset.Zero) {
                    val oct = Path().apply {
                        for (i in 0 until 8) {
                            val a = Math.toRadians(-90.0 + i * 45.0)
                            val x = (134 * cos(a)).toFloat()
                            val y = (134 * sin(a)).toFloat()
                            if (i == 0) moveTo(x, y) else lineTo(x, y)
                        }
                        close()
                    }
                    drawPath(oct, Xian.Jade.copy(alpha = .6f), style = Stroke(1.5f))
                    for (i in 0 until 8) rotate(i * 45f, Offset.Zero) {
                        for (j in 0 until 3) {
                            val y = -(104f + j * 8f)
                            val yang = ((i shr j) and 1) == 1
                            if (yang) {
                                drawLine(Xian.Gold, Offset(-12f, y), Offset(12f, y), 4f, StrokeCap.Round)
                            } else {
                                drawLine(Xian.Gold, Offset(-12f, y), Offset(-3.5f, y), 4f, StrokeCap.Round)
                                drawLine(Xian.Gold, Offset(3.5f, y), Offset(12f, y), 4f, StrokeCap.Round)
                            }
                        }
                    }
                }

                // 3. two small orbits around the orb
                rotate(orbitA, Offset.Zero) {
                    drawCircle(Xian.Jade.copy(alpha = .2f), 92f, style = Stroke(1f))
                    drawCircle(Xian.Gold, 4f, Offset(-65f, -65f))
                    drawCircle(Xian.Jade, 4f, Offset(76f, 52f))
                }
                rotate(orbitB, Offset.Zero) {
                    drawCircle(Xian.Gold.copy(alpha = .22f), 82f, style = Stroke(1f))
                    drawCircle(Xian.Gold, 4f, Offset(0f, -82f))
                }

                // 4. the realm ring (one ring, badge swaps per realm)
                val a = swap.value
                withTransform({ scale(.88f + .12f * a, .88f + .12f * a, Offset.Zero) }) {
                    drawCircle(Color.White.copy(alpha = .14f * a), RING_R, style = Stroke(7f))

                    val p = progress.value
                    if (p > .003f) {
                        rotate(-90f, Offset.Zero) {
                            drawArc(
                                brush = if (ready) Brush.linearGradient(listOf(Xian.Gold, Xian.GoldPale)) else sweepBrush,
                                startAngle = 0f,
                                sweepAngle = p * 360f,
                                useCenter = false,
                                topLeft = Offset(-RING_R, -RING_R),
                                size = Size(RING_R * 2, RING_R * 2),
                                alpha = a,
                                style = Stroke(8f, cap = StrokeCap.Round),
                            )
                        }
                    }
                    rotate(comet, Offset.Zero) {
                        drawArc(
                            Xian.Jade.copy(alpha = .5f * a), 0f, 14.4f, false,
                            Offset(-RING_R, -RING_R), Size(RING_R * 2, RING_R * 2),
                            style = Stroke(2f, cap = StrokeCap.Round),
                        )
                    }

                    // 9 layer gems
                    for (k in 1..LAYERS_PER_REALM) {
                        val lit = k <= state.layersLit
                        val r = if (k == LAYERS_PER_REALM) 7f else 5f
                        val c = nodePos(k)
                        drawCircle((if (lit) Xian.Gold else Plate).copy(alpha = a), r, c)
                        drawCircle((if (lit) Xian.GoldPale else NodeEdge).copy(alpha = a), r, c, style = Stroke(1.5f))
                    }

                    // realm badge at the top: the ring's end meets the name
                    drawCircle(if (peak) Color(0xFF5A3D10) else Plate, 16f, Offset(0f, -RING_R))
                    drawCircle(if (peak) Xian.GoldPale else Xian.Gold, 16f, Offset(0f, -RING_R),
                        style = Stroke(if (peak) 3f else 2.5f))
                    badgePaint.color = (if (peak) Xian.GoldPale else Xian.Gold).copy(alpha = a).toArgb()
                    drawIntoNative { c ->
                        val y = -RING_R - (badgePaint.ascent() + badgePaint.descent()) / 2f
                        c.drawText(state.realm.glyph, 0f, y, badgePaint)
                    }

                    // the bead that travels the ring
                    val ang = Math.toRadians(-90.0 + progress.value * 360.0)
                    val bead = Offset((RING_R * cos(ang)).toFloat(), (RING_R * sin(ang)).toFloat())
                    drawCircle(Xian.Gold.copy(alpha = .53f * a), 11.5f, bead)
                    drawCircle(Color(0xFFFFF6D0).copy(alpha = a), 7f, bead)
                }

                // 5. ripples
                ripples.forEach { r ->
                    val t = r.progress.value
                    if (t > 0f) {
                        val radius = r.startR + (r.endR - r.startR) * t
                        drawCircle(r.color.copy(alpha = .9f * (1f - t)), radius, r.center, style = Stroke(r.width))
                    }
                }
            }
        }

        SpiritOrb(
            realm = state.realm,
            charging = state.breakingThrough,
            size = orbSize,
            calligraphy = calligraphyFamily,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

private inline fun DrawScope.drawIntoNative(block: (android.graphics.Canvas) -> Unit) {
    block(drawContext.canvas.nativeCanvas)
}

/** The glowing jade orb at the centre. Colors change with the realm; swells on breakthrough. */
@Composable
fun SpiritOrb(
    realm: Realm,
    charging: Boolean,
    size: androidx.compose.ui.unit.Dp,
    calligraphy: FontFamily,
    modifier: Modifier = Modifier,
) {
    val pulse by rememberInfiniteTransition(label = "orb").animateFloat(
        1f, 1.055f, infiniteRepeatable(tween(1750, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse",
    )
    val charge = remember { Animatable(0f) }
    LaunchedEffect(charging) {
        if (charging) {
            charge.animateTo(1f, tween(900))
            charge.animateTo(0f, tween(1000))
        } else charge.snapTo(0f)
    }
    val spec = tween<Color>(1500)
    val hi by animateColorAsState(realm.orb.highlight, spec, label = "hi")
    val mid by animateColorAsState(realm.orb.mid, spec, label = "mid")
    val deep by animateColorAsState(realm.orb.deep, spec, label = "deep")
    val shade by animateColorAsState(realm.orb.shadow, spec, label = "shade")
    val glow by animateColorAsState(realm.orb.glow, spec, label = "glow")

    Box(
        modifier
            .size(size)
            .graphicsLayer {
                val sc = pulse * (1f + .45f * charge.value)
                scaleX = sc; scaleY = sc
            }
            .drawBehind {
                val r = this.size.minDimension / 2f
                // outer glow
                drawCircle(
                    Brush.radialGradient(
                        listOf(glow.copy(alpha = .45f), glow.copy(alpha = .12f), Color.Transparent),
                        center = center, radius = r * 2.2f,
                    ), radius = r * 2.2f,
                )
                // glossy sphere: light from the upper left
                drawCircle(
                    Brush.radialGradient(
                        0f to hi, .22f to mid, .58f to deep, .78f to shade, 1f to shade,
                        center = Offset(this.size.width * .35f, this.size.height * .30f),
                        radius = this.size.width * .95f,
                    ), radius = r,
                )
                // faint outer ring
                drawCircle(Color(0x44B3FFDA), r * 1.18f, style = Stroke(1.dp.toPx()))
                // white-out while charging
                if (charge.value > 0f) drawCircle(Color.White.copy(alpha = .55f * charge.value), r)
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "道",
            fontFamily = calligraphy,
            fontSize = (size.value * .32f).sp,
            color = Color(0xFFEDFFF5),
            style = TextStyle(shadow = Shadow(Color(0xFFA0FFD1), blurRadius = 18f)),
        )
    }
}

/** Soft vertical pillar of light above the orb; turns gold and brightens when a breakthrough is ready. */
@Composable
private fun BeamLayer(ready: Boolean, modifier: Modifier = Modifier) {
    val flick by rememberInfiniteTransition(label = "beam").animateFloat(
        .45f, .7f, infiniteRepeatable(tween(3000), RepeatMode.Reverse), label = "flick",
    )
    val widthFrac by animateFloatAsState(if (ready) .30f else .21f, tween(800), label = "beamW")
    val color by animateColorAsState(if (ready) Xian.Gold else Xian.Jade, tween(600), label = "beamC")
    Canvas(
        modifier.graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
            alpha = if (ready) .9f else flick
        },
    ) {
        val w = size.width * widthFrac
        val left = (size.width - w) / 2f
        val h = size.height * .48f
        val top = size.height / 2f - h
        drawRect(
            Brush.horizontalGradient(
                listOf(Color.Transparent, color.copy(alpha = .55f), Color.Transparent),
                startX = left, endX = left + w,
            ),
            topLeft = Offset(left, top), size = Size(w, h),
        )
        // fade the top away
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = top, endY = top + h),
            topLeft = Offset(left, top), size = Size(w, h), blendMode = BlendMode.DstIn,
        )
    }
}
