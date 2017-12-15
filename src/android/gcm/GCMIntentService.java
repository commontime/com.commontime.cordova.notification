package com.commontime.plugin.notification.gcm;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParserException;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.XmlResourceParser;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;
import android.util.Log;

import com.commontime.plugin.notification.Notification;
import com.commontime.testbed.R;
import com.google.android.gcm.GCMBaseIntentService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static android.R.id.input;

@SuppressLint("NewApi")
public class GCMIntentService extends GCMBaseIntentService {

	private static final String TAG = "GCMIntentService";
	private Map<String, String> preferences = new HashMap<String, String>();

	public GCMIntentService() {
		super("GCMIntentService");
	}

	@Override
	public void onRegistered(Context context, String regId) {

		Log.v(TAG, "onRegistered: "+ regId);

		JSONObject json;

		try
		{
			json = new JSONObject().put("event", "registered");
			json.put("regid", regId);

			Log.v(TAG, "onRegistered: " + json.toString());

			// Send this JSON data to the JavaScript application above EVENT should be set to the msg type
			// In this case this is the registration ID
			Notification.deviceRegisteredForPush(regId);

		}
		catch( JSONException e)
		{
			// No message to the user is sent, JSON failed
			Log.e(TAG, "onRegistered: JSON exception");
		}
	}

	@Override
	public void onUnregistered(Context context, String regId) {
		Log.d(TAG, "onUnregistered - regId: " + regId);
	}

	@Override
	protected void onMessage(Context context, Intent intent) {
		Log.d(TAG, "onMessage - context: " + context);

		fetchPreferences();

		// Extract the payload from the message
		Bundle extras = intent.getExtras();
		if (extras != null)
		{
			if(TextUtils.isEmpty(extras.getString("message")) && TextUtils.isEmpty(extras.getString("title"))) {
				Notification.firePushReceivedEvent(extras);
			} else {
				String inAppPush = preferences.get("inAppPush");
				System.out.println("inAppPush: " + inAppPush);
				if(inAppPush != null && inAppPush.equalsIgnoreCase("immediate")) {
					if(Notification.isInBackground()) {
						createNotification(context, extras);
					} else {
						Notification.firePushReceivedEvent(extras);
					}
				} else {
					createNotification(context, extras);
				}
			}
		}
	}

	public void createNotification(Context context, Bundle extras)
	{
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		String appName = getAppName(this);

		Intent notificationIntent = new Intent(this, PushHandlerActivity.class);
		notificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
		notificationIntent.putExtra("pushBundle", extras);

		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
		
		int defaults = android.app.Notification.DEFAULT_ALL;

		if (extras.getString("defaults") != null) {
			try {
				defaults = Integer.parseInt(extras.getString("defaults"));
			} catch (NumberFormatException e) {}
		}

		NotificationCompat.Builder mBuilder =
			new NotificationCompat.Builder(context)
				.setDefaults(defaults)
				.setWhen(System.currentTimeMillis())
				.setContentTitle(extras.getString("title"))
				.setTicker(extras.getString("title"))
				.setContentIntent(contentIntent)
				.setAutoCancel(true);

		if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			try {
				Class<?> cls  = Class.forName(getPackageName() + ".R$drawable");
				int resId = (Integer) cls.getDeclaredField("icontransparent").get(Integer.class);
				mBuilder.setSmallIcon(resId);
			} catch (Exception ignore) {
				mBuilder.setSmallIcon(context.getApplicationInfo().icon);
			}
		} else {
			mBuilder.setSmallIcon(context.getApplicationInfo().icon);
		}

		if( extras.containsKey("vibrate")) {
			try {
				JSONArray patternJSON = new JSONArray(extras.getString("vibrate"));
				long[] pattern = new long[patternJSON.length()];
				for( int i = 0; i < patternJSON.length(); i++ ) {
					pattern[i] = patternJSON.getLong(i);
				}
				mBuilder.setVibrate(pattern);

			} catch(JSONException e) {
				e.printStackTrace();
			}
		}

