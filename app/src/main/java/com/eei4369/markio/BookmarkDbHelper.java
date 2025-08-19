package com.eei4369.markio;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.eei4369.markio.BookmarkContract.BookmarkEntry;

// Helper class for managing database creation and version management.
public class BookmarkDbHelper extends SQLiteOpenHelper {

    public static final int DATABASE_VERSION = 2;
    public static final String DATABASE_NAME = "Markio.db";

    // SQL statement to create the bookmarks table.
    private static final String SQL_CREATE_ENTRIES =
            "CREATE TABLE " + BookmarkEntry.TABLE_NAME + " (" +
                    BookmarkEntry._ID + " INTEGER PRIMARY KEY," +
                    BookmarkEntry.COLUMN_NAME_TITLE + " TEXT," +
                    BookmarkEntry.COLUMN_NAME_NOTES + " TEXT," +
                    BookmarkEntry.COLUMN_NAME_CONTENT_TYPE + " TEXT," +
                    BookmarkEntry.COLUMN_NAME_CONTENT_URI + " TEXT," +
                    BookmarkEntry.COLUMN_NAME_LINK_URL + " TEXT," +
                    BookmarkEntry.COLUMN_NAME_GEOGRAPHIC_LOCATION + " TEXT," +
                    BookmarkEntry.COLUMN_NAME_TIMESTAMP + " INTEGER," +
                    BookmarkEntry.COLUMN_NAME_TAGS + " TEXT DEFAULT ''" +
                    ")";

    // SQL statement to delete the bookmarks table.
    private static final String SQL_DELETE_ENTRIES =
            "DROP TABLE IF EXISTS " + BookmarkEntry.TABLE_NAME;

    public BookmarkDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    // Called when the database is created for the first time.
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_ENTRIES);
    }

    // Called when the database needs to be upgraded.
    // for handling schema changes without losing data.
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {


        if (oldVersion < 2) {

            // Add the new 'tags' column to the existing table
            db.execSQL("ALTER TABLE " + BookmarkEntry.TABLE_NAME +
                    " ADD COLUMN " + BookmarkEntry.COLUMN_NAME_TAGS + " TEXT DEFAULT ''");
        }

    }

    // Called when the database needs to be downgraded.
    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        onUpgrade(db, oldVersion, newVersion);
    }
}