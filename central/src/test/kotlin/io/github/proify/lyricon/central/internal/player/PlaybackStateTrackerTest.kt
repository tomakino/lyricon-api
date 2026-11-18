/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.central.internal.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStateTrackerTest {

    private class FakeClock {
        var now: Long = 1_000_000L
        fun read(): Long = now
    }

    private fun tracker(clock: FakeClock, manual: () -> Long = { 5_000L }) =
        PlaybackStateTracker(clock = clock::read).apply {
            manualPositionReader = manual
        }

    // ---- 手动模式 ----

    @Test
    fun manualModeReadsManualPosition() {
        val tracker = tracker(FakeClock())

        assertEquals(5_000L, tracker.computePosition())
        assertFalse(tracker.isStateDriven)
    }

    @Test
    fun manualModeWithoutReaderReturnsZero() {
        val clock = FakeClock()
        val tracker = PlaybackStateTracker(clock = clock::read)

        assertEquals(0L, tracker.computePosition())
    }

    @Test
    fun manualModeClampsNegativePosition() {
        val tracker = tracker(FakeClock(), manual = { -100L })

        assertEquals(0L, tracker.computePosition())
    }

    // ---- 驱动模式 ----

    @Test
    fun drivingPositionAdvancesWithElapsedTime() {
        val clock = FakeClock()
        val tracker = tracker(clock)
        tracker.usePlaybackState(
            playing = true,
            position = 10_000L,
            lastUpdateTime = 1_000_000L,
            playbackSpeed = 1.0f
        )

        clock.now = 1_000_100L
        assertEquals(10_100L, tracker.computePosition())
        assertTrue(tracker.isStateDriven)
    }

    @Test
    fun pausedDrivingReturnsBasePosition() {
        val clock = FakeClock()
        val tracker = tracker(clock)
        tracker.usePlaybackState(
            playing = false,
            position = 20_000L,
            lastUpdateTime = 1_000_000L,
            playbackSpeed = 1.0f
        )

        clock.now = 1_000_500L
        assertEquals(20_000L, tracker.computePosition())
    }

    @Test
    fun speedDifferentialIsApplied() {
        val clock = FakeClock()
        val tracker = tracker(clock)
        tracker.usePlaybackState(
            playing = true,
            position = 0L,
            lastUpdateTime = 1_000_000L,
            playbackSpeed = 2.0f
        )

        clock.now = 1_000_050L
        assertEquals(100L, tracker.computePosition())
    }

    @Test
    fun zeroLastUpdateTimeFallsBackToBase() {
        val tracker = tracker(FakeClock())
        tracker.usePlaybackState(
            playing = true,
            position = 7_000L,
            lastUpdateTime = 0L,
            playbackSpeed = 1.0f
        )

        assertEquals(7_000L, tracker.computePosition())
    }

    @Test
    fun negativeElapsedTimeIsClamped() {
        val clock = FakeClock()
        val tracker = tracker(clock)
        tracker.usePlaybackState(
            playing = true,
            position = 10_000L,
            lastUpdateTime = 1_000_000L,
            playbackSpeed = 1.0f
        )

        clock.now = 1_000_000L - 5_000L
        assertEquals(10_000L, tracker.computePosition())
    }

    @Test
    fun drivingWithoutStateFallsBackToZero() {
        val tracker = tracker(FakeClock())
        tracker.usePlaybackState(
            playing = true,
            position = 0L,
            lastUpdateTime = 0L,
            playbackSpeed = 1.0f
        )

        assertEquals(0L, tracker.computePosition())
    }

    // ---- 模式切换 ----

    @Test
    fun manualSeekAdvancesWhilePlayingAndReleasesWhenReaderCatchesUp() {
        val clock = FakeClock()
        var reader = 5_000L
        val tracker = tracker(clock, manual = { reader })

        tracker.useManualMode(playing = true)
        tracker.seekTo(30_000L)

        assertEquals(30_000L, tracker.computePosition())
        clock.now += 50L
        assertEquals(30_050L, tracker.computePosition())

        reader = 30_100L
        assertEquals(30_100L, tracker.computePosition())
    }

    @Test
    fun manualSeekSmallDeltaIsNotReleasedImmediately() {
        val clock = FakeClock()
        var reader = 30_500L
        val tracker = tracker(clock, manual = { reader })
        tracker.useManualMode(playing = true)

        tracker.seekTo(30_000L)
        assertEquals(30_000L, tracker.computePosition())

        clock.now += 50L
        assertEquals(30_050L, tracker.computePosition())

        reader = 30_100L
        assertEquals(30_100L, tracker.computePosition())
    }

    @Test
    fun pausedSmallSeekIsNotReleasedImmediately() {
        val clock = FakeClock()
        var reader = 30_500L
        val tracker = tracker(clock, manual = { reader })
        tracker.useManualMode(playing = false)

        tracker.seekTo(30_000L)
        clock.now += 500L

        assertEquals(30_000L, tracker.computePosition())
    }

    @Test
    fun pausedSeekDoesNotExpire() {
        val clock = FakeClock()
        val tracker = tracker(clock, manual = { 30_500L })
        tracker.useManualMode(playing = false)

        tracker.seekTo(30_000L)
        clock.now += 5_000L

        assertEquals(30_000L, tracker.computePosition())
    }

    @Test
    fun lowWriteRateDoesNotReleaseSeekEarly() {
        val clock = FakeClock()
        var reader = 30_500L
        val tracker = tracker(clock, manual = { reader })
        tracker.useManualMode(playing = true)

        tracker.seekTo(30_000L)
        clock.now += 1_000L

        // reader 未更新时不能因 expected 追平旧值而释放
        assertEquals(31_000L, tracker.computePosition())

        reader = 31_000L
        assertEquals(31_000L, tracker.computePosition())
    }

    @Test
    fun pausedAfterSeekKeepsAdvancedPosition() {
        val clock = FakeClock()
        var reader = 5_000L
        val tracker = tracker(clock, manual = { reader })
        tracker.useManualMode(playing = true)
        tracker.seekTo(30_000L)

        clock.now += 5_000L
        tracker.useManualMode(playing = false)

        assertEquals(35_000L, tracker.computePosition())

        tracker.useManualMode(playing = true)
        clock.now += 1_000L
        assertEquals(36_000L, tracker.computePosition())
    }

    @Test
    fun pausedSeekFollowsProviderCorrection() {
        val clock = FakeClock()
        var reader = 30_500L
        val tracker = tracker(clock, manual = { reader })
        tracker.useManualMode(playing = false)
        tracker.seekTo(30_000L)

        reader = 29_800L
        assertEquals(29_800L, tracker.computePosition())
    }

    @Test
    fun manualSeekDoesNotAdvanceWhenPaused() {
        val clock = FakeClock()
        val tracker = tracker(clock, manual = { 5_000L })

        tracker.useManualMode(playing = false)
        tracker.seekTo(30_000L)
        clock.now += 500L

        assertEquals(30_000L, tracker.computePosition())
    }

    @Test
    fun manualSeekTimesOutBackToReader() {
        val clock = FakeClock()
        val tracker = tracker(clock, manual = { 5_000L })

        tracker.useManualMode(playing = true)
        tracker.seekTo(30_000L)
        clock.now += 2_001L

        assertEquals(5_000L, tracker.computePosition())
    }

    @Test
    fun drivingSeekUpdatesBasePosition() {
        val clock = FakeClock()
        val tracker = tracker(clock)
        tracker.usePlaybackState(
            playing = true,
            position = 10_000L,
            lastUpdateTime = clock.now,
            playbackSpeed = 1.0f
        )

        tracker.seekTo(50_000L)
        clock.now += 1_000L

        assertEquals(51_000L, tracker.computePosition())
    }

    @Test
    fun manualModeSwitchReturnsToReader() {
        val clock = FakeClock()
        val tracker = tracker(clock, manual = { 8_888L })
        tracker.usePlaybackState(
            playing = true,
            position = 1_000L,
            lastUpdateTime = 100L,
            playbackSpeed = 1.0f
        )

        tracker.useManualMode()

        assertEquals(8_888L, tracker.computePosition())
        assertFalse(tracker.isStateDriven)
    }

    @Test
    fun stopDrivingReportsPreviousMode() {
        val tracker = tracker(FakeClock())

        assertFalse(tracker.stopDriving())

        tracker.usePlaybackState(
            playing = true,
            position = 1_000L,
            lastUpdateTime = 100L,
            playbackSpeed = 1.0f
        )
        assertTrue(tracker.stopDriving())
        assertFalse(tracker.isStateDriven)
    }
}
