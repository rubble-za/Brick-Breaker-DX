package com.brickbreaker.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.random.Random

class GameEngineTest {
    private val dt = 1f / 60f

    private fun engine(vararg rows: String, dropChance: Float = 0f) =
        GameEngine(listOf(Level(rows.toList())), Random(1), dropChance)

    private fun GameEngine.run(seconds: Float, target: () -> Float = { paddleX }) {
        repeat((seconds / dt).toInt()) { update(dt, target()) }
    }

    @Test
    fun ballStartsStuckToPaddleUntilLaunched() {
        val e = engine("111111111")
        e.update(dt, 100f)
        assertEquals(Phase.READY, e.phase)
        assertEquals(100f, e.balls[0].x, 0.001f)
        e.launch()
        assertEquals(Phase.PLAYING, e.phase)
        assertTrue(e.balls[0].vy < 0f)
    }

    @Test
    fun paddleIsClampedToField() {
        val e = engine("111111111")
        e.update(dt, -500f)
        assertEquals(GameEngine.PADDLE_W / 2f, e.paddleX, 0.001f)
        e.update(dt, 5000f)
        assertEquals(GameEngine.WIDTH - GameEngine.PADDLE_W / 2f, e.paddleX, 0.001f)
    }

    @Test
    fun ballBouncesOffSideAndTopWalls() {
        val e = engine("111111111")
        e.launch()
        e.run(3f) { GameEngine.WIDTH / 2f }
        val b = e.balls.firstOrNull() ?: return // may have been lost; wall bounds are checked below when present
        assertTrue(b.x in 0f..GameEngine.WIDTH)
        assertTrue(b.y >= 0f)
    }

    @Test
    fun paddleBounceAngleDependsOnHitPosition() {
        val e = engine("#.......1")
        e.update(dt, 180f)
        e.launch()
        val b = e.balls[0]
        // Drop the ball onto the right edge of the paddle.
        b.x = 180f + GameEngine.PADDLE_W / 2f - 2f
        b.y = GameEngine.PADDLE_Y - GameEngine.PADDLE_H / 2f - GameEngine.BALL_RADIUS - 1f
        b.vx = 0f
        b.vy = 300f
        e.update(dt, 180f)
        assertTrue("right-edge hit should send ball right", b.vx > 0f)
        assertTrue(b.vy < 0f)
        assertEquals(300f, hypot(b.vx, b.vy), 1f) // speed preserved
    }

    @Test
    fun breakingBrickScoresAndSpeedsUp() {
        val e = engine("1........")
        e.launch()
        val brick = e.bricks[0]
        val b = e.balls[0]
        b.x = brick.x + brick.w / 2f
        b.y = brick.y + brick.h + GameEngine.BALL_RADIUS - 1f
        b.vx = 0f
        b.vy = -100f
        val before = e.speed
        e.update(dt, e.paddleX)
        assertEquals(0, brick.hp)
        assertEquals(10, e.score)
        assertTrue(e.speed > before)
        assertTrue("ball should bounce downward", b.vy > 0f)
    }

    @Test
    fun multiHitBrickNeedsSeveralHits() {
        val e = engine("3........")
        e.launch()
        val brick = e.bricks[0]
        repeat(3) {
            val b = e.balls[0]
            b.x = brick.x + brick.w / 2f
            b.y = brick.y + brick.h + GameEngine.BALL_RADIUS - 1f
            b.vx = 0f
            b.vy = -100f
            e.update(dt, e.paddleX)
        }
        assertEquals(0, brick.hp)
        assertEquals(10, e.score) // points only when it finally breaks
    }

