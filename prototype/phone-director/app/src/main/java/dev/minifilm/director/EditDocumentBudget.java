package dev.minifilm.director;

import org.json.JSONException;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;

/** Shared bound for the exact local edit document written, recovered and saved to Files. */
public final class EditDocumentBudget {
    public static final int MAX_BYTES = 1_048_576;
    public static final String TOO_LARGE_MESSAGE = "The edit list exceeds 1 MB. Reduce subtitle segments or text, including words outside your selected cuts, before exporting. Existing words and original clips were kept.";

    private EditDocumentBudget() { }

    /** Preserve the complete pretty JSON and count its actual UTF-8 bytes, never characters. */
    static byte[] encode(JSONObject document) throws JSONException {
        if (document == null) throw new IllegalArgumentException("The edit list is unavailable. Review your cuts before exporting.");
        byte[] bytes = document.toString(2).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException(TOO_LARGE_MESSAGE);
        return bytes;
    }
}
