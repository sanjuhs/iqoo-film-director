package dev.minifilm.director;

import static org.junit.Assert.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Creator-policy and configuration freshness checks. No frame, native inference, camera or audio.
 * The final method constructs/disposes a real bundled pose client but submits no image.
 * It checks the production callback-admission predicate, not measured asynchronous abort timing.
 */
@RunWith(AndroidJUnit4.class)
public final class FramingTargetTest {
    @Test public void oldAndInvalidShotTargetsNormalizeWithoutChangingEditableFieldsOrDurationBounds() {
        Shot legacy = new Shot("id", "Name", "Direction", "Caption", 4200);
        assertEquals(FramingTarget.SCENE_DEFAULT, legacy.framingTarget);
        for (String invalid : new String[]{null, "", "unknown", "FULL_OUTFIT", " full_outfit "}) {
            assertFalse(FramingTarget.isValid(invalid));
            Shot shot = new Shot("id", "Name", "Direction", "Caption", 4200, invalid);
            assertEquals(FramingTarget.SCENE_DEFAULT, shot.framingTarget);
            assertEquals("id", shot.id); assertEquals("Name", shot.title);
            assertEquals("Direction", shot.instruction); assertEquals("Caption", shot.caption);
            assertEquals(4200, shot.targetDurationMs);
        }
        for (String target : targets()) {
            assertTrue(FramingTarget.isValid(target)); assertEquals(target, FramingTarget.normalize(target));
            assertEquals(target, new Shot("id", "Name", "Direction", "Caption", 4200, target).framingTarget);
            assertFalse(FramingTarget.label(target).trim().isEmpty());
        }
        assertEquals(2000, new Shot("i", "n", "d", "c", 100, FramingTarget.FULL_OUTFIT).targetDurationMs);
        assertEquals(60000, new Shot("i", "n", "d", "c", 90000, FramingTarget.FACE_SHOULDERS).targetDurationMs);
    }

    @Test public void sceneDefaultPreservesPersonExclusionsWhileExplicitTargetsOverrideSceneNames() {
        assertTrue(FramingTarget.requiresPerson(FramingTarget.SCENE_DEFAULT, "Fashion", "Hero pose"));
        assertTrue(FramingTarget.requiresPerson(FramingTarget.SCENE_DEFAULT, "Talking head", "Hook"));
        assertFalse(FramingTarget.requiresPerson(FramingTarget.SCENE_DEFAULT, "Product reveal", "Hero"));
        for (String title : new String[]{"Cutaway", "Show your world", "Detail", "Fabric", "Product", "Texture", "Sleeve"}) {
            assertFalse(title, FramingTarget.requiresPerson(FramingTarget.SCENE_DEFAULT, "Fashion", title));
            assertTrue(title, FramingTarget.requiresPerson(FramingTarget.FULL_OUTFIT, "Product reveal", title));
            assertTrue(title, FramingTarget.requiresPerson(FramingTarget.FACE_SHOULDERS, "Product reveal", title));
        }
        for (String target : new String[]{FramingTarget.OBJECT_DETAIL, FramingTarget.MANUAL}) {
            assertFalse(FramingTarget.requiresPerson(target, "Fashion", "Hero pose"));
            assertFalse(FramingTarget.requiresPerson(target, "Talking head", "Hook"));
        }
        assertTrue(FramingTarget.requiresPerson(null, null, null));
        assertFalse(FramingTarget.requiresPerson("unknown", "Fashion", "Detail"));
    }

    @Test public void renewedPlanIdentityPreservesCreatorFramingAndEditsWithoutSharingShotObjects() {
        java.util.List<Shot> original = new java.util.ArrayList<>();
        for (String target : targets()) original.add(new Shot("old-" + target, "Creator title", "Creator direction", "Creator caption", 4200, target));
        // Identity renewal must retain edits rather than recompute timing from a constructor default.
        original.get(0).targetDurationMs = 5100;
        java.util.List<Shot> first = ShotCoverage.freshPlan(original), second = ShotCoverage.freshPlan(original);
        assertEquals(original.size(), first.size());
        for (int i = 0; i < original.size(); i++) {
            Shot before = original.get(i), copy = first.get(i);
            assertNotSame(before, copy); assertNotEquals(before.id, copy.id); assertNotEquals(copy.id, second.get(i).id);
            assertEquals(before.title, copy.title); assertEquals(before.instruction, copy.instruction);
            assertEquals(before.caption, copy.caption); assertEquals(before.targetDurationMs, copy.targetDurationMs);
            assertEquals(before.framingTarget, copy.framingTarget);
        }
        first.get(1).framingTarget = FramingTarget.MANUAL; first.get(1).instruction = "Edited independent copy";
        assertEquals(FramingTarget.FULL_OUTFIT, original.get(1).framingTarget);
        assertEquals(FramingTarget.FULL_OUTFIT, second.get(1).framingTarget);
        assertEquals("Creator direction", original.get(1).instruction);
    }

