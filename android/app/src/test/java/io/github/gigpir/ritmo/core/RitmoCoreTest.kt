package io.github.gigpir.ritmo.core

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The same checks as the iPhone app's tests, so the two apps count workouts alike. */
class StepTest {
    @Test fun tabata() {
        val steps = Workout.samples[0].steps
        assertEquals(16, steps.size)
        assertEquals(Step.Phase.PREPARE, steps.first().phase)
        assertEquals(Step.Phase.WORK, steps.last().phase) // the final Rest is dropped
        assertEquals(listOf(7, 8), steps.map { it.round }.takeLast(2)) // Rest of round 7, then the last Work
    }

    @Test fun sampleDurations() {
        // Tabata 4:00, HIIT 60/15 39:25, HIIT 40/20 9:50, Boxing 47:10, circuit 12:10
        assertEquals(listOf(240, 2365, 590, 2830, 730), Workout.samples.map { it.duration })
    }

    @Test fun cyclesWithRestBetween() {
        // Prepare 0:10 · [Work 1:00, Rest 0:15] × 6 rounds · 5 cycles · 0:45 between cycles
        val steps = Workout.samples[1].steps
        assertEquals(30, steps.count { it.phase == Step.Phase.WORK })
        assertEquals(listOf(45, 45, 45, 45), steps.filter { it.phase == Step.Phase.RECOVER }.map { it.seconds })
        assertEquals(5, steps.last().cycle)
    }

    @Test fun restBeforeRecoverIsDropped() {
        val steps = Workout.samples[4].steps
        assertEquals(2, steps.count { it.phase == Step.Phase.RECOVER })
        steps.zipWithNext { a, b -> assertFalse(a.phase == Step.Phase.REST && b.phase == Step.Phase.RECOVER) }
        assertEquals("Plank", steps.last().name)
    }

    @Test fun restStaysBetweenCyclesWithoutRecover() {
        val workout = Workout(name = "Two cycles", prepare = 0, intervals = listOf(Interval.work(20), Interval.rest(10)), rounds = 2, cycles = 2)
        val w = Step.Phase.WORK
        val r = Step.Phase.REST
        assertEquals(listOf(w, r, w, r, w, r, w), workout.steps.map { it.phase })
        assertEquals(listOf(1, 1, 1, 1, 2, 2, 2), workout.steps.map { it.cycle })
    }

    @Test fun startsFollowEachOther() {
        val steps = Workout.samples[4].steps
        assertEquals(0, steps.first().start)
        steps.zipWithNext { a, b -> assertEquals(a.end, b.start) }
    }

    @Test fun summaries() {
        assertEquals(
            listOf(
                "8 rounds · 0:20 / 0:10",
                "6 rounds · 1:00 / 0:15 · 5 cycles",
                "10 rounds · 0:40 / 0:20",
                "12 rounds · 3:00 / 1:00",
                "6 intervals · 3 cycles",
            ),
            Workout.samples.map { it.summary },
        )
    }

    @Test fun clockTexts() {
        assertEquals("0:09", clockText(9))
        assertEquals("12:00", clockText(720))
        assertEquals("1:02:30", clockText(3750))
    }
}

class SavingTest {
    @Test fun savedWorkoutsRoundTrip() {
        val text = Workout.samples.toJson().toString()
        assertEquals(Workout.samples, JSONArray(text).toWorkouts())
    }

    @Test fun readsListsSavedBeforeTheRename() {
        val saved = """
            [{"id": "6F9619FF-8B86-D011-B42D-00C04FC964FF", "name": "Old", "prepare": 10, "rounds": 2,
              "sets": 3, "recover": 30,
              "intervals": [{"id": "7F9619FF-8B86-D011-B42D-00C04FC964FF", "name": "Work", "kind": "work", "seconds": 20}]}]
        """
        val workout = JSONArray(saved).toWorkouts().first()
        assertEquals(3, workout.cycles)
        assertEquals(30, workout.cycleRest)
    }
}

class SharingTest {
    @Test fun fileRoundTrip() {
        assertEquals(Workout.samples, WorkoutFile.decode(WorkoutFile.encode(Workout.samples)))
    }

    @Test fun keepsSlashesReadable() {
        assertTrue(WorkoutFile.encode(Workout.samples).contains("\"HIIT 60/15\""))
    }

    @Test fun readsAHandWrittenFile() {
        val file = """
            {"format": "ritmo", "version": 1, "workouts": [
              {"name": "HIIT 60/15", "prepare": 10, "rounds": 6, "cycles": 5, "cycleRest": 45,
               "intervals": [{"kind": "work", "seconds": 60}, {"kind": "rest", "seconds": 15}]}]}
        """
        val workouts = WorkoutFile.decode(file)
        assertEquals(1, workouts.size)
        assertEquals(listOf("Work", "Rest"), workouts[0].intervals.map { it.name })
        assertEquals(2365, workouts[0].duration) // 39:25
    }

