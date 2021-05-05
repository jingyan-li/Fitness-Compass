package ch.ethz.jingyli.mobilegis.compassnavigate;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Color;
import android.graphics.Point;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.esri.arcgisruntime.ArcGISRuntimeEnvironment;
import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.data.Feature;
import com.esri.arcgisruntime.data.FeatureQueryResult;
import com.esri.arcgisruntime.data.QueryParameters;
import com.esri.arcgisruntime.data.ServiceFeatureTable;
import com.esri.arcgisruntime.geometry.Envelope;
import com.esri.arcgisruntime.geometry.Geometry;
import com.esri.arcgisruntime.geometry.SpatialReference;
import com.esri.arcgisruntime.layers.FeatureLayer;
import com.esri.arcgisruntime.loadable.LoadStatus;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.BasemapStyle;
import com.esri.arcgisruntime.mapping.GeoElement;
import com.esri.arcgisruntime.mapping.Viewpoint;
import com.esri.arcgisruntime.mapping.view.Callout;
import com.esri.arcgisruntime.mapping.view.DefaultMapViewOnTouchListener;
import com.esri.arcgisruntime.mapping.view.IdentifyLayerResult;
import com.esri.arcgisruntime.mapping.view.MapView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;

import java.util.Map;


public class TrackReviewActivity extends AppCompatActivity {
    /**
     * ArcGIS related variables
     */
    private MapView mMapView;
    private ArcGISMap map;
    private ServiceFeatureTable trackServiceFeatureTable;
    private FeatureLayer trackFeatureLayer;
    private Callout mCallout;
    private Viewpoint viewPoint;
    /**
     * some widgets
     */
    private Button searchBtn;
    private Spinner userSpinner;
    private Switch layerSwitch;

