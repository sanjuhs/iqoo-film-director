package dev.minifilm.director;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Offline editable starting plans. Templates are explicitly not LLM output. */
public final class DirectorEngine {
    public static final String SOURCE_LABEL = "Editable template · no LLM generation";

    public static List<Shot> plan(String brief, String style) {
        String text = (brief == null ? "" : brief).trim();
        String selected = style == null ? "" : style.trim().toLowerCase(Locale.ROOT);
        String mode = selected.isEmpty() ? text.toLowerCase(Locale.ROOT) : selected;
        List<Shot> shots = new ArrayList<>();
        if (mode.contains("product") || mode.contains("reveal") && !mode.contains("fashion")) {
            add(shots, "The hook", "Hold your product beside you. Say who it is for in one sentence.", "MEET YOUR NEW FAVORITE", 5000);
            add(shots, "The reveal", "Bring the product into the center slowly. Hold it still for a beat.", "THE REVEAL", 4000);
            add(shots, "One useful detail", "Bring a useful product detail toward the lens. Hold it still for a beat.", "SMALL DETAILS. BIG DIFFERENCE.", 4000);
            add(shots, "In use", "Show one real use of the product. Keep the movement slow and repeatable.", "HERE'S HOW I USE IT", 5000);
            add(shots, "Your verdict", "Return to camera and give your honest one-line verdict. Pause at the end.", "YOUR TAKE", 4000);
        } else if (mode.contains("intro") || mode.contains("introduc")) {
            add(shots, "Hello", "Look at the lens and say your name. Keep your shoulders relaxed.", "HELLO, I'M…", 5000);
            add(shots, "What you do", "Say what you make or do in one clear sentence. Keep your hands relaxed.", "WHAT I DO", 5000);
            add(shots, "Show your world", "Show something that represents your work. Bring it toward the lens slowly.", "A LITTLE OF MY WORLD", 4000);
            add(shots, "Your personality", "Tell us one unexpected thing about you. Talk like you would to a friend.", "THE FUN PART", 5000);
            add(shots, "Stay for more", "Finish with what people can expect next. Hold your smile for a beat.", "LET'S MAKE SOMETHING", 4000);
        } else if (mode.contains("talk") || mode.contains("story") || mode.contains("yap")) {
            add(shots, "The hook", "Look at the lens. Begin with the most interesting sentence.", "LET ME TELL YOU THIS", 5000);
            add(shots, "Set the scene", "Explain what happened in one or two sentences. Keep looking at the lens.", "HERE'S THE CONTEXT", 7000);
            add(shots, "A cutaway", "Show a detail connected to your story. Hold it still before and after the movement.", "THE LITTLE DETAILS", 4000);
            add(shots, "The turning point", "Return to camera. Tell the key moment and leave a short pause afterward.", "AND THEN…", 7000);
            add(shots, "The takeaway", "Say the thing you want people to remember. Finish naturally, then hold for a beat.", "MY TAKEAWAY", 5000);
        } else {
            add(shots, "Outfit hero", "Stand where your full outfit is visible. Relax your shoulders, then hold a pose.", "TODAY'S LOOK", 5000);
            add(shots, "Favorite detail", "Bring your favorite garment detail toward the lens. Stay wearing your outfit and hold still.", "IT'S IN THE DETAILS", 4000);
            add(shots, "A little movement", "Check your path first. Take two small steps, turn gently, then pause.", "MOVE WITH IT", 4000);
            add(shots, "Side silhouette", "Turn slightly sideways. Keep one hand relaxed and let the outfit's shape show.", "A DIFFERENT ANGLE", 4000);
            add(shots, "The sign-off", "Come back to your favorite pose. Look into the lens and give a small smile.", "MAKE IT YOURS", 4000);
        }
        return shots;
    }

    private static void add(List<Shot> list, String title, String instruction, String caption, long duration) {
        list.add(new Shot("shot-" + (list.size() + 1), title, instruction, caption, duration));
    }
}
