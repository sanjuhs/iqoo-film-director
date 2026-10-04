package dev.minifilm.director;

import java.util.Locale;

/** Explicit creator intent and conservative observation wording, not framing quality or readiness. */
public final class FramingTarget {
    public static final String SCENE_DEFAULT = "scene_default";
    public static final String FULL_OUTFIT = "full_outfit";
    public static final String FACE_SHOULDERS = "face_shoulders";
    public static final String OBJECT_DETAIL = "object_detail";
    public static final String MANUAL = "manual";

    private FramingTarget() { }

    /** Canonical keys only. Unknown legacy/missing values preserve the existing scene policy. */
    public static String normalize(String target) { return isValid(target) ? target : SCENE_DEFAULT; }

    public static boolean isValid(String target) {
        return SCENE_DEFAULT.equals(target) || FULL_OUTFIT.equals(target)
                || FACE_SHOULDERS.equals(target) || OBJECT_DETAIL.equals(target) || MANUAL.equals(target);
    }

    public static String label(String target) {
        switch (normalize(target)) {
            case FULL_OUTFIT: return "Full outfit";
            case FACE_SHOULDERS: return "Face & shoulders";
            case OBJECT_DETAIL: return "Object / detail";
            case MANUAL: return "Manual preview only";
            default: return "Scene default (suggested)";
        }
    }

    public static boolean requiresPerson(String target, String style, String title) {
        switch (normalize(target)) {
            case FULL_OUTFIT:
            case FACE_SHOULDERS: return true;
            case OBJECT_DETAIL:
            case MANUAL: return false;
            default:
                if (lower(style).contains("product")) return false;
                String name = lower(title);
                return !name.contains("cutaway") && !name.contains("world") && !name.contains("detail")
                        && !name.contains("fabric") && !name.contains("product")
                        && !name.contains("texture") && !name.contains("sleeve");
        }
    }

    /** An outfit target requests room for shoes; landmarks cannot identify shoes or prove cropping. */
    public static boolean requestsFullOutfit(String target, String style) {
        String value = normalize(target);
        return FULL_OUTFIT.equals(value) || SCENE_DEFAULT.equals(value) && lower(style).contains("fashion");
    }

    /** Interpret only the existing qualified landmark label, never a score or automatic approval.
     * Lower body unconfirmed does not prove it is out of frame. Object detail is not evaluated by pose.
     * Scene default has no explicit saved-footage target without its original scene context.
     */
    public static String describeSample(String target, String sampleLabel) {
        String value = normalize(target);
        if (MANUAL.equals(value)) return "Manual framing selected. Review this sampled frame yourself.";
        if (OBJECT_DETAIL.equals(value))
            return "Object/detail framing selected. Person landmarks do not assess the object or its detail; review this sample yourself.";
        if (SCENE_DEFAULT.equals(value))
            return "Scene default does not specify a saved-footage framing target. Review this sampled frame yourself.";
        boolean full = "full-body".equals(sampleLabel);
        boolean upper = full || "waist-up".equals(sampleLabel) || "head-and-shoulders".equals(sampleLabel);
        if (FULL_OUTFIT.equals(value)) {
            if (full) return "Full outfit requested. Head and lower-body landmarks were confirmed in this sample; check shoes and the actual moving cut yourself.";
            if (upper) return "Full outfit requested. Head and shoulders were confirmed, but lower body was not fully confirmed. Check shoes and the preview yourself.";
            return "Full outfit requested. Person framing could not be confirmed in this sample. Review the actual frame yourself.";
        }
        if (upper) return "Face & shoulders requested. Head and shoulder landmarks were confirmed in this sample. Review the actual moving cut yourself.";
        return "Face & shoulders requested. Head and shoulders could not be confirmed in this sample. Review the actual frame yourself.";
    }

    private static String lower(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT); }
}
