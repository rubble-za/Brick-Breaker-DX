package com.brickbreaker

import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.brickbreaker.audio.SoundBoard
import com.brickbreaker.engine.Brick
import com.brickbreaker.engine.GameEngine
import com.brickbreaker.engine.GameEvent
import com.brickbreaker.engine.Phase
import com.brickbreaker.engine.PowerUp
import com.brickbreaker.engine.PowerUpType
import kotlin.math.max

private const val STEP = 1f / 120f // fixed simulation step, independent of display refresh rate
private val MinTopHeight = 96.dp
private val MinBottomHeight = 110.dp
private val RingRadius = 28.dp

private val Background = Color(0xFF0B0F1A)
private val PanelColor = Color(0xFF1B2440)
private val FieldColor = Color(0xFF131A2B)
private val FieldBorder = Color(0xFF2C3A63)
private val PaddleColor = Color(0xFFE8EEF9)
private val BallColor = Color(0xFFFFD166)
private val SplitBallColor = Color(0xFF2EC4B6)
private val GunColor = Color(0xFFB5179E)
private val StickyPaddleColor = Color(0xFFFFB703)
private val BulletColor = Color(0xFFFFF3B0)
private val SteelColor = Color(0xFF6B7280)
private val GuideColor = Color(0xFF4895EF)
private val BrickColors = mapOf(
    1 to Color(0xFF4CC9F0),
    2 to Color(0xFF4895EF),
    3 to Color(0xFF7209B7),
)

private fun PowerUpType.color() = when (this) {
    PowerUpType.WIDE -> Color(0xFF52B788)
    PowerUpType.MULTI -> Color(0xFFF77F00)
    PowerUpType.SLOW -> Color(0xFF90E0EF)
    PowerUpType.LIFE -> Color(0xFFEF476F)
    PowerUpType.GUNS -> Color(0xFFB5179E)
    PowerUpType.STICKY -> Color(0xFFFFB703)
    PowerUpType.SPLIT -> Color(0xFF2EC4B6)
}

private fun PowerUpType.label() = when (this) {
    PowerUpType.WIDE -> "W"
    PowerUpType.MULTI -> "M"
    PowerUpType.SLOW -> "S"
    PowerUpType.LIFE -> "+"
    PowerUpType.GUNS -> "G"
    PowerUpType.STICKY -> "C" // catch
    PowerUpType.SPLIT -> "Y" // forks into more balls
}

