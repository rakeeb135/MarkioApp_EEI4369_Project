package com.eei4369.markio;

import android.provider.BaseColumns;

// Defines the table and column names for the bookmarks database.
public final class BookmarkContract {
    // To prevent accidental instantiation, make the constructor private.
    private BookmarkContract() {}

    /* Inner class that defines the table contents */
    public static class BookmarkEntry implements BaseColumns {
        public static final String TABLE_NAME = "bookmarks";
        public static final String COLUMN_NAME_TITLE = "title";
        public static final String COLUMN_NAME_NOTES = "notes";
        public static final String COLUMN_NAME_CONTENT_TYPE = "content_type";
        public static final String COLUMN_NAME_CONTENT_URI = "content_uri";
        public static final String COLUMN_NAME_LINK_URL = "link_url";
        public static final String COLUMN_NAME_GEOGRAPHIC_LOCATION = "geographic_location";
        public static final String COLUMN_NAME_TIMESTAMP = "timestamp";
        public static final String COLUMN_NAME_TAGS = "tags";
    }
}