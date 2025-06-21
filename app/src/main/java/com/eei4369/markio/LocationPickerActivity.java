package com.eei4369.markio;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.content.Intent;
import android.widget.Toast;
import androidx.annotation.NonNull;
import java.util.Locale;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

public class LocationPickerActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap; // Google Map instance
    private LatLng selectedLatLng; // Stores the coordinates of the location selected by the user

    // UI elements from the layout
    private MaterialButton buttonSelectLocation;
    // MaterialToolbar toolbar

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_picker); // Connects to this activity's layout

        // Initialize the toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbarLocationPicker);
        setSupportActionBar(toolbar); // Set this toolbar as the activity's action bar

        // Configure the toolbar: show a back button and set title
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true); // Show back arrow
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle("Pick Location"); // Title for this screen
        }
        toolbar.setNavigationOnClickListener(v -> finish()); // What happens when back button is clicked

        // Initialize our buttons
        buttonSelectLocation = findViewById(R.id.buttonSelectLocation);
        MaterialButton buttonCancelLocation = findViewById(R.id.buttonCancelLocation);

        // Set up click listeners for the buttons
        buttonSelectLocation.setOnClickListener(v -> returnSelectedLocation()); // When select button is clicked
        buttonCancelLocation.setOnClickListener(v -> finish()); // When cancel button is clicked

        // Get the map fragment from the layout and get notified when the map is ready
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this); // 'this' means onMapReady will be called when map is ready
        }
    }

    /** Called when the Google Map is fully loaded and ready to be used. */
    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap; // Assign the ready map to our mMap variable

        // Set an initial default location for the map (center of Sri Lanka)
        LatLng defaultLocation = new LatLng(7.8731, 80.7718);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 8f)); // Move camera to default location with zoom

        // selected location is default
        selectedLatLng = defaultLocation;
        mMap.addMarker(new MarkerOptions().position(selectedLatLng).title("Selected Location")); // Place a marker

        // Get initial coordinates from the intent (if passed from AddEditBookmarkActivity)
        // Showing where the bookmark was already located.
        double initialLatitude = getIntent().getDoubleExtra("initial_latitude", 0.0);
        double initialLongitude = getIntent().getDoubleExtra("initial_longitude", 0.0);

        if (initialLatitude != 0.0 || initialLongitude != 0.0) {
            // If an initial location was provided, move the map and place a marker there
            LatLng initialLatLng = new LatLng(initialLatitude, initialLongitude);
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(initialLatLng, 15)); // Zoom closer to this specific spot
            mMap.clear(); // Remove any default markers
            mMap.addMarker(new MarkerOptions().position(initialLatLng).title("Initial Location"));
            selectedLatLng = initialLatLng; // Set this as the initially selected location
        }


        // Set a listener for simple map clicks to change the selected location
        mMap.setOnMapClickListener(latLng -> {
            mMap.clear(); // Remove any previous markers
            mMap.addMarker(new MarkerOptions().position(latLng).title("Selected Location")); // Add new marker
            selectedLatLng = latLng; // Update our stored selected coordinates
        });

        // Set a listener for long map presses (can also be used for selecting location)
        mMap.setOnMapLongClickListener(latLng -> {
            mMap.clear(); // Remove any previous markers
            mMap.addMarker(new MarkerOptions().position(latLng).title("Selected Location (Long Press)"));
            selectedLatLng = latLng;
        });
    }

    /** Sends the chosen latitude and longitude back to the previous activity (AddEditBookmarkActivity). */
    private void returnSelectedLocation() {
        if (selectedLatLng != null) {
            Intent resultIntent = new Intent(); // Create a new intent to send data back
            resultIntent.putExtra("latitude", selectedLatLng.latitude); // Put latitude
            resultIntent.putExtra("longitude", selectedLatLng.longitude); // Put longitude
            setResult(RESULT_OK, resultIntent); // Indicate that the selection was successful
            finish(); // Close this LocationPickerActivity
        } else {
            Toast.makeText(this, "No location selected.", Toast.LENGTH_SHORT).show();
            setResult(RESULT_CANCELED); // Indicate that the selection was cancelled
            finish();
        }
    }
}