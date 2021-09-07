# Compass app in Android

:watch: ​Course project for Mobile GIS & LBS 

## Description

A new start-up company asks you to develop a location-based fitness app based on Android.
The fitness app aims at encouraging the users to get out of their home and do a round trip
running or walking near their home.

## Highlights

The major functions implemented include:
 - directing and giving instructions to the destination by a compass, 
 - recording users' trajectories and uploading to ArcGIS Server once the trip is finished,
 - calculating rewards of the trip according to speed, distance and other factors, and
 - creating an AR scene where users can locate their rewards around them and storing rewards as spatial anchors. 

## Functions

### Compass navigation functions

:point_right: The app should read a list of possible checkpoints from the file system

:point_right: After starting the app, a user can select one checkpoint from a drop-down that they want to go to

:point_right: A basic compass screen shows the direction and distance to the checkpoint’s position
(like on a radar)

- Compass directs to the position of checkpoint

:point_right: ​Show the current speed of the user and the temperature

:point_right: ​When the user reaches the checkpoint, the app should notify the user with either text or audio. 

:point_right: ​To make the trip a complete round trip, the compass should then point towards the starting location again.

:point_right: ​Once the user completed the round trip (i.e., arrive the starting location), the app should again notify the user, provide a reward, and save the data to a CSV file. 

### Sharing trajectories

Users need an ArcGIS API key to upload their records to ArcGIS server.

:point_right: **Social sharing option:** users can share their latest record as a simple text. 

 - Users can share by click the share button in the main activity.
 - When users click back button to quit the app, a dialog will pop up to ask for sharing.

:point_right: **Track upload:** When a trip is finished, a dialog pops up to ask for uploading records to ArcGIS server. Users can click `SHARE` to view the trajectory and checkpoint, and upload the records.

:point_right: **Track review:**  Users can view all tracks by clicking the map button in the main activity. 

- Users can select from the spinner to show records of a certain user
- Users can tap a trajectory to look through the attributes of the trajectory.

### Locating rewards in AR Scene

Users need to download Google Play Services for AR, and have an Azure account to upload spatial anchors.

:point_right: **Locating rewards**: users can find the rewards they got in the past trips, and place the rewards in an AR scene. Finally the placed rewards are uploaded as Azure spatial anchors.

:point_right: **Placing rewards**: users can re-establish all previously stored rewards (i.e., spatial anchors) in the AR scene.

:point_right: **Cleaning rewards**: users can delete all located rewards and relocate them.

## UI

### Compass navigation

<img src="./example/screenshot.png" alt="main activity" style="zoom:60%;" />

<img src="./example/screenshot2.png" alt="UI_Fullscreen" style="zoom:60%;" />

### Sharing trajectories

<img src="./example/upload_track.png" alt="upload track" style="zoom:60%;" />

<img src="./example/track_review.png" alt="track review" style="zoom:60%;" />

### Locating rewards in AR Scene

<img src="./example/ar1.png" alt="track review" style="zoom:60%;" />



## Requirements

Android Studio 3.4.1 

API level min. 19

Google Play Services for AR installed in the device