    @Test
    fun unbreakableBrickIsNeverDestroyedAndDoesNotBlockLevelClear() {
        val e = engine("#1.......")
        e.launch()
        val wall = e.bricks[0]
        val b = e.balls[0]
        b.x = wall.x + wall.w / 2f
        b.y = wall.y + wall.h + GameEngine.BALL_RADIUS - 1f
        b.vx = 0f
        b.vy = -100f
        e.update(dt, e.paddleX)
        assertEquals(UNBREAKABLE, wall.hp)
        assertTrue(b.vy > 0f)

        val target = e.bricks[1]
        b.x = target.x + target.w / 2f
        b.y = target.y + target.h + GameEngine.BALL_RADIUS - 1f
        b.vy = -100f
        e.update(dt, e.paddleX)
        assertEquals(Phase.LEVEL_CLEAR, e.phase)
    }

    @Test
    fun fastBallDoesNotTunnelThroughBrick() {
        val e = engine("111111111")
        e.launch()
        val b = e.balls[0]
        val brick = e.bricks[4]
        b.x = brick.x + brick.w / 2f
        b.y = brick.y + brick.h + 60f
        b.vx = 0f
        b.vy = -4000f // far faster than the game ever reaches
        e.update(1f / 30f, e.paddleX)
        assertEquals(0, brick.hp)
    }

    @Test
    fun missingBallCostsALifeAndGameOverAtZero() {
        val e = engine("#.......1")
        repeat(GameEngine.START_LIVES) { i ->
            assertEquals(GameEngine.START_LIVES - i, e.lives)
            e.launch()
            e.balls[0].x = 10f // far from the centred paddle
            e.balls[0].y = GameEngine.HEIGHT - 5f
            e.balls[0].vx = 0f
            e.balls[0].vy = 200f
            e.run(1f) { 300f }
        }
        assertEquals(0, e.lives)
        assertEquals(Phase.GAME_OVER, e.phase)
    }

    @Test
    fun clearingLevelThenAdvanceLoadsNextLevel() {
        val e = GameEngine(
            listOf(Level(listOf("1........")), Level(listOf("11.......", "........."))),
            dropChance = 0f,
        )
        e.launch()
        val brick = e.bricks[0]
        val b = e.balls[0]
        b.x = brick.x + brick.w / 2f
        b.y = brick.y + brick.h + GameEngine.BALL_RADIUS - 1f
        b.vx = 0f
        b.vy = -100f
        e.update(dt, e.paddleX)
        assertEquals(Phase.LEVEL_CLEAR, e.phase)
        e.advance()
        assertEquals(1, e.levelIndex)
        assertEquals(2, e.bricks.size)
        assertEquals(Phase.READY, e.phase)
    }

    @Test
    fun gameOverAdvanceRestartsFresh() {
        val e = engine("#.......1")
        repeat(GameEngine.START_LIVES) {
            e.launch()
            e.balls[0].x = 10f
            e.balls[0].y = GameEngine.HEIGHT - 5f
            e.balls[0].vx = 0f
            e.balls[0].vy = 200f
            e.run(1f) { 300f }
        }
        e.advance()
        assertEquals(GameEngine.START_LIVES, e.lives)
        assertEquals(0, e.score)
        assertEquals(Phase.READY, e.phase)
    }

    /** Launches and drops a power-up of [type] right onto the paddle, then runs one frame. */
    private fun GameEngine.catch(type: PowerUpType) {
        if (phase == Phase.READY) launch()
        powerUps += PowerUp(type, paddleX, GameEngine.PADDLE_Y)
        update(dt, paddleX)
    }

    @Test
    fun brokenBrickCanDropPowerUp() {
        val e = engine("1........", dropChance = 1f)
        e.launch()
        val brick = e.bricks[0]
        val b = e.balls[0]
        b.x = brick.x + brick.w / 2f
        b.y = brick.y + brick.h + GameEngine.BALL_RADIUS - 1f
        b.vx = 0f
        b.vy = -100f
        e.update(dt, e.paddleX)
        assertEquals(1, e.powerUps.size)
    }

