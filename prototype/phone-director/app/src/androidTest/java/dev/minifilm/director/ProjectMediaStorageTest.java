package dev.minifilm.director;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Read-only logical-byte inventory of this app's MiniFilm gallery rows, including pending rows. */
@RunWith(AndroidJUnit4.class)
public final class ProjectMediaStorageTest {
    private static final String PATH = "Movies/MiniFilm/";

    @Test(timeout = 30_000)
    public void inventoryOnlyOwnedMiniFilmGalleryBytesWithoutOpeningCaptureOrReadingMediaContent() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertCaptureDenied(context);
        int rows = 0, pending = 0;
        long bytes = 0;
        try {
            Uri collection = MediaStore.setIncludePending(MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
            try (Cursor cursor = context.getContentResolver().query(collection,
                    new String[] {MediaStore.Video.Media._ID, MediaStore.Video.Media.OWNER_PACKAGE_NAME,
                            MediaStore.Video.Media.RELATIVE_PATH, MediaStore.Video.Media.IS_PENDING, MediaStore.Video.Media.SIZE},
                    MediaStore.Video.Media.OWNER_PACKAGE_NAME + "=? AND " + MediaStore.Video.Media.RELATIVE_PATH + "=?",
                    new String[] {context.getPackageName(), PATH}, null)) {
                if (cursor == null) throw new IllegalStateException("Inventory unavailable");
                assertTrue("Bound gallery provider work to 256 owned outputs", cursor.getCount() <= 256);
                while (cursor.moveToNext()) {
                    assertEquals(context.getPackageName(), cursor.getString(1));
                    assertEquals(PATH, cursor.getString(2));
                    int isPending = cursor.getInt(3); assertTrue(isPending == 0 || isPending == 1);
                    if (cursor.isNull(4)) throw new IllegalStateException("Logical size unavailable");
                    long reportedSize = cursor.getLong(4);
                    if (reportedSize < 0) throw new IllegalStateException("Logical size unavailable");
                    Uri owned = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0));
                    long statSize;
                    try (ParcelFileDescriptor file = context.getContentResolver().openFileDescriptor(owned, "r")) {
                        if (file == null) throw new IllegalStateException("Owned stat unavailable");
                        statSize = file.getStatSize(); // No stream/content reads or playback.
                    }
                    if (statSize < 0) throw new IllegalStateException("Owned stat unavailable");
                    // Pending-row metadata may lag an in-progress file. Use the larger independent
                    // observation conservatively, without summing the same output twice.
                    bytes = Math.addExact(bytes, Math.max(reportedSize, statSize));
                    rows++; pending += isPending;
                }
            }
        } catch (Exception unavailable) {
            // Provider exceptions may include row URIs. Report a fixed unknown result, without
            // pretending a failed query/stat was an empty gallery or logging identifiers.
            Log.i("MiniFilmStorageTest", new JSONObject().put("status", "unknown").toString());
            throw new AssertionError("Owned gallery logical-byte inventory is unknown: query/stat unavailable.");
        } finally { assertCaptureDenied(context); }
        assertTrue(rows >= 0 && rows <= 256); assertTrue(pending >= 0 && pending <= rows); assertTrue(bytes >= 0);
        Log.i("MiniFilmStorageTest", new JSONObject().put("ownedRows", rows).put("pendingRows", pending)
                .put("ownedGalleryLogicalBytes", bytes).toString());
    }

    private static void assertCaptureDenied(Context context) {
        assertEquals("Gallery accounting must keep microphone permission denied", PackageManager.PERMISSION_DENIED,
                context.checkSelfPermission(Manifest.permission.RECORD_AUDIO));
        assertEquals("Gallery accounting must keep camera permission denied", PackageManager.PERMISSION_DENIED,
                context.checkSelfPermission(Manifest.permission.CAMERA));
    }
}
