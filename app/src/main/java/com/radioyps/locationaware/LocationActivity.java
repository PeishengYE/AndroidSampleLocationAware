/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.radioyps.locationaware;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


import com.example.android.location.R;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class LocationActivity extends AppCompatActivity implements OnMapReadyCallback{
    private static final String TAG = LocationActivity.class.getSimpleName();
    private TextView mLatLng;
    private TextView mAddress;
    private TextView mHistoryInfo;
    private Button mFineProviderButton;
    private Button mBothProviderButton;
    private LocationManager mLocationManager;
    private Handler mHandler;
    private boolean mGeocoderAvailable;
    private boolean mUseFine;
    private boolean mUseBoth;

    // Keys for maintaining UI states after rotation.
    private static final String KEY_FINE = "use_fine";
    private static final String KEY_BOTH = "use_both";
    // UI handler codes.
    private static final int UPDATE_ADDRESS = 1;
    private static final int UPDATE_HISTORY_INFO = 2;

    private static final int TEN_SECONDS = 10000;
    private static final int TEN_METERS = 10;
    private static final int TWO_MINUTES = 1000 * 60 * 2;
    private final String LOCATION_SAVING_FILE = "address.all";
    private StringBuilder stringBuilder = null;


    private double mLocationLatitude =45.4222399;
    private double mLocationLongtitude = -73.91762163;
    private int distance = 0;
    private  static  final  int MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 0x12;
       /**
     * This sample demonstrates how to incorporate location based services in your app and
     * process location updates.  The app also shows how to convert lat/long coordinates to
     * human-readable addresses.
     */
    @SuppressLint("NewApi")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        stringBuilder = new StringBuilder();

        // Restore apps state (if exists) after rotation.
        if (savedInstanceState != null) {
            mUseFine = savedInstanceState.getBoolean(KEY_FINE);
            mUseBoth = savedInstanceState.getBoolean(KEY_BOTH);
        } else {
            mUseFine = false;
            mUseBoth = false;
        }
        mLatLng = (TextView) findViewById(R.id.latlng);
        mAddress = (TextView) findViewById(R.id.label_address);
        mHistoryInfo = (TextView) findViewById(R.id.historyInfo);
        // Receive location updates from the fine location provider (gps) only.
        mFineProviderButton = (Button) findViewById(R.id.provider_fine);
        // Receive location updates from both the fine (gps) and coarse (network) location
        // providers.
        mBothProviderButton = (Button) findViewById(R.id.provider_both);

        mLocationLatitude = getPreferredLocationlatitude(this);
        mLocationLongtitude = getPreferredLocationLongitude(this);

        // The isPresent() helper method is only available on Gingerbread or above.
        mGeocoderAvailable =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD && Geocoder.isPresent();

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

            } else {

                // No explanation needed, we can request the permission.

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);

                // MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        }

        // Handler for updating text fields on the UI like the lat/long and address.
        mHandler = new Handler() {
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case UPDATE_ADDRESS:
                        mAddress.setText((String) msg.obj);
                        break;
                    case UPDATE_HISTORY_INFO:
                        mLatLng.setText((String) msg.obj);
                        updateHistoryInfo((String) msg.obj);
                        break;
                }
            }
        };
        // Get a reference to the LocationManager object.
        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        // Get the SupportMapFragment and request notification
        // when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map_fragment_container);
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {

        // and move the map's camera to the same location.
        LatLng sydney = new LatLng(mLocationLatitude, mLocationLongtitude);
        googleMap.addMarker(new MarkerOptions().position(sydney)
                .title("Marker Primonics"));
        googleMap.moveCamera(CameraUpdateFactory.newLatLng(sydney));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // Restores UI states after rotation.
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_FINE, mUseFine);
        outState.putBoolean(KEY_BOTH, mUseBoth);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mLocationLatitude = getPreferredLocationlatitude(this);
        mLocationLongtitude = getPreferredLocationLongitude(this);
        setup();
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Check if the GPS setting is currently enabled on the device.
        // This verification should be done during onStart() because the system calls this method
        // when the user returns to the activity, which ensures the desired location provider is
        // enabled each time the activity resumes from the stopped state.
        LocationManager locationManager =
                (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        final boolean gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

        if (!gpsEnabled) {
            // Build an alert dialog here that requests that the user enable
            // the location services, then when the user clicks the "OK" button,
            // call enableLocationSettings()
            new EnableGpsDialogFragment().show(getSupportFragmentManager(), "enableGpsDialog");
        }
    }

    // Method to launch Settings
    private void enableLocationSettings() {
        Intent settingsIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        startActivity(settingsIntent);
    }

    // Stop receiving location updates whenever the Activity becomes invisible.
    @Override
    protected void onStop() {
        super.onStop();
       // mLocationManager.removeUpdates(listener);
    }

    @Override
    protected void onDestroy() {
        mLocationManager.removeUpdates(listener);
        super.onDestroy();
    }

    // Set up fine and/or coarse location providers depending on whether the fine provider or
    // both providers button is pressed.
    private void setup() {
        Location gpsLocation = null;
        Location networkLocation = null;
        mLocationManager.removeUpdates(listener);
        mLatLng.setText(R.string.unknown);
        mAddress.setText(R.string.unknown);
        // Get fine location updates only.
        if (mUseFine) {
            mFineProviderButton.setBackgroundResource(R.drawable.button_active);
            mBothProviderButton.setBackgroundResource(R.drawable.button_inactive);
            // Request updates from just the fine (gps) provider.
            gpsLocation = requestUpdatesFromProvider(
                    LocationManager.GPS_PROVIDER, R.string.not_support_gps);
            // Update the UI immediately if a location is obtained.
            if (gpsLocation != null) updateUILocation(gpsLocation);
        } else if (mUseBoth) {
            // Get coarse and fine location updates.
            mFineProviderButton.setBackgroundResource(R.drawable.button_inactive);
            mBothProviderButton.setBackgroundResource(R.drawable.button_active);
            // Request updates from both fine (gps) and coarse (network) providers.
            gpsLocation = requestUpdatesFromProvider(
                    LocationManager.GPS_PROVIDER, R.string.not_support_gps);
            networkLocation = requestUpdatesFromProvider(
                    LocationManager.NETWORK_PROVIDER, R.string.not_support_network);

            // If both providers return last known locations, compare the two and use the better
            // one to update the UI.  If only one provider returns a location, use it.
            if (gpsLocation != null && networkLocation != null) {
                updateUILocation(getBetterLocation(gpsLocation, networkLocation));
            } else if (gpsLocation != null) {
                updateUILocation(gpsLocation);
            } else if (networkLocation != null) {
                updateUILocation(networkLocation);
            }
        }
    }



    /**
     * Method to register location updates with a desired location provider.  If the requested
     * provider is not available on the device, the app displays a Toast with a message referenced
     * by a resource id.
     *
     * @param provider Name of the requested provider.
     * @param errorResId Resource id for the string message to be displayed if the provider does
     *                   not exist on the device.
     * @return A previously returned {@link android.location.Location} from the requested provider,
     *         if exists.
     */
    private Location requestUpdatesFromProvider(final String provider, final int errorResId) {
        Location location = null;
        if (mLocationManager.isProviderEnabled(provider)) {
            mLocationManager.requestLocationUpdates(provider, TEN_SECONDS, TEN_METERS, listener);
            location = mLocationManager.getLastKnownLocation(provider);
        } else {
            Toast.makeText(this, errorResId, Toast.LENGTH_LONG).show();
        }
        return location;
    }

    // Callback method for the "fine provider" button.
    public void useFineProvider(View v) {
        mUseFine = true;
        mUseBoth = false;
        setup();
    }

    // Callback method for the "both providers" button.
    public void useCoarseFineProviders(View v) {
        mUseFine = false;
        mUseBoth = true;
        setup();
    }

    private void doReverseGeocoding(Location location) {
        // Since the geocoding API is synchronous and may take a while.  You don't want to lock
        // up the UI thread.  Invoking reverse geocoding in an AsyncTask.
        (new ReverseGeocodingTask(this)).execute(new Location[] {location});
    }

    private void vibrating(String text){
        long[] pattern = MorseCodeConverter.pattern(text);

        // Start the vibration
        Vibrator vibrator = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
        vibrator.vibrate(pattern, -1);

    }

    private void updateUILocation(Location location) {
        float[] results = new float[1];

        Location.distanceBetween(mLocationLongtitude, mLocationLatitude,location.getLatitude(), location.getLongitude(), results );
        System.out.println("Distance is: " + results[0]);
        distance = Math.round(results[0]);

        if(distance < 2000){
            vibrating("abcd");
        }

       // String tmpLocation = location.getLatitude() + ", " + location.getLongitude() + ", " + distance;
        String tmpLocation = "Distance to Primonic: " + distance;
        // We're sending the update to a handler which then updates the UI with the new
        // location.
        Message.obtain(mHandler,
                UPDATE_HISTORY_INFO,tmpLocation
                ).sendToTarget();

        Log.d(TAG, "updateUILocation() >> get current location: " + tmpLocation);
        saveLocations(location);
        // Bypass reverse-geocoding only if the Geocoder service is available on the device.
//        if (mGeocoderAvailable) doReverseGeocoding(location);
    }

    private final LocationListener listener = new LocationListener() {

        @Override
        public void onLocationChanged(Location location) {
            // A new location update is received.  Do something useful with it.  Update the UI with
            // the location update.
            updateUILocation(location);
        }

        @Override
        public void onProviderDisabled(String provider) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }
    };

    /** Determines whether one Location reading is better than the current Location fix.
      * Code taken from
      * http://developer.android.com/guide/topics/location/obtaining-user-location.html
      *
      * @param newLocation  The new Location that you want to evaluate
      * @param currentBestLocation  The current Location fix, to which you want to compare the new
      *        one
      * @return The better Location object based on recency and accuracy.
      */
    protected Location getBetterLocation(Location newLocation, Location currentBestLocation) {
        if (currentBestLocation == null) {
            // A new location is always better than no location
            return newLocation;
        }

        // Check whether the new location fix is newer or older
        long timeDelta = newLocation.getTime() - currentBestLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
        boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
        boolean isNewer = timeDelta > 0;

        // If it's been more than two minutes since the current location, use the new location
        // because the user has likely moved.
        if (isSignificantlyNewer) {
            return newLocation;
        // If the new location is more than two minutes older, it must be worse
        } else if (isSignificantlyOlder) {
            return currentBestLocation;
        }

        // Check whether the new location fix is more or less accurate
        int accuracyDelta = (int) (newLocation.getAccuracy() - currentBestLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;

        // Check if the old and new location are from the same provider
        boolean isFromSameProvider = isSameProvider(newLocation.getProvider(),
                currentBestLocation.getProvider());

        // Determine location quality using a combination of timeliness and accuracy
        if (isMoreAccurate) {
            return newLocation;
        } else if (isNewer && !isLessAccurate) {
            return newLocation;
        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
            return newLocation;
        }
        return currentBestLocation;
    }

    /** Checks whether two providers are the same */
    private boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) {
          return provider2 == null;
        }
        return provider1.equals(provider2);
    }

    // AsyncTask encapsulating the reverse-geocoding API.  Since the geocoder API is blocked,
    // we do not want to invoke it from the UI thread.
    private class ReverseGeocodingTask extends AsyncTask<Location, Void, Void> {
        Context mContext;

        public ReverseGeocodingTask(Context context) {
            super();
            mContext = context;
        }

        @Override
        protected Void doInBackground(Location... params) {
            Geocoder geocoder = new Geocoder(mContext, Locale.getDefault());

            Location loc = params[0];
            List<Address> addresses = null;
            try {
                addresses = geocoder.getFromLocation(loc.getLatitude(), loc.getLongitude(), 1);
            } catch (IOException e) {
                e.printStackTrace();
                // Update address field with the exception.
                Message.obtain(mHandler, UPDATE_ADDRESS, e.toString()).sendToTarget();
            }
            if (addresses != null && addresses.size() > 0) {
                Address address = addresses.get(0);
                // Format the first line of address (if available), city, and country name.
                String addressText = String.format("%s, %s, %s",
                        address.getMaxAddressLineIndex() > 0 ? address.getAddressLine(0) : "",
                        address.getLocality(),
                        address.getCountryName());
                // Update address field on UI.
                Message.obtain(mHandler, UPDATE_ADDRESS, addressText).sendToTarget();
            }
            return null;
        }
    }

    /**
     * Dialog to prompt users to enable GPS on the device.
     */
    private class EnableGpsDialogFragment extends DialogFragment {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.enable_gps)
                    .setMessage(R.string.enable_gps_dialog)
                    .setPositiveButton(R.string.enable_gps, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            enableLocationSettings();
                        }
                    })
                    .create();
        }
    }


