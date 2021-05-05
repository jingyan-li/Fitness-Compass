package ch.ethz.jingyli.mobilegis.compassnavigate;

import android.annotation.SuppressLint;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.esri.arcgisruntime.ArcGISRuntimeEnvironment;
import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.data.Feature;
import com.esri.arcgisruntime.data.FeatureEditResult;
import com.esri.arcgisruntime.data.ServiceFeatureTable;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.geometry.Polyline;
import com.esri.arcgisruntime.geometry.SpatialReference;
import com.esri.arcgisruntime.geometry.SpatialReferences;
import com.esri.arcgisruntime.layers.FeatureLayer;
import com.esri.arcgisruntime.loadable.LoadStatus;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.BasemapStyle;
import com.esri.arcgisruntime.mapping.Viewpoint;
import com.esri.arcgisruntime.mapping.view.Graphic;
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay;
import com.esri.arcgisruntime.mapping.view.MapView;
import com.esri.arcgisruntime.symbology.SimpleLineSymbol;
import com.esri.arcgisruntime.symbology.SimpleMarkerSymbol;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import ch.ethz.jingyli.mobilegis.compassnavigate.Fragment.BackDialogFragment;
import ch.ethz.jingyli.mobilegis.compassnavigate.Fragment.UploadTrackDialogFragment;

import static ch.ethz.jingyli.mobilegis.compassnavigate.Utils.pointStringToPoint;
import static ch.ethz.jingyli.mobilegis.compassnavigate.Utils.trackStringToPolyline;


