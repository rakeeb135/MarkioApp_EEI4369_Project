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
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AddEditBookmarkActivity extends AppCompatActivity {

    // Unique request codes for managing permissions
    private static final int PERMISSION_REQUEST_CODE_CAMERA_STORAGE = 101;
    private static final int PERMISSION_REQUEST_CODE_LOCATION = 102;

    // UI components
    private TextInputEditText editTextTitle, editTextNotes, editTextLinkUrl, editTextTags;
    private ImageView imageViewContentPreview, imageViewLocationMapPreview;
    private TextView textViewLocation;

    // Database helper for managing bookmark data
    private BookmarkDbHelper dbHelper;
    private long bookmarkId = -1; // -1 indicates a new bookmark, otherwise it's an existing ID

    // Data to be saved
    private Uri currentContentUri = null; // URI for attached image or file
    private String currentGeographicLocation = ""; // Stores "latitude,longitude"
    private String currentAddress = "Location: Not selected"; // Readable street address

    // Google Maps API key obtained from manifest
    private String googleMapsApiKey = "";

    // Launchers for activities that return a result (camera, file picker, map)
    private ActivityResultLauncher<Uri> cameraLauncher;
    private ActivityResultLauncher<String[]> filePickerLauncher;
    private ActivityResultLauncher<Intent> locationPickerLauncher;

    // Client for getting the device's last known location
    private FusedLocationProviderClient fusedLocationClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_bookmark);

        // Initialize database and location clients
        dbHelper = new BookmarkDbHelper(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Retrieve the Google Maps API key from the app's manifest file
        try {
            Bundle metaData = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA).metaData;
            if (metaData != null) {
                googleMapsApiKey = metaData.getString("com.google.android.geo.API_KEY");
            }
        } catch (PackageManager.NameNotFoundException ignored) {
            // The API key won't be available, so the static map preview won't work.
        }

        // Initialize all UI elements by their IDs
        MaterialToolbar toolbar = findViewById(R.id.toolbarAddEdit);
        editTextTitle = findViewById(R.id.editTextTitle);
        editTextNotes = findViewById(R.id.editTextNotes);
        editTextTags = findViewById(R.id.editTextTags);
        editTextLinkUrl = findViewById(R.id.editTextLinkUrl);
        imageViewContentPreview = findViewById(R.id.imageViewContentPreview);
        imageViewLocationMapPreview = findViewById(R.id.imageViewLocationMapPreview);
        textViewLocation = findViewById(R.id.textViewLocation);
        textViewLocation.setText(currentAddress);

        // Initialize and set up click listeners for all buttons
        MaterialButton buttonTakePhoto = findViewById(R.id.buttonTakePhoto);
        MaterialButton buttonSelectFile = findViewById(R.id.buttonSelectFile);
        MaterialButton buttonPickLocation = findViewById(R.id.buttonPickLocation);
        MaterialButton buttonSaveBookmark = findViewById(R.id.buttonSaveBookmark);
        MaterialButton buttonDeleteBookmark = findViewById(R.id.buttonDeleteBookmark);
        MaterialButton buttonCancel = findViewById(R.id.buttonCancel);

        buttonTakePhoto.setOnClickListener(v -> checkCameraStoragePermissions());
        buttonSelectFile.setOnClickListener(v -> selectFile());
        buttonPickLocation.setOnClickListener(v -> launchLocationPicker());
        buttonSaveBookmark.setOnClickListener(v -> saveOrUpdateBookmark());
        buttonDeleteBookmark.setOnClickListener(v -> deleteBookmark());
        buttonCancel.setOnClickListener(v -> finish());

        // Set up the toolbar with a back button to exit the activity
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        // Set up the launchers for receiving results from other activities
        setupActivityResultsLaunchers();

        // Check if we are editing an existing bookmark or creating a new one
        if (getIntent().hasExtra("bookmark_id")) {
            bookmarkId = getIntent().getLongExtra("bookmark_id", -1);
            toolbar.setTitle("Edit Bookmark");
            buttonDeleteBookmark.setVisibility(View.VISIBLE);
            loadBookmarkData(bookmarkId);
        } else {
            toolbar.setTitle("Add Bookmark");
            buttonDeleteBookmark.setVisibility(View.GONE);
            checkLocationPermissionsAndFetch(); // Get the current device location for a new bookmark
        }
    }

    /**
     * Sets up the launchers for receiving results from the camera, file picker, and location picker.
     */
    private void setupActivityResultsLaunchers() {
        // Handles the result from the camera. If a photo was taken, display a preview.
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

        // Handles the result from the file picker. If a file was selected, display a preview.
        filePickerLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) {
                currentContentUri = uri;
                Toast.makeText(this, "File selected!", Toast.LENGTH_SHORT).show();
                displayContentPreview(currentContentUri);
                // Persist read permissions so the app can access the file later
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                Toast.makeText(this, "File selection cancelled or failed.", Toast.LENGTH_SHORT).show();
                currentContentUri = null;
                imageViewContentPreview.setVisibility(View.GONE);
            }
        });

        // Handles the result from the location picker map activity.
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
            }
        });
    }

    /**
     * Fetches an existing bookmark from the database and populates the UI for editing.
     *
     * @param id The ID of the bookmark to load.
     */
    private void loadBookmarkData(long id) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String[] projection = {
                BookmarkEntry.COLUMN_NAME_TITLE, BookmarkEntry.COLUMN_NAME_NOTES,
                BookmarkEntry.COLUMN_NAME_CONTENT_URI, BookmarkEntry.COLUMN_NAME_LINK_URL,
                BookmarkEntry.COLUMN_NAME_GEOGRAPHIC_LOCATION, BookmarkEntry.COLUMN_NAME_TAGS
        };
        Cursor cursor = db.query(BookmarkEntry.TABLE_NAME, projection, BookmarkEntry._ID + " = ?",
                new String[]{String.valueOf(id)}, null, null, null);

        if (cursor != null && cursor.moveToFirst()) {
            try {
                // Populate text fields
                editTextTitle.setText(cursor.getString(cursor.getColumnIndexOrThrow(BookmarkEntry.COLUMN_NAME_TITLE)));
                editTextNotes.setText(cursor.getString(cursor.getColumnIndexOrThrow(BookmarkEntry.COLUMN_NAME_NOTES)));
                editTextTags.setText(cursor.getString(cursor.getColumnIndexOrThrow(BookmarkEntry.COLUMN_NAME_TAGS)));

                String linkUrl = cursor.getString(cursor.getColumnIndexOrThrow(BookmarkEntry.COLUMN_NAME_LINK_URL));
                if (!TextUtils.isEmpty(linkUrl)) editTextLinkUrl.setText(linkUrl);

                // Load content preview if a URI exists
                String contentUriStr = cursor.getString(cursor.getColumnIndexOrThrow(BookmarkEntry.COLUMN_NAME_CONTENT_URI));
                if (!TextUtils.isEmpty(contentUriStr)) {
                    currentContentUri = Uri.parse(contentUriStr);
                    displayContentPreview(currentContentUri);
                }

                // Load location data and display the map preview
                currentGeographicLocation = cursor.getString(cursor.getColumnIndexOrThrow(BookmarkEntry.COLUMN_NAME_GEOGRAPHIC_LOCATION));
                if (!TextUtils.isEmpty(currentGeographicLocation) && !currentGeographicLocation.equals("0.000000,0.000000")) {
                    String[] latLon = currentGeographicLocation.split(",");
                    if (latLon.length == 2) {
                        double latitude = Double.parseDouble(latLon[0]);
                        double longitude = Double.parseDouble(latLon[1]);
                        geocodeLocationAndSetText(latitude, longitude);
                        displayStaticMap(currentGeographicLocation);
                    }
                } else {
                    currentAddress = "Location: Not selected";
                    textViewLocation.setText(currentAddress);
                    imageViewLocationMapPreview.setVisibility(View.GONE);
                }
            } catch (Exception e) {
                // Handle any errors during data retrieval and parsing
                Toast.makeText(this, "Error loading bookmark data.", Toast.LENGTH_LONG).show();
            } finally {
                cursor.close();
            }
        } else {
            Toast.makeText(this, "Bookmark not found!", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    /**
     * Checks for necessary camera and storage permissions before launching the camera.
     */
    private void checkCameraStoragePermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CODE_CAMERA_STORAGE);
        } else {
            launchCamera();
        }
    }

    /**
     * Starts the camera app to take a photo. A temporary file is created to store the image.
     */
    private void launchCamera() {
        File photoFile = null;
        try {
            photoFile = createImageFile();
        } catch (IOException ex) {
            Toast.makeText(this, "Error creating photo file.", Toast.LENGTH_LONG).show();
            return;
        }
        if (photoFile != null) {
            // Get a URI for the temporary file using FileProvider
            currentContentUri = FileProvider.getUriForFile(this, "com.eei4369.markio.fileprovider", photoFile);
            cameraLauncher.launch(currentContentUri);
        }
    }

    /**
     * Creates an empty image file in the app's private pictures directory.
     *
     * @return The newly created file.
     * @throws IOException if the file cannot be created.
     */
    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (storageDir == null) throw new IOException("No external pictures directory.");
        return File.createTempFile("JPEG_" + timeStamp + "_", ".jpg", storageDir);
    }

    /**
     * Launches the system file picker to allow the user to select various file types.
     */
    private void selectFile() {
        String[] mimeTypes = {
                "image/*", "video/*", "audio/*", "application/pdf",
                "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        };
        filePickerLauncher.launch(mimeTypes);
    }

    /**
     * Displays a visual preview of the selected content based on its MIME type.
     *
     * @param uri The URI of the content to display.
     */
    private void displayContentPreview(Uri uri) {
        String mimeType = getContentResolver().getType(uri);
        if (mimeType != null) {
            if (mimeType.startsWith("image/")) {
                Glide.with(this).load(uri).into(imageViewContentPreview);
            } else if (mimeType.startsWith("video/")) {
                imageViewContentPreview.setImageResource(android.R.drawable.ic_media_play);
            } else if (mimeType.startsWith("audio/")) {
                imageViewContentPreview.setImageResource(android.R.drawable.ic_lock_silent_mode_off);
            } else {
                imageViewContentPreview.setImageResource(android.R.drawable.ic_menu_agenda);
            }
        }
        imageViewContentPreview.setVisibility(View.VISIBLE);
    }

    /**
     * Handles the results of permission requests.
     */
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

    /**
     * Checks for location permissions and, if granted, calls the method to fetch the location.
     */
    private void checkLocationPermissionsAndFetch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_CODE_LOCATION);
        } else {
            fetchLastLocation();
        }
    }

    /**
     * Retrieves the last known device location and updates the UI.
     */
    @SuppressWarnings("MissingPermission")
    private void fetchLastLocation() {
        // This check is required even with the @SuppressWarnings annotation
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

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

    /**
     * Uses a Geocoder to convert latitude and longitude coordinates into a human-readable address.
     *
     * @param latitude  The latitude coordinate.
     * @param longitude The longitude coordinate.
     */
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
                currentAddress = "Location: " + addressOnly;
            } else {
                currentAddress = "Location: " + coordsString + " (No address found)";
            }
        } catch (IOException e) {
            currentAddress = "Location: " + coordsString + " (Network error)";
        }
        textViewLocation.setText(currentAddress);
    }

    /**
     * Starts the LocationPickerActivity to let the user select a location on a map.
     */
    private void launchLocationPicker() {
        Intent intent = new Intent(this, LocationPickerActivity.class);
        if (!TextUtils.isEmpty(currentGeographicLocation) && !currentGeographicLocation.equals("0.000000,0.000000")) {
            try {
                String[] latLon = currentGeographicLocation.split(",");
                intent.putExtra("initial_latitude", Double.parseDouble(latLon[0]));
                intent.putExtra("initial_longitude", Double.parseDouble(latLon[1]));
            } catch (NumberFormatException ignored) {
                // If parsing fails, the map will use a default location
            }
        }
        locationPickerLauncher.launch(intent);
    }

    /**
     * Loads a static map image from the Google Maps Static API and displays it as a preview.
     *
     * @param geoCoordinates The "latitude,longitude" string for the map marker.
     */
    private void displayStaticMap(String geoCoordinates) {
        if (TextUtils.isEmpty(geoCoordinates) || TextUtils.isEmpty(googleMapsApiKey) || geoCoordinates.equals("0.000000,0.000000")) {
            imageViewLocationMapPreview.setVisibility(View.GONE);
            return;
        }

        String[] latLon = geoCoordinates.split(",");
        if (latLon.length != 2) {
            imageViewLocationMapPreview.setVisibility(View.GONE);
            return;
        }

        String staticMapUrl = String.format(Locale.getDefault(),
                "https://maps.googleapis.com/maps/api/staticmap?center=%s,%s&zoom=15&size=400x200&markers=color:red|label:L|%s,%s&key=%s",
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

    /**
     * Saves a new bookmark or updates an existing one in the database.
     */
    private void saveOrUpdateBookmark() {
        String title = editTextTitle.getText().toString().trim();
        String notes = editTextNotes.getText().toString().trim();
        String tags = editTextTags.getText().toString().trim();
        String linkUrl = editTextLinkUrl.getText().toString().trim();

        // Title is a required field
        if (TextUtils.isEmpty(title)) {
            editTextTitle.setError("Title cannot be empty.");
            return;
        }

        // Determine the content type based on the attached file or link
        String contentType = "note";
        if (currentContentUri != null) {
            String mimeType = getContentResolver().getType(currentContentUri);
            if (mimeType != null) {
                if (mimeType.startsWith("image/")) contentType = "image";
                else if (mimeType.startsWith("video/")) contentType = "video";
                else if (mimeType.startsWith("audio/")) contentType = "audio";
                else contentType = "document";
            } else {
                contentType = "document";
            }
        } else if (!TextUtils.isEmpty(linkUrl)) {
            contentType = "link";
            if (!linkUrl.startsWith("http://") && !linkUrl.startsWith("https://")) {
                linkUrl = "http://" + linkUrl; // Prepend protocol for a valid URL
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
            // Insert a new bookmark
            long resultId = db.insert(BookmarkEntry.TABLE_NAME, null, values);
            if (resultId != -1) Toast.makeText(this, "Bookmark added!", Toast.LENGTH_SHORT).show();
            else Toast.makeText(this, "Error adding bookmark.", Toast.LENGTH_SHORT).show();
        } else {
            // Update an existing bookmark
            int rowsAffected = db.update(BookmarkEntry.TABLE_NAME, values, BookmarkEntry._ID + " = ?", new String[]{String.valueOf(bookmarkId)});
            if (rowsAffected > 0) Toast.makeText(this, "Bookmark updated!", Toast.LENGTH_SHORT).show();
            else Toast.makeText(this, "Error updating bookmark.", Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    /**
     * Deletes the current bookmark from the database.
     */
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

    /**
     * Closes the database helper to prevent memory leaks when the activity is destroyed.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}