    private final String TAG="TrackReviewActivity";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_track_review);
        // get UI
        searchBtn = (Button) findViewById(R.id.track_review_search_button);
        userSpinner = (Spinner) findViewById(R.id.track_review_spinner_simple);

        // set your API key, Read the api key from string.xml
        // authentication with an API key or named user is required to access basemaps and other location services
        ArcGISRuntimeEnvironment.setApiKey(getString(R.string.ARCGIS_API));
        // inflate MapView from layout
        mMapView = (MapView)findViewById(R.id.mapView);
        // create a map with streets basemap
        map = new ArcGISMap(BasemapStyle.ARCGIS_NAVIGATION_NIGHT);

        // set the map to be displayed in this view
        mMapView.setMap(map);
        viewPoint = new Viewpoint(47.408992, 8.507847,
                5000);
        mMapView.setViewpoint(viewPoint);

        trackServiceFeatureTable = new ServiceFeatureTable(getString(R.string.URL_ROUND_TRIP_TRACK));
        trackFeatureLayer = new FeatureLayer(trackServiceFeatureTable);
        // add the layer to the map
        map.getOperationalLayers().add(trackFeatureLayer);

        // Load all possible users from feature layer, and set up spinner
        getPossibleUsers();

        // get the callout that shows attributes
        mCallout = mMapView.getCallout();

        // set an on touch listener to listen for click events
        mMapView.setOnTouchListener(new DefaultMapViewOnTouchListener(this, mMapView) {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                // remove any existing callouts
                if (mCallout.isShowing()) {
                    mCallout.dismiss();
                }
                // get the point that was clicked and convert it to a point in map coordinates
                final Point screenPoint = new Point(Math.round(e.getX()), Math.round(e.getY()));
                // create a selection tolerance
                int tolerance = 10;
                // use identifyLayerAsync to get tapped features
                final ListenableFuture<IdentifyLayerResult> identifyLayerResultListenableFuture = mMapView
                        .identifyLayerAsync(trackFeatureLayer, screenPoint, tolerance, false, 1);
                identifyLayerResultListenableFuture.addDoneListener(() -> {
                    try {
                        IdentifyLayerResult identifyLayerResult = identifyLayerResultListenableFuture.get();
                        // create a textview to display field values
                        TextView calloutContent = new TextView(getApplicationContext());
                        calloutContent.setTextColor(Color.BLACK);
                        calloutContent.setSingleLine(false);
                        calloutContent.setVerticalScrollBarEnabled(true);
                        calloutContent.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
                        calloutContent.setMovementMethod(new ScrollingMovementMethod());
                        calloutContent.setLines(5);
                        for (GeoElement element : identifyLayerResult.getElements()) {
                            Feature feature = (Feature) element;
                            // create a map of all available attributes as name value pairs
                            Map<String, Object> attr = feature.getAttributes();
                            String calloutText = getCalloutText(attr);
                            calloutContent.setText(calloutText);
                            // center the mapview on selected feature
                            Envelope envelope = feature.getGeometry().getExtent();
                            mMapView.setViewpointGeometryAsync(envelope, 200);
                            // show callout
                            mCallout.setLocation(envelope.getCenter());
                            mCallout.setContent(calloutContent);
                            mCallout.show();
                        }
                    } catch (Exception e1) {
                        Log.e(getResources().getString(R.string.app_name), "Select feature failed: " + e1.getMessage());
                    }
                });
                return super.onSingleTapConfirmed(e);
            }
        });

        searchBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                String spinner_choice = userSpinner.getSelectedItem().toString();
                if (!spinner_choice.equals(getString(R.string.Track_Review_Spinner_All))){
                    String userid = spinner_choice.replace(getString(R.string.Track_Review_Spinner_User),"");
                    searchForUserId(userid);
                }else{
                    showAllFeatures(trackServiceFeatureTable, trackFeatureLayer);
                }

            }
        });
    }

    /**
     * Convert Milliseconds String into Date String
     * @param timestamp Timestamp in milliseconds, stored as String type
     * @return Text of date
     */
    private String dateToString(Object timestamp){
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(Long.parseLong((String)timestamp));
        Date date = calendar.getTime();
        String dateText = date.toString();
        return dateText;
    }

    /**
     * Based on the feature table attributes, generate a text showing the attribute information
     * @param attr trackFeatureTable attribute
     * @return the generated text to be shown in callout
     */
    private String getCalloutText(Map<String, Object> attr){
        String text = "";
        text += String.format("User ID: %d\n", attr.get("user_id"));
        text += String.format("Track ID: %d\n", attr.get("track_id"));
        text += String.format("Start time: %s\n", dateToString(attr.get("start_timestamp")));
        text += String.format("Distance: %.2f km\n", attr.get("distance"));
        text += String.format("Duration: %.0f seconds\n", attr.get("duration"));
        text += String.format("Average speed: %.2f km/h\n", attr.get("average_speed"));
        text += String.format("Average temperature: %.2f C\n", attr.get("average_temp"));
        text += String.format("Reward: %s\n", attr.get("reward"));
        return text;
    }

    /**
     * Given a user id, show the records only from this user
     * @param searchId user id
     */
    private void searchForUserId(final String searchId) {
        // Show certain user id
        String queryClause = "user_id <> " + searchId;
        String inverseQueryClause = "user_id = " + searchId;
        queryFeature(queryClause, trackServiceFeatureTable, trackFeatureLayer, false);
        queryFeature(inverseQueryClause, trackServiceFeatureTable, trackFeatureLayer, true);
    }

    /**
     * Show all features in the feature table over the feature layer
     * @param mServiceFeatureTable service feature table to query from
     * @param mFeatureLayer feature layer to show the query result
     */
    private void showAllFeatures(ServiceFeatureTable mServiceFeatureTable, FeatureLayer mFeatureLayer){
        // Show all features
        String queryClause = "1=1";
        queryFeature(queryClause, mServiceFeatureTable, mFeatureLayer, true);
    }

    /**
     * Query the feature by query clause from Service Feature Table, and show the features of which the visibility is set as true
     * @param queryClause SQL where clause to query from the Service Feature Table
     * @param mServiceFeatureTable the Service Feature Table to be queried
     * @param mFeatureLayer the Feature Layer to show the selected features
     * @param visible true if the queried features are set visible, and vice versa
     */
    private void queryFeature(String queryClause, ServiceFeatureTable mServiceFeatureTable, FeatureLayer mFeatureLayer, boolean visible){

        // create objects required to do a selection with a query
        QueryParameters query = new QueryParameters();
        query.setWhereClause(queryClause);
        // call select features
        final ListenableFuture<FeatureQueryResult> future = mServiceFeatureTable.queryFeaturesAsync(query);
        // add done loading listener to fire when the selection returns
        future.addDoneListener(() -> {
            try {
                // call get on the future to get the result
                FeatureQueryResult result = future.get();
                // check there are some results
                Iterator<Feature> resultIterator = result.iterator();
                ArrayList<Feature> queriedFeatures = new ArrayList<Feature>();
                // queried features extent
                double maxX = Double.NEGATIVE_INFINITY;
                double maxY = Double.NEGATIVE_INFINITY;
                double minX = Double.POSITIVE_INFINITY;
                double minY = Double.POSITIVE_INFINITY;
                while (resultIterator.hasNext()) {
                    // get the extent of the first feature in the result to zoom to
                    Feature feature = resultIterator.next();
                    queriedFeatures.add(feature);
                    Geometry geometry = feature.getGeometry();
                    if (geometry!=null){
                        Envelope envelope = geometry.getExtent();
                        if(envelope.getXMax()>maxX) maxX = envelope.getXMax();
                        if(envelope.getYMax()>maxY) maxY = envelope.getYMax();
                        if(envelope.getXMin()<minX) minX = envelope.getXMin();
                        if(envelope.getYMin()<minY) minY = envelope.getYMin();
                    }
                }
                if(!queriedFeatures.isEmpty()){
                    mFeatureLayer.setFeaturesVisible(queriedFeatures, visible);
                    if(visible){
                        SpatialReference spatialReference = mFeatureLayer.getSpatialReference();
                        Envelope envelope = new Envelope(maxX,maxY,minX,minY, spatialReference);
                        mMapView.setViewpointGeometryAsync(envelope, 10);
                    }
                }else{
                    if(visible){
                        Toast.makeText(this, "There is no record for: "+queryClause, Toast.LENGTH_LONG).show();
                    }
                }
            } catch (Exception e) {
                String error = "Feature search failed for: " + queryClause + ". Error: " + e.getMessage();
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                Log.e("Track Reviewer", error);
            }
        });
    }


    /**
     * setUpSpinner
     * Create spinner. Choices are the user ids queried from Service Feature Table
     */
    private void setUpSpinner(String[] userIds){
        String[] allChoices = new String[userIds.length+1];
        allChoices[0] = getString(R.string.Track_Review_Spinner_All);
        for (int i=1;i<allChoices.length;i++){
            allChoices[i] = getString(R.string.Track_Review_Spinner_User)+userIds[i-1];
        }
        // create an ArrayAdapter using checkpoints names (string array) and a default spinner layout
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item, allChoices);
        // specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // apply the adapter to the spinner
        userSpinner.setAdapter(adapter);
    }

    /**
     * Query the trackServiceFeatureTable to get all user ids, and set up the spinner to show user ids
     */
    private void getPossibleUsers(){
        HashSet<Integer> userIds = new HashSet<Integer>();
        String queryClause ="1=1";
        // create objects required to do a selection with a query
        QueryParameters query = new QueryParameters();
        query.setWhereClause(queryClause);

        // call select features
        final ListenableFuture<FeatureQueryResult> future = trackServiceFeatureTable.queryFeaturesAsync(query);
        // add done loading listener to fire when the selection returns
        future.addDoneListener(() -> {
            try {
                // call get on the future to get the result
                FeatureQueryResult result = future.get();
                // check there are some results
                Iterator<Feature> resultIterator = result.iterator();
                while (resultIterator.hasNext()) {
                    Feature feature = resultIterator.next();
                    Map<String,Object> attr = feature.getAttributes();
                    userIds.add((Integer) attr.get("user_id"));
                }

                // convert interger array to string array
                Integer[] userId_list = userIds.toArray(new Integer[userIds.size()]);
                Arrays.sort(userId_list);
                String str = Arrays.toString(userId_list)
                        .replaceAll("\\s+", "");
                String[] strArray = str.substring(1, str.length() - 1)
                        .split(",");
                // set up spinner
                setUpSpinner(strArray);
            }catch (Exception e) {
                String error = "Feature search failed for: " + queryClause + ". Error: " + e.getMessage();
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                Log.e("Track Reviewer", error);
            }
        });
    }

}