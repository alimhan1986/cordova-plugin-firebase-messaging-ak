package by.chemerisuk.cordova.firebase;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.service.notification.StatusBarNotification;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.net.URL;
import java.net.HttpURLConnection;
import java.io.InputStream;
import java.io.IOException;

import com.wazzup.mobile.MainActivity;

import static android.content.ContentResolver.SCHEME_ANDROID_RESOURCE;


public class FirebaseMessagingPluginService extends FirebaseMessagingService {
    private static final String TAG = "FCMPluginService";

    public static final String ACTION_FCM_MESSAGE = "by.chemerisuk.cordova.firebase.ACTION_FCM_MESSAGE";
    public static final String EXTRA_FCM_MESSAGE = "by.chemerisuk.cordova.firebase.EXTRA_FCM_MESSAGE";
    public static final String ACTION_FCM_TOKEN = "by.chemerisuk.cordova.firebase.ACTION_FCM_TOKEN";
    public static final String EXTRA_FCM_TOKEN = "by.chemerisuk.cordova.firebase.EXTRA_FCM_TOKEN";
    public final static String NOTIFICATION_ICON_KEY = "com.google.firebase.messaging.default_notification_icon";
    public final static String NOTIFICATION_COLOR_KEY = "com.google.firebase.messaging.default_notification_color";
    public final static String NOTIFICATION_CHANNEL_KEY = "com.google.firebase.messaging.default_notification_channel_id";

    private LocalBroadcastManager broadcastManager;
    private NotificationManager notificationManager;
    private int defaultNotificationIcon;
    private int defaultNotificationColor;
    private String defaultNotificationChannel;

