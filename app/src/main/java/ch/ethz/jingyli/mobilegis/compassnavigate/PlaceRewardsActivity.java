package ch.ethz.jingyli.mobilegis.compassnavigate;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.esri.arcgisruntime.ArcGISRuntimeEnvironment;
import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.data.Feature;
import com.esri.arcgisruntime.data.FeatureQueryResult;
import com.esri.arcgisruntime.data.QueryParameters;
import com.esri.arcgisruntime.data.ServiceFeatureTable;
import com.esri.arcgisruntime.layers.FeatureLayer;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.Sceneform;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.Renderable;

import com.google.ar.sceneform.rendering.ShapeFactory;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;
import com.microsoft.azure.spatialanchors.AnchorLocateCriteria;
import com.microsoft.azure.spatialanchors.CloudSpatialAnchor;
import com.microsoft.azure.spatialanchors.CloudSpatialAnchorSession;
import com.microsoft.azure.spatialanchors.LocateAnchorStatus;
import com.microsoft.azure.spatialanchors.SessionLogLevel;

import org.w3c.dom.Text;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import ch.ethz.jingyli.mobilegis.compassnavigate.Fragment.HelpDialogFragment;
import ch.ethz.jingyli.mobilegis.compassnavigate.Fragment.ShareDialogFragment;
import ch.ethz.jingyli.mobilegis.compassnavigate.Model.AppAnchor;


