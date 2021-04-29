package ch.ethz.jingyli.mobilegis.compassnavigate;

import android.location.Location;

import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.geometry.PointCollection;
import com.esri.arcgisruntime.geometry.Polyline;
import com.esri.arcgisruntime.geometry.SpatialReference;


import java.util.ArrayList;
import java.util.Calendar;

public class Utils {
    /**
     * Get current time
     * @return current time in milliseconds
     */
    public static long getCurrentTime(){
        //creating Calendar instance
        Calendar calendar = Calendar.getInstance();
        //Returns current time in millis
        long timeMilli2 = calendar.getTimeInMillis();
        return timeMilli2;
    }

    /**
     * Check which rewards the trip gets
     * @param avg_speed: average speed (km/h)
     * @param distance: distance (m)
     * @param avg_temp: average temperature (degree)
     * @return reward string
     */
    public static String checkRewards(double avg_speed, double distance, double avg_temp){
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

    public static Point locationToPoint(Location location,
                                  SpatialReference spatialReference) {
        Point point  = new Point(location.getLongitude(), location.getLatitude(), spatialReference);
        return point;
    }
    public static Polyline locationArrayToPolyline(ArrayList<Location> traj, SpatialReference spatialReference){
        PointCollection pointCollection = new PointCollection(spatialReference);
        for(Location location: traj){
            pointCollection.add(locationToPoint(location, spatialReference));
        }
        Polyline polyline = new Polyline(pointCollection, spatialReference);
        return polyline;
    }

    public static String locationArrayToString(ArrayList<Location> traj){
        String data = "";
        for(Location location: traj){
            data += String.format("%f,%f,", location.getLongitude(), location.getLatitude());
        }
        return data.substring(0, data.length()-1);
    }
    public static String locationToString(Location location){
        return String.format("%f,%f", location.getLongitude(),location.getLatitude());
    }

    public static Polyline trackStringToPolyline(String traj, SpatialReference spatialReference){
        PointCollection pointCollection = new PointCollection(spatialReference);
        String[] coords = traj.split(",");
        for(int i=1;i<coords.length;i+=2){
            double lng = Double.parseDouble(coords[i-1]);
            double lat = Double.parseDouble(coords[i]);
            Point point = new Point(lng, lat, spatialReference);
            pointCollection.add(point);
        }
        return new Polyline(pointCollection, spatialReference);
    }
    public static Point pointStringToPoint(String point, SpatialReference spatialReference){
        String[] coords = point.split(",");
        double lng = Double.parseDouble(coords[0]);
        double lat = Double.parseDouble(coords[1]);
        return new Point(lng,lat,spatialReference);
    }
}
