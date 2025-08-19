package com.eei4369.markio;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.flexbox.FlexboxLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.eei4369.markio.BookmarkContract.BookmarkEntry;

import java.util.HashSet;
import java.util.Set;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private BookmarkDbHelper dbHelper;
    private TextView textViewBookmarkCount;
    private TextView textViewImageCount;
    private TextView textViewLinkCount;
    private TextView textViewDocumentCount;
    private FlexboxLayout flexboxLayoutTags;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new BookmarkDbHelper(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        textViewBookmarkCount = findViewById(R.id.textViewBookmarkCount);
        textViewImageCount = findViewById(R.id.textViewImageCount);
        textViewLinkCount = findViewById(R.id.textViewLinkCount);
        textViewDocumentCount = findViewById(R.id.textViewDocumentCount);
        flexboxLayoutTags = findViewById(R.id.flexboxLayoutTags);

        LinearLayout layoutAllBookmarks = findViewById(R.id.layoutAllBookmarks);
        LinearLayout layoutFilterImages = findViewById(R.id.layoutFilterImages);
        LinearLayout layoutFilterLinks = findViewById(R.id.layoutFilterLinks);
        LinearLayout layoutFilterDocuments = findViewById(R.id.layoutFilterDocuments);

        layoutAllBookmarks.setOnClickListener(v -> launchBookmarkListActivity(null, null, null));
        layoutFilterImages.setOnClickListener(v -> launchBookmarkListActivity("image", null, null));
        layoutFilterLinks.setOnClickListener(v -> launchBookmarkListActivity("link", null, null));
        layoutFilterDocuments.setOnClickListener(v -> launchBookmarkListActivity("document", null, null));

        FloatingActionButton fabAddBookmark = findViewById(R.id.fabAddBookmark);
        fabAddBookmark.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, AddEditBookmarkActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateBookmarkCounts();
    }

    // Updates bookmark counts and displays unique tags.
    private void updateBookmarkCounts() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        long allBookmarksCount = getCount(db, null, null);
        textViewBookmarkCount.setText(allBookmarksCount + " items");

        long imageCount = getCount(db, BookmarkEntry.COLUMN_NAME_CONTENT_TYPE, "image");
        textViewImageCount.setText(String.valueOf(imageCount));

        long linkCount = getCount(db, BookmarkEntry.COLUMN_NAME_CONTENT_TYPE, "link");
        textViewLinkCount.setText(String.valueOf(linkCount));

        long documentCount = getCount(db, BookmarkEntry.COLUMN_NAME_CONTENT_TYPE, "document_type_only");
        textViewDocumentCount.setText(String.valueOf(documentCount));

        loadAndDisplayTags(db);
    }

    // Counts bookmarks based on content type or other criteria.
    private long getCount(SQLiteDatabase db, String column, String value) {
        Cursor cursor = null;
        long count = 0;
        try {
            String selection = null;
            String[] selectionArgs = null;

            if (column != null && value != null) {
                if ("document_type_only".equals(value)) {
                    // Count items that are not 'image' and not 'link'.
                    selection = BookmarkEntry.COLUMN_NAME_CONTENT_TYPE + " != ? AND " +
                            BookmarkEntry.COLUMN_NAME_CONTENT_TYPE + " != ?";
                    selectionArgs = new String[]{"image", "link"};
                } else {
                    selection = column + " = ?";
                    selectionArgs = new String[]{value};
                }
            }

            cursor = db.query(BookmarkEntry.TABLE_NAME, new String[]{"COUNT(*)"}, selection, selectionArgs, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                count = cursor.getLong(0);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return count;
    }

    // Fetches and displays unique tags as clickable chips.
    private void loadAndDisplayTags(SQLiteDatabase db) {
        flexboxLayoutTags.removeAllViews(); // Clear existing chips

        Set<String> uniqueTags = new HashSet<>(); // Use a Set to ensure tags are unique
        Cursor cursor = null;
        try {
            cursor = db.query(BookmarkEntry.TABLE_NAME, new String[]{BookmarkEntry.COLUMN_NAME_TAGS}, null, null, null, null, null);

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String tagsString = cursor.getString(cursor.getColumnIndexOrThrow(BookmarkEntry.COLUMN_NAME_TAGS));
                    if (!TextUtils.isEmpty(tagsString)) {
                        String[] tagsArray = tagsString.split(",");
                        for (String tag : tagsArray) {
                            if (!TextUtils.isEmpty(tag.trim())) {
                                // Add cleaned, lowercase tag to the set.
                                uniqueTags.add(tag.trim().toLowerCase(Locale.getDefault()));
                            }
                        }
                    }
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        // Create a Chip for each unique tag.
        for (String tag : uniqueTags) {
            Chip chip = new Chip(this);
            chip.setText("#" + tag);
            chip.setClickable(true);
            chip.setFocusable(true);
            chip.setChipBackgroundColorResource(R.color.white);
            chip.setTextColor(ContextCompat.getColor(this, R.color.black));
            chip.setRippleColorResource(R.color.teal_200);
            chip.setOnClickListener(v -> launchBookmarkListActivity(null, null, tag)); // Filter list by this tag
            flexboxLayoutTags.addView(chip);
        }
    }

    // Launches BookmarkListActivity with specified filters or search query.
    private void launchBookmarkListActivity(String filterType, String searchQuery, String tagFilter) {
        Intent intent = new Intent(MainActivity.this, BookmarkListActivity.class);
        if (filterType != null) {
            intent.putExtra("filter_type", filterType);
        }
        if (searchQuery != null && !searchQuery.isEmpty()) {
            intent.putExtra("search_query", searchQuery);
        }
        if (tagFilter != null && !tagFilter.isEmpty()) {
            intent.putExtra("tag_filter", tagFilter);
        }
        startActivity(intent);
    }

    // Inflates menu items and sets up search functionality for the toolbar.
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        final SearchView searchView = (SearchView) searchItem.getActionView();
        if (searchView != null) {
            searchView.setQueryHint("Search bookmarks or tags...");
            searchView.setSubmitButtonEnabled(true);

            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    if (!TextUtils.isEmpty(query)) {
                        launchBookmarkListActivity(null, query, null);
                    } else {
                        Toast.makeText(MainActivity.this, "Enter a search term.", Toast.LENGTH_SHORT).show();
                    }
                    searchView.clearFocus(); // Hide keyboard
                    searchItem.collapseActionView(); // Collapse search bar
                    return true;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    return false; // Not performing search as text changes
                }
            });
        }
        return true;
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) {
            dbHelper.close(); // Close database connection
        }
    }
}