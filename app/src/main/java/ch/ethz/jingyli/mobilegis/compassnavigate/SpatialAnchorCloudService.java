package ch.ethz.jingyli.mobilegis.compassnavigate;

import android.app.Application;

import com.microsoft.CloudServices;

public class SpatialAnchorCloudService extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Use application's context to initialize CloudServices!
        CloudServices.initialize(this);
    }
}
