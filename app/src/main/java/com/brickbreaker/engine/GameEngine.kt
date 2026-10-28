package com.brickbreaker.engine

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

enum class Phase { READY, PLAYING, LEVEL_CLEAR, GAME_OVER }

enum class PowerUpType { WIDE, MULTI, SLOW, LIFE, GUNS, STICKY, SPLIT }

/** Things that happen in the simulation, for the UI to turn into sound and vibration. */
enum class GameEvent {
    LAUNCH, WALL_HIT, PADDLE_HIT, BRICK_HIT, BRICK_BREAK, STEEL_HIT, POWERUP, LIFE_LOST, LEVEL_CLEAR, GAME_OVER,
    SHOOT, STICK, SPLIT
}

/** hp of an indestructible brick. */
const val UNBREAKABLE = -1

class Brick(val x: Float, val y: Float, val w: Float, val h: Float, var hp: Int) {
    val breakable get() = hp != UNBREAKABLE
    val alive get() = hp != 0
}

class Ball(var x: Float, var y: Float, var vx: Float, var vy: Float) {
    /** Held on the paddle by the sticky power-up until launched; [offset] is its x distance from the paddle centre. */
    var stuck = false
    var offset = 0f
}

/** A shot fired from the paddle's guns; x, y is its centre. */
class Bullet(var x: Float, var y: Float)

/** A falling pickup; x, y is its centre. */
class PowerUp(val type: PowerUpType, var x: Float, var y: Float)

/**
 * Pure game simulation with no Android dependencies. Coordinates are logical units,
 * origin top-left, y down, in a [WIDTH] x [HEIGHT] play field the renderer scales to the screen.
 */