    @Override
    public void onCreate() {
        Log.i(TAG, "create");
        broadcastManager = LocalBroadcastManager.getInstance(this);
        notificationManager = ContextCompat.getSystemService(this, NotificationManager.class);

        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(getApplicationContext().getPackageName(), PackageManager.GET_META_DATA);
            defaultNotificationIcon = ai.metaData.getInt(NOTIFICATION_ICON_KEY, ai.icon);
            defaultNotificationChannel = ai.metaData.getString(NOTIFICATION_CHANNEL_KEY, "default");
            defaultNotificationColor = ContextCompat.getColor(this, ai.metaData.getInt(NOTIFICATION_COLOR_KEY));
        } catch (PackageManager.NameNotFoundException e) {
            // Log.e(TAG, "Failed to load meta-data", e);
        } catch(Resources.NotFoundException e) {
            // Log.e(TAG, "Failed to load notification color", e);
        }
        // On Android O or greater we need to create a new notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel defaultChannel = notificationManager.getNotificationChannel(defaultNotificationChannel);
            if (defaultChannel == null) {
                notificationManager.createNotificationChannel(
                        new NotificationChannel(defaultNotificationChannel, "Firebase", NotificationManager.IMPORTANCE_HIGH));
            }
        }

        // Runnable runnable = new Runnable() {
        //     public void run() {
        //         int j = 0;
        //         while(j < 15) {
        //             j++;
        //             StatusBarNotification [] nots = notificationManager.getActiveNotifications();
        //             for (int i = 0; i < nots.length; i++) {
        //                 String title = nots[i].getNotification().extras.getString("android.title");
        //                 String tag = nots[i].getTag();
        //                 int id = nots[i].getId();
        //                 if (title == null) {
        //                     notificationManager.cancel(tag, id);
        //                     j = 15;
        //                 }
        //             }
        //             try {
        //                 Thread.sleep(200);
        //             } catch (InterruptedException e) {
        //                 // nothing
        //             }
        //         }
        //     }
        // };
        // Thread thread = new Thread(runnable);
        // thread.start();
    }

    @Override
    public void onNewToken(String token) {
        FirebaseMessagingPlugin.sendToken(token);

        Intent intent = new Intent(ACTION_FCM_TOKEN);
        intent.putExtra(EXTRA_FCM_TOKEN, token);
        broadcastManager.sendBroadcast(intent);
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.i(TAG, "message--------------------------------------");
        Log.i(TAG, "body: " + remoteMessage.getData());
        try {
            JSONObject data = new JSONObject(remoteMessage.getData());
            String tag = String.join(data.getString("chatType"), data.getString("chatId"), data.getString("channelId"));
            String eventType = data.getString("eventType");
            boolean isPaused = FirebaseMessagingPlugin.isPaused();
            // Log.i(TAG, "tag: " + tag);
            // Log.i(TAG, "eventType: " + eventType);
            // Log.i(TAG, "dataInData: " + dataInData);
            // Log.i(TAG, "isDev: " + isDev);
            // Log.i(TAG, "message: " + message);
            if (eventType.equals("inputMessage") && isPaused) {
                Context ctx = this;
                Runnable runnable = new Runnable() {
                    public void run() {
                        try {
                            String isDev = data.getString("isDev");
                            JSONObject dataInData = new JSONObject(data.getString("data"));
                            JSONObject message = new JSONObject(dataInData.getString("message"));
                            String messageType = message.getString("type");
                            String text = messageType.equals("2") ? message.getString("filename") : message.getString("text");
                            String avatar = dataInData.getString("avatar");
                            int chatUnanswered = dataInData.getInt("chatUnanswered");
                            Bitmap icon = getBitmapFromURL("https://store.dev-wazzup24.com/" + avatar);
                            Log.i(TAG, "chatUnanswered: " + String.valueOf(chatUnanswered));
                            Intent intent = new Intent(ctx, MainActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            PendingIntent pendingIntent = PendingIntent.getActivity(ctx, 0, intent, 0);

                            NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, "default")
                                .setContentTitle(dataInData.getString("contactName") + " — " + dataInData.getString("channelName"))
                                .setContentText(text)
                                .setGroup(tag)
                                .setLargeIcon(icon)
                                .setSmallIcon(defaultNotificationIcon)
                                .setColor(defaultNotificationColor)
                                .setNumber(10)
                                .setAutoCancel(true)
                                // .setSilent(true)
                                .setContentIntent(pendingIntent)
                                .setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
                                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
                            notificationManager.notify(tag, 0, builder.build());
                        } catch (JSONException e) {
                            Log.e(TAG, "onMessageReceived JSONException", e);
                        }
                    }
                };
                Thread thread = new Thread(runnable);
                thread.start();
            }

            if (eventType.equals("outputMessage") || eventType.equals("clearUnanswered")) {
                notificationManager.cancel(tag, 0);
            }
        } catch (JSONException e) {
            Log.e(TAG, "onMessageReceived JSONException", e);
        } catch (Exception e1) {
            Log.e(TAG, "onMessageReceived Exception", e1);
        }
        Log.i(TAG, "isPaused: " + String.valueOf(FirebaseMessagingPlugin.isPaused()));
        // StatusBarNotification [] nots = notificationManager.getActiveNotifications();
        // Log.i(TAG, "nots lenght: " + String.valueOf(nots.length));
        FirebaseMessagingPlugin.sendNotification(remoteMessage);
        // Intent intent = new Intent(ACTION_FCM_MESSAGE);
        // intent.putExtra(EXTRA_FCM_MESSAGE, remoteMessage);
        // broadcastManager.sendBroadcast(intent);

        // if (FirebaseMessagingPlugin.isForceShow()) {
        //     RemoteMessage.Notification notification = remoteMessage.getNotification();
        //     if (notification != null) {
        //         showAlert(notification);
        //     }
        // }
    }

    public static Bitmap getBitmapFromURL(String src) {
        try {
            URL url = new URL(src);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            Bitmap myBitmap = BitmapFactory.decodeStream(input);
            return myBitmap;
        } catch (IOException e) {
            // Log exception
            return null;
        }
    }

    private void showAlert(RemoteMessage.Notification notification) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, getNotificationChannel(notification))
                .setSound(getNotificationSound(notification.getSound()))
                .setContentTitle(notification.getTitle())
                .setContentText(notification.getBody())
                .setGroup(notification.getTag())
                .setSmallIcon(defaultNotificationIcon)
                .setColor(defaultNotificationColor)
                // must set priority to make sure forceShow works properly
                .setPriority(1);
        notificationManager.notify(0, builder.build());
        // dismiss notification to hide icon from status bar automatically
        new Handler(getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                notificationManager.cancel(0);
            }
        }, 3000);
    }

    private String getNotificationChannel(RemoteMessage.Notification notification) {
        String channel = notification.getChannelId();
        if (channel == null) {
            return defaultNotificationChannel;
        } else {
            return channel;
        }
    }

    private Uri getNotificationSound(String soundName) {
        if (soundName == null || soundName.isEmpty()) {
            return null;
        } else if (soundName.equals("default")) {
            return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        } else {
            return Uri.parse(SCHEME_ANDROID_RESOURCE + "://" + getApplicationContext().getPackageName() + "/raw/" + soundName);
        }
    }
}