private boolean saveLocations(Location location){
    Boolean ret = false;
	FileOutputStream outputStream;
	StringBuilder tmp = new StringBuilder();
    tmp.append(location.getLatitude() + ", " + location.getLongitude());
	try {
	  outputStream = openFileOutput(LOCATION_SAVING_FILE, Context.MODE_APPEND);
	  outputStream.write(tmp.toString().getBytes());
	  outputStream.close();
        ret = true;
	} catch (Exception e) {
	  e.printStackTrace();
	}
    return ret;
}

   public boolean isExternalStorageWritable() {
    String state = Environment.getExternalStorageState();
    if (Environment.MEDIA_MOUNTED.equals(state)) {
        return true;
    }
    return false;
}

    private void updateHistoryInfo(String info){
        String formattedDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Calendar.getInstance().getTime());
        stringBuilder.append(info);
        stringBuilder.append(": ");
        stringBuilder.append(formattedDate);
        stringBuilder.append("\n");
        mHistoryInfo.setText(stringBuilder.toString());
    }

    public static double getPreferredLocationLongitude(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String tmp = prefs.getString(context.getString(R.string.pref_location_1_longitude_key),
                context.getString(R.string.pref_location_longitude_primonics));
        return Double.parseDouble(tmp);
    }


    public static double getPreferredLocationlatitude(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String tmp = prefs.getString(context.getString(R.string.pref_location_1_latitude_key),
                context.getString(R.string.pref_location_latitude_primonics));
        return Double.parseDouble(tmp);
    }
}
