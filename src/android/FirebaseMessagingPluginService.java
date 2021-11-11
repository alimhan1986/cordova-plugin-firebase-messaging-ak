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

import me.leolin.shortcutbadger.ShortcutBadger;

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
                NotificationChannel mChannel = new NotificationChannel(defaultNotificationChannel, "Firebase", NotificationManager.IMPORTANCE_HIGH);
                mChannel.setShowBadge(true);
                notificationManager.createNotificationChannel(mChannel);
            } else {
                defaultChannel.setShowBadge(true);
            }
        }
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
        try {
            JSONObject data = new JSONObject(remoteMessage.getData());
            boolean hasTag = !data.isNull("chatType") && !data.isNull("chatId") && !data.isNull("channelId");
            String tag = hasTag ? data.getString("chatType") + data.getString("chatId") + data.getString("channelId") : "";
            String eventType = data.getString("eventType");
            boolean isPaused = FirebaseMessagingPlugin.isPaused();
            if (eventType.equals("inputMessage") && isPaused && hasTag) {
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
                            Bitmap icon = getBitmapFromURL("https://store." + (Boolean.valueOf(isDev) ? "dev-" : "") + "wazzup24.com/" + avatar);
                            Intent intent = new Intent(ctx, MainActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            PendingIntent pendingIntent = PendingIntent.getActivity(ctx, 0, intent, 0);

                            NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, "default")
                                .setContentTitle(dataInData.getString("contactName") + " — " + dataInData.getString("channelName"))
                                .setContentText(text)
                                .setGroup(tag)
                                .setLargeIcon(icon)
                                .setSmallIcon(ctx.getResources().getIdentifier("icon", "drawable", ctx.getPackageName()))
                                .setColor(defaultNotificationColor)
                                .setAutoCancel(true)
                                .setNumber(chatUnanswered)
                                .setDefaults(Notification.DEFAULT_SOUND)
                                // .setSilent(true)
                                .setContentIntent(pendingIntent)
                                .setPriority(NotificationCompat.PRIORITY_MAX);

                            StatusBarNotification [] nots = notificationManager.getActiveNotifications();
                            boolean hasNot = false;
                            for (int i = 0; i < nots.length; i++) {
                                String notTag = nots[i].getTag();
                                if (notTag.equals(tag)) {
                                    hasNot = true;
                                }
                            }
                            
                            if (!hasNot) notificationManager.notify(tag, 0, builder.build());
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

            // if (eventType.equals("counterUpdate")) {
            //     Context context = getApplicationContext();
            //     JSONObject dataInData = new JSONObject(data.getString("data"));
            //     int badgeCount = dataInData.getInt("counter");
            //     if (badgeCount > 0) {
            //         ShortcutBadger.applyCount(context, badgeCount);
            //     } else {
            //         ShortcutBadger.removeCount(context);
            //     }
            // }
        } catch (JSONException e) {
            Log.e(TAG, "onMessageReceived JSONException", e);
        } catch (Exception e1) {
            Log.e(TAG, "onMessageReceived Exception", e1);
        }
        FirebaseMessagingPlugin.sendNotification(remoteMessage);
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
