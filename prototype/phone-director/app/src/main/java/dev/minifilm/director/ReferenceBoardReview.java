package dev.minifilm.director;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.os.Looper;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;
import java.util.ArrayList;
import java.util.List;

/** Explicit review of sampled reference moments; no camera, inference, persistence or automatic apply. */
public final class ReferenceBoardReview {
    private static final int MAX_NOTE = 70;
    private ReferenceBoardReview() { }

    public interface Listener {
        /** Receiver owns this independent reviewed board and must close it when no longer needed. */
        void onConfirmed(ReferenceBoard reviewedBoard, String summary);
        void onDiscarded();
    }

    /** Main thread, resumed activity only. Later/back/stop dismiss without a result, preserving caller's draft.
     * Returns null when the activity cannot safely show UI. Never closes the caller-owned board.
     */
    public static AlertDialog show(Activity activity, ReferenceBoard board, Listener listener) {
        if (Looper.myLooper() != Looper.getMainLooper())
            throw new IllegalStateException("Review reference moments on the main thread.");
        if (board == null || listener == null) throw new IllegalArgumentException("A reference board and listener are required.");
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return null;
        Lifecycle lifecycle = activity instanceof LifecycleOwner ? ((LifecycleOwner) activity).getLifecycle() : null;
        if (lifecycle != null && !lifecycle.getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) return null;

        LinearLayout form = new LinearLayout(activity); form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(activity, 20), dp(activity, 12), dp(activity, 20), dp(activity, 12));
        TextView instruction = new TextView(activity);
        instruction.setText("Review these moments in reference time order. Each is one approximate frame; it cannot establish motion, audio or the whole story. Correct guesses before using them. Unconfirmed text is not saved.");
        form.addView(instruction);
        TextView cueHint = new TextView(activity);
        cueHint.setText("To keep your own direction, name one shot and its action in a note, for example Detail: Show a detail you choose. Named actions stay as your directions; other notes inform an editable AI draft. Review its captions too.");
        form.addView(cueHint);
        List<EditText> fields = new ArrayList<>(); List<CheckBox> selected = new ArrayList<>();
        List<ImageView> views = new ArrayList<>(); List<Bitmap> images = new ArrayList<>();
        for (int i = 0; i < board.frames.size(); i++) {
            ReferenceBoard.FrameDraft frame = board.frames.get(i);
            TextView label = new TextView(activity);
            label.setText("Moment " + (i + 1) + " · near " + SubtitleTime.format(frame.requestedTimeMs) + " seconds");
            label.setPadding(0, dp(activity, 16), 0, dp(activity, 6)); form.addView(label);
            Bitmap copy = frame.thumbnailCopy();
            if (copy != null) {
                ImageView image = new ImageView(activity); image.setImageBitmap(copy);
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                image.setContentDescription("Reference moment " + (i + 1) + " near " + SubtitleTime.format(frame.requestedTimeMs) + " seconds");
                form.addView(image, new LinearLayout.LayoutParams(-1, dp(activity, 180)));
                views.add(image); images.add(copy);
            } else {
                TextView saved = new TextView(activity);
                saved.setText("Saved reviewed notes; re-read moments to see the frames.");
                form.addView(saved);
            }
            CheckBox use = new CheckBox(activity); use.setText("Use this moment"); use.setChecked(frame.selected);
            form.addView(use); selected.add(use);
            EditText notes = new EditText(activity); notes.setText(frame.notes); notes.setHint("Your short shot note · up to 70 characters");
            notes.setMinLines(2); notes.setEnabled(frame.selected); form.addView(notes); fields.add(notes);
            use.setOnCheckedChangeListener((button, checked) -> { notes.setEnabled(checked); notes.setError(null); });
        }
        TextView limit = new TextView(activity); limit.setPadding(0, dp(activity, 10), 0, 0);
        limit.setText("Selected notes: up to 70 characters each and 210 total including times. Excluded drafts are preserved. Keep the original time order; arrange your own shots in the plan.");
        form.addView(limit);
        ScrollView scroll = new ScrollView(activity); scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Review reference moments").setView(scroll)
                .setPositiveButton("Use reviewed moments", null).setNeutralButton("Later", null)
                .setNegativeButton("Discard", null).create();
        final Runnable[] result = {null};
        LifecycleEventObserver stopped = (owner, event) -> { if (event == Lifecycle.Event.ON_STOP) dialog.dismiss(); };
        dialog.setOnDismissListener(ignored -> {
            if (lifecycle != null) lifecycle.removeObserver(stopped);
            // The board owns different bitmaps. Detach all UI copies before recycling or notifying the caller.
            for (ImageView view : views) view.setImageDrawable(null);
            for (Bitmap image : images) if (!image.isRecycled()) image.recycle();
            if (result[0] != null) { Runnable callback = result[0]; result[0] = null; callback.run(); }
        });
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(view -> {
                result[0] = listener::onDiscarded; dialog.dismiss();
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                ArrayList<String> corrected = new ArrayList<>(); ArrayList<Boolean> included = new ArrayList<>();
                boolean valid = true, any = false;
                for (int i = 0; i < fields.size(); i++) {
                    EditText field = fields.get(i); String text = field.getText().toString().trim();
                    boolean use = selected.get(i).isChecked(); field.setError(null);
                    if (!use) text = board.frames.get(i).notes;
                    else if (text.length() > MAX_NOTE) { field.setError("Shorten this note to 70 characters; nothing was truncated."); valid = false; }
                    else if (text.isEmpty()) { field.setError("Add a short note or deselect this moment."); valid = false; }
                    corrected.add(text); included.add(use); any |= use;
                }
                if (!valid) { limit.setText("Correct the highlighted notes before confirming. Nothing has been applied."); return; }
                if (!any) { limit.setText("Select at least one moment to use, or choose Discard."); return; }
                ReferenceBoard reviewed = null;
                try {
                    reviewed = board.reviewedCopy(corrected, included);
                    String summary = reviewed.planningSummary();
                    if (summary.length() > 210) throw new IllegalArgumentException("Shorten the selected notes: the summary including times must fit 210 characters.");
                    ReferenceBoard confirmed = reviewed;
                    result[0] = () -> listener.onConfirmed(confirmed, summary);
                    dialog.dismiss();
                } catch (IllegalArgumentException invalid) {
                    if (reviewed != null) reviewed.close();
                    limit.setText(invalid.getMessage() == null ? "Shorten the selected notes to fit 210 characters including times." : invalid.getMessage());
                }
            });
        });
        if (lifecycle != null) lifecycle.addObserver(stopped);
        dialog.show();
        return dialog;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