    @Test public void onlyFullOutfitOrFashionSceneDefaultRequestsShoesAndNoPortraitObjectOrManualDoes() {
        for (String style : new String[]{"Fashion", "Talking head", "Product reveal", null}) {
            assertTrue(FramingTarget.requestsFullOutfit(FramingTarget.FULL_OUTFIT, style));
            assertFalse(FramingTarget.requestsFullOutfit(FramingTarget.FACE_SHOULDERS, style));
            assertFalse(FramingTarget.requestsFullOutfit(FramingTarget.OBJECT_DETAIL, style));
            assertFalse(FramingTarget.requestsFullOutfit(FramingTarget.MANUAL, style));
        }
        assertTrue(FramingTarget.requestsFullOutfit(FramingTarget.SCENE_DEFAULT, "Fashion"));
        assertFalse(FramingTarget.requestsFullOutfit(FramingTarget.SCENE_DEFAULT, "Talking head"));
        assertFalse(FramingTarget.requestsFullOutfit(FramingTarget.SCENE_DEFAULT, "Product reveal"));
    }

    @Test public void sampledObservationKeepsUnknownsAndDoesNotTurnLandmarksIntoQualityOrObjectClaims() {
        String missingLower = FramingTarget.describeSample(FramingTarget.FULL_OUTFIT, "head-and-shoulders");
        assertTrue(missingLower.contains("lower body was not fully confirmed")); assertTrue(missingLower.contains("Check shoes"));
        for (String sample : new String[]{"full-body", "waist-up", "head-and-shoulders", "empty or unclear", "review needed", "invented", null}) {
            String portrait = FramingTarget.describeSample(FramingTarget.FACE_SHOULDERS, sample);
            assertFalse(portrait.toLowerCase(java.util.Locale.ROOT).contains("shoes"));
            if ("full-body".equals(sample) || "waist-up".equals(sample) || "head-and-shoulders".equals(sample))
                assertTrue(portrait.contains("landmarks were confirmed"));
            else assertTrue(portrait.contains("could not be confirmed"));
            String object = FramingTarget.describeSample(FramingTarget.OBJECT_DETAIL, sample);
            assertTrue(object.contains("do not assess the object"));
            assertTrue(FramingTarget.describeSample(FramingTarget.MANUAL, sample).contains("Review this sampled frame yourself"));
            assertTrue(FramingTarget.describeSample(FramingTarget.SCENE_DEFAULT, sample).contains("does not specify"));
            for (String target : targets()) {
                String description = FramingTarget.describeSample(target, sample).toLowerCase(java.util.Locale.ROOT);
                assertFalse(description.contains("ready")); assertFalse(description.contains("passed"));
                assertFalse(description.contains("good shot")); assertFalse(description.contains("cropped feet"));
            }
        }
    }

    @Test public void productionConfigurationAdmissionRejectsTargetModeChangesIncludingChangeBackAndClosedCoach() {
        AtomicInteger callbacks = new AtomicInteger(); PoseCoach coach = new PoseCoach((cue,count,latency)->callbacks.incrementAndGet());
        try {
            PoseCoach.Configuration initial = coach.configurationSnapshot(); assertTrue(coach.acceptsConfiguration(initial));
            coach.setFramingTarget(FramingTarget.FACE_SHOULDERS);
            assertFalse(coach.acceptsConfiguration(initial)); PoseCoach.Configuration portrait = coach.configurationSnapshot();
            assertTrue(coach.acceptsConfiguration(portrait)); coach.setFramingTarget(FramingTarget.FACE_SHOULDERS);
            assertSame(portrait, coach.configurationSnapshot());
            coach.setFramingTarget(FramingTarget.SCENE_DEFAULT);
            assertFalse("Changing back cannot revive an earlier frame", coach.acceptsConfiguration(initial));
            PoseCoach.Configuration fashion = coach.configurationSnapshot(); coach.setMode("Talking head");
            assertFalse(coach.acceptsConfiguration(fashion)); coach.setMode("Fashion");
            assertFalse(coach.acceptsConfiguration(fashion)); coach.setMode("Product reveal");
            assertFalse(coach.acceptsConfiguration(coach.configurationSnapshot()));
            coach.setFramingTarget(FramingTarget.FACE_SHOULDERS);
            PoseCoach.Configuration productPortrait = coach.configurationSnapshot(); assertTrue(coach.acceptsConfiguration(productPortrait));
            coach.setEnabled(false); assertFalse(coach.acceptsConfiguration(productPortrait)); coach.setEnabled(true);
            coach.setFramingTarget(FramingTarget.OBJECT_DETAIL); assertFalse(coach.acceptsConfiguration(coach.configurationSnapshot()));
            coach.setFramingTarget(FramingTarget.MANUAL); assertFalse(coach.acceptsConfiguration(coach.configurationSnapshot()));
            coach.setFramingTarget(FramingTarget.FULL_OUTFIT); PoseCoach.Configuration full = coach.configurationSnapshot();
            assertTrue(coach.acceptsConfiguration(full)); coach.close(); assertFalse(coach.acceptsConfiguration(full));
            assertEquals("No frame was submitted", 0, callbacks.get());
        } finally { coach.close(); }
    }

    private static String[] targets() {
        return new String[]{FramingTarget.SCENE_DEFAULT, FramingTarget.FULL_OUTFIT, FramingTarget.FACE_SHOULDERS,
                FramingTarget.OBJECT_DETAIL, FramingTarget.MANUAL};
    }
}