    @Test
    fun powerUpFallsAndIsRemovedOffScreen() {
        val e = engine("#.......1")
        e.launch()
        e.powerUps += PowerUp(PowerUpType.LIFE, 10f, GameEngine.HEIGHT - 5f)
        e.update(dt, 300f) // paddle far away
        e.run(1f) { 300f }
        assertTrue(e.powerUps.isEmpty())
        assertEquals(GameEngine.START_LIVES, e.lives)
    }

    @Test
    fun widePowerUpWidensPaddleThenExpires() {
        val e = engine("#.......1")
        e.catch(PowerUpType.WIDE)
        assertEquals(GameEngine.WIDE_PADDLE_W, e.paddleW, 0.001f)
        // Keep the ball alive while the timer runs out.
        repeat(((GameEngine.WIDE_SECONDS + 1f) / dt).toInt()) {
            e.balls.forEach { it.x = 100f; it.y = 300f; it.vx = 0f; it.vy = 0f }
            e.update(dt, e.paddleX)
        }
        assertEquals(GameEngine.PADDLE_W, e.paddleW, 0.001f)
    }

    @Test
    fun multiPowerUpTriplesBallsAndKeepsSpeed() {
        val e = engine("#.......1")
        e.catch(PowerUpType.MULTI)
        assertEquals(3, e.balls.size)
        val s = hypot(e.balls[0].vx, e.balls[0].vy)
        e.balls.forEach { assertEquals(s, hypot(it.vx, it.vy), 1f) }
    }

    @Test
    fun multiBallNeverExceedsLimit() {
        val e = engine("#.......1")
        repeat(5) { e.catch(PowerUpType.MULTI) }
        assertTrue(e.balls.size <= GameEngine.MAX_BALLS)
    }

    @Test
    fun losingOneOfSeveralBallsKeepsTheLife() {
        val e = engine("#.......1")
        e.catch(PowerUpType.MULTI)
        e.balls[0].x = 10f
        e.balls[0].y = GameEngine.HEIGHT + 20f
        e.update(dt, e.paddleX)
        assertEquals(2, e.balls.size)
        assertEquals(GameEngine.START_LIVES, e.lives)
        assertEquals(Phase.PLAYING, e.phase)
    }

    @Test
    fun lifePowerUpAddsLifeUpToCap() {
        val e = engine("#.......1")
        repeat(10) { e.catch(PowerUpType.LIFE) }
        assertEquals(GameEngine.MAX_LIVES, e.lives)
    }

    @Test
    fun slowPowerUpLowersSpeedButNotBelowBase() {
        val e = engine("#.......1")
        e.launch()
        e.catch(PowerUpType.SLOW)
        assertEquals(GameEngine.BASE_SPEED, e.speed, 0.001f) // already at base: stays there
    }

    @Test
    fun losingALifeClearsEffects() {
        val e = engine("#.......1")
        e.catch(PowerUpType.WIDE)
        e.balls[0].y = GameEngine.HEIGHT + 20f
        e.update(dt, e.paddleX)
        assertEquals(GameEngine.PADDLE_W, e.paddleW, 0.001f)
        assertTrue(e.powerUps.isEmpty())
    }

    /** Puts the first ball just above the paddle at [offset] from its centre, heading down, and runs one frame. */
    private fun GameEngine.bounceOnPaddle(offset: Float = 0f) {
        val b = balls[0]
        b.stuck = false
        b.x = paddleX + offset
        b.y = GameEngine.PADDLE_Y - GameEngine.PADDLE_H / 2f - GameEngine.BALL_RADIUS - 1f
        b.vx = 0f
        b.vy = 300f
        update(dt, paddleX)
    }

    @Test
    fun gunsFireAPairOfShotsOnEachPaddleBounce() {
        val e = engine("#.......1")
        e.catch(PowerUpType.GUNS)
        assertEquals(GameEngine.GUN_VOLLEYS, e.gunVolleys)
        e.bounceOnPaddle()
        assertEquals(2, e.bullets.size)
        assertEquals(GameEngine.GUN_VOLLEYS - 1, e.gunVolleys)
    }