public class PlaceRewardsActivity extends AppCompatActivity
        implements HelpDialogFragment.HelpDialogListener{
    // -------------- Variables to query feature from ArcGIS Feature Layer ---------------
    private ServiceFeatureTable trackServiceFeatureTable;
    private FeatureLayer trackFeatureLayer;

    // --------------Variables for runtime checking---------------------------------------
    // boolean for checking if Google Play Services for AR if necessary.
    private boolean mUserRequestedInstall = true;
    // Camera Permission
    private static final int CAMERA_PERMISSION_CODE = 0;
    private static final String CAMERA_PERMISSION = Manifest.permission.CAMERA;


    //---------------------- Variables for placing object-----------------------------------
    // Variables for loading 3d models
    private HashMap<String, Float> rewardModelScales; // Mapping: <rewards, modelScale>
    // Variables for scanning progress
    private float recommendedSessionProgress = 0f;

    // Variables for tap and place
    private ArFragment arFragment;


    //-------------------- Variables for spatial anchor ------------------------------------
    // Variables for spatial anchor session
    private ArSceneView sceneView;
    private CloudSpatialAnchorSession cloudSession;
    private boolean sessionInited = false;

    private final Object syncTaps = new Object();
    private String tappedAnchorName;

    private Renderable nodeRenderable = null;

    // Variables for uploading spatial anchors
    private String anchorId;
    private boolean scanningForUpload = false;
    private final Object syncSessionProgress = new Object();
    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    // Variables for reload spatial anchors
    private HashMap<String, AppAnchor> reloadRewards;  //<anchorId, appAnchor>
    // Variables for new placing spatial anchors
    private HashMap<String, AppAnchor> newPlaceRewards; //<rewardName, appAnchor>

    //----------------- Variables for UI Widgets ------------------------------------------
    private Button actionBtn;
    private Button backBtn;
    private TextView statusText;
    private TextView scanProgressText;
    private Spinner spinner;
    private Button helpBtn;

    //----------------- Variables for runtime flags ---------------------------------------
    private String currentStep = "";
    private final String STEP_START_APP = "Start";
    private final String STEP_LOAD_FROM_CLOUD = "Load Previous Anchors From Cloud";
    private final String STEP_REMOVE_CURRENT_ANCHOR = "Remove All";
    private final String STEP_PLACE_NEW_ANCHOR = "Start to Place New Anchors";
    private final String STEP_FINISH_PLACE = "Finish and Save";
    private final String STEP_CLEAR_RESTART = "Remove All and Restart";
    private ArrayList<CloudSpatialAnchor> currentCloudAnchors;
    private ArrayList<AnchorNode> currentAnchorNodes;
    private ArrayList<String> currentLoadedModels;

    //----------------- Variables for I/O -------------------------------------------------
    private final String ANCHOR_FILE_PATH ="rewards_anchors.csv";
    private HashMap<String, String> rewardToModelPath; // reward name : model path

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_place_rewards);

        // ArcGIS Feature Query for rewards & Load required models
        // set your API key, Read the api key from string.xml
        // authentication with an API key or named user is required to access basemaps and other location services
        ArcGISRuntimeEnvironment.setApiKey(getString(R.string.ARCGIS_API));

        // Get UI Widgets
        statusText = (TextView)findViewById(R.id.statusText);
        scanProgressText = (TextView)findViewById(R.id.scanProgressText);
        actionBtn = (Button)findViewById(R.id.actionButton);
        backBtn = (Button)findViewById(R.id.backButton);
        spinner = (Spinner)findViewById(R.id.spinner);
        helpBtn = (Button)findViewById(R.id.helpButton);

        // Initialize variables
        currentCloudAnchors = new ArrayList<>();
        currentAnchorNodes = new ArrayList<>();
        currentLoadedModels = new ArrayList<>();
        reloadRewards = new HashMap<>();
        newPlaceRewards = new HashMap<>();

        // Mapping of rewards
        rewardModelScales = new HashMap<>();
        rewardModelScales.put(getString(R.string.apple), 0.001f);
        rewardModelScales.put(getString(R.string.banana), 0.005f);
        rewardModelScales.put(getString(R.string.peach), 0.03f);
        rewardModelScales.put(getString(R.string.icecream), 0.05f);
        rewardModelScales.put(getString(R.string.watermelon), 1.0f);
        rewardToModelPath = new HashMap<>();
        rewardToModelPath.put(getString(R.string.apple), getString(R.string.model_apple));
        rewardToModelPath.put(getString(R.string.banana), getString(R.string.model_banana));
        rewardToModelPath.put(getString(R.string.peach), getString(R.string.model_peach));
        rewardToModelPath.put(getString(R.string.icecream), getString(R.string.model_icecream));
        rewardToModelPath.put(getString(R.string.watermelon), getString(R.string.model_watermelon));

        // Enable AR-related functionality on ARCore supported devices only.
        checkARCoreSupported();

        // Hook up handleTap listener
        getSupportFragmentManager().addFragmentOnAttachListener((fragmentManager, fragment) -> {
            if (fragment.getId() == R.id.ux_fragment) {
                arFragment = (ArFragment) fragment;
                arFragment.setOnTapArPlaneListener(this::handleTap);
            }
        });
        if (savedInstanceState == null) {
            if (Sceneform.isSupported(this)) {
                getSupportFragmentManager().beginTransaction()
                        .add(R.id.ux_fragment, ArFragment.class, null)
                        .commit();
            }
        }

        // Progress steps
        updateCurrentStep(STEP_START_APP);
        checkPreviousRecord();

        // UI Widgets clicked listener
        actionBtn.setOnClickListener(new View.OnClickListener(){
             @Override
             public void onClick(View view) {
                 takeActions();
             }
        });
        // Back to main activity
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                backToMainActivity();
            }
        });
        // Get help
        helpBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                askForHelp();
            }
        });
        // Spinner
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                statusText.setText("");
                scanProgressText.setText("");
                String selectedReward = spinner.getSelectedItem().toString();
                if(selectedReward.equals(getString(R.string.ar_spinner_default))){
                    return;
                }
                AppAnchor myAnchor = newPlaceRewards.get(selectedReward);
                if(myAnchor.isUploaded()){
                    statusText.setText(String.format("Successfully uploaded %s!", selectedReward));
                }
                if(myAnchor.getTapExecutedFlag() && (!myAnchor.isUploaded())){
                    statusText.setText(String.format("Upload failed for %s, please relocate your reward!", selectedReward));
                    if(myAnchor.getAnchorNode()!=null){
                        AnchorNode anchorNode = myAnchor.getAnchorNode();
                        anchorNode.getAnchor().detach();
                        anchorNode.setParent(null);
                        anchorNode = null;
                    }
                    myAnchor.setTapExecutedFlag(false);
                }

            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

    }
    private void askForHelp(){
        // Open a dialog to ask if user is willing to ask for help
        DialogFragment dialog = new HelpDialogFragment();
        dialog.show(getSupportFragmentManager(), getString(R.string.FRAGMENT_TAG_HELP_DIALOG));
    }

    @Override
    public void onDialogPositiveClick(DialogFragment dialog) {
        assert dialog.getTag() != null;
        if (dialog.getTag().equals(getString(R.string.FRAGMENT_TAG_HELP_DIALOG))){
            // Trigger getting help intent
            String helpText = getString(R.string.GET_HELP_TEXT);
            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_TEXT, helpText);
            sendIntent.setType("text/plain");

            Intent shareIntent = Intent.createChooser(sendIntent, null);
            startActivity(shareIntent);
        }
    }

    @Override
    public void onDialogNegativeClick(DialogFragment dialog) {
        assert dialog.getTag() != null;
    }


    private void backToMainActivity(){
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
    }

    /**
     * Check if there is previously placed anchors (in local storage).
     * If yes, reload them from cloud.
     * If not, start to place new anchors.
     */
    private void checkPreviousRecord(){
        this.reloadRewards = readCSV(ANCHOR_FILE_PATH);
        if(this.reloadRewards.size()<1){
            // No previous records
            updateCurrentStep(STEP_PLACE_NEW_ANCHOR);
        }else{
            // Contains previous methods
            updateCurrentStep(STEP_LOAD_FROM_CLOUD);
        }
    }

    /**
     * Update current step and update text in action button
     * @param stepName
     */
    private void updateCurrentStep(String stepName){
        this.currentStep = stepName;
        this.actionBtn.setText(stepName);
    }

    /**
     * Switch between different steps
     */
    private void takeActions(){
        switch (currentStep){
            case STEP_START_APP:
                break;
            case STEP_LOAD_FROM_CLOUD:
                Toast.makeText(this,"Loading previously located anchors...",Toast.LENGTH_LONG).show();
                reloadRewardsAnchorsRecords();
                break;
            case STEP_REMOVE_CURRENT_ANCHOR:
                deleteCurrentSpatialAnchor(true);
                updateCurrentStep(STEP_PLACE_NEW_ANCHOR);
                break;
            case STEP_PLACE_NEW_ANCHOR:
                Toast.makeText(this,"Loading your rewards from map server...",Toast.LENGTH_LONG).show();
                queryUserFeatureLayer();
                updateCurrentStep(STEP_FINISH_PLACE);
                break;
            case STEP_FINISH_PLACE:
                saveRewardAnchorRecords();
                break;
            case STEP_CLEAR_RESTART:
                clearNewPlacing();
                checkPreviousRecord();
                updateCurrentStep(STEP_LOAD_FROM_CLOUD);
                break;
        }
    }

    /**
     * Reload rewards anchor record from cloud
     */
    private void reloadRewardsAnchorsRecords(){
        // Add reloaded rewards in reward lists
        for(String rewardID : reloadRewards.keySet()){
            AppAnchor myAnchor = reloadRewards.get(rewardID);
            String rewardName = myAnchor.getAnchorName();
            myAnchor.setModelPath(rewardToModelPath.get(rewardName));
            myAnchor.setRenderScale(rewardModelScales.get(rewardName));
            loadModels(myAnchor, reloadRewards.size(), false);
        }

        // Relocate all previously placed anchors from cloud
        String[] anchorIdList = reloadRewards.keySet().toArray(new String[0]);
        initializeSession();
        AnchorLocateCriteria criteria = new AnchorLocateCriteria();
        criteria.setIdentifiers(anchorIdList);
        cloudSession.createWatcher(criteria);
    }

    /**
     * Clear newly added anchors in the frame
     */
    private void clearNewPlacing(){
        this.currentLoadedModels = new ArrayList<>();
        if(this.currentAnchorNodes.isEmpty()){
            return;
        }
        // Delete anchor from ar scene
        for(AnchorNode anchorNode : this.currentAnchorNodes){
            if(anchorNode!=null){
                anchorNode.getAnchor().detach();
                anchorNode.setParent(null);
                anchorNode = null;
            }
        }
        this.currentAnchorNodes = new ArrayList<>();
        this.newPlaceRewards = new HashMap<>();

        statusText.setText("");
        scanProgressText.setText("");
        clearSpinner();
    }



    /**
     * Delete spatial anchors in current ar scene
     * @param saved true if they are also saved in local storage, delete them
     */
    private void deleteCurrentSpatialAnchor(boolean saved){
        this.currentLoadedModels = new ArrayList<>();
        if(this.currentCloudAnchors.isEmpty() || this.currentAnchorNodes.isEmpty()){
            return;
        }
        // Delete anchor from ar scene
        for(AnchorNode anchorNode : this.currentAnchorNodes){
            anchorNode.getAnchor().detach();
            anchorNode.setParent(null);
            anchorNode = null;
        }
        this.currentAnchorNodes = new ArrayList<>();
        // TODO: Delete spatial anchor in the cloud
        for(CloudSpatialAnchor cloudSpatialAnchor : this.currentCloudAnchors){
            cloudSession.deleteAnchorAsync(cloudSpatialAnchor);
        }
        this.currentCloudAnchors = new ArrayList<>();
        // Delete csv content
        if(saved){
            writeToCSV("", ANCHOR_FILE_PATH);
        }
        Toast.makeText(this,"Spatial anchors are successfully deleted!", Toast.LENGTH_LONG).show();
    }

    /**
     * Save the uploaded rewards and related anchor ID to local storage
     */
    private void saveRewardAnchorRecords(){
        // Wait until all rewards are marked and spatial anchors uploaded
        for(String reward : newPlaceRewards.keySet()){
            AppAnchor myAnchor = newPlaceRewards.get(reward);
            if (!myAnchor.isUploaded()){
                Toast.makeText(this, "There are still some rewards not located.", Toast.LENGTH_LONG).show();
                return;
            }
        }
        // Save rewards and anchor IDs locally
        String saveString = "";
        for(String reward : this.newPlaceRewards.keySet()){
            AppAnchor myAnchor = newPlaceRewards.get(reward);
            saveString += String.format("%s,%s,%d\n", reward, myAnchor.getAnchorID(), myAnchor.getCount());
        }
        writeToCSV(saveString, ANCHOR_FILE_PATH);
        updateCurrentStep(STEP_CLEAR_RESTART);
    }


    /**
     * Download rewards of a specific user from ArcGIS Feature Layer
     */
    private void queryUserFeatureLayer(){
        // Add all possible reward names
        ArrayList<String> rewardName = new ArrayList<>();
        rewardName.add(getString(R.string.apple));
        rewardName.add(getString(R.string.banana));
        rewardName.add(getString(R.string.icecream));
        rewardName.add(getString(R.string.peach));
        rewardName.add(getString(R.string.watermelon));
        String queryRewardSet = String.join("','", rewardName);

        trackServiceFeatureTable = new ServiceFeatureTable(getString(R.string.URL_ROUND_TRIP_TRACK));
        trackFeatureLayer = new FeatureLayer(trackServiceFeatureTable);
        String userid = getString(R.string.user_id);
        //TODO: Change back user id
//        String userid = "11";
        String queryClause = "user_id = " + userid + " AND reward IN ('"+queryRewardSet+"')";

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
                ArrayList<String> rewards = new ArrayList<String>();
                while (resultIterator.hasNext()) {
                    // get the reward of each queried feature
                    Feature feature = resultIterator.next();
                    String reward = (String) feature.getAttributes().get("reward");
                    rewards.add(reward);
                }
                if(!rewards.isEmpty()){
                    // Count frequency for each type of rewards
                    Map<String, Long> counts =
                            rewards.stream().collect(Collectors.groupingBy(e -> e, Collectors.counting()));
                    // Initialize reward storage information
                    for (String reward : counts.keySet()){
                        AppAnchor myAnchor = new AppAnchor(reward);
                        myAnchor.setCount(counts.get(reward).intValue());
                        myAnchor.setModelPath(rewardToModelPath.get(reward));
                        myAnchor.setRenderScale(rewardModelScales.get(reward));
                        newPlaceRewards.put(reward, myAnchor);
                    }
                    // Load 3D models of rewards
                    for (String reward : counts.keySet()){
                        loadModels(newPlaceRewards.get(reward), newPlaceRewards.size(), true);

                    }
                    Log.d("ASA QueryFeatureTable", "Rewards downloaded: "+String.join(", ", rewards));
                }else{
                    // UI No rewards from feature table
                    Toast.makeText(this, "There is no reward for: "+queryClause, Toast.LENGTH_LONG).show();
                }
            } catch (Exception e) {
                String error = "Feature search failed for: " + queryClause + ". Error: " + e.getMessage();
                // UI ERROR in get rewards from feature table
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                Log.e("ASA QueryFeatureTable", error);
            }
        });
    }

    /**
     * When user tap the frame, if app is in adding new anchor process, then add new anchor to the frame and upload
     * @param hitResult
     * @param plane
     * @param motionEvent
     */
    protected void handleTap(HitResult hitResult, Plane plane, MotionEvent motionEvent) {
        Log.d("ASA HandleTap","Handle Tapped!");
        if(this.currentStep!=STEP_FINISH_PLACE){
            return;
        }
        if(spinner==null){
            return;
        }
        if (spinner.getSelectedItem()==null){
            return;
        }
        String modelName = spinner.getSelectedItem().toString();
        if(modelName.equals(getString(R.string.ar_spinner_default))){
            return;
        }
        // ----------- Initialize spatial anchor cloud session --------------------------
        AppAnchor myAnchor = this.newPlaceRewards.get(modelName);
        this.tappedAnchorName = modelName;
        synchronized (this.syncTaps) {
            if (myAnchor.getTapExecutedFlag()) {
                return;
            }
            myAnchor.setTapExecutedFlag(true);
        }

        initializeSession();

        // ------------- Draw 3D Model at the place of tap -----------------------------

        AnchorNode anchorNode = new AnchorNode();
        anchorNode.setAnchor(hitResult.createAnchor());
        anchorNode.setParent(arFragment.getArSceneView().getScene());
        this.currentAnchorNodes.add(anchorNode);
        myAnchor.setAnchorNode(anchorNode);
        myAnchor.drawAnchorModelinScene(arFragment);

        // ------------ Upload the spatial anchor ----------------------------------------
        // Newly added for spatial anchor
        CloudSpatialAnchor cloudAnchor = new CloudSpatialAnchor();
        cloudAnchor.setLocalAnchor(anchorNode.getAnchor());
        uploadCloudAnchorAsync(cloudAnchor)
                .thenAccept(id -> {
                    myAnchor.setAnchorID(id);
                    myAnchor.setUploaded(true);
                    Log.i("ASAInfo", String.format("Cloud Anchor created: %s, %s", myAnchor.getAnchorName(), myAnchor.getAnchorID()));
                    //ui for finished uploading
                    runOnUiThread(() -> {
                        scanProgressText.setText("");
                        statusText.setText(String.format("Successfully uploaded %s!", modelName));
                    });
                });

    }

    /**
     * Upload the spatial anchor to the cloud
     * @param anchor added anchor
     * @return
     */
    private CompletableFuture<String> uploadCloudAnchorAsync(CloudSpatialAnchor anchor) {
        synchronized (this.syncSessionProgress) {
            this.scanningForUpload = true;
        }
        return CompletableFuture.runAsync(() -> {
            try {
                float currentSessionProgress;
                do {
                    synchronized (this.syncSessionProgress) {
                        currentSessionProgress = this.recommendedSessionProgress;
                    }
                    if (currentSessionProgress < 1.0) {
                        Thread.sleep(500);
                    }
                }
                while (currentSessionProgress < 1.0);
                synchronized (this.syncSessionProgress) {
                    this.scanningForUpload = false;
                }
                //TODO: ui for once collected enough frames
                runOnUiThread(() -> {
                    scanProgressText.setText(String.format("%s : Scanning finished!", this.tappedAnchorName));
                    statusText.setText(String.format("Now uploading %s...", this.tappedAnchorName));
                });
                this.cloudSession.createAnchorAsync(anchor).get();
            } catch (InterruptedException | ExecutionException e) {
                Log.e("ASAError Upload", e.toString());
                statusText.setText("An error occurred in uploading. Please relocate the anchor!");

                // Remove the anchor node drawn before
                AnchorNode anchorNodeToDelete = this.currentAnchorNodes.get(this.currentAnchorNodes.size()-1);
                anchorNodeToDelete.getAnchor().detach();
                anchorNodeToDelete.setParent(null);
                anchorNodeToDelete = null;
                this.currentAnchorNodes.remove(this.currentAnchorNodes.size()-1);
                // Reactivate tap execution
                synchronized (this.syncTaps) {
                        this.newPlaceRewards.get(this.tappedAnchorName).setTapExecutedFlag(false);
                }
//                throw new RuntimeException(e);
            }
        }, executorService).thenApply(ignore -> anchor.getIdentifier());
    }

    /**
     * Initialize Spatial Anchor Service Session
     * Once called, it will ensure an Azure Spatial Anchors session is created and properly
     * initialized during the startup of your app.
     */
    private void initializeSession() {
        if(!sessionInited){
            this.sceneView = arFragment.getArSceneView();
            Scene scene = arFragment.getArSceneView().getScene();
            scene.addOnUpdateListener(frameTime -> {
                if (this.cloudSession != null) {
                    this.cloudSession.processFrame(sceneView.getArFrame());
                }
            });
        }
        if (this.cloudSession != null){
            this.cloudSession.close();
        }
        // Initialize session
        this.cloudSession = new CloudSpatialAnchorSession();
        this.cloudSession.setSession(sceneView.getSession());
        this.cloudSession.setLogLevel(SessionLogLevel.Information);
        this.cloudSession.addOnLogDebugListener(args -> Log.d("ASAInfo",
                args.getMessage()));
        this.cloudSession.addErrorListener(args -> Log.e("ASAError", String.format("%s: %s",
                args.getErrorCode().name(), args.getErrorMessage())));

        // Scanning to collect frames of session
        this.cloudSession.addSessionUpdatedListener(args -> {
            synchronized (this.syncSessionProgress) {
                this.recommendedSessionProgress =
                        args.getStatus().getRecommendedForCreateProgress();
                Log.i("ProgressInfo", String.format("Scanning progress: %f",
                        this.recommendedSessionProgress));
                if (!this.scanningForUpload)
                {
                    return;
                }
            }

            runOnUiThread(() -> {
                synchronized (this.syncSessionProgress) {
                    int progressNum = (int) Math.round(this.recommendedSessionProgress*100.0);
                    if(progressNum>100){
                        progressNum = 100;
                    }
                    // UI for showing progress during collecting frames
                    scanProgressText.setText(String.format("Session progress: %d%%",
                            progressNum));
                }
            });
        });

        // When the spatial anchor we saved in cloud is located
        this.cloudSession.addAnchorLocatedListener(args -> {
            Log.d("ASA LocatedListener","Hi");
            try{
                LocateAnchorStatus status = args.getStatus();

                runOnUiThread(() -> {
                    switch (status) {
                        case AlreadyTracked:
                            break;
                        case Located:
                            runOnUiThread(()->{
                                CloudSpatialAnchor foundAnchor = args.getAnchor();
                                String anchorId = foundAnchor.getIdentifier();
                                AppAnchor myAnchor = reloadRewards.get(anchorId);
                                String anchorName = myAnchor.getAnchorName();

                                AnchorNode anchorNode =  new AnchorNode();
                                anchorNode.setAnchor(foundAnchor.getLocalAnchor());
                                this.currentCloudAnchors.add(foundAnchor);
                                this.currentAnchorNodes.add(anchorNode);
                                myAnchor.setAnchorNode(anchorNode);

                                Log.d("ASALocated", "Spatial Anchor Located : "+anchorId+","+anchorName);
                                myAnchor.drawAnchorModelinScene(arFragment);
                                MaterialFactory.makeOpaqueWithColor(this, new Color(android.graphics.Color.WHITE))
                                        .thenAccept(greenMaterial -> {
                                            this.nodeRenderable = ShapeFactory.makeSphere(0.01f, new Vector3(0.0f, 0.0f, 0.0f), greenMaterial);
                                            anchorNode.setRenderable(nodeRenderable);
                                            anchorNode.setParent(arFragment.getArSceneView().getScene());
                                        });
                                if(this.currentAnchorNodes.size()==this.reloadRewards.size()){
                                    // Finished loading
                                    updateCurrentStep(STEP_REMOVE_CURRENT_ANCHOR);
                                }else{
                                    int left = this.reloadRewards.size() - this.currentAnchorNodes.size();
                                    Toast.makeText(this, String.format("Successfully find %s, %d left to be found!", myAnchor.getAnchorName(), left),Toast.LENGTH_SHORT).show();
                                }
                            });
                            break;

                        case NotLocatedAnchorDoesNotExist:
                            Toast.makeText(this, "Anchor does not exist ",Toast.LENGTH_LONG).show();
                            break;
                    }
                });
            }catch (Exception e){
                Log.e("ASA LocatedListener", e.toString());
            }
        });
        this.cloudSession.getConfiguration().setAccountId(getString(R.string.accountID));
        this.cloudSession.getConfiguration().setAccountKey(getString(R.string.accountKey));
        this.cloudSession.getConfiguration().setAccountDomain(getString(R.string.accountDomain));
        this.cloudSession.start();
        sessionInited = true;
    }



    /**
     * A function to load the 3D models
     * For local 3D models, only .glb or .gltf (2.0) can be loaded
     */
    public void loadModels(AppAnchor myAnchor, int fullLoadedSize, boolean setUpSpinnerFlag) {
        WeakReference<PlaceRewardsActivity> weakActivity = new WeakReference<>(this);

        ModelRenderable.builder()
                .setSource(this, Uri.parse(myAnchor.getModelPath()))
                .setIsFilamentGltf(true)
                .build()
                .thenAccept(model -> {
                    myAnchor.setModel(model);
                    PlaceRewardsActivity activity = weakActivity.get();
                    if (activity != null) {
                        activity.currentLoadedModels.add(myAnchor.getAnchorName());
                        // Check if all required models are loaded
                        if(activity.currentLoadedModels.size()==fullLoadedSize){
                            // TODO: UI successfully loaded all required 3D models
//                            Toast.makeText(this, "Load model successfully", Toast.LENGTH_LONG).show();
                            statusText.setText("");
                            Log.d("ASA LoadModel","Models loaded!");
                            if(setUpSpinnerFlag){
                                setUpSpinner();
                            }
                        }
                    }
                })
                .exceptionally(throwable -> {
                    // TODO: UI cannot load 3D Model
                    Toast.makeText(this, "Unable to load model", Toast.LENGTH_LONG).show();
                    return null;
                });
        ViewRenderable.builder()
                .setView(this, R.layout.view_reward_card)
                .build()
                .thenAccept(viewRenderable -> {
                    TextView textView = viewRenderable.getView().findViewById(R.id.titleText);
                    textView.setText(String.format("%s : %d", myAnchor.getAnchorName(), myAnchor.getCount()));
                    myAnchor.setTitleModel(viewRenderable);
//                    PlaceRewardsActivity activity = weakActivity.get();
//                    if (activity != null) {
//                        activity.rewardsTitleModel.put(myAnchor.getAnchorName(), viewRenderable);}
                })
                .exceptionally(throwable -> {
                    // TODO: UI cannot load 3D Model Title
                    Toast.makeText(this, "Unable to load title model", Toast.LENGTH_LONG).show();
                    return null;
                });
    }

    /**
     * setUpSpinner
     * Create spinner. Choices are the user ids queried from Service Feature Table
     */
    private void setUpSpinner(){
        String[] allChoices = new String[newPlaceRewards.size()+1];
        allChoices[0] = getString(R.string.ar_spinner_default);
        int i = 1;
        for(String reward : newPlaceRewards.keySet()){
            allChoices[i] = reward;
            i++;
        }
        // create an ArrayAdapter using checkpoints names (string array) and a default spinner layout
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item, allChoices);
        // specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // apply the adapter to the spinner
        spinner.setAdapter(adapter);
        spinner.setVisibility(View.VISIBLE);
    }


    private void clearSpinner(){
        spinner.setVisibility(View.GONE);
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
                FileOutputStream outputStream = new FileOutputStream(file, false);
                PrintWriter writer = new PrintWriter(outputStream);

                writer.println(line);
                writer.close();
                outputStream.close();
                Log.d("ASA FileLog", "File Saved :  " + file.getPath());

                Toast.makeText(this, getString(R.string.record_saved),Toast.LENGTH_LONG).show();
                return;
            }catch(IOException e){
                Log.e("ASA FileLog", "Fail to write file "+ e.toString());
            }
        }else{
            Log.e("ASA FileLog", "SD card not mounted");
        }
        Toast.makeText(this, getString(R.string.record_not_saved),Toast.LENGTH_LONG).show();
    }

    /**
     * Read csv file about reward - anchor id mapping
     * @param path path of file to read from
     * @return
     */
    private HashMap<String, AppAnchor> readCSV(String path){
        HashMap<String, AppAnchor> reward_to_anchor = new HashMap<>();
        if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){
            try{
                //Get the text file
                File file = new File(getExternalFilesDir(null),path);

                //Read text from file
                StringBuilder text = new StringBuilder();

                BufferedReader br = new BufferedReader(new FileReader(file));
                String line;

                while ((line = br.readLine()) != null) {
                    String[] recs = line.replace("\n","").split(",");
                    if(recs.length>1){
                        String rewardName = recs[0];
                        String anchorID = recs[1];
                        int rewardNum = Integer.parseInt(recs[2]);
                        AppAnchor myAnchor = new AppAnchor(rewardName, anchorID, rewardNum);
                        reward_to_anchor.put(anchorID, myAnchor);
                    }
                }
                br.close();
                Log.d("ASA FileLog", "Read anchors from local storage!");
                return reward_to_anchor;
            }catch(IOException e){
                Log.e("ASA FileLog", "Fail to read file: "+e.toString());
            }
        }else{
            Log.e("ASA FileLog", "SD card not mounted");
        }
        // UI Cannot find local file
        Toast.makeText(this, getString(R.string.record_not_exist),Toast.LENGTH_LONG).show();
        return reward_to_anchor;
    }

    @Override
    protected void onResume() {
        super.onResume();

        // ARCore requires camera permission to operate.
        if (!(ContextCompat.checkSelfPermission(this, CAMERA_PERMISSION)
                == PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(
                    this, new String[] {CAMERA_PERMISSION}, CAMERA_PERMISSION_CODE);
            //return;
        }

        // Ensure that Google Play Services for AR and ARCore device profile data are
        // installed and up to date.
        try {
            switch (ArCoreApk.getInstance().requestInstall(this, mUserRequestedInstall)) {
                case INSTALL_REQUESTED:
                    // When this method returns `INSTALL_REQUESTED`:
                    // 1. ARCore pauses this activity.
                    // 2. ARCore prompts the user to install or update Google Play
                    //    Services for AR (market://details?id=com.google.ar.core).
                    // 3. ARCore downloads the latest device profile data.
                    // 4. ARCore resumes this activity. The next invocation of
                    //    requestInstall() will either return `INSTALLED` or throw an
                    //    exception if the installation or update did not succeed.
                    mUserRequestedInstall = false;
                    return;
            }
        }
        catch (UnavailableUserDeclinedInstallationException | UnavailableDeviceNotCompatibleException e) {
            // Display an appropriate message to the user and return gracefully.
            Toast.makeText(this, "Exception creating session: " + e, Toast.LENGTH_LONG)
                    .show();
            return;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);

        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (results.length>0 && results[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, getString(R.string.toast_camera_permission), Toast.LENGTH_LONG)
                        .show();
            }
        }
    }

    /**
     * Check if Google Play for AR is supported
     */
    void checkARCoreSupported() {
        ArCoreApk.Availability availability = ArCoreApk.getInstance().checkAvailability(this);
        if (availability.isTransient()) {
            // Continue to query availability at 5Hz while compatibility is checked in the background.
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    checkARCoreSupported();
                }
            }, 200);
        }
    }
}