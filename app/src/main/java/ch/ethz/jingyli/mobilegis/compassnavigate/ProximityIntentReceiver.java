package ch.ethz.jingyli.mobilegis.compassnavigate;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.LocationManager;
import android.util.Log;
import androidx.core.app.NotificationCompat;

/**
 * This is an external BroadcastReceiver, which runs independently of our MainActivity. It will create
 * notification in the notification bar to tell user he or she almost arrived the destination
 *
 * Ref: lab material
 */
public class ProximityIntentReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String name = intent.getStringExtra("name");
        Boolean entering = intent.getBooleanExtra(LocationManager.KEY_PROXIMITY_ENTERING, false);
        Log.d("ProximityIntentReceiver","Received!");
        if (entering) {
            Log.d(getClass().getSimpleName(), "Entering Geofence " + name);
            sendNotification(context, "You almost arrive at " + name);
        } else {
            Log.d(getClass().getSimpleName(), "Exiting Geofence " + name);
            sendNotification(context, "You are leaving " + name);
        }
    }


    /**
     * Posts a notification in the notification bar when a transition is detected.
     * If the user clicks the notification, control goes to the MainActivity.
     *
     * See https://developer.android.com/training/location/geofencing.html for more details.
     */
    private void sendNotification(Context context, String notificationDetails) {
        // Create an explicit content Intent that starts the main Activity.
        Intent notificationIntent = new Intent(context, MainActivity.class);

        // Construct a task stack.
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);

        // Add the main Activity to the task stack as the parent.
        stackBuilder.addParentStack(MainActivity.class);

        // Push the content Intent onto the stack.
        stackBuilder.addNextIntent(notificationIntent);

        // Get a PendingIntent containing the entire back stack.
        PendingIntent notificationPendingIntent =
                stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

        // Get a notification builder that's compatible with platform versions >= 4
        String CHANNEL_ID = context.getResources().getString(R.string.channel_id);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID);

        // Define the notification settings.
        builder.setSmallIcon(R.drawable.ic_launcher_foreground)
                // In a real app, you may want to use a library like Volley
                // to decode the Bitmap.
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(),
                        R.drawable.ic_launcher_foreground))
                .setColor(Color.RED)
                .setContentTitle(notificationDetails)
                .setContentText(context.getString(R.string.proximity_text))
                .setContentIntent(notificationPendingIntent);

        // Dismiss notification once the user touches it.
        builder.setAutoCancel(true);

        // Get an instance of the Notification manager
        NotificationManager mNotificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // Issue the notification
        mNotificationManager.notify(0, builder.build());
    }

}