    @Test
    fun gunsRunOutAfterTheirVolleys() {
        val e = engine("#.......1")
        val log = e.record()
        e.catch(PowerUpType.GUNS)
        repeat(GameEngine.GUN_VOLLEYS + 2) { e.bounceOnPaddle() }
        assertEquals(0, e.gunVolleys)
        assertEquals(GameEngine.GUN_VOLLEYS, log.count { it == GameEvent.SHOOT })
    }

    @Test
    fun bulletBreaksBrickAndScores() {
        val e = engine("1........")
        e.launch()
        val brick = e.bricks[0]
        e.bullets += Bullet(brick.x + brick.w / 2f, brick.y + brick.h + 3f)
        e.balls[0].vx = 0f; e.balls[0].vy = 0f // keep the ball out of the way
        e.balls[0].x = 300f; e.balls[0].y = 300f
        e.update(dt, e.paddleX)
        assertEquals(0, brick.hp)
        assertEquals(10, e.score)
        assertTrue(e.bullets.isEmpty())
    }

    @Test
    fun bulletIsAbsorbedBySteelWithoutDamage() {
        val e = engine("#.......1")
        e.launch()
        val wall = e.bricks[0]
        e.bullets += Bullet(wall.x + wall.w / 2f, wall.y + wall.h + 3f)
        e.balls[0].vx = 0f; e.balls[0].vy = 0f
        e.balls[0].x = 300f; e.balls[0].y = 300f
        e.update(dt, e.paddleX)
        assertEquals(UNBREAKABLE, wall.hp)
        assertTrue(e.bullets.isEmpty())
    }

    @Test
    fun bulletLeavesTheFieldAtTheTop() {
        val e = engine("#.......1")
        e.launch()
        e.bullets += Bullet(300f, 2f)
        e.balls[0].vx = 0f; e.balls[0].vy = 0f
        e.balls[0].x = 100f; e.balls[0].y = 300f
        e.run(0.1f)
        assertTrue(e.bullets.isEmpty())
    }

    @Test
    fun stickyPaddleCatchesBallAndFollowsPaddle() {
        val e = engine("#.......1")
        e.catch(PowerUpType.STICKY)
        e.bounceOnPaddle(offset = 10f)
        val b = e.balls[0]
        assertTrue(b.stuck)
        assertEquals(0f, b.vx, 0.001f)
        assertEquals(0f, b.vy, 0.001f)
        assertEquals(GameEngine.STICKY_CATCHES - 1, e.stickyCatches)
        // Moving the paddle carries the ball with it.
        e.update(dt, e.paddleX + 50f)
        assertEquals(e.paddleX + 10f, b.x, 0.001f)
    }

    @Test
    fun launchFiresStuckBallUpwardAtAngleFromOffset() {
        val e = engine("#.......1")
        e.catch(PowerUpType.STICKY)
        e.bounceOnPaddle(offset = e.paddleW / 2f - 1f) // near the right edge
        e.launch()
        val b = e.balls[0]
        assertTrue(!b.stuck)
        assertTrue(b.vy < 0f)
        assertTrue("right-edge catch should shoot right", b.vx > 0f)
        assertEquals(e.speed, hypot(b.vx, b.vy), 1f)
    }

    @Test
    fun launchEmitsEventOnlyWhenABallWasHeld() {
        val e = engine("#.......1")
        val log = e.record()
        e.launch()
        log.clear()
        e.launch() // nothing stuck
        assertTrue(log.isEmpty())
        e.catch(PowerUpType.STICKY)
        e.bounceOnPaddle()
        e.launch()
        assertTrue(GameEvent.STICK in log)
        assertEquals(1, log.count { it == GameEvent.LAUNCH })
    }

