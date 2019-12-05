package com.example.findalot;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.view.Gravity;
import android.widget.TextView;
import android.util.Log;
import android.content.Intent;
import android.os.Bundle;

import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


public class MainActivity extends AppCompatActivity {
    private FirebaseAuth mAuth;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;

    private static final String TAG = "MainActivity";
    FirebaseFirestore db = FirebaseFirestore.getInstance();
    private TextView locationText;
    private TextView floor1Spots;
    private TextView floor2Spots;
    private TextView floor3Spots;


    private double userLatitude = 0.0, userLongitude = 0.0;
    private boolean isContinue = false;
    private boolean isGPS = false;
    private int numOpenSpots = 0;
    List<Long> openSpots = new ArrayList<>();
    List<Long> openSpotIDs = new ArrayList<>();

    private StringBuilder stringBuilder;
    private Button logout_btn;
    private Button parked_btn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        floor1Spots = findViewById(R.id.container1);
        floor1Spots.setGravity(Gravity.CENTER);
        floor2Spots = findViewById(R.id.container2);
        floor2Spots.setGravity(Gravity.CENTER);
        floor3Spots = findViewById(R.id.container3);
        floor3Spots.setGravity(Gravity.CENTER);


        locationText = findViewById(R.id.welcomeMsg);
        parked_btn = findViewById(R.id.parkedBtn);
        logout_btn = findViewById(R.id.logoutBtn);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(1 * 1000); // 1 seconds
        locationRequest.setFastestInterval(1 * 1000); // 1 seconds

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
                        userLatitude = location.getLatitude();
                        userLongitude = location.getLongitude();
                        if (!isContinue) {
                            locationText.setText(String.format(Locale.US, "Lat: %s\nLong: %s", userLatitude, userLongitude));
                        } else {
                            if(locationText.getText().length() > 1)
                                stringBuilder.setLength(0);

                            stringBuilder.append("Lat: ");
                            stringBuilder.append(userLatitude);
                            stringBuilder.append("\nLong: ");
                            stringBuilder.append(userLongitude);
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
            checkSpot();
//            Log.d(TAG, "Checking your spot homie");
        });

        logout_btn.setOnClickListener(v -> {
            Log.d(TAG, "Logging out");
            signOut();
        });
    }


    @Override
    public void onStart() {
        super.onStart();
        // Check if user is signed in (non-null) and update UI accordingly.
        FirebaseUser currentUser = mAuth.getCurrentUser();
        String uid = currentUser.getUid();
        getLocation();
        for (int x = 1; x <= 3; x++)
            getOpenSpots(x);
//        Toast.makeText(this, "UID: " + uid, Toast.LENGTH_SHORT).show();

    }

    private void updateSpot(String id) {
        Map<String, Object> map = new HashMap<>();
        map.put("isTaken",false);

        db.collection("Spots").document(id).update(map);

        Log.d(TAG, "SPOT UPDATED => " + db.collection("Spots").document(id));

    }


    private void getOpenSpots(int floor) {
        db.collection("Spots")
                .whereEqualTo("isTaken", false)
                .whereEqualTo("floor", floor)
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @RequiresApi(api = Build.VERSION_CODES.N)
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
//                            openSpots.clear();
//
//                            for (QueryDocumentSnapshot document : task.getResult()) {
////                                Log.d(TAG, "SUCCESS BRO: " + document.getId() + " => " + document.getData());
//                                openSpots.add((Long) document.getData().get("spotNum"));
//                            }
//                            Collections.sort(openSpots);
                            numOpenSpots = task.getResult().size();

                            if(floor == 1) {
                                floor1Spots.setText(numOpenSpots + " open spots");
//                                openSpots.forEach(spot->  {
//                                    floor1Spots.append(" , " + spot);
//                                });
                            } else if(floor == 2)
                                floor2Spots.setText(numOpenSpots + " open spots");
                            else
                                floor3Spots.setText(numOpenSpots + " open spots");

                        } else {
                            Log.d(TAG, "Error getting documents: ", task.getException());
                        }
                    }
                });
    }

    private void checkSpot() {

        Map<String, Object> currUserLocation = new HashMap<>();
        currUserLocation.put("latitude",userLatitude);
        currUserLocation.put("longitude",userLongitude);
        Toast.makeText(this, currUserLocation.toString(), Toast.LENGTH_SHORT).show();

        db.collection("Spots")
                .whereEqualTo("location", currUserLocation)
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @RequiresApi(api = Build.VERSION_CODES.N)
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                Log.d(TAG, "SUCCESS BRO: " + document.getId() + " => " + document.getData());
                                updateSpot(document.getId());
                                Log.d(TAG, "UPDATING SPOT: " + document.getData().get("spotNum"));
                                getOpenSpots(Math.toIntExact((Long) document.getData().get("floor")));
                            }
                        } else {
                            Log.d(TAG, "ERROR GETTING  DOCUMENTS ", task.getException());
                        }
                    }
                });
    }

    private void getLocation() {
        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    AppConstants.LOCATION_REQUEST);

        } else {
            if (isContinue) {
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
            } else {
                fusedLocationClient.getLastLocation().addOnSuccessListener(MainActivity.this, location -> {
                    if (location != null) {
                        userLatitude = location.getLatitude();
                        userLongitude = location.getLongitude();
                        locationText.setText(String.format(Locale.US, "%s , %s", userLatitude, userLongitude));
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
                        fusedLocationClient.getLastLocation().addOnSuccessListener(MainActivity.this, location -> {
                            if (location != null) {
                                userLatitude = location.getLatitude();
                                userLongitude = location.getLongitude();
                                locationText.setText(String.format(Locale.US, "%s - %s", userLatitude, userLongitude));
                                Toast.makeText(this, "Location Updated", Toast.LENGTH_SHORT).show();
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