		if( extras.containsKey("sound")) {
			try {

				int notId = 0;
				if( extras.containsKey("notId")) {
					notId = Integer.parseInt(extras.getString("notId"));
				}

				JSONObject sound = new JSONObject(extras.getString("sound"));

				boolean loop = sound.optBoolean("loop", false);
				int volumePercentage = sound.optInt("volume", 100);
				String file = sound.getString("file");

				NotificationMediaPlayer.getInstance().play( context, file, volumePercentage, loop, notId );

				Intent deleteNotificationIntent = new Intent(this, PushHandlerActivity.class);
				deleteNotificationIntent.setAction( getPackageName() + "|deleted");
				deleteNotificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

				Bundle deleteBundle = (Bundle) extras.clone();
				deleteNotificationIntent.putExtra("pushBundle", deleteBundle);

				PendingIntent deleteIntent = PendingIntent.getActivity(this, 0, deleteNotificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
				mBuilder.setDeleteIntent(deleteIntent);

			} catch (JSONException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		if( extras.containsKey("actions") ) {

			try {
				JSONArray actions = new JSONArray(extras.getString("actions"));
				for( int i = 0; i < actions.length(); i++ ) {
                    JSONObject actObj = actions.getJSONObject(i);
    				String title = actObj.getString("title");

					Intent actionNotificationIntent = new Intent(this, PushHandlerActivity.class);
					actionNotificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

					JSONObject actionResponse = new JSONObject();
					actionNotificationIntent.setAction(actObj.getString("identifier"));

					Bundle actionBundle = (Bundle) extras.clone();

					actionNotificationIntent.putExtra("pushBundle", actionBundle);

					PendingIntent actionContentIntent = PendingIntent.getActivity(this, 0, actionNotificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

					NotificationCompat.Action action = new NotificationCompat.Action(0, title, actionContentIntent );
                    mBuilder.addAction(action);
                }
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}

		String message = extras.getString("message");
		if (message != null) {
			mBuilder.setContentText(message);
		} else {
			mBuilder.setContentText("<missing message content>");
		}

		String msgcnt = extras.getString("msgcnt");
		if (msgcnt != null) {
			mBuilder.setNumber(Integer.parseInt(msgcnt));
		}
		
		int notId = 0;
		
		try {
			notId = Integer.parseInt(extras.getString("notId"));
		}
		catch(NumberFormatException e) {
			Log.e(TAG, "Number format exception - Error parsing NotificationWrapper ID: " + e.getMessage());
		}
		catch(Exception e) {
			Log.e(TAG, "Number format exception - Error parsing NotificationWrapper ID" + e.getMessage());
		}
		
		mNotificationManager.notify((String) appName, notId, mBuilder.build());
	}
	
	private static String getAppName(Context context)
	{
		CharSequence appName = 
				context
					.getPackageManager()
					.getApplicationLabel(context.getApplicationInfo());
		
		return (String)appName;
	}
	
	@Override
	public void onError(Context context, String errorId) {
		Log.e(TAG, "onError - errorId: " + errorId);
	}

	protected void fetchPreferences() {
		final int identifier = this.getResources().getIdentifier("config","xml", this.getPackageName());
		final XmlResourceParser parser = this.getResources().getXml(identifier);

		try {
			for (int eventType = -1; eventType != XmlResourceParser.END_DOCUMENT; eventType = parser.next()) {

				if (eventType == XmlResourceParser.START_TAG) {
					String s = parser.getName();

					String prefName = null;
					String prefValue = null;

					if (s.equals("preference")) {
						for (int attr = 0; attr < parser.getAttributeCount(); attr++) {
							String name = parser.getAttributeName(attr);
							String val = parser.getAttributeValue(attr);

							if( name.equals( "name") ) {
								prefName = val;
							} else if( name.equals( "value") ) {
								prefValue = val;
							}

							if( prefName != null && prefValue != null ) {
								preferences.put(prefName, prefValue);
								System.out.println(prefName +":"+prefValue);
								break;
							}
						}
					}
				}
			}
		} catch (XmlPullParserException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}