class GameEngine(
    private val levels: List<Level> = Levels.all,
    private val random: Random = Random.Default,
    private val dropChance: Float = 0.15f,
) {
    companion object {
        const val WIDTH = 360f
        const val HEIGHT = 640f
        const val BALL_RADIUS = 6f
        const val PADDLE_W = 64f
        const val PADDLE_H = 12f
        const val PADDLE_Y = HEIGHT - 60f
        const val START_LIVES = 3
        const val MAX_LIVES = 5
        const val BASE_SPEED = 340f
        const val MAX_SPEED = 560f
        const val SPEED_GAIN_PER_BRICK = 1.01f
        const val MAX_BOUNCE_ANGLE = Math.PI.toFloat() / 3f // 60 degrees from vertical

        const val POWERUP_W = 24f
        const val POWERUP_H = 14f
        const val POWERUP_FALL_SPEED = 120f
        const val WIDE_PADDLE_W = 100f
        const val WIDE_SECONDS = 12f
        const val MAX_BALLS = 6
        const val SLOW_FACTOR = 0.75f
        const val GUN_VOLLEYS = 6 // paddle bounces that fire a pair of shots
        const val STICKY_CATCHES = 5 // paddle bounces that catch the ball instead
        const val BULLET_SPEED = 520f
        const val BULLET_W = 3f
        const val BULLET_H = 10f
        const val SPLIT_BREAKS = 5 // brick hits that each spawn an extra ball
        private const val SPLIT_ANGLE = 0.4f // radians between multi-ball copies

        /** Relative drop odds; the extra life is deliberately the rarest. */
        val DROP_WEIGHTS = mapOf(
            PowerUpType.WIDE to 3,
            PowerUpType.MULTI to 3,
            PowerUpType.SLOW to 2,
            PowerUpType.GUNS to 3,
            PowerUpType.STICKY to 3,
            PowerUpType.SPLIT to 3,
            PowerUpType.LIFE to 1,
        )

        fun pickPowerUpType(random: Random): PowerUpType {
            var roll = random.nextInt(DROP_WEIGHTS.values.sum())
            for ((type, weight) in DROP_WEIGHTS) {
                if (roll < weight) return type
                roll -= weight
            }
            error("unreachable: roll is always below the total weight")
        }

        private const val BRICK_TOP = 70f
        private const val BRICK_GAP = 3f
        private const val BRICK_H = 18f
        private const val SIDE_MARGIN = 12f
    }

    var phase = Phase.READY; private set
    var score = 0; private set
    var lives = START_LIVES; private set
    var levelIndex = 0; private set
    var paddleX = WIDTH / 2f; private set // centre of the paddle
    var paddleW = PADDLE_W; private set
    var speed = BASE_SPEED; private set

    val balls = mutableListOf<Ball>()
    val bricks = mutableListOf<Brick>()
    val powerUps = mutableListOf<PowerUp>()
    val bullets = mutableListOf<Bullet>()

    var gunVolleys = 0; private set
    var stickyCatches = 0; private set
    var splitCharges = 0; private set

    // Balls born mid-step; added once the ball loop finishes so the list isn't changed while iterating.
    private val pendingBalls = mutableListOf<Ball>()

    /** Called synchronously from [update] and [launch] whenever something noteworthy happens. */
    var onEvent: ((GameEvent) -> Unit)? = null

    private var wideTimer = 0f

    init { loadLevel(0) }

    private fun emit(event: GameEvent) { onEvent?.invoke(event) }

    private fun loadLevel(index: Int) {
        levelIndex = index
        bricks.clear()
        val level = levels[index % levels.size]
        val brickW = (WIDTH - 2 * SIDE_MARGIN - (Levels.COLS - 1) * BRICK_GAP) / Levels.COLS
        level.rows.forEachIndexed { row, line ->
            line.forEachIndexed cell@{ col, ch ->
                val hp = when (ch) {
                    '.' -> return@cell
                    '#' -> UNBREAKABLE
                    else -> ch.digitToInt()
                }
                bricks += Brick(
                    SIDE_MARGIN + col * (brickW + BRICK_GAP),
                    BRICK_TOP + row * (BRICK_H + BRICK_GAP),
                    brickW, BRICK_H, hp,
                )
            }
        }
        speed = BASE_SPEED
        resetBall()
    }

    /** Puts a fresh ball on the paddle and drops any active pickups and effects. */
    private fun resetBall() {
        balls.clear()
        powerUps.clear()
        bullets.clear()
        paddleW = PADDLE_W
        wideTimer = 0f
        gunVolleys = 0
        stickyCatches = 0
        splitCharges = 0
        pendingBalls.clear()
        balls += Ball(paddleX, PADDLE_Y - PADDLE_H / 2f - BALL_RADIUS, 0f, 0f)
        phase = Phase.READY
    }

    /** Starts the ball moving from the paddle, or fires any balls held by the sticky power-up. */
    fun launch() {
        when (phase) {
            Phase.READY -> {
                val angle = Math.toRadians(15.0).toFloat() // slightly right of vertical so it isn't a dead straight shot
                balls[0].vx = speed * sin(angle)
                balls[0].vy = -speed * cos(angle)
                phase = Phase.PLAYING
                emit(GameEvent.LAUNCH)
            }
            Phase.PLAYING -> releaseStuckBalls()
            else -> Unit
        }
    }

    /** Fires held balls at the same angle a normal bounce would give from where they sit on the paddle. */
    private fun releaseStuckBalls() {
        var released = false
        for (b in balls) {
            if (!b.stuck) continue
            val angle = (b.offset / (paddleW / 2f)).coerceIn(-1f, 1f) * MAX_BOUNCE_ANGLE
            b.vx = speed * sin(angle)
            b.vy = -speed * cos(angle)
            b.stuck = false
            released = true
        }
        if (released) emit(GameEvent.LAUNCH)
    }

    /** Moves on after a cleared level, or restarts after game over. */
    fun advance() {
        when (phase) {
            Phase.LEVEL_CLEAR -> loadLevel(levelIndex + 1)
            Phase.GAME_OVER -> { score = 0; lives = START_LIVES; loadLevel(0) }
            else -> Unit
        }
    }

    fun update(dt: Float, paddleTargetX: Float) {
        val half = paddleW / 2f
        paddleX = paddleTargetX.coerceIn(half, WIDTH - half)
        if (phase == Phase.READY) {
            balls[0].x = paddleX
            return
        }
        if (phase != Phase.PLAYING) return

        if (wideTimer > 0f) {
            wideTimer -= dt
            if (wideTimer <= 0f) paddleW = PADDLE_W
        }

        // Substep so a fast ball never moves more than half its radius at a time (no tunnelling).
        val steps = max(1, Math.ceil((speed * dt / (BALL_RADIUS / 2f)).toDouble()).toInt())
        val h = dt / steps
        repeat(steps) {
            if (phase == Phase.PLAYING) step(h)
        }
    }

    private fun step(dt: Float) {
        val it = balls.iterator()
        while (it.hasNext()) {
            val b = it.next()
            if (b.stuck) {
                // Ride along with the paddle, staying on it if it has shrunk since the catch.
                b.offset = b.offset.coerceIn(-paddleW / 2f, paddleW / 2f)
                b.x = paddleX + b.offset
                b.y = PADDLE_Y - PADDLE_H / 2f - BALL_RADIUS
                continue
            }
            b.x += b.vx * dt
            b.y += b.vy * dt
            collideWalls(b)
            collidePaddle(b)
            collideBricks(b)
            if (b.y - BALL_RADIUS > HEIGHT) it.remove()
        }
        balls += pendingBalls
        pendingBalls.clear()
        updateBullets(dt)
        updatePowerUps(dt)
        if (balls.isEmpty()) {
            lives--
            if (lives <= 0) {
                phase = Phase.GAME_OVER
                emit(GameEvent.GAME_OVER)
            } else {
                resetBall()
                emit(GameEvent.LIFE_LOST)
            }
        } else if (bricks.none { it.breakable && it.alive }) {
            phase = Phase.LEVEL_CLEAR
            emit(GameEvent.LEVEL_CLEAR)
        }
    }

    private fun collideWalls(b: Ball) {
        if (b.x - BALL_RADIUS < 0f) { b.x = BALL_RADIUS; b.vx = abs(b.vx); emit(GameEvent.WALL_HIT) }
        if (b.x + BALL_RADIUS > WIDTH) { b.x = WIDTH - BALL_RADIUS; b.vx = -abs(b.vx); emit(GameEvent.WALL_HIT) }
        if (b.y - BALL_RADIUS < 0f) { b.y = BALL_RADIUS; b.vy = abs(b.vy); emit(GameEvent.WALL_HIT) }
    }

    private fun collidePaddle(b: Ball) {
        if (b.vy <= 0f) return
        val left = paddleX - paddleW / 2f
        val top = PADDLE_Y - PADDLE_H / 2f
        val inX = b.x + BALL_RADIUS >= left && b.x - BALL_RADIUS <= left + paddleW
        val inY = b.y + BALL_RADIUS >= top && b.y - BALL_RADIUS <= top + PADDLE_H
        if (!(inX && inY)) return
        // Bounce angle depends on where the ball lands: centre goes straight up, edges go 60 degrees out.
        val offset = ((b.x - paddleX) / (paddleW / 2f)).coerceIn(-1f, 1f)
        val angle = offset * MAX_BOUNCE_ANGLE
        val s = hypot(b.vx, b.vy)
        b.vx = s * sin(angle)
        b.vy = -s * cos(angle)
        b.y = top - BALL_RADIUS
        emit(GameEvent.PADDLE_HIT)
        if (stickyCatches > 0) {
            stickyCatches--
            b.stuck = true
            b.offset = (b.x - paddleX).coerceIn(-paddleW / 2f, paddleW / 2f)
            b.vx = 0f
            b.vy = 0f
            emit(GameEvent.STICK)
        }
        if (gunVolleys > 0) fireGuns()
    }

    private fun fireGuns() {
        gunVolleys--
        val y = PADDLE_Y - PADDLE_H / 2f
        bullets += Bullet(paddleX - paddleW / 2f + 6f, y)
        bullets += Bullet(paddleX + paddleW / 2f - 6f, y)
        emit(GameEvent.SHOOT)
    }

    private fun updateBullets(dt: Float) {
        val it = bullets.iterator()
        shots@ while (it.hasNext()) {
            val s = it.next()
            s.y -= BULLET_SPEED * dt
            if (s.y + BULLET_H / 2f < 0f) { it.remove(); continue }
            for (brick in bricks) {
                if (!brick.alive) continue
                val overlapX = abs(s.x - (brick.x + brick.w / 2f)) <= (BULLET_W + brick.w) / 2f
                val overlapY = abs(s.y - (brick.y + brick.h / 2f)) <= (BULLET_H + brick.h) / 2f
                if (overlapX && overlapY) {
                    hit(brick) // steel soaks the shot up; breakable bricks take a hit like from the ball
                    it.remove()
                    continue@shots
                }
            }
        }
    }

    private fun collideBricks(b: Ball) {
        for (brick in bricks) {
            if (!brick.alive) continue
            val cx = b.x.coerceIn(brick.x, brick.x + brick.w)
            val cy = b.y.coerceIn(brick.y, brick.y + brick.h)
            val dx = b.x - cx
            val dy = b.y - cy
            val d2 = dx * dx + dy * dy
            if (d2 >= BALL_RADIUS * BALL_RADIUS) continue

            if (d2 == 0f) {
                // Centre is inside the brick: bounce back the way it came.
                b.vy = -b.vy
            } else {
                val dist = hypot(dx, dy)
                // Reflect on the dominant axis of the contact normal, forcing the sign so it can't re-enter.
                if (abs(dx) > abs(dy)) b.vx = abs(b.vx) * if (dx > 0f) 1f else -1f
                else b.vy = abs(b.vy) * if (dy > 0f) 1f else -1f
                val push = BALL_RADIUS - dist
                b.x += dx / dist * push
                b.y += dy / dist * push
            }
            hit(brick)
            if (brick.breakable) trySplit(b)
            return // one brick per substep keeps corner hits predictable
        }
    }

    /** With the split power-up active, a ball striking a breakable brick spawns an extra ball fanned to one side. */
    private fun trySplit(b: Ball) {
        if (splitCharges <= 0 || b.stuck || balls.size + pendingBalls.size >= MAX_BALLS) return
        splitCharges--
        pendingBalls += rotated(b, if (splitCharges % 2 == 0) SPLIT_ANGLE else -SPLIT_ANGLE)
        emit(GameEvent.SPLIT)
    }

    private fun hit(brick: Brick) {
        if (!brick.breakable) { emit(GameEvent.STEEL_HIT); return }
        brick.hp--
        if (brick.hp > 0) { emit(GameEvent.BRICK_HIT); return }
        emit(GameEvent.BRICK_BREAK)
        score += 10
        setSpeed(min(MAX_SPEED, speed * SPEED_GAIN_PER_BRICK))
        if (random.nextFloat() < dropChance) {
            powerUps += PowerUp(
                pickPowerUpType(random),
                brick.x + brick.w / 2f,
                brick.y + brick.h / 2f,
            )
        }
    }

    /** Changes the base speed and rescales every ball's velocity to match. */
    private fun setSpeed(newSpeed: Float) {
        val scale = newSpeed / speed
        for (b in balls) { b.vx *= scale; b.vy *= scale }
        speed = newSpeed
    }

    private fun updatePowerUps(dt: Float) {
        val it = powerUps.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.y += POWERUP_FALL_SPEED * dt
            val caught = abs(p.x - paddleX) <= (paddleW + POWERUP_W) / 2f &&
                abs(p.y - PADDLE_Y) <= (PADDLE_H + POWERUP_H) / 2f
            if (caught) {
                apply(p.type)
                it.remove()
                emit(GameEvent.POWERUP)
            } else if (p.y - POWERUP_H / 2f > HEIGHT) {
                it.remove()
            }
        }
    }

    private fun apply(type: PowerUpType) {
        when (type) {
            PowerUpType.WIDE -> { paddleW = WIDE_PADDLE_W; wideTimer = WIDE_SECONDS }
            PowerUpType.MULTI -> splitBalls()
            PowerUpType.SLOW -> setSpeed(max(BASE_SPEED, speed * SLOW_FACTOR))
            PowerUpType.LIFE -> lives = min(MAX_LIVES, lives + 1)
            PowerUpType.GUNS -> gunVolleys = GUN_VOLLEYS
            PowerUpType.STICKY -> stickyCatches = STICKY_CATCHES
            PowerUpType.SPLIT -> splitCharges = SPLIT_BREAKS
        }
    }

    /** Replaces each ball with itself plus two copies fanned either side, up to [MAX_BALLS]. */
    private fun splitBalls() {
        val extra = mutableListOf<Ball>()
        for (b in balls) {
            if (b.stuck) continue // a held ball has no velocity to fan out
            if (balls.size + extra.size + 2 > MAX_BALLS) break
            extra += rotated(b, SPLIT_ANGLE)
            extra += rotated(b, -SPLIT_ANGLE)
        }
        balls += extra
    }

    private fun rotated(b: Ball, angle: Float): Ball {
        val c = cos(angle)
        val s = sin(angle)
        return Ball(b.x, b.y, b.vx * c - b.vy * s, b.vx * s + b.vy * c)
    }
}
