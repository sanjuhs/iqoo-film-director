package dev.minifilm.director;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

/** Synthetic metadata and text only; no files, camera, audio, inference or quality measurement. */
@RunWith(AndroidJUnit4.class)
public final class ShotPlanSnapshotTest {
    @Test public void preservesIdentityOrderExactContentsAndIsolatesCallerMutations() throws Exception {
        Shot first = shot("current-b", "Detail"), second = shot("current-a", "Hero");
        first.instruction = "  Show a chosen detail.\nThen pause.  ";
        first.caption = "Caption\nwith punctuation!"; first.targetDurationMs = 5746;
        ArrayList<Shot> plan = new ArrayList<>(Arrays.asList(first, second));
        ShotPlanSnapshot snapshot = ShotPlanSnapshot.capture(plan, "Editable template · exact label");
        first.id = "mutated"; first.title = "Changed"; first.instruction = "Changed";
        first.caption = "Changed"; first.targetDurationMs = 2000; plan.clear();
        ShotPlanSnapshot.Entry preserved = snapshot.entries.get(0);
        assertEquals("current-b", preserved.id); assertEquals("Detail", preserved.title);
        assertEquals("  Show a chosen detail.\nThen pause.  ", preserved.instruction);
        assertEquals("Caption\nwith punctuation!", preserved.caption); assertEquals(5746, preserved.targetDurationMs);
        assertEquals(0, preserved.shotIndex); assertEquals("current-a", snapshot.entries.get(1).id);
        assertEquals("Editable template · exact label", snapshot.sourceLabel);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.entries.clear());
        JSONObject json = snapshot.toJson();
        assertEquals(1, json.getJSONArray("shots").getJSONObject(0).getInt("order"));
        assertEquals(2, json.getJSONArray("shots").getJSONObject(1).getInt("order"));
        json.getJSONArray("shots").getJSONObject(0).put("title", "JSON mutation");
        json.put("sourceLabel", "JSON mutation");
        assertEquals("Detail", snapshot.toJson().getJSONArray("shots").getJSONObject(0).getString("title"));
        assertEquals(snapshot.sourceLabel, snapshot.toJson().getString("sourceLabel"));
        assertEquals("minifilm.shot-plan.v1", json.getString("schema"));
    }

    @Test public void exactAssociationsResolveNamesWhileOldIdsStayExplicitlyUnknown() {
        ShotPlanSnapshot snapshot = ShotPlanSnapshot.capture(Collections.singletonList(shot("new-hero", "Hero")), "Manual plan");
        Take take = take(); take.inMs = 1001; take.outMs = 5746;
        take.reviewedShotIds = new ArrayList<>(Arrays.asList("old-hero", "new-hero", "new-hero"));
        String notes = snapshot.readableNotes(Collections.singletonList(take));
        assertNull(snapshot.find("old-hero")); assertNull(snapshot.find("NEW-HERO"));
        assertSame(snapshot.entries.get(0), snapshot.find("new-hero"));
        assertTrue(notes.contains("Cut 1: 1.001–5.746s"));
        assertTrue(notes.contains("Creator assignment: 1. Hero"));
        assertEquals(notes.indexOf("Creator assignment:"), notes.lastIndexOf("Creator assignment:"));
        assertTrue(notes.contains("Unknown or earlier-plan assignment"));
        assertFalse(notes.contains("old-hero")); assertFalse(notes.contains("new-hero"));
    }

    @Test public void notesExcludeSourceAndProvenancePathsAndDoNotImplyApproval() throws Exception {
        ShotPlanSnapshot snapshot = ShotPlanSnapshot.capture(Collections.singletonList(shot("plan-id", "Hero")), "Local AI · synthetic test");
        Take take = take(); take.reviewedShotIds.add("plan-id");
        String notes = snapshot.readableNotes(Collections.singletonList(take));
        assertFalse(notes.contains("/private/")); assertFalse(notes.contains("file:"));
        assertFalse(notes.contains("SECRET-PROVENANCE")); assertFalse(notes.contains(take.uri.toString()));
        assertTrue(notes.contains("Local AI · synthetic test")); assertTrue(notes.contains("Direction: Stand still"));
        JSONObject json = snapshot.toJson();
        assertEquals(3, json.length()); assertFalse(json.has("brief")); assertFalse(json.has("sourceUri"));
        assertFalse(json.has("reviewed")); assertFalse(json.has("approved"));
        assertEquals(7, json.getJSONArray("shots").getJSONObject(0).length());
        assertEquals("scene_default", json.getJSONArray("shots").getJSONObject(0).getString("framingTarget"));
    }

    @Test public void emptyContextAndUnassignedOrUnselectedCutsRemainHonest() throws Exception {
        ShotPlanSnapshot empty = ShotPlanSnapshot.empty();
        assertTrue(empty.isEmpty()); assertEquals("", empty.sourceLabel);
        assertEquals(0, empty.toJson().getJSONArray("shots").length());
        Take unassigned = take(), unselected = take(); unselected.selected = false;
        unselected.reviewedShotIds.add("anything");
        String notes = empty.readableNotes(Arrays.asList(unassigned, unselected));
        assertTrue(notes.contains("No current plan was included"));
        assertTrue(notes.contains("No creator-reviewed shot assignment"));
        assertFalse(notes.contains("Cut 2:"));
        assertTrue(empty.readableNotes(null).contains("No selected cuts"));
    }

    @Test public void limitsRejectInvalidMetadataWithoutTruncatingAllowedText() {
        Shot valid = shot(repeat(160), repeat(140));
        valid.instruction = repeat(2000); valid.caption = repeat(1000); valid.targetDurationMs = 60000;
        assertEquals(2000, ShotPlanSnapshot.capture(Collections.singletonList(valid), repeat(200)).entries.get(0).instruction.length());
        assertThrows(IllegalArgumentException.class, () -> ShotPlanSnapshot.capture(null, ""));
        assertThrows(IllegalArgumentException.class, () -> ShotPlanSnapshot.capture(Collections.singletonList(null), ""));
        assertThrows(IllegalArgumentException.class, () -> ShotPlanSnapshot.capture(Collections.singletonList(valid), null));
        assertThrows(IllegalArgumentException.class, () -> ShotPlanSnapshot.capture(Collections.singletonList(valid), repeat(201)));
        for (String field : new String[]{"id", "title", "instruction", "caption"}) {
            Shot bad = shot("id", "Title");
            if (field.equals("id")) bad.id = repeat(161);
            if (field.equals("title")) bad.title = repeat(141);
            if (field.equals("instruction")) bad.instruction = repeat(2001);
            if (field.equals("caption")) bad.caption = repeat(1001);
            assertThrows(field, IllegalArgumentException.class, () -> ShotPlanSnapshot.capture(Collections.singletonList(bad), ""));
        }
        Shot duplicate = shot("same", "Second");
        assertThrows(IllegalArgumentException.class, () -> ShotPlanSnapshot.capture(Arrays.asList(shot("same", "First"), duplicate), ""));
        assertThrows(IllegalArgumentException.class, () -> ShotPlanSnapshot.capture(Collections.singletonList(shot(" ", "Blank ID")), ""));
        Shot badDuration = shot("id", "Title"); badDuration.targetDurationMs = 1999;
        assertThrows(IllegalArgumentException.class, () -> ShotPlanSnapshot.capture(Collections.singletonList(badDuration), ""));
        badDuration.targetDurationMs = 60001;
        assertThrows(IllegalArgumentException.class, () -> ShotPlanSnapshot.capture(Collections.singletonList(badDuration), ""));
        ArrayList<Shot> tooMany = new ArrayList<>(); for (int i = 0; i < 13; i++) tooMany.add(shot("id-" + i, "Title"));
        assertThrows(IllegalArgumentException.class, () -> ShotPlanSnapshot.capture(tooMany, ""));
        Shot blanks = new Shot("blank-fields", "", "", "", 2000);
        assertEquals("", ShotPlanSnapshot.capture(Collections.singletonList(blanks), "").entries.get(0).instruction);
    }

    private static Shot shot(String id, String title) { return new Shot(id, title, "Stand still", "Synthetic caption", 4000); }
    private static Take take() { return new Take(Uri.parse("file:///private/SECRET-SOURCE.mp4"), "SECRET-PROVENANCE", "Not emitted", "", 5746); }
    private static String repeat(int count) { char[] chars = new char[count]; Arrays.fill(chars, 'x'); return new String(chars); }
}