/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class UploadTrackActivity extends AppCompatActivity implements BackDialogFragment.BackDialogListener {
    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 500;

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
     * Initialize variables
     */
    private static final SpatialReference SPATIAL_REFERENCE = SpatialReferences.getWgs84();
    private MapView mMapView;
    private ArcGISMap map;
    private static final double VIEWPOINT_SCALE = 5000;
    private boolean uploaded;
    /**
     * Some widgets
     */
    private Button uploadBtn;
    private Button backBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_upload_track);

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

        //Get widgets
        uploadBtn = (Button) findViewById(R.id.uploadBtn);
        backBtn = (Button) findViewById(R.id.backBtn);
        //Initialize variables
        uploaded = false;

        //SET ARCGIS API KEY
        ArcGISRuntimeEnvironment.setApiKey(getString(R.string.ARCGIS_API));

        // Get intent
        Intent intent = getIntent();
        HashMap<String, Object> trackAttributes = (HashMap<String, Object> ) intent.getSerializableExtra(getString(R.string.EXTRA_TRACK_ATTRIBUTE));
        HashMap<String, Object> pointAttributes = (HashMap<String, Object> ) intent.getSerializableExtra(getString(R.string.EXTRA_POINT_ATTRIBUTE));
        Polyline trackGeometry = trackStringToPolyline(intent.getStringExtra(getString(R.string.EXTRA_TRACK_GEOMETRY)),SPATIAL_REFERENCE);
        Point pointGeometry = pointStringToPoint(intent.getStringExtra(getString(R.string.EXTRA_POINT_GEOMETRY)),SPATIAL_REFERENCE);

        // Get mapview
        mMapView = (MapView) findViewById(R.id.mapView);

        // create a map with streets basemap
        map = new ArcGISMap(BasemapStyle.ARCGIS_NAVIGATION_NIGHT);

        // Add graphic layer
        GraphicsOverlay overlay = new GraphicsOverlay();
        mMapView.getGraphicsOverlays().add(overlay);
        SimpleMarkerSymbol s = new SimpleMarkerSymbol(SimpleMarkerSymbol.Style.CROSS, Color.parseColor(getString(R.color.purple_200)), 20);
        Graphic g1 = new Graphic(pointGeometry, s);
        SimpleLineSymbol routeSymbol = new SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.parseColor(getString(R.color.purple_500)), 4);
        Graphic routeGraphic = new Graphic(trackGeometry, routeSymbol);
        overlay.getGraphics().add(routeGraphic);
        overlay.getGraphics().add(g1);

        mMapView.setMap(map);
        mMapView.setViewpoint(new Viewpoint(pointGeometry, VIEWPOINT_SCALE));

        uploadBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                uploadRecordToArcgisServer(pointAttributes, pointGeometry, trackAttributes, trackGeometry);
            }
        });
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(uploaded){
                    triggerBackToMain();
                }else{
                    DialogFragment dialog = new BackDialogFragment();
                    dialog.show(getSupportFragmentManager(), getString(R.string.FRAGMENT_TAG_BACK_DIALOG));
                }
            }
        });

    }

    /**
     * Back to Main Activity
     */
    private void triggerBackToMain(){
        Intent intent = new Intent(this,
                MainActivity.class);
        startActivity(intent);
    }

    /**
     * Add point feature and trajectory feature to ArcGIS service layer
     * @param checkpointAttributes attributes of checkpoint feature
     * @param point geometry of checkpoint
     * @param trackAttributes attributes of track feature
     * @param track geometry of track
     */
    private void uploadRecordToArcgisServer(Map<String, Object> checkpointAttributes, Point point, Map<String, Object> trackAttributes, Polyline track){
        //TODO Implement method uploadRecordToArcgisServer

        // create service feature table from URL
        ServiceFeatureTable trackServiceFeatureTable = new ServiceFeatureTable(getString(R.string.URL_ROUND_TRIP_TRACK));
        // create a feature layer from table
        FeatureLayer trackFeatureLayer = new FeatureLayer(trackServiceFeatureTable);
        trackFeatureLayer.loadAsync();
        // Loading done listener
        trackServiceFeatureTable.addDoneLoadingListener(()-> {
            if (trackServiceFeatureTable.getLoadStatus()== LoadStatus.LOADED){
                // creates a new feature using default attributes and point
                Feature feature = trackServiceFeatureTable.createFeature(trackAttributes, track);
                // check if feature can be added to feature table
                if (trackServiceFeatureTable.canAdd()) {
                    // add the new feature to the feature table and to server
                    trackServiceFeatureTable.addFeatureAsync(feature).addDoneListener(() -> applyEdits(trackServiceFeatureTable,"Your trajectory"));
                } else {
                    runOnUiThread(() -> logToUser(true, getString(R.string.error_cannot_add_to_feature_table)+' '+getString(R.string.URL_ROUND_TRIP_TRACK)));
                }
            }else{
                Log.d("ArcGIS","TRACK Feature table not loaded. Error: "+trackServiceFeatureTable.getLoadError().getCause());
            }
        });

        // create service feature table from URL
        ServiceFeatureTable checkpointServiceFeatureTable = new ServiceFeatureTable(getString(R.string.URL_ROUND_TRIP_CHECKPOINT));
        // create a feature layer from table
        FeatureLayer pointFeatureLayer = new FeatureLayer(checkpointServiceFeatureTable);
        pointFeatureLayer.loadAsync();
        checkpointServiceFeatureTable.addDoneLoadingListener(()->{
            if(checkpointServiceFeatureTable.getLoadStatus()==LoadStatus.LOADED){
                // creates a new feature using default attributes and point
                Feature checkpointFeature = checkpointServiceFeatureTable.createFeature(checkpointAttributes, point);
                // check if feature can be added to feature table
                if (checkpointServiceFeatureTable.canAdd()) {
                    // add the new feature to the feature table and to server
                    checkpointServiceFeatureTable.addFeatureAsync(checkpointFeature).addDoneListener(() -> applyEdits(checkpointServiceFeatureTable,"Your checkpoint"));
                } else {
                    runOnUiThread(() -> logToUser(true, getString(R.string.error_cannot_add_to_feature_table)+' '+getString(R.string.URL_ROUND_TRIP_CHECKPOINT)));
                }
            }else{
                Log.d("ArcGIS","CHECKPOINT Feature table not loaded. Error: "+checkpointServiceFeatureTable.getLoadError().getCause());
            }
        });


    }
    /**
     * Sends any edits on the ServiceFeatureTable to the server.
     *
     * @param featureTable service feature table
     */
    private void applyEdits(ServiceFeatureTable featureTable, String tableName) {

        // apply the changes to the server
        final ListenableFuture<List<FeatureEditResult>> editResult = featureTable.applyEditsAsync();
        editResult.addDoneListener(() -> {
            try {
                List<FeatureEditResult> editResults = editResult.get();
                // check if the server edit was successful
                if (editResults != null && !editResults.isEmpty()) {
                    if (!editResults.get(0).hasCompletedWithErrors()) {
                        runOnUiThread(() -> logToUser(false, tableName+" "+getString(R.string.feature_added)));
                        uploaded = true;
                    } else {
                        throw editResults.get(0).getError();
                    }
                }
            } catch (InterruptedException | ExecutionException e) {
                runOnUiThread(() -> logToUser(true, getString(R.string.error_applying_edits)
                        + '\n' + Objects.requireNonNull(e.getCause()).getMessage()));
            }
        });
    }
    /**
     * Shows a Toast to user and logs to logcat.
     *
     * @param isError whether message is an error. Determines log level.
     * @param message message to display
     */
    private void logToUser(boolean isError, String message) {
        String TAG = UploadTrackActivity.class.getSimpleName();
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        if (isError) {
            Log.e(TAG, message);
        } else {
            Log.d(TAG, message);
        }
    }

    // ---------------------UI FUNCTIONS-------------------------------

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

    @Override
    public void onDialogPositiveClick(DialogFragment dialog) {
        if(dialog.getTag().equals(getString(R.string.FRAGMENT_TAG_BACK_DIALOG))){
            triggerBackToMain();
        }
    }

    @Override
    public void onDialogNegativeClick(DialogFragment dialog) {

    }
}