@Composable
fun GameScreen() {
    val context = LocalContext.current
    val engine = remember { GameEngine() }
    var tick by remember { mutableIntStateOf(0) } // bumped every frame to redraw; the engine itself isn't Compose state
    var targetX by remember { mutableFloatStateOf(GameEngine.WIDTH / 2f) }
    var dragging by remember { mutableStateOf(false) }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val prefs = remember { GamePrefs(context) }
    val sound = remember { SoundBoard() }
    val haptics = remember { Haptics(context) }
    var soundOn by remember { mutableStateOf(prefs.soundOn) }
    var hapticsOn by remember { mutableStateOf(prefs.hapticsOn) }
    var best by remember { mutableIntStateOf(prefs.highScore) }
    var paused by remember { mutableStateOf(false) }

    fun saveBest() {
        if (engine.score > best) {
            best = engine.score
            prefs.highScore = best
        }
    }

    DisposableEffect(engine) {
        engine.onEvent = { event ->
            if (soundOn) sound.play(event)
            if (hapticsOn) haptics.play(event)
            if (event == GameEvent.GAME_OVER || event == GameEvent.LEVEL_CLEAR) saveBest()
        }
        onDispose {
            engine.onEvent = null
            sound.release()
            haptics.release()
        }
    }

    // Leaving the app mid-ball pauses the game and keeps the score safe.
    val lifecycle = (context as ComponentActivity).lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                saveBest()
                if (engine.phase == Phase.PLAYING) paused = true
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        var last = 0L
        var acc = 0f
        while (true) {
            withFrameNanos { now ->
                if (paused) {
                    acc = 0f // don't bank paused time as catch-up
                } else if (last != 0L) {
                    // Clamp so returning from the background doesn't run a huge catch-up burst.
                    acc += ((now - last) / 1e9f).coerceAtMost(0.1f)
                    while (acc >= STEP) {
                        engine.update(STEP, targetX)
                        acc -= STEP
                    }
                }
                last = now
                tick++
            }
        }
    }

    tick // read here so the HUD text (score, lives, phase message) recomposes with the engine

    BoxWithConstraints(Modifier.fillMaxSize().background(Background)) {
        val layout = FieldLayout.compute(
            constraints.maxWidth.toFloat(),
            constraints.maxHeight.toFloat(),
            with(density) { MinTopHeight.toPx() },
            with(density) { MinBottomHeight.toPx() },
        )
        // FieldLayout is rebuilt every frame, so gesture code must not use it as a key (that restarts the
        // gesture each frame and cancels every touch); it reads the latest value through this instead.
        val currentLayout by rememberUpdatedState(layout)

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // The ring sits under the paddle and tracks the finger 1:1. Wherever the drag starts, the gap
                    // between finger and ring is kept, so the paddle never jumps; moving the finger a pixel moves
                    // the ring a pixel.
                    var grabOffset = 0f
                    detectDragGestures(
                        onDragStart = { pos ->
                            grabOffset = ringCenter(currentLayout, engine).x - pos.x
                            dragging = true
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    ) { change, _ ->
                        if (!paused) {
                            val half = engine.paddleW / 2f
                            val fieldX = (change.position.x + grabOffset - currentLayout.left) / currentLayout.scale
                            targetX = fieldX.coerceIn(half, GameEngine.WIDTH - half)
                        }
                        change.consume()
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures {
                        if (paused) return@detectTapGestures
                        when (engine.phase) {
                            Phase.READY -> engine.launch()
                            Phase.LEVEL_CLEAR, Phase.GAME_OVER -> engine.advance()
                            Phase.PLAYING -> engine.launch() // fires any ball held by the sticky paddle
                        }
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                tick // read so this draw block is invalidated each frame
                drawField(engine, layout, textMeasurer, density)
                drawBottomPanel(engine, layout, dragging)
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(with(density) { layout.top.toDp() })
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Left card: score and best.
                Column(
                    Modifier.weight(1f).fillMaxHeight().background(PanelColor, RoundedCornerShape(14.dp)).padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("SCORE", color = Color(0xFF8D99AE), fontSize = 11.sp)
                    Text("${engine.score}", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("BEST ${max(best, engine.score)}", color = Color(0xFFFFD166), fontSize = 13.sp)
                }
                // Right card: lives, level, pause, and counters for any active power-ups.
                Column(
                    Modifier.weight(1f).fillMaxHeight().background(PanelColor, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("♥ ${engine.lives}", color = Color(0xFFEF476F), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Lv ${engine.levelIndex + 1}", color = Color.White, fontSize = 16.sp)
                        Box(
                            Modifier.size(40.dp).clickable { paused = true },
                            contentAlignment = Alignment.Center,
                        ) { Text("II", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (engine.gunVolleys > 0) Chip("G ${engine.gunVolleys}", PowerUpType.GUNS.color())
                        if (engine.stickyCatches > 0) Chip("C ${engine.stickyCatches}", PowerUpType.STICKY.color())
                        if (engine.splitCharges > 0) Chip("Y ${engine.splitCharges}", PowerUpType.SPLIT.color())
                    }
                }
            }

            val message = when (engine.phase) {
                Phase.READY -> "Tap to launch"
                Phase.LEVEL_CLEAR -> "Level clear!\nTap to continue"
                Phase.GAME_OVER -> "Game over\nTap to restart"
                Phase.PLAYING -> if (engine.balls.any { it.stuck }) "Tap to shoot" else null
            }
            if (message != null) {
                Text(
                    message,
                    color = Color.White,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = with(density) { (layout.top + layout.fieldHeight * 0.55f).toDp() }),
                )
            }

            if (paused) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xE60B0F1A))
                        .pointerInput(Unit) { detectTapGestures { } }, // swallow taps so nothing leaks to the game
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("Paused", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { paused = false }) { Text("Resume", fontSize = 18.sp) }
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = { soundOn = !soundOn; prefs.soundOn = soundOn }) {
                        Text("Sound: ${if (soundOn) "On" else "Off"}")
                    }
                    OutlinedButton(onClick = { hapticsOn = !hapticsOn; prefs.hapticsOn = hapticsOn }) {
                        Text("Vibration: ${if (hapticsOn) "On" else "Off"}")
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Text(
        text,
        color = Color.Black,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.background(color, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Centre of the drag ring in screen pixels: under the paddle, in the middle of the bottom panel. */
private fun ringCenter(layout: FieldLayout, engine: GameEngine) = Offset(
    layout.left + engine.paddleX * layout.scale,
    layout.fieldBottom + layout.bottomHeight / 2f,
)

private fun DrawScope.drawBottomPanel(engine: GameEngine, layout: FieldLayout, dragging: Boolean) {
    val inset = 8.dp.toPx()
    drawRoundRect(
        PanelColor,
        Offset(inset, layout.fieldBottom + inset),
        Size(size.width - 2 * inset, layout.bottomHeight - 2 * inset),
        CornerRadius(14.dp.toPx()),
    )

    val ring = ringCenter(layout, engine)

    // Chevrons at both ends of the field's width show which way the ring slides.
    val guideInset = 34.dp.toPx()
    drawChevron(Offset(layout.left + guideInset, ring.y), pointLeft = true)
    drawChevron(Offset(layout.left + layout.fieldWidth - guideInset, ring.y), pointLeft = false)

    // The ring itself, with a stylised fingerprint inside.
    val r = RingRadius.toPx()
    val accent = if (dragging) StickyPaddleColor else PaddleColor
    drawCircle(Color(0xFF0B0F1A), r, ring)
    drawCircle(accent.copy(alpha = 0.85f), r, ring, style = Stroke(3.dp.toPx()))
    val print = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round)
    listOf(5f, 9.5f, 14f, 18.5f).forEachIndexed { i, dp ->
        val pr = dp.dp.toPx()
        drawArc(
            accent.copy(alpha = 0.9f),
            startAngle = 195f + i * 22f,
            sweepAngle = 255f - i * 12f,
            useCenter = false,
            topLeft = Offset(ring.x - pr, ring.y - pr),
            size = Size(pr * 2, pr * 2),
            style = print,
        )
    }
    drawCircle(accent, 1.8.dp.toPx(), ring)
}

private fun DrawScope.drawChevron(center: Offset, pointLeft: Boolean) {
    val h = 15.dp.toPx()
    val w = 9.dp.toPx() * if (pointLeft) 1f else -1f
    val path = Path().apply {
        moveTo(center.x + w, center.y - h)
        lineTo(center.x - w, center.y)
        lineTo(center.x + w, center.y + h)
    }
    drawPath(
        path,
        GuideColor.copy(alpha = 0.85f),
        style = Stroke(4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

private fun DrawScope.drawField(
    engine: GameEngine,
    layout: FieldLayout,
    textMeasurer: TextMeasurer,
    density: Density,
) {
    withTransform({
        translate(layout.left, layout.top)
        scale(layout.scale, layout.scale, pivot = Offset.Zero)
    }) {
        drawRect(FieldColor, Offset.Zero, Size(GameEngine.WIDTH, GameEngine.HEIGHT))
        drawRect(FieldBorder, Offset.Zero, Size(GameEngine.WIDTH, GameEngine.HEIGHT), style = Stroke(2f))

        for (brick in engine.bricks) if (brick.alive) drawBrick(brick)
        for (p in engine.powerUps) drawPowerUpBody(p)

        val paddleLeft = engine.paddleX - engine.paddleW / 2f
        val paddleTop = GameEngine.PADDLE_Y - GameEngine.PADDLE_H / 2f
        if (engine.gunVolleys > 0) {
            // Two barrels poking out of the top corners, matching where the shots leave.
            for (gx in listOf(paddleLeft + 6f, paddleLeft + engine.paddleW - 6f)) {
                drawRect(GunColor, Offset(gx - 2f, paddleTop - 6f), Size(4f, 8f))
            }
        }
        drawRoundRect(
            if (engine.stickyCatches > 0) StickyPaddleColor else PaddleColor,
            Offset(paddleLeft, paddleTop),
            Size(engine.paddleW, GameEngine.PADDLE_H),
            CornerRadius(GameEngine.PADDLE_H / 2f),
        )
        for (s in engine.bullets) {
            drawRect(
                BulletColor,
                Offset(s.x - GameEngine.BULLET_W / 2f, s.y - GameEngine.BULLET_H / 2f),
                Size(GameEngine.BULLET_W, GameEngine.BULLET_H),
            )
        }
        val ballColor = if (engine.splitCharges > 0) SplitBallColor else BallColor
        for (ball in engine.balls) drawCircle(ballColor, GameEngine.BALL_RADIUS, Offset(ball.x, ball.y))
    }

    // Letters are drawn outside the transform so their size stays crisp and isn't scaled twice.
    val style = TextStyle(
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = with(density) { (GameEngine.POWERUP_H * 0.8f * layout.scale).toSp() },
    )
    for (p in engine.powerUps) {
        val text = textMeasurer.measure(p.type.label(), style)
        drawText(
            text,
            topLeft = Offset(
                layout.left + p.x * layout.scale - text.size.width / 2f,
                layout.top + p.y * layout.scale - text.size.height / 2f,
            ),
        )
    }
}

private fun DrawScope.drawBrick(brick: Brick) {
    val color = if (brick.breakable) BrickColors.getValue(brick.hp.coerceIn(1, 3)) else SteelColor
    drawRoundRect(color, Offset(brick.x, brick.y), Size(brick.w, brick.h), CornerRadius(3f))
}

private fun DrawScope.drawPowerUpBody(p: PowerUp) {
    val topLeft = Offset(p.x - GameEngine.POWERUP_W / 2f, p.y - GameEngine.POWERUP_H / 2f)
    val size = Size(GameEngine.POWERUP_W, GameEngine.POWERUP_H)
    drawRoundRect(p.type.color(), topLeft, size, CornerRadius(GameEngine.POWERUP_H / 2f))
    drawRoundRect(Color.White, topLeft, size, CornerRadius(GameEngine.POWERUP_H / 2f), style = Stroke(1f))
}
