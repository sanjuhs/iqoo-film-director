package dev.minifilm.director;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import static org.junit.Assert.*;

/** Synthetic metadata only: no media, camera, microphone, inference or shot-quality claim. */
@RunWith(AndroidJUnit4.class)
public final class ShotCoverageTest {
    @Test public void captureProvenanceAndLegacyIdsDoNotImplyReviewedCoverage() {
        Shot shot = shot("shot-1", "Hero");
        Take take = take("shot-1");
        assertTrue(take.reviewedShotIds.isEmpty());
        ShotCoverage.Result unreviewed = ShotCoverage.evaluate(Collections.singletonList(shot), Collections.singletonList(take));
        assertEquals(0, unreviewed.coveredCount);
        assertEquals(0, unreviewed.nextMissingIndex);
        take.reviewedShotIds.add("shot-1");
        assertEquals(1, ShotCoverage.evaluate(Collections.singletonList(shot), Collections.singletonList(take)).coveredCount);
        List<Shot> replacement = ShotCoverage.freshPlan(Collections.singletonList(shot));
        assertEquals(0, ShotCoverage.evaluate(replacement, Collections.singletonList(take)).coveredCount);
        assertEquals("shot-1", take.shotId);
    }

    @Test public void onlySelectedNonemptyInBoundsCutsCount() {
        List<Shot> plan = Arrays.asList(shot("a", "Hero"), shot("b", "Detail"));
        Take valid = mapped("a"); valid.inMs = 100; valid.outMs = 900;
        Take unselected = mapped("b"); unselected.selected = false;
        Take negative = mapped("b"); negative.inMs = -1;
        Take empty = mapped("b"); empty.outMs = empty.inMs;
        Take reversed = mapped("b"); reversed.inMs = 700; reversed.outMs = 600;
        Take beyond = mapped("b"); beyond.outMs = beyond.durationMs + 1;
        Take noDuration = mapped("b"); noDuration.durationMs = 0;
        Take absentSource = mapped("b"); absentSource.uri = null;
        Take remoteSource = mapped("b"); remoteSource.uri = Uri.parse("https://example.invalid/synthetic.mp4");
        Take unsupportedSource = mapped("b"); unsupportedSource.uri = Uri.parse("android.resource://synthetic/raw/clip");
        ShotCoverage.Result result = ShotCoverage.evaluate(plan,
                Arrays.asList(valid, unselected, negative, empty, reversed, beyond, noDuration,
                        absentSource, remoteSource, unsupportedSource));
        assertEquals(2, result.totalCount);
        assertEquals(1, result.coveredCount);
        assertEquals(1, result.nextMissingIndex);
        assertEquals(1, result.entries.get(0).reviewedSelectedTakeCount);
        assertEquals(0, result.entries.get(1).reviewedSelectedTakeCount);
        Take providerSource = mapped("b"); providerSource.uri = Uri.parse("content://synthetic/selected-clip");
        assertEquals("Local provider URI accepted without opening it", 1,
                ShotCoverage.evaluate(plan, Collections.singletonList(providerSource)).coveredCount);
    }

    @Test public void multipleAssociationsDeduplicatePerTakeAndPreserveInputs() {
        List<Shot> plan = Arrays.asList(shot("a", "Hero"), shot("b", "Movement"), shot("c", "Detail"));
        Take multi = take("provenance-only");
        multi.reviewedShotIds = new ArrayList<>(Arrays.asList("a", "a", "b", "unknown", null));
        Take second = mapped("a");
        List<String> originalMappings = new ArrayList<>(multi.reviewedShotIds);
        ShotCoverage.Result result = ShotCoverage.evaluate(plan, Arrays.asList(multi, second));
        assertEquals(2, result.coveredCount);
        assertEquals(2, result.entries.get(0).reviewedSelectedTakeCount);
        assertEquals(1, result.entries.get(1).reviewedSelectedTakeCount);
        assertEquals(2, result.nextMissingIndex);
        assertEquals(originalMappings, multi.reviewedShotIds);
        assertEquals("provenance-only", multi.shotId);
        assertEquals(0, multi.inMs); assertEquals(1000, multi.outMs); assertTrue(multi.selected);
        assertThrows(UnsupportedOperationException.class, () -> result.entries.clear());
        multi.reviewedShotIds.clear();
        assertEquals("Result is a scalar snapshot", 2, result.entries.get(0).reviewedSelectedTakeCount);
    }

    @Test public void freshPlansHaveDistinctNamespacesAndIndependentEditableCopies() {
        Shot first = shot("same-old-id", "Hero"); first.targetDurationMs = 1234;
        Shot second = shot("same-old-id", "Detail");
        List<Shot> original = Arrays.asList(first, second);
        List<Shot> a = ShotCoverage.freshPlan(original), b = ShotCoverage.freshPlan(original);
        HashSet<String> ids = new HashSet<>();
        for (int i = 0; i < original.size(); i++) {
            Shot source = original.get(i), copy = a.get(i);
            assertNotSame(source, copy);
            assertEquals(source.title, copy.title); assertEquals(source.instruction, copy.instruction);
            assertEquals(source.caption, copy.caption); assertEquals(source.targetDurationMs, copy.targetDurationMs);
            assertTrue(ids.add(copy.id)); assertTrue(ids.add(b.get(i).id));
            assertNotEquals(source.id, copy.id);
        }
        a.get(0).title = "Creator edit";
        assertEquals("Hero", first.title);
        assertEquals("same-old-id", first.id);
        assertThrows(IllegalArgumentException.class, () -> ShotCoverage.freshPlan(null));
        assertThrows(IllegalArgumentException.class, () -> ShotCoverage.freshPlan(Arrays.asList(first, null)));
    }

    @Test public void missingIndexTracksCurrentOrderAndCompleteOrEmptyPlans() {
        List<Shot> plan = Arrays.asList(shot("b", "Detail"), shot("a", "Hero"));
        ShotCoverage.Result missing = ShotCoverage.evaluate(plan, Collections.singletonList(mapped("a")));
        assertEquals(0, missing.nextMissingIndex); assertEquals("b", missing.entries.get(0).shotId);
        ShotCoverage.Result complete = ShotCoverage.evaluate(plan, Arrays.asList(mapped("a"), mapped("b")));
        assertEquals(-1, complete.nextMissingIndex); assertEquals(2, complete.coveredCount);
        assertEquals(-1, ShotCoverage.evaluate(Collections.emptyList(), null).nextMissingIndex);
        Take absentMappings = take("a"); absentMappings.reviewedShotIds = null;
        assertEquals(0, ShotCoverage.evaluate(plan, Arrays.asList(null, absentMappings)).coveredCount);
    }

    private static Shot shot(String id, String title) { return new Shot(id, title, "Stand still", "Synthetic", 4000); }
    private static Take take(String provenance) { return new Take(Uri.parse("file:///synthetic/metadata-only.mp4"), provenance, "Synthetic", "", 1000); }
    private static Take mapped(String id) { Take take = take("unreviewed-provenance"); take.reviewedShotIds.add(id); return take; }
}
