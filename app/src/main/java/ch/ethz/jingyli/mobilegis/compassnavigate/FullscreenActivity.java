package ch.ethz.jingyli.mobilegis.compassnavigate;

import android.Manifest;
import android.annotation.SuppressLint;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import com.github.capur16.digitspeedviewlib.DigitSpeedView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

/**
 * This activity....
 * @author Jingyan Li
 * @subject Mobile GIS and LBS --Assignment 1
 * @reference
 */
public class FullscreenActivity extends AppCompatActivity implements LocationListener, SensorEventListener {
    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 300;
    private final Handler mHideHandler = new Handler();
    private View mContentView;
    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar

            // Note that some of these constants are new as of API 16 (Jelly Bean)
            // and API 19 (KitKat). It is safe to use them, as they are inlined
            // at compile-time and do nothing on earlier devices.
            mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    };
    private View mControlsView;
    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.show();
            }
            mControlsView.setVisibility(View.VISIBLE);
        }
    };
    private boolean mVisible;
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };
    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    private final View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            switch (motionEvent.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (AUTO_HIDE) {
                        delayedHide(AUTO_HIDE_DELAY_MILLIS);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    view.performClick();
                    break;
                default:
                    break;
            }
            return false;
        }
    };

    /**
     * Set up some variables
     */
    private LocationManager locationManager;
    private SensorManager sensorManager;
    private Map<String, Double> lastDistToCenter;
    private Map<String, Geofence> checkpoints;  //Map<Geofence name, Geofence object>
    private Geofence currentCheckpoint;
    private final double GEOFENCE_RADIUS = 10.0; //unit: meter
    private final double ARRIVE_THRESHOLD = 4.0; //unit: meter

    private Trip currentTrip;
    private float currentTemperature = -100;
    private float currentAzimuthRotation = 0;
    private boolean inTrip=false;
    private float currentAngle = 0;

    private final String REWARD_FILE_PATH="user_records.csv";

    /**
     * Set up some widgets
     */
    private Spinner checkPointSpinner;
    private Button startBtn;
    private Button cancelBtn;
    private TextView checkpointText;
    // compass
    private ImageView image;
    private TextView compassAngle;
    private float currentDegree = .0f;
    // speed
    private DigitSpeedView digitSpeedView;
    // temperature
    private DigitSpeedView digitTempView;
    // distance
    private DigitSpeedView digitDistanceView;

    /**
     * Set up broadcast receiver
     */
    private BroadcastReceiver localProximityBroadcastReceiver;
    private BroadcastReceiver localLocationChangedBroadcastReceiver;
    private BroadcastReceiver proximityIntentReceiver;

    /**
     * Define intent action string
     */
    private static final String PROX_ALERT_INTENT =
            "ch.ethz.jingyli.mobilegis.compassnavigate.PROXIMITY_ALERT";
    private static final String ON_LOCATION_CHANGED_INTENT = "ch.ethz.jingyli.mobilegis.compassnavigate.ONLOCATIONCHANGED_ALERT";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_fullscreen);

        mVisible = true;
        mControlsView = findViewById(R.id.fullscreen_content_controls);
        mContentView = findViewById(R.id.fullscreen_content);

        // Set up the user interaction to manually show or hide the system UI.
        mContentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggle();
            }
        });

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        findViewById(R.id.dummy_button).setOnTouchListener(mDelayHideTouchListener);

        // Initiate widgets
        checkPointSpinner = (Spinner) findViewById(R.id.checkpoint_spinner);
        startBtn = (Button) findViewById(R.id.start_button);
        cancelBtn = (Button) findViewById(R.id.cancel_button);
        image = (ImageView)findViewById(R.id.imageViewCompass);
        compassAngle = (TextView)findViewById(R.id.compass_angle);
        digitSpeedView = (DigitSpeedView)findViewById(R.id.digit_speed_view);
        digitDistanceView = (DigitSpeedView)findViewById(R.id.digit_distance_view);
        digitTempView = (DigitSpeedView)findViewById(R.id.digit_temperature_view);
        checkpointText = (TextView)findViewById(R.id.checkpoint_selection_text);

        // Initiate variables
        lastDistToCenter = new HashMap<>();
        checkpoints = new HashMap<>();
        readCheckPoints();
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        // Set up spinner
        setUpSpinner();

        // Check location permission
        checkPermissions();
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        // External storage write permission
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);

        // Add broadcast receivers for proximity / location changed intents
        localProximityBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String name = intent.getStringExtra("name");
                boolean entering = intent.getBooleanExtra(LocationManager.KEY_PROXIMITY_ENTERING, false);
                if (entering){
                    Toast.makeText(context, "You almost arrive at "+name, Toast.LENGTH_LONG).show();
                }else{
                    Toast.makeText(context, "You are leaving "+name, Toast.LENGTH_LONG).show();
                }
            }
        };
        localLocationChangedBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String name = intent.getStringExtra("name");
                double distance = intent.getDoubleExtra("distance",0);
                double speed = intent.getDoubleExtra("speed",0);
                double angle = intent.getDoubleExtra("angle",0);
                double temperature = intent.getDoubleExtra("temperature",0);
                updateDistSpeedUI(distance,speed);
                updateTemperatureUI((float)temperature);
                compassAnimate((float) angle,currentAzimuthRotation);
            }
        };
        proximityIntentReceiver = new ProximityIntentReceiver();

        // Navigation button
        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // get selected check point from spinner
                String selectedCheckpoint = checkPointSpinner.getSelectedItem().toString();
                Log.d("Navigation button","Selected checkpoint: "+selectedCheckpoint);
                checkpointText.setText(getString(R.string.checkpoint_headto));
                // add proximity alert
                addProximityAlert(selectedCheckpoint);
                inTrip=true;
            }
        });

        cancelBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                inTrip=false;
                if(currentTrip!=null){
                    finishTrip(false);
                }else{
                    removeProximityAlert();
                }

            }
        });
    }

    @Override
    protected void onStart() {
        checkPermissions();
        createNotificationChannel();
        // location manager is supposed to be re-registered here, to test if the UI can be updated in the background, I just make them into comments.
//        if (currentCheckpoint!=null){
//            addProximityAlert(currentCheckpoint.getName());
//        }
        registerReceiver(localProximityBroadcastReceiver, new IntentFilter(PROX_ALERT_INTENT));
        registerReceiver(localLocationChangedBroadcastReceiver, new IntentFilter(ON_LOCATION_CHANGED_INTENT));
        registerReceiver(proximityIntentReceiver, new IntentFilter(PROX_ALERT_INTENT));
        loadAmbientTemperature();
        loadOrientation();
        super.onStart();
        Log.d("App","Start");
    }

    @Override
    protected void onStop() {
        // These objects are supposed to be unregistered here. But to test if the UI can be updated in the background, I just make them into comments.
//        locationManager.removeUpdates(this);
//        sensorManager.unregisterListener(this);
        unregisterReceiver(localProximityBroadcastReceiver);
        unregisterReceiver(localLocationChangedBroadcastReceiver);
        unregisterReceiver(proximityIntentReceiver);
        Log.d("App","Stop");
        super.onStop();
    }
    @Override
    protected void onPause(){
        Log.d("App","Pause");
        super.onPause();
    }
    @Override
    protected void onResume(){
        Log.d("App","Resume");
        super.onResume();
    }
    @Override
    protected void onDestroy(){
        Log.d("App","Destroy");
        super.onDestroy();
    }
    @Override
    public void onBackPressed(){
        // Move the activity to background
        moveTaskToBack(false);

    }
    /**
     * readCheckPoints
     * Read check points from res/raw/checkpoints.csv and store the check points in the hashmap as Geofence object
     */
    private void readCheckPoints(){
        try{
            InputStream inputStream = getResources().openRawResource(R.raw.checkpoints);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            String title = bufferedReader.readLine();
            String eachline = bufferedReader.readLine();
            int record_num = 0;
            while (eachline!=null){
                String[] tokens = eachline.split(";");
                String name = tokens[0];
                String geofenceName = tokens[0];
                double lng = Double.parseDouble(tokens[1]);
                double lat = Double.parseDouble(tokens[2]);
                checkpoints.put(geofenceName, new Geofence(geofenceName, lat, lng, GEOFENCE_RADIUS));
//                Log.d("ReadFile",String.format("content: %s, %.5f", geofenceName, checkpoints.get(geofenceName).getLatitude()));
                eachline = bufferedReader.readLine();
                record_num++;
            }
            Log.d("Read Checkpoints.csv",String.format("Read res/raw/checkpoints.csv with %d records",record_num));
            bufferedReader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * setUpSpinner
     * Create spinner. Choices are the checkpoints (stored in `checkpoints`).
     */
    private void setUpSpinner(){
        String[] geofenceNames = checkpoints.keySet().toArray(new String[0]);
        // create an ArrayAdapter using checkpoints names (string array) and a default spinner layout
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,geofenceNames);
        // specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // apply the adapter to the spinner
        checkPointSpinner.setAdapter(adapter);
    }

    /**
     * We use this function to check for permissions, and pop up a box asking for them,
     * in case a user hasn't given them yet.
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void checkPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, 1);
        }
    }

    @Override
    public boolean shouldShowRequestPermissionRationale(@NonNull String permission) {
        return super.shouldShowRequestPermissionRationale(permission);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String permissions[],
            int[] grantResults) {
        switch (requestCode) {
            case 1:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(FullscreenActivity.this, "Permission Granted!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(FullscreenActivity.this, "Permission Denied! Please go to Settings to accept position permission!", Toast.LENGTH_SHORT).show();
                }
        }
    }

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.channel_name);
            String description = getString(R.string.channel_description);
            String CHANNEL_ID = getString(R.string.channel_id);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    /**
     * Add temperature listener
     * Toast if it does not exist
     * Temperature will then recorded as -100
     */
    private void loadAmbientTemperature() {
        // Load ambient temperature sensor
        Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);

        if (sensor != null) {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST);
            Log.d("Ambient Temperature","Sensor registered");
        } else {
            Toast.makeText(this, "The device does not support temperature sensor!", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Add accelerometer & magnetic field sensor
     * Toast if it does not exit
     * User then needs to find north by himself
     */
    private void loadOrientation(){
        // Load ambient temperature sensor
        Sensor mAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor mGeomagnetic = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        if (mAccelerometer != null) {
            sensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);
            Log.d("Orientation","accelerometer registered");
        } else {
            Toast.makeText(this, "The device does not support orienting to north! Please find north by yourself:D", Toast.LENGTH_LONG).show();
        }

        if (mGeomagnetic != null) {
            sensorManager.registerListener(this, mGeomagnetic, SensorManager.SENSOR_DELAY_FASTEST);
            Log.d("Orientation","Geomagnetic registered");
        } else {
            Toast.makeText(this, "The device does not support orienting to north! Please find north by yourself:D", Toast.LENGTH_LONG).show();
        }
    }

    float[] mGravity;
    float[] mGeomagnetic;
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType()==Sensor.TYPE_AMBIENT_TEMPERATURE && event.values.length > 0) {
            currentTemperature = event.values[0];
            Log.d("temperature sensor","current temp: "+currentTemperature);
            if(inTrip) updateTemperatureUI(currentTemperature);
        }
        // Get azimuth
        float azimut;
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
            mGravity = event.values;
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
            mGeomagnetic = event.values;
        if (mGravity != null && mGeomagnetic != null) {
            float R[] = new float[9];
            float I[] = new float[9];
            if (SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic)) {
                // orientation contains azimut, pitch and roll
                float orientation[] = new float[3];
                SensorManager.getOrientation(R, orientation);
                azimut = orientation[0];
                float nowRotation = -(float) azimut * 360 / (2 * 3.14159f);
                if (currentAzimuthRotation != nowRotation){
                    currentAzimuthRotation = nowRotation;
                    Log.d("Orientation sensor","azimuth rotation: "+currentAzimuthRotation);
                    if(inTrip) compassAnimate(currentAngle,currentAzimuthRotation);
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    /**
     * Adds a proximity alert, when users' location changes, UI updates distance, angle, temperature and speed
     * @param name   The name of the check point.
     */
    private void addProximityAlert(String name) {
        try {
            currentCheckpoint = checkpoints.get(name);
            lastDistToCenter.put(name, Double.MAX_VALUE);
            // Initiate trip instance
            currentTrip = new Trip(currentCheckpoint.getLocation());
            // start the location updates.
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 5, this);
        } catch (SecurityException e) {
            Log.d("ERROR", e.getMessage());
        }
    }

    /**
     * Remove the proximity alert after the trip is finished or user cancels the trip
     */
    private void removeProximityAlert(){
        try{
            locationManager.removeUpdates(this);

            currentCheckpoint = null;
            currentTrip = null;
            lastDistToCenter = new HashMap<>();
            updateDistSpeedUI(0,0);
            compassAnimate(0,0);
            updateTemperatureUI(0);
            checkpointText.setText(getString(R.string.checkpoint_selection));
        }catch (SecurityException e) {
            Log.d("ERROR", e.getMessage());
        }
    }

    /**
     * We use this method to send an intent stating that we are in proximity of a certain object,
     * denoted by its "name". The boolean passed along tells us if we are entering of leaving
     * the proximity.
     *
     * @param name     The name of the proximity area.
     * @param entering True if we're entering, false otherwise.
     */
    private void sendProximityIntent(String name, boolean entering) {
        Intent i = new Intent(PROX_ALERT_INTENT);
        i.putExtra("name", name);
        i.putExtra(LocationManager.KEY_PROXIMITY_ENTERING, entering);
        // Send the intent as a broadcast here. Every BroadcastReceiver, which is registered
        // to listen to PROX_ALERT_INTENT will receive this intent!
        // For example, ProximityIntentReceiver is registered in the Manifest, and the
        // localBroadcastReceiver of MainActivity in onResume(...).
        this.sendBroadcast(i);
    }

    /**
     * Send an intent every time onLocationChanged triggered to update the distance and the angle to the selected checkpoint
     * @param name  Geofence name
     * @param distance Distance from current location to the selected checkpoint
     * @param angle Angle from current location to the selected checkpoint
     * @param speed Current speed
     * @param temperature Current temperature
     */
    private void sendLocationChangedIntent(String name, double distance, double angle, double speed, double temperature){
        // Create intent storing all information to be updated
        Intent i = new Intent(ON_LOCATION_CHANGED_INTENT);
        i.putExtra("name", name);
        i.putExtra("distance", distance);
        i.putExtra("angle", angle);
        i.putExtra("speed", speed);
        i.putExtra("temperature", temperature);
        // send broadcast
        this.sendBroadcast(i);
    }

    /**
     * LocationManager method
     * @param location current location of user
     */
    @Override
    public void onLocationChanged(@NonNull Location location) {
        // Test whether the current trip started or not
        if(!currentTrip.testStarted()){
            currentTrip.startTrip(location, getCurrentTime());
        }
        else{
            currentTrip.addIntermediatePoint(location, getCurrentTime(), currentTemperature);
        }

        // Compute the distance to the checkpoint
        double distance = 0.;
        // Get angle: Degree of east to the north
        double angle = 0.;
        // Temperature, speed sensor
        double temperature = currentTemperature;
        double speed = currentTrip.getCurrentSpeed();

        // Check whether user arrived the destination
        int trip_stage = currentTrip.testStage(location, ARRIVE_THRESHOLD);
        switch (trip_stage){
            case R.string.trip_stage_arrive_checkpoint:
                Log.d("Trip stage","Arrived check point");
                Toast.makeText(this, String.format("You arrived the checkpoint %s\nNow going back...", currentCheckpoint.getName()),Toast.LENGTH_LONG).show();
                distance = currentTrip.currentDistanceToStart();
                angle = currentTrip.currentAngleToStart();
                currentCheckpoint = new Geofence("Startpoint",currentTrip.getStartPoint(), GEOFENCE_RADIUS);
                lastDistToCenter.put("Startpoint", Double.MAX_VALUE);
                checkpointText.setText(getString(R.string.checkpoint_goback));
                // Update UI
                updateDistSpeedUI(distance, speed);
                compassAnimate((float)angle,currentAzimuthRotation);
                break;
            case R.string.trip_stage_find_checkpoint:
                distance = currentTrip.currentDistanceToCheck();
                angle = currentTrip.currentAngleToCheck();
                // Update UI
                updateDistSpeedUI(distance, speed);
                compassAnimate((float)angle,currentAzimuthRotation);
                break;
            case R.string.trip_stage_find_startpoint:
                distance = currentTrip.currentDistanceToStart();
                angle = currentTrip.currentAngleToStart();
                // Update UI
                updateDistSpeedUI(distance, speed);
                compassAnimate((float)angle,currentAzimuthRotation);
                break;
            case R.string.trip_stage_arrive_startpoint:
                Log.d("Trip stage","Arrived start point");
                // User return to start point, finish the trip
                finishTrip(true);
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + trip_stage);
        }
        currentAngle = (float) angle;
        // Send current distance, angle, speed, temperature as an intent; This cannot support background running. So I just make it into comments.
//        sendLocationChangedIntent(geofenceName, distance, angle, speed, temperature);

        // Send notification if user approaching or leaving the destination geofence
        if (currentCheckpoint != null) {
            double geofenceRadius = currentCheckpoint.getRadius();
            String geofenceName = currentCheckpoint.getName();
            if (distance < geofenceRadius && lastDistToCenter.get(geofenceName) > geofenceRadius) {
                sendProximityIntent(geofenceName, true);
            } else if (distance > geofenceRadius && lastDistToCenter.get(geofenceName) < geofenceRadius) {
                // In the opposite case, we must be leaving the geofence.
                sendProximityIntent(geofenceName, false);
            }
            lastDistToCenter.put(geofenceName, distance);
        }

    }

    /**
     * Update UI of speed and distance
     * @param distance current distance (meter) to destination
     * @param speed current speed (km/h)
     */
    private void updateDistSpeedUI(double distance, double speed){
        // Speed
        digitSpeedView.updateSpeed((int) Math.round(speed));
        // Distance
        digitDistanceView.updateSpeed((int)distance);
    }

    /**
     * Update compass animation
     * @param angle east to the north
     * @param nowAzimuthRotation device rotation to north
     */
    private void compassAnimate(float angle, float nowAzimuthRotation){
        // Compass Animation
        Log.d("Orientation","AzimuthRotation: "+nowAzimuthRotation+" Angle to north: "+angle);
        float degree = (float) ((nowAzimuthRotation+angle+360)%360);

        compassAngle.setText(String.format("%3d°", (int)degree));
        // Create a rotation animation (reverse turn degree degrees)
        RotateAnimation ra = new RotateAnimation(currentDegree, degree,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);

        // How long the animation will take place
        ra.setDuration(210);
        // Set the animation after the end of the reservation status
        ra.setFillAfter(true);
        // Start the animation
        image.startAnimation(ra);
        currentDegree = degree;
    }

    /**
     * Update UI for temperature
     * @param temperature current temperature
     */
    private void updateTemperatureUI(float temperature){
        // Temperature
        digitTempView.updateSpeed((int)temperature);
    }

    /**
     * When the trip canceled or accomplished, finish the trip
     * Give user feedback about metrics of the trip
     * If the trip is successfully accomplished, save the rewards, records in SD Card
     * @param arrived true if the trip is successfully accomplished
     */
    private void finishTrip(boolean arrived){
        inTrip = false;
        double totalDistance = currentTrip.getTotalDistance();
        double totalDuration = currentTrip.getTotalDuration();
        double avg_speed = currentTrip.getAverageSpeed();
        double avg_temperature = currentTrip.getAverageTemparture();

        String record = String.format(
                "\nYour moving distance:%.2f meters\nDuration:%.2f seconds\n",
                currentTrip.getTotalDistance(),
                currentTrip.getTotalDuration());

        // UI updates when trip finished -> release total distance, duration...
        if(arrived){
            String reward = checkRewards(avg_speed, totalDistance, avg_temperature);
            String text = getString(R.string.trip_stage_arrive_startpoint);
            Toast.makeText(this, text+record+String.format("You get %s as a reward!!", reward), Toast.LENGTH_LONG).show();
            // Save record to csv
            String line = String.format("%d,%.5f,%.5f,%.5f,%.5f,%.2f,%.1f,%.2f,%.2f,%s",
                    currentTrip.getStartTime(), //timestamp miliseconds
                    currentTrip.getStartPoint().getLongitude(),
                    currentTrip.getStartPoint().getLatitude(),
                    currentTrip.getCheckPoint().getLongitude(),
                    currentTrip.getCheckPoint().getLatitude(),
                    totalDistance, //meter
                    totalDuration, //seconds
                    avg_speed, //km/h
                    avg_temperature,
                    reward);
            writeToCSV(line, REWARD_FILE_PATH);
        }else{
            Toast.makeText(this, "You cancel the trip!"+record, Toast.LENGTH_LONG).show();
        }
        removeProximityAlert();
    }


    // ===================== UTIL FUNCTIONS ====================
    /**
     * Get current time
     * @return current time in milliseconds
     */
    private long getCurrentTime(){
        //creating Calendar instance
        Calendar calendar = Calendar.getInstance();
        //Returns current time in millis
        long timeMilli2 = calendar.getTimeInMillis();
        return timeMilli2;
    }

    /**
     * Write the record to CSV in the SD card
     * @param line write lines
     * @param path file name
     */
    private void writeToCSV(String line, String path){
        if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){
            try{
                File file = new File (getExternalFilesDir(null), path);
                FileOutputStream outputStream = new FileOutputStream(file, true);
                PrintWriter writer = new PrintWriter(outputStream);

                writer.println(line);
                writer.close();
                outputStream.close();
                Log.d("FileLog", "File Saved :  " + file.getPath());
            }catch(IOException e){
                Log.e("FileLog", "File to write file");
            }
        }else{
            Log.e("FileLog", "SD card not mounted");
        }
    }

    /**
     * Check which rewards the trip gets
     * @param avg_speed: average speed (km/h)
     * @param distance: distance (m)
     * @param avg_temp: average temperature (degree)
     * @return reward string
     */
    private String checkRewards(double avg_speed, double distance, double avg_temp){
        if(avg_speed>=4 && avg_speed<6 && distance<=1000 && avg_temp<20 && avg_speed>4){
            return "Peach";
        }
        if(avg_speed>=4 && avg_speed<6 && distance>1000 && avg_temp>=20){
            return "Watermelon";
        }
        if(avg_speed>=6 && avg_speed<8 && distance>0 && avg_temp>=20){
            return "Ice Cream";
        }
        if(avg_speed>=8 && distance>1 && avg_temp>4000 && avg_temp<20){
            return "Banana";
        }
        else {
            return "Apple";
        }
    }

    //======================== UI functions ==================================

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100);
    }

    private void toggle() {
        if (mVisible) {
            hide();
        } else {
            show();
        }
    }

    private void hide() {
        // Hide UI first
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        mControlsView.setVisibility(View.GONE);
        mVisible = false;

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    private void show() {
        // Show the system bar
        mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        mVisible = true;

        // Schedule a runnable to display UI elements after a delay
        mHideHandler.removeCallbacks(mHidePart2Runnable);
        mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
    }

    /**
     * Schedules a call to hide() in delay milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }


}