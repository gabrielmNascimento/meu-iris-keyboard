package nabu.iris.keyboard.latin;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import nabu.iris.keyboard.R;

public final class ClipboardPhraseImportActivity extends Activity {
    private static final String TAG = "ClipboardPhraseImport";
    private static final int REQUEST_IMPORT_FILE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        try {
            startActivityForResult(intent, REQUEST_IMPORT_FILE);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "No document picker is available", e);
            Toast.makeText(this, R.string.clipboard_import_picker_unavailable,
                    Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_IMPORT_FILE) {
            return;
        }
        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            importFile(data.getData());
        }
        finish();
    }

    private void importFile(Uri uri) {
        List<String> phrases = new ArrayList<>();
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new IOException("Unable to open selected file");
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String phrase = line.trim();
                    if (!phrase.isEmpty()) {
                        phrases.add(phrase);
                    }
                }
            }
        } catch (IOException | SecurityException e) {
            Log.e(TAG, "Unable to import saved phrases", e);
            Toast.makeText(this, R.string.clipboard_import_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        if (phrases.isEmpty()) {
            Toast.makeText(this, R.string.clipboard_import_no_phrases, Toast.LENGTH_SHORT).show();
            return;
        }

        ClipboardHistoryManager manager = new ClipboardHistoryManager(this);
        int addedCount = manager.addSavedPhrases(phrases);
        sendBroadcast(new Intent(ClipboardBarController.ACTION_SAVED_PHRASES_UPDATED)
                .setPackage(getPackageName()));
        Toast.makeText(this, getResources().getQuantityString(
                R.plurals.clipboard_import_success, addedCount, addedCount), Toast.LENGTH_SHORT)
                .show();
    }
}
