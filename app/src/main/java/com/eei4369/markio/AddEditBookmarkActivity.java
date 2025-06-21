package com.eei4369.markio;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.eei4369.markio.BookmarkContract.BookmarkEntry;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AddEditBookmarkActivity extends AppCompatActivity {

    // Request codes for permissions
    private static final int PERMISSION_REQUEST_CODE_CAMERA_STORAGE = 101;
    private static final int PERMISSION_REQUEST_CODE_LOCATION = 102;

    // Google Maps API Key
    private String googleMapsApiKey = "";

    // Content and location data
    private Uri currentContentUri = null; // URI for attached image/file
    private String currentGeographicLocation = ""; // Stores "latitude,longitude"
    private String currentAddress = "Location: Not selected"; // Readable address

    // UI elements
    private TextInputEditText editTextTitle, editTextNotes, editTextLinkUrl, editTextTags;
    private ImageView imageViewContentPreview, imageViewLocationMapPreview;
    private TextView textViewLocation;

    private BookmarkDbHelper dbHelper;
    private long bookmarkId = -1; // -1 for new bookmark, otherwise existing ID

    // Activity launchers for various tasks
    private ActivityResultLauncher<Uri> cameraLauncher;
    private ActivityResultLauncher<String[]> filePickerLauncher;
    private ActivityResultLauncher<Intent> locationPickerLauncher;

    private FusedLocationProviderClient fusedLocationClient; // For device location

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_bookmark);

        dbHelper = new BookmarkDbHelper(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Try to get Google Maps API key from AndroidManifest.xml
        try {
            Bundle metaData = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA).metaData;
            if (metaData != null) {
                googleMapsApiKey = metaData.getString("com.google.android.geo.API_KEY");
            }
        } catch (PackageManager.NameNotFoundException ignored) {
            // Ignore if key not found, map preview just won't show
        }

        // Initialize UI components
        MaterialToolbar toolbar = findViewById(R.id.toolbarAddEdit);
        editTextTitle = findViewById(R.id.editTextTitle);
        editTextNotes = findViewById(R.id.editTextNotes);
        editTextTags = findViewById(R.id.editTextTags);
        editTextLinkUrl = findViewById(R.id.editTextLinkUrl);
        imageViewContentPreview = findViewById(R.id.imageViewContentPreview);
        imageViewLocationMapPreview = findViewById(R.id.imageViewLocationMapPreview);
        textViewLocation = findViewById(R.id.textViewLocation);
        textViewLocation.setText(currentAddress);

        // Buttons
        MaterialButton buttonTakePhoto = findViewById(R.id.buttonTakePhoto);
        MaterialButton buttonSelectFile = findViewById(R.id.buttonSelectFile);
        MaterialButton buttonPickLocation = findViewById(R.id.buttonPickLocation);
        MaterialButton buttonSaveBookmark = findViewById(R.id.buttonSaveBookmark);
        MaterialButton buttonDeleteBookmark = findViewById(R.id.buttonDeleteBookmark);
        MaterialButton buttonCancel = findViewById(R.id.buttonCancel);

        // Toolbar setup
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish()); // Back button closes activity

        // Set up button click listeners
        buttonTakePhoto.setOnClickListener(v -> checkCameraStoragePermissions());
        buttonSelectFile.setOnClickListener(v -> selectFile());
        buttonPickLocation.setOnClickListener(v -> launchLocationPicker());
        buttonSaveBookmark.setOnClickListener(v -> saveOrUpdateBookmark());
        buttonDeleteBookmark.setOnClickListener(v -> deleteBookmark());
        buttonCancel.setOnClickListener(v -> finish());

        setupActivityResultsLaunchers(); // Set up launchers for external activities

        // Load bookmark data if editing, otherwise try to get current location
        if (getIntent().hasExtra("bookmark_id")) {
            bookmarkId = getIntent().getLongExtra("bookmark_id", -1);
            toolbar.setTitle("Edit Bookmark");
            buttonDeleteBookmark.setVisibility(View.VISIBLE);
            loadBookmarkData(bookmarkId);
        } else {
            toolbar.setTitle("Add Bookmark");
            buttonDeleteBookmark.setVisibility(View.GONE);
            checkLocationPermissionsAndFetch(); // Get current device location for new bookmarks
        }
    }

    /** Sets up launchers for camera, file picker, and location picker results. */
    private void setupActivityResultsLaunchers() {
        // Handle photo capture result
        cameraLauncher = registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
            if (success) {
                Toast.makeText(this, "Photo taken!", Toast.LENGTH_SHORT).show();
                displayContentPreview(currentContentUri);
            } else {
                Toast.makeText(this, "Photo cancelled or failed.", Toast.LENGTH_SHORT).show();
                currentContentUri = null;
                imageViewContentPreview.setVisibility(View.GONE);
            }
        });

        // Handle file selection result
        filePickerLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) {
                currentContentUri = uri;
                Toast.makeText(this, "File selected!", Toast.LENGTH_SHORT).show();
                displayContentPreview(currentContentUri);
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                Toast.makeText(this, "File selection cancelled or failed.", Toast.LENGTH_SHORT).show();
                currentContentUri = null;
                imageViewContentPreview.setVisibility(View.GONE);
            }
        });

        // Handle location picker activity result
        locationPickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                double latitude = result.getData().getDoubleExtra("latitude", 0.0);
                double longitude = result.getData().getDoubleExtra("longitude", 0.0);
                currentGeographicLocation = String.format(Locale.getDefault(), "%.6f,%.6f", latitude, longitude);
                geocodeLocationAndSetText(latitude, longitude);
                displayStaticMap(currentGeographicLocation);
                Toast.makeText(this, "Location selected from map!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Location selection cancelled.", Toast.LENGTH_SHORT).show();
                // Fallback to current device location if map selection was cancelled
                if (currentGeographicLocation.isEmpty() || currentGeographicLocation.equals("0.000000,0.000000")) {
                    checkLocationPermissionsAndFetch();
                }
            }
        });
    }

    /** Loads bookmark data into UI for editing. */
    private void loadBookmarkData(long id) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String[] projection = {
                BookmarkEntry.COLUMN_NAME_TITLE, BookmarkEntry.COLUMN_NAME_NOTES,
                BookmarkEntry.COLUMN_NAME_CONTENT_URI, BookmarkEntry.COLUMN_NAME_LINK_URL,
                BookmarkEntry.COLUMN_NAME_GEOGRAPHIC_LOCATION, BookmarkEntry.COLUMN_NAME_TAGS
        };
        Cursor cursor = db.query(BookmarkEntry.TABLE_NAME, projection, BookmarkEntry._ID + " = ?",
                new String[]{String.valueOf(id)}, null, null, null);

        String geoLoc = ""; // Declared here for wider scope, initialized empty
        if (cursor != null && cursor.moveToFirst()) {
            try {
                editTextTitle.setText(cursor.getString(cursor.getColumnIndexOrThrow(BookmarkEntry.COLUMN_NAME_TITLE)));
                editTextNotes.setText(cursor.getString(cursor.getColumnIndexOrThrow(BookmarkEntry.COLUMN_NAME_NOTES)));
                editTextTags.setText(cursor.getString(cursor.getColumnIndexOrThrow(BookmarkEntry.COLUMN_NAME_TAGS)));

                String linkUrl = cursor.getString(cursor.getColumnIndexOrThrow(BookmarkEntry.COLUMN_NAME_LINK_URL));
                if (linkUrl != null && !linkUrl.isEmpty()) editTextLinkUrl.setText(linkUrl);

                String contentUriStr = cursor.getString(cursor.getColumnIndexOrThrow(BookmarkEntry.COLUMN_NAME_CONTENT_URI));
                if (contentUriStr != null && !contentUriStr.isEmpty()) {
                    currentContentUri = Uri.parse(contentUriStr);
                    displayContentPreview(currentContentUri);
                }

                geoLoc = cursor.getString(cursor.getColumnIndexOrThrow(BookmarkEntry.COLUMN_NAME_GEOGRAPHIC_LOCATION)); // Assigned value here
                if (geoLoc != null && !geoLoc.isEmpty() && !geoLoc.equals("0.000000,0.000000")) {
                    currentGeographicLocation = geoLoc;
                    String[] latLon = geoLoc.split(",");
                    geocodeLocationAndSetText(Double.parseDouble(latLon[0]), Double.parseDouble(latLon[1]));
                    displayStaticMap(currentGeographicLocation);
                } else {
                    currentGeographicLocation = "";
                    currentAddress = "Location: Not selected";
                    textViewLocation.setText(currentAddress);
                    imageViewLocationMapPreview.setVisibility(View.GONE);
                }
            } catch (NumberFormatException e) {
                // Now geoLoc is accessible here because it's declared outside the try
                currentAddress = "Location: " + geoLoc + " (Error)";
                textViewLocation.setText(currentAddress);
            } finally {
                cursor.close();
            }
        } else {
            Toast.makeText(this, "Bookmark not found!", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    /** Checks camera permission and launches camera if granted. */
    private void checkCameraStoragePermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CODE_CAMERA_STORAGE);
        } else {
            launchCamera();
        }
    }

    /** Launches camera app to take a photo. */
    private void launchCamera() {
        File photoFile = null;
        try {
            photoFile = createImageFile();
        } catch (IOException ex) {
            Toast.makeText(this, "Error creating photo file.", Toast.LENGTH_LONG).show();
            return;
        }
        if (photoFile != null) {
            currentContentUri = FileProvider.getUriForFile(this, "com.eei4369.markio.fileprovider", photoFile);
            cameraLauncher.launch(currentContentUri);
        }
    }

    /** Creates an empty image file for camera output. */
    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (storageDir == null) throw new IOException("No external pictures directory.");
        return File.createTempFile("JPEG_" + timeStamp + "_", ".jpg", storageDir);
    }

    /** Launches system file picker to select a file. */
    private void selectFile() {
        String[] mimeTypes = {
                "image/*", "application/pdf", "text/plain", "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-powerpoint", "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "audio/*", "video/*"
        };
        filePickerLauncher.launch(mimeTypes);
    }

    /** Displays a preview of the selected content (image, video, etc.). */
    private void displayContentPreview(Uri uri) {
        String mimeType = getContentResolver().getType(uri);
        if (mimeType != null && mimeType.startsWith("image/")) {
            Glide.with(this).load(uri).into(imageViewContentPreview);
        } else if (mimeType != null && mimeType.startsWith("video/")) {
            imageViewContentPreview.setImageResource(android.R.drawable.ic_media_play);
        } else if (mimeType != null && mimeType.startsWith("audio/")) {
            imageViewContentPreview.setImageResource(android.R.drawable.ic_lock_silent_mode_off);
        } else {
            imageViewContentPreview.setImageResource(android.R.drawable.ic_menu_agenda);
        }
        imageViewContentPreview.setVisibility(View.VISIBLE);
    }

    /** Handles permission request results (e.g., Camera, Location). */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE_CAMERA_STORAGE) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Camera permission granted!", Toast.LENGTH_SHORT).show();
                launchCamera();
            } else {
                Toast.makeText(this, "Camera permission denied.", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == PERMISSION_REQUEST_CODE_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Location permission granted!", Toast.LENGTH_SHORT).show();
                fetchLastLocation();
            } else {
                Toast.makeText(this, "Location permission denied.", Toast.LENGTH_LONG).show();
                currentAddress = "Location: Permission denied";
                textViewLocation.setText(currentAddress);
                imageViewLocationMapPreview.setVisibility(View.GONE);
            }
        }
    }

    /** Checks location permission and fetches last known location if granted. */
    private void checkLocationPermissionsAndFetch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_CODE_LOCATION);
        } else {
            fetchLastLocation();
        }
    }

    /** Fetches the last known device location. */
    @SuppressWarnings("MissingPermission")
    private void fetchLastLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if (location != null) {
                    double latitude = location.getLatitude();
                    double longitude = location.getLongitude();
                    currentGeographicLocation = String.format(Locale.getDefault(), "%.6f,%.6f", latitude, longitude);
                    geocodeLocationAndSetText(latitude, longitude);
                    displayStaticMap(currentGeographicLocation);
                } else {
                    Toast.makeText(AddEditBookmarkActivity.this, "Could not get device location. Turn on GPS.", Toast.LENGTH_LONG).show();
                    currentGeographicLocation = "";
                    currentAddress = "Location: Not available";
                    textViewLocation.setText(currentAddress);
                    imageViewLocationMapPreview.setVisibility(View.GONE);
                }
            }).addOnFailureListener(this, e -> {
                Toast.makeText(AddEditBookmarkActivity.this, "Error fetching location.", Toast.LENGTH_LONG).show();
                currentGeographicLocation = "";
                currentAddress = "Location: Error fetching";
                textViewLocation.setText(currentAddress);
                imageViewLocationMapPreview.setVisibility(View.GONE);
            });
        }
    }

    /** Converts lat/lon to readable address and updates UI. */
    private void geocodeLocationAndSetText(double latitude, double longitude) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        String coordsString = String.format(Locale.getDefault(), "%.6f,%.6f", latitude, longitude);
        currentGeographicLocation = coordsString;

        try {
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address returnedAddress = addresses.get(0);
                StringBuilder addressBuilder = new StringBuilder();
                for (int i = 0; i <= returnedAddress.getMaxAddressLineIndex(); i++) {
                    addressBuilder.append(returnedAddress.getAddressLine(i)).append("\n");
                }
                String addressOnly = addressBuilder.toString().trim();

                currentAddress = (addressOnly.isEmpty() || addressOnly.split(",").length <= 1) ?
                        "Location: " + addressOnly + " (" + coordsString + ")" : "Location: " + addressOnly;
            } else {
                currentAddress = "Location: " + coordsString + " (No address found)";
            }
        } catch (IOException e) {
            currentAddress = "Location: " + coordsString + " (Network error)";
        }
        textViewLocation.setText(currentAddress);
    }

    /** Launches LocationPickerActivity to select a location on map. */
    private void launchLocationPicker() {
        Intent intent = new Intent(this, LocationPickerActivity.class);
        if (!currentGeographicLocation.isEmpty() && !currentGeographicLocation.equals("0.000000,0.000000")) {
            try {
                String[] latLon = currentGeographicLocation.split(",");
                intent.putExtra("initial_latitude", Double.parseDouble(latLon[0]));
                intent.putExtra("initial_longitude", Double.parseDouble(latLon[1]));
            } catch (NumberFormatException ignored) {
                // If parsing fails, map picker will use default
            }
        }
        locationPickerLauncher.launch(intent);
    }

    /** Displays a static map preview using Google Maps Static API. */
    private void displayStaticMap(String geoCoordinates) {
        if (geoCoordinates == null || geoCoordinates.isEmpty() || googleMapsApiKey.isEmpty() || geoCoordinates.equals("0.000000,0.000000")) {
            imageViewLocationMapPreview.setVisibility(View.GONE);
            return;
        }

        String[] latLon = geoCoordinates.split(",");
        if (latLon.length != 2) {
            imageViewLocationMapPreview.setVisibility(View.GONE);
            return;
        }

        String staticMapUrl = String.format(Locale.getDefault(),
                "https://maps.googleapis.com/maps/api/staticmap?center=%s,%s&zoom=15&size=400x200&maptype=roadmap&markers=color:red|label:L|%s,%s&key=%s",
                latLon[0], latLon[1], latLon[0], latLon[1], googleMapsApiKey);

        Glide.with(this).load(staticMapUrl)
                .placeholder(android.R.drawable.ic_menu_mapmode)
                .error(android.R.drawable.ic_delete)
                .listener(new RequestListener<android.graphics.drawable.Drawable>() {
                    @Override
                    public boolean onLoadFailed(@androidx.annotation.Nullable GlideException e, Object model, Target<android.graphics.drawable.Drawable> target, boolean isFirstResource) {
                        imageViewLocationMapPreview.setVisibility(View.GONE);
                        Toast.makeText(AddEditBookmarkActivity.this, "Map preview not available.", Toast.LENGTH_LONG).show();
                        return false;
                    }
                    @Override
                    public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model, Target<android.graphics.drawable.Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        imageViewLocationMapPreview.setVisibility(View.VISIBLE);
                        return false;
                    }
                }).into(imageViewLocationMapPreview);
    }

    /** Saves a new bookmark or updates an existing one. */
    private void saveOrUpdateBookmark() {
        String title = editTextTitle.getText().toString().trim();
        String notes = editTextNotes.getText().toString().trim();
        String tags = editTextTags.getText().toString().trim();
        String linkUrl = editTextLinkUrl.getText().toString().trim();

        if (TextUtils.isEmpty(title)) {
            editTextTitle.setError("Title cannot be empty.");
            return;
        }

        String contentType = "note";
        if (currentContentUri != null) {
            String mimeType = getContentResolver().getType(currentContentUri);
            if (mimeType != null) {
                if (mimeType.startsWith("image/")) contentType = "image";
                else if (mimeType.startsWith("video/")) contentType = "video";
                else if (mimeType.startsWith("audio/")) contentType = "audio";
                else if (mimeType.contains("pdf")) contentType = "document";
                else contentType = "file";
            } else {
                contentType = "file";
            }
        } else if (!TextUtils.isEmpty(linkUrl)) {
            contentType = "link";
            if (!linkUrl.startsWith("http://") && !linkUrl.startsWith("https://")) {
                linkUrl = "http://" + linkUrl;
            }
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(BookmarkEntry.COLUMN_NAME_TITLE, title);
        values.put(BookmarkEntry.COLUMN_NAME_NOTES, notes);
        values.put(BookmarkEntry.COLUMN_NAME_CONTENT_TYPE, contentType);
        values.put(BookmarkEntry.COLUMN_NAME_CONTENT_URI, currentContentUri != null ? currentContentUri.toString() : null);
        values.put(BookmarkEntry.COLUMN_NAME_LINK_URL, linkUrl);
        values.put(BookmarkEntry.COLUMN_NAME_GEOGRAPHIC_LOCATION, currentGeographicLocation);
        values.put(BookmarkEntry.COLUMN_NAME_TIMESTAMP, System.currentTimeMillis());
        values.put(BookmarkEntry.COLUMN_NAME_TAGS, tags);

        if (bookmarkId == -1) {
            long resultId = db.insert(BookmarkEntry.TABLE_NAME, null, values);
            if (resultId != -1) Toast.makeText(this, "Bookmark added!", Toast.LENGTH_SHORT).show();
            else Toast.makeText(this, "Error adding bookmark.", Toast.LENGTH_SHORT).show();
        } else {
            int rowsAffected = db.update(BookmarkEntry.TABLE_NAME, values, BookmarkEntry._ID + " = ?", new String[]{String.valueOf(bookmarkId)});
            if (rowsAffected > 0) Toast.makeText(this, "Bookmark updated!", Toast.LENGTH_SHORT).show();
            else Toast.makeText(this, "Error updating bookmark.", Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    /** Deletes the current bookmark. */
    private void deleteBookmark() {
        if (bookmarkId == -1) {
            Toast.makeText(this, "Cannot delete non-existent bookmark.", Toast.LENGTH_SHORT).show();
            return;
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int rowsDeleted = db.delete(BookmarkEntry.TABLE_NAME, BookmarkEntry._ID + " = ?", new String[]{String.valueOf(bookmarkId)});

        if (rowsDeleted > 0) {
            Toast.makeText(this, "Bookmark deleted!", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            Toast.makeText(this, "Error deleting bookmark.", Toast.LENGTH_SHORT).show();
        }
    }

    /** Closes the database helper when activity is destroyed. */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}