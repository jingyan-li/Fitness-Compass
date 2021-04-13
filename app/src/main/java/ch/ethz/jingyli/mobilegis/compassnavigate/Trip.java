package ch.ethz.jingyli.mobilegis.compassnavigate;

import android.location.Location;
import java.util.ArrayList;



/**
 * Class to represent a round trip, with a start point and a check point
 * Users start the trip from start point, add intermediate points on the way to check point, then go back to the start point
 * Users can get duration, distance, average speed and average temperature of the trip
 * Users can check in which stage he or she is in (on the way to start point or to check point or arrived)
 *
 * @author Jingyan Li
 */

public class Trip {
    private Location startPoint;
    private Location checkPoint;
    private long startTime; //Time in milliseconds
    private boolean started = false;

    // Variables to record along trip
    private Location currentLocation;
    private long currentTime; // current time in milliseconds
    private double totalDuration; // duration of the trip in seconds.
    private double totalDistance; // accumulated distance in meter.
    private double currentSpeed;
    private boolean goTrip;
    private ArrayList<Double> temperatureList;

    /**
     * Create a new trip
     * @param checkPoint: checkPoint heading to
     */
    public Trip(Location checkPoint){
        this.checkPoint = checkPoint;
        temperatureList = new ArrayList<>();
    }

    // ----------------------FUNCTIONAL METHODS----------------------

    /**
     * Use this method when a trip starts
     * @param startPoint: start point of a trip
     * @param nowTime: start time in milliseconds
     */
    public void startTrip(Location startPoint, long nowTime){
        this.started = true;
        this.goTrip = true;
        // Set start point
        this.startPoint = startPoint;
        this.currentLocation = startPoint;
        // Set start time
        this.startTime = nowTime;
        this.currentTime = nowTime;
        // Initiate accumulated distance to zero
        this.totalDistance = 0.;
        this.totalDuration = 0.;
    }

    /**
     * Add intermediate point of the trip.
     * @param nowPoint: Current point
     * @param nowTime: Current time in milliseconds
     * @param temperature: Current temperature
     */
    public void addIntermediatePoint(Location nowPoint, long nowTime, double temperature){
        // Get distance between last location and current location
        double distance = nowPoint.distanceTo(this.currentLocation);
        // Get duration in seconds between last location and current location
        double duration = (nowTime - this.currentTime)/1000.0;
        // Calculate current speed (m/s)
        this.currentSpeed = distance/duration;

        // Update total distance/duration, current time, current location
        this.totalDistance += distance;
        this.totalDuration += duration;
        this.currentTime = nowTime;
        this.currentLocation = nowPoint;
        this.temperatureList.add(temperature);
    }

    /**
     * Test the trip stage
     * @param nowPoint current location
     * @param threshold threshold in meters of evaluating whether arrived the checkpoint
     * @return resource id (int) of trip stage
     */
    public int testStage(Location nowPoint, double threshold) {
        if(this.goTrip){
            if(testArrived(nowPoint, checkPoint, threshold)){
                // Arrive at checkpoint
                this.goTrip = false;
                return R.string.trip_stage_arrive_checkpoint;
            }else{
                // On the way to find checkpoint
                return R.string.trip_stage_find_checkpoint;
            }
        }else{
            if(testArrived(nowPoint, startPoint, threshold)){
                // Arrive at startpoint
                this.goTrip = true;
                return R.string.trip_stage_arrive_startpoint;
            }else{
                // On the way to find startpoint
                return R.string.trip_stage_find_startpoint;
            }
        }
    }

    /**
     * Test whether user arrived the checkpoint.
     * @param nowPoint: current location
     * @param destPoint: destination location (check point or start point)
     * @param threshold: threshold in meters of evaluating whether arrived the checkpoint
     * @return arrived: If the current location of user to the check point is smaller than the threshold, return true
     */
    private boolean testArrived(Location nowPoint, Location destPoint, double threshold) {
        double distance = nowPoint.distanceTo(destPoint);
        boolean arrived = distance <= threshold;
        return arrived;
    }


    /**
     * Test if the trip starts or not
     * @return true if the trip already starts
     */
    public boolean testStarted(){
        return this.started;
    }

    /**
     * Get current distance to start point
     * @return double distance in meter
     */
    public double currentDistanceToStart(){
        return this.currentLocation.distanceTo(this.startPoint);
    }

    /**
     * Get current distance to checkpoint
     * @return double distance in meter
     */
    public double currentDistanceToCheck(){
        return this.currentLocation.distanceTo(this.checkPoint);
    }

    /**
     * Get current east to the north angle to start point
     * @return double angle in degree
     */
    public double currentAngleToStart(){
        return this.currentLocation.bearingTo(this.startPoint);
    }

    /**
     * Get current east to the north angle to check point
     * @return double angle in degree
     */
    public double currentAngleToCheck(){
        return this.currentLocation.bearingTo(this.checkPoint);
    }

    // ---------------------- Getters ----------------------
    public Location getStartPoint() {
        return startPoint;
    }

    public Location getCheckPoint() {
        return checkPoint;
    }

    /**
     * Get start time timestamp in milliseconds
     * @return long start time timestamp in milliseconds
     */
    public long getStartTime() {
        return startTime;
    }

    public Location getCurrentLocation() {
        return currentLocation;
    }

    /**
     * Get current speed in km/h
     * @return double current speed in km/h
     */
    public double getCurrentSpeed() {
        // convert m/s to km/h
        return currentSpeed*3.6;
    }

    /**
     * Get total distance of the trip
     * @return double total distance in meter
     */
    public double getTotalDistance(){
        return totalDistance;
    }

    /**
     * Get total duration of the trip
     * @return double total duration in seconds
     */
    public double getTotalDuration(){
        return totalDuration;
    }

    /**
     * Get average temperature of the trip
     * @return double average temperature in degree
     */
    public double getAverageTemparture(){
        Double sum = 0.;
        if(!temperatureList.isEmpty()) {
            for (Double temperature : temperatureList) {
                sum += temperature;
            }
            return sum.doubleValue() / temperatureList.size();
        }
        return -100;
    }

    /**
     * Get average speed of the trip
     * @return double average speed in km/h
     */
    public double getAverageSpeed(){
        // convert m/s to km/h
        return totalDistance/totalDuration*3.6;
    }

}
