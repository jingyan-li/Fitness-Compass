package ch.ethz.jingyli.mobilegis.compassnavigate;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;

import com.esri.arcgisruntime.ArcGISRuntimeEnvironment;
import com.esri.arcgisruntime.layers.ArcGISTiledLayer;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.Viewpoint;
import com.esri.arcgisruntime.mapping.view.MapView;

public class ShowFeature extends AppCompatActivity {
    private MapView mMapView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_feature);

        // set your API key, Read the api key from string.xml
        // authentication with an API key or named user is required to access basemaps and other location services
        ArcGISRuntimeEnvironment.setApiKey(getString(R.string.ARCGIS_API));
        // inflate MapView from layout
        mMapView = (MapView)findViewById(R.id.mapView);
        // Create a map with tiled layer as basemap
        ArcGISTiledLayer world_topo = new ArcGISTiledLayer(getString(R.string.world_topo_service));
        Basemap basemap = new Basemap(world_topo);
        ArcGISMap map = new ArcGISMap(basemap);

        // Listener on change in map load status
        map.addLoadStatusChangedListener(loadStatusChangedEvent -> {
            String mapLoadStatus = loadStatusChangedEvent.getNewLoadStatus().name();
            Log.d("Map load status",mapLoadStatus);
        });
        // set the map to be displayed in this view
        mMapView.setMap(map);
        mMapView.setViewpoint(new Viewpoint(47.408992, 8.507847,
                500));
    }
}