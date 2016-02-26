package com.commontime.plugin.notification.gcm;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

import com.commontime.plugin.notification.Notification;

import org.json.JSONException;
import org.json.JSONObject;

public class PushHandlerActivity extends Activity
{
	private static String TAG = "PushHandlerActivity"; 

	/*
	 * this activity will be started if the user touches a notification that we own. 
	 * We send it's data off to the push plugin for processing.
	 * If needed, we boot up the main activity to kickstart the application. 
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		Log.v(TAG, "onCreate");

		boolean isPushPluginActive = Notification.isActive();
		processPushBundle(isPushPluginActive);

		finish();

		Log.v(TAG, String.valueOf(isPushPluginActive));

		if (!isPushPluginActive) {
			forceMainActivityReload();
		}
	}

	/**
	 * Takes the pushBundle extras from the intent, 
	 * and sends it through to the PushPlugin for processing.
	 */
	private void processPushBundle(boolean isPushPluginActive)
	{
		Bundle extras = getIntent().getExtras();

		if (extras != null)	{
			Notification.firePushReceivedEvent(extras.getBundle("pushBundle"));
		}
	}

	/**
	 * Forces the main activity to re-launch if it's unloaded.
	 */
	private void forceMainActivityReload()
	{
		PackageManager pm = getPackageManager();
		Intent launchIntent = pm.getLaunchIntentForPackage(getApplicationContext().getPackageName());    		
		startActivity(launchIntent);
	}

  @Override
  protected void onResume() {
    super.onResume();
    final NotificationManager notificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
    notificationManager.cancelAll();
  }

}