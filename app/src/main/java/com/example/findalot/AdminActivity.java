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
import android.widget.Button;
import android.widget.TextView;
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
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Locale;

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



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();
        locationText = findViewById(R.id.locationText);
        parked_btn = findViewById(R.id.addSpot_btn);
        logout_btn = findViewById(R.id.logout_btn);

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
            getLocation();
        });
        logout_btn.setOnClickListener(v -> {
            Log.d(TAG, "Logging out");
            signOut();
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
        String uid = currentUser.getUid();

        if(uid != "wDQeIigGkEWNotUInU6IDT7wOXe2")
            loginActivity();
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
