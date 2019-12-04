package com.example.findalot;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AdminActivity extends AppCompatActivity {
    private FirebaseAuth mAuth;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;

    private static final String TAG = "MainActivity";
    FirebaseFirestore db = FirebaseFirestore.getInstance();
    private TextView locationText;
    private Button parked_btn;
    private Button logout_btn;
    private double wayLatitude = 0.0, wayLongitude = 0.0;
    private boolean isContinue = false;
    private boolean isGPS = false;
    private StringBuilder stringBuilder;
    public Integer spinnerVal = 0;
    private int floorCount = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();
        locationText = findViewById(R.id.locationText);
        parked_btn = findViewById(R.id.addSpot_btn);
        logout_btn = findViewById(R.id.logout_btn);
        Spinner spinner = findViewById(R.id.floors_spinner);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                Object spinnerVal = Integer.parseInt(parent.getItemAtPosition(pos).toString());
                setSpinnerVal(Integer.parseInt(parent.getItemAtPosition(pos).toString()));
                Toast.makeText(AdminActivity.this, spinnerVal + " <--",
                        Toast.LENGTH_SHORT).show();
            }
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.floors_array, android.R.layout.simple_spinner_item);
        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        spinner.setAdapter(adapter);


        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
//        locationRequest.setInterval(10 * 1000); // 10 seconds
//        locationRequest.setFastestInterval(5 * 1000); // 5 seconds

        new GpsUtils(this).turnGPSOn(isGPSEnable -> {
            // turn on GPS
            isGPS = isGPSEnable;
        });

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }
                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        wayLatitude = location.getLatitude();
                        wayLongitude = location.getLongitude();
                        if (!isContinue) {
                            locationText.setText(String.format(Locale.US, "Lat: %s\nLong: %s", wayLatitude, wayLongitude));
                        } else {
                            stringBuilder.append("Lat: ");
                            stringBuilder.append(wayLatitude);
                            stringBuilder.append("\nLong: ");
                            stringBuilder.append(wayLongitude);
                            stringBuilder.append("\n\n");
                            locationText.setText(stringBuilder.toString());
                        }
                        if (!isContinue && fusedLocationClient != null) {
                            fusedLocationClient.removeLocationUpdates(locationCallback);
                        }
                    }
                }
            }
        };
        parked_btn.setOnClickListener(v -> {
            isContinue = true;
            stringBuilder = new StringBuilder();
            getFloorCount(spinnerVal);
        });
        logout_btn.setOnClickListener(v -> {
            Log.d(TAG, "Logging out");
            signOut();
        });
    }
    private void setSpinnerVal(int value) {
        spinnerVal = value;
    }
    private void getFloorCount(int floor) {
        floorCount = 0;
        db.collection("Spots")
                .whereEqualTo("floor", floor)
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                Log.d(TAG, document.getId() + " => " + document.getData());
                            }
                            floorCount=task.getResult().size();
                            addSpot(floor);
                            Toast.makeText(AdminActivity.this, floorCount + " --> finished async",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Log.d(TAG, "Error getting documents: ", task.getException());
                        }
                    }
                });
    }
    private void incrementCount() {
        floorCount++;

    }

    private void addSpot(int floor) {
        Toast.makeText(AdminActivity.this, floorCount + " / " + wayLongitude + " / " + wayLatitude,
                Toast.LENGTH_SHORT).show();

        Map<String, Object> coordinates = new HashMap<>();
        coordinates.put("latitude",wayLatitude);
        coordinates.put("longitude",wayLongitude);

        Map<String, Object> spot = new HashMap<>();
        spot.put("floor", floor);
        spot.put("isTaken", false);
        spot.put("location", coordinates);
        spot.put("number", floorCount+1);
        spot.put("spotHolder", null);

        // Add a new document with a generated ID
        db.collection("Spots")
                .add(spot)
                .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                    @Override
                    public void onSuccess(DocumentReference documentReference) {
                        Log.d(TAG, "DocumentSnapshot added with ID: " + documentReference.getId());
                        Toast.makeText(AdminActivity.this, "Spot successfully added :) ",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "Error adding document", e);
                    }
                });
    }

    private void getLocation() {
        if (ActivityCompat.checkSelfPermission(AdminActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(AdminActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(AdminActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    AppConstants.LOCATION_REQUEST);

        } else {
            if (isContinue) {
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
            } else {
                fusedLocationClient.getLastLocation().addOnSuccessListener(AdminActivity.this, location -> {
                    if (location != null) {
                        wayLatitude = location.getLatitude();
                        wayLongitude = location.getLongitude();
                        locationText.setText(String.format(Locale.US, "%s - %s", wayLatitude, wayLongitude));
                    } else {
                        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
                    }
                });
            }
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 1000: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    if (isContinue) {
                        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
                    } else {
                        fusedLocationClient.getLastLocation().addOnSuccessListener(AdminActivity.this, location -> {
                            if (location != null) {
                                wayLatitude = location.getLatitude();
                                wayLongitude = location.getLongitude();
                                locationText.setText(String.format(Locale.US, "%s - %s", wayLatitude, wayLongitude));
                            } else {
                                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
                            }
                        });
                    }
                } else {
                    Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
                }
                break;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == AppConstants.GPS_REQUEST) {
                isGPS = true; // flag maintain before get location
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        // Check if user is signed in (non-null) and update UI accordingly.
        FirebaseUser currentUser = mAuth.getCurrentUser();
//        String user = currentUser;
        getLocation();
//        if(user != "wDQeIigGkEWNotUInU6IDT7wOXe2")
//            loginActivity();
    }

    public void signOut() {
        // [START auth_sign_out]
        mAuth.getInstance().signOut();
        Log.d(TAG, "Successfully signed out");
        loginActivity();
        // [END auth_sign_out]
    }
    private void loginActivity() {
        Intent i = new Intent(this, LoginActivity.class);
        startActivity(i);
    }

}