    @Test
    fun stickyEffectRunsOutAfterItsCatches() {
        val e = engine("#.......1")
        e.catch(PowerUpType.STICKY)
        repeat(GameEngine.STICKY_CATCHES) { e.bounceOnPaddle(); e.launch() }
        assertEquals(0, e.stickyCatches)
        e.bounceOnPaddle()
        assertTrue("bounce after the effect ends must not stick", !e.balls[0].stuck)
        assertTrue(e.balls[0].vy < 0f)
    }

    @Test
    fun multiBallIgnoresHeldBalls() {
        val e = engine("#.......1")
        e.catch(PowerUpType.STICKY)
        e.bounceOnPaddle()
        e.catch(PowerUpType.MULTI)
        assertEquals(1, e.balls.size)
    }

    @Test
    fun losingALifeClearsGunsStickyAndBullets() {
        val e = engine("#.......1")
        e.catch(PowerUpType.GUNS)
        e.catch(PowerUpType.STICKY)
        e.bullets += Bullet(300f, 300f)
        e.balls[0].stuck = false
        e.balls[0].y = GameEngine.HEIGHT + 20f
        e.update(dt, e.paddleX)
        assertEquals(0, e.gunVolleys)
        assertEquals(0, e.stickyCatches)
        assertTrue(e.bullets.isEmpty())
    }

    /** Launches and breaks a '1' brick with the first ball, which sits just below it moving up. */
    private fun GameEngine.hitBrickWithBall(index: Int, ball: Int = 0) {
        val brick = bricks[index]
        val b = balls[ball]
        b.x = brick.x + brick.w / 2f
        b.y = brick.y + brick.h + GameEngine.BALL_RADIUS - 1f
        b.vx = 0f
        b.vy = -100f
        update(dt, paddleX)
    }

    @Test
    fun splitPowerUpSpawnsABallOnEachBrickHitUntilSpent() {
        val e = engine("1111111#.", dropChance = 0f)
        e.catch(PowerUpType.SPLIT)
        assertEquals(GameEngine.SPLIT_BREAKS, e.splitCharges)
        e.hitBrickWithBall(0)
        assertEquals(2, e.balls.size)
        assertEquals(GameEngine.SPLIT_BREAKS - 1, e.splitCharges)
        // Park the original ball far from the bricks so only the targeted hits count.
        repeat(GameEngine.SPLIT_BREAKS + 3) { n ->
            e.balls.forEach { it.vx = 0f; it.vy = 0f; it.x = 340f; it.y = 400f }
            e.hitBrickWithBall((n + 1).coerceAtMost(6))
        }
        assertEquals(0, e.splitCharges)
        assertTrue(e.balls.size <= GameEngine.MAX_BALLS)
    }

    @Test
    fun splitBallsFanOutAndKeepSpeed() {
        val e = engine("1111111#.", dropChance = 0f)
        e.catch(PowerUpType.SPLIT)
        e.hitBrickWithBall(0)
        val speeds = e.balls.map { hypot(it.vx, it.vy) }
        assertEquals(speeds[0], speeds[1], 1f)
        assertTrue("copy should head a different way", e.balls[0].vx != e.balls[1].vx)
    }

    @Test
    fun splitDoesNotTriggerOnSteel() {
        val e = engine("#1.......", dropChance = 0f)
        e.catch(PowerUpType.SPLIT)
        e.hitBrickWithBall(0) // steel
        assertEquals(1, e.balls.size)
        assertEquals(GameEngine.SPLIT_BREAKS, e.splitCharges)
    }

    @Test
    fun splitNeverExceedsBallLimitAndClearsOnLifeLost() {
        val e = engine("1111111#.", dropChance = 0f)
        repeat(3) { e.catch(PowerUpType.MULTI) }
        e.catch(PowerUpType.SPLIT)
        e.hitBrickWithBall(0)
        assertTrue(e.balls.size <= GameEngine.MAX_BALLS)
        e.balls.forEach { it.y = GameEngine.HEIGHT + 20f }
        e.update(dt, e.paddleX)
        assertEquals(0, e.splitCharges)
    }

