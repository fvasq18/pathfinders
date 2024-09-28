package unco.edu.pathfinders;

import android.os.Bundle;
import android.util.Log;
import android.widget.SearchView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.maps.android.PolyUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private LatLng location1;
    private LatLng location2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map_fragment);
        mapFragment.getMapAsync(this);

        // SearchView for entering two locations
        SearchView searchView = findViewById(R.id.search_view);
        searchView.setQueryHint("Enter two locations separated by a comma");

        // Handle search query submission
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                String[] locations = query.split(",");
                if (locations.length == 2) {
                    String location1Str = locations[0].trim();
                    String location2Str = locations[1].trim();

                    // Geocode both locations
                    geocodeLocation(location1Str, 1);
                    geocodeLocation(location2Str, 2);
                } else {
                    Toast.makeText(MainActivity.this, "Please enter two locations separated by a comma.", Toast.LENGTH_LONG).show();
                }
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });

        // Zoom In and Zoom Out Buttons
        findViewById(R.id.btn_zoom_in).setOnClickListener(v -> {
            if (mMap != null) {
                mMap.animateCamera(CameraUpdateFactory.zoomIn());
            }
        });

        findViewById(R.id.btn_zoom_out).setOnClickListener(v -> {
            if (mMap != null) {
                mMap.animateCamera(CameraUpdateFactory.zoomOut());
            }
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
    }

    // Geocode the entered location to get LatLng
    private void geocodeLocation(String location, int locationIndex) {
        try {
            // URL encode the location string
            String encodedLocation = URLEncoder.encode(location, StandardCharsets.UTF_8.toString());
            String urlString = "https://maps.googleapis.com/maps/api/geocode/json?address=" + encodedLocation + "&key=AIzaSyBZmGdCsZeGTn42o8lpbGlPk2a1_ukycr0";

            // Log the request URL for debugging
            Log.d("GeocodeRequest", "Request URL: " + urlString);

            new Thread(() -> {
                try {
                    URL url = new URL(urlString);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");

                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        response.append(line);
                    }
                    in.close();

                    // Log the raw API response for debugging
                    Log.d("GeocodeResponse", response.toString());

                    JSONObject jsonObject = new JSONObject(response.toString());
                    JSONArray results = jsonObject.getJSONArray("results");

                    if (results.length() > 0) {
                        JSONObject locationObject = results.getJSONObject(0).getJSONObject("geometry").getJSONObject("location");
                        LatLng latLng = new LatLng(locationObject.getDouble("lat"), locationObject.getDouble("lng"));

                        runOnUiThread(() -> {
                            if (locationIndex == 1) {
                                location1 = latLng;
                            } else {
                                location2 = latLng;
                            }

                            // If both locations are available, plot the route
                            if (location1 != null && location2 != null) {
                                plotRoute(location1, location2);
                            }
                        });
                    } else {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Address not found!", Toast.LENGTH_SHORT).show());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    // Function to plot the route between two locations
    private void plotRoute(LatLng origin, LatLng destination) {
        String urlString = "https://maps.googleapis.com/maps/api/directions/json?origin=" +
                origin.latitude + "," + origin.longitude + "&destination=" +
                destination.latitude + "," + destination.longitude + "&key=YOUR_API_KEY";

        new Thread(() -> {
            try {
                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
                in.close();

                // Parse the API response
                JSONObject jsonObject = new JSONObject(response.toString());
                String status = jsonObject.getString("status");

                // Check if the API found a route
                if (status.equals("OK")) {
                    JSONArray routes = jsonObject.getJSONArray("routes");
                    JSONObject route = routes.getJSONObject(0);
                    JSONObject overviewPolyline = route.getJSONObject("overview_polyline");
                    String encodedPolyline = overviewPolyline.getString("points");

                    runOnUiThread(() -> drawPolyline(encodedPolyline));
                } else {
                    // Handle no route found or other errors
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "No route found or error: " + status, Toast.LENGTH_LONG).show();
                    });
                    Log.e("DirectionsAPI", "Error: " + status);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }


    // Decode polyline and draw it on the map
    private void drawPolyline(String encodedPolyline) {
        List<LatLng> decodedPath = PolyUtil.decode(encodedPolyline);
        PolylineOptions polylineOptions = new PolylineOptions().color(0xFF0000FF).width(8);
        for (LatLng latLng : decodedPath) {
            polylineOptions.add(latLng);
        }
        mMap.addPolyline(polylineOptions);

        // Automatically adjust the zoom and camera to show the entire route
        LatLngBounds bounds = LatLngBounds.builder()
                .include(location1)  // Include the first location
                .include(location2)  // Include the second location
                .build();

        // Animate the camera to fit both locations and the route on the map
        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));  // Padding of 100
    }
}
