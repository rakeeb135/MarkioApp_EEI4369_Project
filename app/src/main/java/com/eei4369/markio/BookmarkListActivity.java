package com.eei4369.markio;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.google.android.material.appbar.MaterialToolbar;
import com.eei4369.markio.BookmarkContract.BookmarkEntry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Arrays; // Needed for Arrays.toString when debugging (removed debug logs now)

public class BookmarkListActivity extends AppCompatActivity implements BookmarkAdapter.OnBookmarkClickListener {

    private BookmarkDbHelper dbHelper;
    private BookmarkAdapter bookmarkAdapter;
    private List<Bookmark> bookmarkList;
    private MaterialToolbar toolbar; // Toolbar reference
    private String currentFilterType = null; // Filter for content type (image, link, document)
    private String currentTagFilter = null; // Filter by a specific tag
    private String currentSearchQuery = null; // Current search term

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bookmark_list);

        dbHelper = new BookmarkDbHelper(this);

        toolbar = findViewById(R.id.toolbarBookmarkList);
        setSupportActionBar(toolbar);
        // Enable back button on toolbar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish()); // Handle back button click

        RecyclerView recyclerView = findViewById(R.id.recyclerViewBookmarks);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        bookmarkList = new ArrayList<>();
        bookmarkAdapter = new BookmarkAdapter(this, bookmarkList, this);
        recyclerView.setAdapter(bookmarkAdapter);

        // Check for filters or search query passed from another activity
        if (getIntent().hasExtra("filter_type")) {
            currentFilterType = getIntent().getStringExtra("filter_type");
        }
        if (getIntent().hasExtra("search_query")) {
            currentSearchQuery = getIntent().getStringExtra("search_query");
        }
        if (getIntent().hasExtra("tag_filter")) {
            currentTagFilter = getIntent().getStringExtra("tag_filter");
        }

        updateToolbarTitle(); // Set toolbar title based on filters/search
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadBookmarks(); // Reload bookmarks whenever activity resumes
    }

    // Updates the toolbar title to reflect active filters or search.
    private void updateToolbarTitle() {
        String title = "My Bookmarks";
        if (currentFilterType != null) {
            if ("image".equals(currentFilterType)) {
                title = "Images";
            } else if ("link".equals(currentFilterType)) {
                title = "Links";
            } else if ("document".equals(currentFilterType)) {
                title = "Documents";
            }
        }
        if (currentTagFilter != null && !currentTagFilter.isEmpty()) {
            title = "Tag: #" + currentTagFilter;
        }
        if (currentSearchQuery != null && !currentSearchQuery.isEmpty()) {
            title = "Searching: '" + currentSearchQuery + "'";
        }
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title);
        }
    }

    // Loads bookmarks from the database, applying current filters and search.
    private void loadBookmarks() {
        bookmarkList.clear();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        // Columns to retrieve from the database
        String[] projection = {
                BookmarkEntry._ID,
                BookmarkEntry.COLUMN_NAME_TITLE,
                BookmarkEntry.COLUMN_NAME_NOTES,
                BookmarkEntry.COLUMN_NAME_CONTENT_TYPE,
                BookmarkEntry.COLUMN_NAME_CONTENT_URI,
                BookmarkEntry.COLUMN_NAME_LINK_URL,
                BookmarkEntry.COLUMN_NAME_GEOGRAPHIC_LOCATION,
                BookmarkEntry.COLUMN_NAME_TIMESTAMP,
                BookmarkEntry.COLUMN_NAME_TAGS
        };

        // Build SQL WHERE clause components dynamically
        List<String> selectionParts = new ArrayList<>();
        List<String> selectionArgs = new ArrayList<>();

        if (currentFilterType != null) {
            if ("image".equals(currentFilterType) || "link".equals(currentFilterType)) {
                selectionParts.add(BookmarkEntry.COLUMN_NAME_CONTENT_TYPE + " = ?");
                selectionArgs.add(currentFilterType);
            } else if ("document".equals(currentFilterType)) {
                // Filter for content types that are NOT image or link
                selectionParts.add(BookmarkEntry.COLUMN_NAME_CONTENT_TYPE + " != ? AND " + BookmarkEntry.COLUMN_NAME_CONTENT_TYPE + " != ?");
                selectionArgs.add("image");
                selectionArgs.add("link");
            }
        }

        if (currentTagFilter != null && !currentTagFilter.isEmpty()) {
            // Search for tags containing the filter (case-insensitive)
            selectionParts.add(BookmarkEntry.COLUMN_NAME_TAGS + " LIKE ?");
            selectionArgs.add("%" + currentTagFilter.toLowerCase(Locale.getDefault()) + "%");
        }

        if (currentSearchQuery != null && !currentSearchQuery.isEmpty()) {
            String lowerCaseQuery = currentSearchQuery.toLowerCase(Locale.getDefault());
            // Search in title, notes, or tags
            String likeClause = " (" + BookmarkEntry.COLUMN_NAME_TITLE + " LIKE ? OR " +
                    BookmarkEntry.COLUMN_NAME_NOTES + " LIKE ? OR " +
                    BookmarkEntry.COLUMN_NAME_TAGS + " LIKE ?) ";
            selectionParts.add(likeClause);
            String searchQueryPattern = "%" + lowerCaseQuery + "%";
            selectionArgs.add(searchQueryPattern);
            selectionArgs.add(searchQueryPattern);
            selectionArgs.add(searchQueryPattern);
        }

        String finalSelection = null;
        String[] finalSelectionArgs = null;

        if (!selectionParts.isEmpty()) {
            finalSelection = TextUtils.join(" AND ", selectionParts); // Combine multiple conditions with AND
            finalSelectionArgs = selectionArgs.toArray(new String[0]); // Convert list to array
        }

        String sortOrder = BookmarkEntry.COLUMN_NAME_TIMESTAMP + " DESC"; // Sort by timestamp, newest first

        Cursor cursor = db.query(
                BookmarkEntry.TABLE_NAME,
                projection,
                finalSelection,
                finalSelectionArgs,
                null, null,
                sortOrder
        );

        if (cursor != null) {
            try {
                // Iterate through cursor results and create Bookmark objects
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(cursor.getColumnIndexOrThrow(BookmarkEntry._ID));
                    String title = cursor.getString(cursor.getColumnIndexOrThrow(BookmarkEntry.COLUMN_NAME_TITLE));
                    String notes = cursor.getString(cursor.getColumnIndexOrThrow(BookmarkEntry.COLUMN_NAME_NOTES));
                    String contentType = cursor.getString(cursor.getColumnIndexOrThrow(BookmarkEntry.COLUMN_NAME_CONTENT_TYPE));
                    String contentUri = cursor.getString(cursor.getColumnIndexOrThrow(BookmarkEntry.COLUMN_NAME_CONTENT_URI));
                    String linkUrl = cursor.getString(cursor.getColumnIndexOrThrow(BookmarkEntry.COLUMN_NAME_LINK_URL));
                    String geographicLocation = cursor.getString(cursor.getColumnIndexOrThrow(BookmarkEntry.COLUMN_NAME_GEOGRAPHIC_LOCATION));
                    long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(BookmarkEntry.COLUMN_NAME_TIMESTAMP));
                    String tags = cursor.getString(cursor.getColumnIndexOrThrow(BookmarkEntry.COLUMN_NAME_TAGS));

                    String readableAddress = "Location: Not available";
                    if (geographicLocation != null && !geographicLocation.isEmpty() && !geographicLocation.equals("0.000000,0.000000")) {
                        try {
                            String[] latLon = geographicLocation.split(",");
                            double lat = Double.parseDouble(latLon[0]);
                            double lon = Double.parseDouble(latLon[1]);
                            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                            List<Address> addresses = geocoder.getFromLocation(lat, lon, 1);
                            if (addresses != null && !addresses.isEmpty()) {
                                Address returnedAddress = addresses.get(0);
                                StringBuilder strReturnedAddress = new StringBuilder();
                                for (int i = 0; i <= returnedAddress.getMaxAddressLineIndex(); i++) {
                                    strReturnedAddress.append(returnedAddress.getAddressLine(i)).append("\n");
                                }
                                String addressOnly = strReturnedAddress.toString().trim();

                                // Format address to be more readable
                                if (addressOnly.isEmpty() || addressOnly.split(",").length <= 1) {
                                    readableAddress = "Location: " + addressOnly + " (" + geographicLocation + ")";
                                } else {
                                    readableAddress = "Location: " + addressOnly;
                                }
                            } else {
                                readableAddress = "Location: " + geographicLocation + " (No address found)";
                            }
                        } catch (IOException | NumberFormatException e) {
                            // Error geocoding location; display raw coordinates
                            readableAddress = "Location: " + geographicLocation + " (Error)";
                        }
                    }

                    bookmarkList.add(new Bookmark(id, title, notes, contentType, contentUri, linkUrl, geographicLocation, readableAddress, timestamp, tags));
                }
            } finally {
                cursor.close(); // Always close the cursor
            }
        }
        bookmarkAdapter.setBookmarkList(bookmarkList); // Update RecyclerView adapter
        if (bookmarkList.isEmpty()) {
            Toast.makeText(this, "No bookmarks found for criteria.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);

        // Set up search functionality in the toolbar
        MenuItem searchItem = menu.findItem(R.id.action_search);
        final SearchView searchView = (SearchView) searchItem.getActionView();
        if (searchView != null) {
            searchView.setQueryHint("Search in this list...");
            searchView.setSubmitButtonEnabled(true);
            // Pre-fill search query if coming from another activity
            if (currentSearchQuery != null && !currentSearchQuery.isEmpty()) {
                searchView.setQuery(currentSearchQuery, false);
                searchItem.expandActionView();
            }
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    if (!TextUtils.isEmpty(query)) {
                        currentSearchQuery = query;
                        currentFilterType = null; // Clear other filters on new search
                        currentTagFilter = null;
                        updateToolbarTitle();
                        loadBookmarks(); // Reload with search query
                    } else {
                        // If search query is cleared, reset filters and reload
                        if (currentSearchQuery != null && !currentSearchQuery.isEmpty()) {
                            currentSearchQuery = null;
                            currentFilterType = null;
                            currentTagFilter = null;
                            updateToolbarTitle();
                            loadBookmarks();
                            Toast.makeText(BookmarkListActivity.this, "Search cleared.", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(BookmarkListActivity.this, "Enter a search term.", Toast.LENGTH_SHORT).show();
                        }
                    }
                    searchView.clearFocus(); // Hide keyboard
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

    // Handles click on a bookmark item. Opens content or link.
    @Override
    public void onBookmarkClick(long id, String contentType, String contentUri, String linkUrl) {
        Intent intent = null;
        Uri uri = null;

        if ("link".equals(contentType) && linkUrl != null && !linkUrl.isEmpty()) {
            try {
                uri = Uri.parse(linkUrl);
                // Prepend http:// if scheme is missing
                if (uri.getScheme() == null || uri.getScheme().isEmpty()) {
                    uri = Uri.parse("http://" + linkUrl);
                }
                intent = new Intent(Intent.ACTION_VIEW, uri); // Open link in browser
            } catch (Exception e) {
                Toast.makeText(this, "Invalid link: " + linkUrl, Toast.LENGTH_LONG).show();
                return;
            }
        } else if (contentUri != null && !contentUri.isEmpty()) {
            uri = Uri.parse(contentUri);
            intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); // Grant temporary read access
        }

        // Try to open the intent
        if (intent != null && intent.resolveActivity(getPackageManager()) != null) {
            try {
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Could not open content. " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        } else {
            // Fallback: if content cannot be viewed, open for edit
            Toast.makeText(this, "Cannot view content directly. Opening for edit.", Toast.LENGTH_SHORT).show();
            onBookmarkLongClick(id);
        }
    }

    // Handles long click on a bookmark item. Opens AddEditBookmarkActivity for editing.
    @Override
    public void onBookmarkLongClick(long id) {
        Intent intent = new Intent(BookmarkListActivity.this, AddEditBookmarkActivity.class);
        intent.putExtra("bookmark_id", id); // Pass bookmark ID for editing
        startActivity(intent);
        Toast.makeText(this, "Editing bookmark...", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) {
            dbHelper.close(); // Close database connection to prevent leaks
        }
    }
}