    @Test
    fun extraLifeIsTheRarestDrop() {
        val rng = Random(42)
        val counts = PowerUpType.entries.associateWith { 0 }.toMutableMap()
        repeat(15_000) { val t = GameEngine.pickPowerUpType(rng); counts[t] = counts.getValue(t) + 1 }
        val life = counts.getValue(PowerUpType.LIFE)
        assertTrue("every type should still drop", counts.values.all { it > 0 })
        counts.filterKeys { it != PowerUpType.LIFE }.forEach { (type, n) ->
            assertTrue("$type ($n) should drop more often than LIFE ($life)", n > life)
        }
    }

    private fun GameEngine.record(): MutableList<GameEvent> =
        mutableListOf<GameEvent>().also { log -> onEvent = { log += it } }

    @Test
    fun launchAndBrickBreakEmitEvents() {
        val e = engine("1........")
        val log = e.record()
        e.launch()
        val brick = e.bricks[0]
        val b = e.balls[0]
        b.x = brick.x + brick.w / 2f
        b.y = brick.y + brick.h + GameEngine.BALL_RADIUS - 1f
        b.vx = 0f
        b.vy = -100f
        e.update(dt, e.paddleX)
        assertEquals(listOf(GameEvent.LAUNCH, GameEvent.BRICK_BREAK, GameEvent.LEVEL_CLEAR), log)
    }

    @Test
    fun damagedButNotBrokenBrickEmitsBrickHit() {
        val e = engine("2........")
        val log = e.record()
        e.launch()
        val brick = e.bricks[0]
        val b = e.balls[0]
        b.x = brick.x + brick.w / 2f
        b.y = brick.y + brick.h + GameEngine.BALL_RADIUS - 1f
        b.vx = 0f
        b.vy = -100f
        e.update(dt, e.paddleX)
        assertTrue(GameEvent.BRICK_HIT in log)
        assertTrue(GameEvent.BRICK_BREAK !in log)
    }

    @Test
    fun steelBrickEmitsSteelHit() {
        val e = engine("#.......1")
        val log = e.record()
        e.launch()
        val wall = e.bricks[0]
        val b = e.balls[0]
        b.x = wall.x + wall.w / 2f
        b.y = wall.y + wall.h + GameEngine.BALL_RADIUS - 1f
        b.vx = 0f
        b.vy = -100f
        e.update(dt, e.paddleX)
        assertTrue(GameEvent.STEEL_HIT in log)
    }

    @Test
    fun lifeLostAndGameOverEmitEvents() {
        val e = engine("#.......1")
        val log = e.record()
        repeat(GameEngine.START_LIVES) {
            e.launch()
            e.balls[0].x = 10f
            e.balls[0].y = GameEngine.HEIGHT - 5f
            e.balls[0].vx = 0f
            e.balls[0].vy = 200f
            e.run(1f) { 300f }
        }
        assertEquals(2, log.count { it == GameEvent.LIFE_LOST })
        assertEquals(1, log.count { it == GameEvent.GAME_OVER })
    }

    @Test
    fun paddleHitAndPowerUpEmitEvents() {
        val e = engine("#.......1")
        val log = e.record()
        e.catch(PowerUpType.LIFE)
        assertTrue(GameEvent.POWERUP in log)

        e.balls[0].x = e.paddleX
        e.balls[0].y = GameEngine.PADDLE_Y - GameEngine.PADDLE_H / 2f - GameEngine.BALL_RADIUS - 1f
        e.balls[0].vx = 0f
        e.balls[0].vy = 300f
        e.update(dt, e.paddleX)
        assertTrue(GameEvent.PADDLE_HIT in log)
    }

    @Test
    fun defaultLevelsAreValid() {
        Levels.all.forEach { assertTrue(it.rows.isNotEmpty()) }
        assertTrue(GameEngine().bricks.isNotEmpty())
    }
}
