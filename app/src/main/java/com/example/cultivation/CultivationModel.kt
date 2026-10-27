package com.example.cultivation

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

object Xian {
    val Ink = Color(0xFF081114)
    val Jade = Color(0xFF7CF2C3)
    val JadeDeep = Color(0xFF2A9A78)
    val Gold = Color(0xFFF3CB83)
    val GoldPale = Color(0xFFFFF1C4)
    val Mist = Color(0xFF92AAA6)
    val Line = Color(0x21C0E4D7)
    val Cinnabar = Color(0xFFC8412F)
}

/** Colors of the spirit orb. Each realm tints the orb differently. */
class OrbPalette(val highlight: Color, val mid: Color, val deep: Color, val shadow: Color, val glow: Color)

data class Realm(
    val glyph: String,
    val name: String,
    val coreName: String,
    /** Qi needed to fill all 9 layers. 0 = final realm (no further breakthrough). */
    val qiNeeded: Int,
    val orb: OrbPalette,
) {
    val isFinal get() = qiNeeded == 0
}

private val Jade = OrbPalette(Color(0xFFD3FFE9), Color(0xFF63D8A7), Color(0xFF137559), Color(0xFF0B302D), Color(0xFF5CE5AD))
private val Cyan = OrbPalette(Color(0xFFD3F6FF), Color(0xFF63B8D8), Color(0xFF135A75), Color(0xFF0B2530), Color(0xFF5CC6E5))
private val Blue = OrbPalette(Color(0xFFD9E0FF), Color(0xFF6F7FE0), Color(0xFF1F2B80), Color(0xFF0D1237), Color(0xFF6E86FF))
private val Violet = OrbPalette(Color(0xFFEBD9FF), Color(0xFFB070E0), Color(0xFF4A1F80), Color(0xFF220D37), Color(0xFFC37CFF))
private val Ember = OrbPalette(Color(0xFFFFE3D3), Color(0xFFE87A63), Color(0xFF7A1F13), Color(0xFF330D0B), Color(0xFFFF8A5C))

val Realms = listOf(
    Realm("氣", "Qi Condensation", "Jade Spirit Core", 180, Jade),
    Realm("基", "Foundation Establishment", "Jade Foundation Core", 360, Jade),
    Realm("丹", "Golden Core", "Golden Dao Core", 700, Cyan),
    Realm("嬰", "Nascent Soul", "Nascent Soul Core", 1200, Blue),
    Realm("神", "Soul Transformation", "Transformed Soul Core", 2000, Violet),
    Realm("虛", "Void Refinement", "Void Core", 0, Ember),
)

const val LAYERS_PER_REALM = 9

data class Toast(val id: Long, val text: String)

data class CultivationUiState(
    val realmIndex: Int = 0,
    val qi: Int = 80,
    val chapters: Int = 12,
    val streakDays: Int = 5,
    val minutesRead: Int = 540,
    val ai: Int = 1280,          // wisdom gathered from reading
    val focus: Int = 72,
    val spirit: Int = 86,
    val daoInsight: Int = 12,
    val breakingThrough: Boolean = false,
    val flash: Boolean = false,
    val toast: Toast? = null,
) {
    val realm: Realm get() = Realms[realmIndex]
    val progress: Float
        get() = if (realm.isFinal) 1f else (qi.toFloat() / realm.qiNeeded).coerceIn(0f, 1f)
    val layersLit: Int
        get() = if (realm.isFinal) LAYERS_PER_REALM
        else min(LAYERS_PER_REALM, floor(progress * LAYERS_PER_REALM + 1e-6f).toInt())
    val readyToBreak: Boolean get() = !realm.isFinal && qi >= realm.qiNeeded
}

/** One-shot visual effects for the dial (ripples, shockwaves). */
sealed interface DialEvent {
    object ChapterRead : DialEvent
    data class LayerReached(val layers: List<Int>) : DialEvent   // 0-based layer indices
    object Burst : DialEvent
}

class CultivationViewModel : ViewModel() {
    private val _state = MutableStateFlow(CultivationUiState())
    val state: StateFlow<CultivationUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<DialEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<DialEvent> = _events.asSharedFlow()

    /** Call this when a chapter is finished. Default adds 50 Qi as required. */
    fun onChapterRead(
        qiGain: Int = 50,
        aiGain: Int = 25,
        minutes: Int = 14,
    ) {
        val before = _state.value
        if (before.breakingThrough) return
        val after = before.copy(
            qi = before.qi + qiGain,
            ai = before.ai + aiGain,
            chapters = before.chapters + 1,
            minutesRead = before.minutesRead + minutes,
            focus = min(100, before.focus + 2),
        )
        var text = "A new chapter nourishes your spirit. +$qiGain Qi · +$aiGain Ai"
        if (!before.realm.isFinal && after.layersLit > before.layersLit) {
            _events.tryEmit(DialEvent.LayerReached((before.layersLit until after.layersLit).toList()))
            text += if (after.layersLit >= LAYERS_PER_REALM) " · Peak reached"
            else " · Layer ${after.layersLit + 1} reached"
        }
        _events.tryEmit(DialEvent.ChapterRead)
        _state.value = after.copy(toast = Toast(System.nanoTime(), text))
    }

    fun beginBreakthrough() {
        val s = _state.value
        if (!s.readyToBreak || s.breakingThrough) return
        viewModelScope.launch {
            _state.update { it.copy(breakingThrough = true) }
            _events.emit(DialEvent.Burst)
            delay(700)
            _state.update { it.copy(flash = true) }
            delay(200)
            _state.update { cur ->
                val next = min(cur.realmIndex + 1, Realms.lastIndex)
                cur.copy(
                    realmIndex = next,
                    qi = max(0, cur.qi - cur.realm.qiNeeded),
                    daoInsight = cur.daoInsight + 1,
                    flash = false,
                    toast = Toast(System.nanoTime(), "Breakthrough complete! You have entered ${Realms[next].name}."),
                )
            }
            delay(1000)
            _state.update { it.copy(breakingThrough = false) }
        }
    }

    fun dismissToast(id: Long) {
        _state.update { if (it.toast?.id == id) it.copy(toast = null) else it }
    }
}