    @Test fun readsTheExampleWrittenOnIPhone() {
        val workouts = WorkoutFile.decode(File("../../examples/HIIT 60-15.ritmo").readText())
        assertEquals(listOf("HIIT 60/15"), workouts.map { it.name })
        assertTrue(workouts[0].hasSameContent(Workout.samples[1]))
    }

    @Test fun readsASingleWorkout() {
        val text = Workout.samples[0].toJson().toString()
        assertEquals(listOf(Workout.samples[0]), WorkoutFile.decode(text))
    }

    @Test fun importSkipsWhatIsAlreadyThereAndUsesNewIds() {
        val mine = Workout.samples.take(2)
        val incoming = listOf(Workout.samples[1], Workout.samples[3], Workout.samples[3])
        val result = WorkoutFile.merge(incoming, mine)
        assertEquals(listOf("Boxing"), result.added.map { it.name })
        assertEquals(listOf("Boxing", "Tabata", "HIIT 60/15"), result.list.map { it.name })
        assertNotEquals(Workout.samples[3].id, result.added[0].id)
        assertTrue(result.added[0].hasSameContent(Workout.samples[3]))
    }
}

class ClockTest {
    private val t0 = 0.0

    private fun tabataClock() = WorkoutClock(Workout.samples[0].steps).apply { resume(t0) }

    @Test fun countsDownRoundingUp() {
        val clock = tabataClock()
        assertEquals(TimerSnapshot(step = 0, shown = 10, finished = false), clock.snapshot(t0))
        assertEquals(TimerSnapshot(step = 1, shown = 20, finished = false), clock.snapshot(t0 + 10))
        assertEquals(1, clock.snapshot(t0 + 29.5).shown)
        assertTrue(clock.isFinished(t0 + 240))
        assertFalse(clock.isFinished(t0 + 239.9))
    }

    @Test fun pauseAndResume() {
        val clock = tabataClock()
        clock.pause(t0 + 15)
        assertEquals(15, clock.snapshot(t0 + 100).shown)
        clock.resume(t0 + 100)
        assertEquals(10, clock.snapshot(t0 + 105).shown)
    }

    @Test fun nextAndPrevious() {
        val clock = tabataClock()
        clock.next(t0 + 15) // from the first Work to the first Rest
        assertEquals(30.0, clock.elapsed(t0 + 15), 0.0)
        clock.previous(t0 + 16) // 1 s into the Rest: back to the Work
        assertEquals(10.0, clock.elapsed(t0 + 16), 0.0)
        clock.previous(t0 + 20) // 4 s into the Work: restart it
        assertEquals(10.0, clock.elapsed(t0 + 20), 0.0)
    }

    @Test fun nextOnLastStepFinishes() {
        val clock = tabataClock()
        clock.next(t0 + 235)
        assertTrue(clock.isFinished(t0 + 235))
    }
}

class CueTest {
    @Test fun tabataCues() {
        val steps = Workout.samples[0].steps
        val clock = WorkoutClock(steps).apply { resume(0.0) }

        val cues = mutableListOf<Cue>()
        var last: TimerSnapshot? = null
        for (tick in 0..2410) { // every 0.1 s, a little past the end
            val snapshot = clock.snapshot(tick / 10.0)
            cues += Cue.due(last, snapshot, steps)
            last = snapshot
        }

        val expected = mutableListOf("Prepare")
        repeat(8) { expected += listOf("Work", "Rest") }
        expected.removeAt(expected.lastIndex)
        assertEquals(expected, cues.filterIsInstance<Cue.Announce>().map { it.text })
        assertEquals(16 * 3, cues.count { it == Cue.Beep })
        assertEquals(Cue.Finish, cues.last())
    }

    @Test fun restartAnnouncesAgain() {
        val steps = Workout.samples[0].steps
        val before = TimerSnapshot(step = 1, shown = 5, finished = false)
        val after = TimerSnapshot(step = 1, shown = 20, finished = false)
        assertEquals(listOf(Cue.Announce("Work")), Cue.due(before, after, steps))
    }

    @Test fun noBeepAtTheStartOfAShortStep() {
        val steps = Workout(name = "Short", prepare = 0, intervals = listOf(Interval.work(3))).steps
        val start = TimerSnapshot(step = 0, shown = 3, finished = false)
        assertEquals(listOf(Cue.Announce("Work")), Cue.due(null, start, steps))
    }
}

class ToneTest {
    @Test fun beepLastsAsAsked() {
        val beep = Tone.beep(880.0, 0.12)
        assertEquals((0.12 * Tone.SAMPLE_RATE).toInt(), beep.size)
        assertEquals(0, beep.first().toInt()) // fades in, so it doesn't click
    }
}
