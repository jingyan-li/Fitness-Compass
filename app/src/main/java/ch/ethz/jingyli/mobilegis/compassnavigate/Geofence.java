package ch.ethz.jingyli.mobilegis.compassnavigate;

import android.location.Location;

/**
 * Geofence: name, lat, lon, radius
 * Ref: lab material
 */

public class Geofence {
    private double latitude;
    private double longitude;
    private double radius;

    private String name;

    /**
     * Creates a new Geofence.
     *
     * @param name The name of this geofence.
     * @param latitude The latitude.
     * @param longitude The longitude.
     * @param radius The radius.
     */
    public Geofence(String name, double latitude, double longitude, double radius) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radius = radius;
    }

    public Geofence(String name, Location location, double radius){
        this.name = name;
        this.latitude = location.getLatitude();
        this.longitude = location.getLongitude();
        this.radius = radius;
    }

    /**
     * Transforms this Geofence into an Android location.
     *
     * @return A location containing latitude and longitude of this Geofence.
     */
    public Location getLocation() {
        Location target = new Location("");
        target.setLatitude(latitude);
        target.setLongitude(longitude);
        return target;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public void setRadius(double radius) {
        this.radius = radius;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getRadius() {
        return radius;
    }

    public String getName() {
        return name;
    }

}
