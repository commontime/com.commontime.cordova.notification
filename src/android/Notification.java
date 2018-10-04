/*
 * Copyright (c) 2013-2015 by appPlant UG. All rights reserved.
 *
 * @APPPLANT_LICENSE_HEADER_START@
 *
 * This file contains Original Code and/or Modifications of Original Code
 * as defined in and that are subject to the Apache License
 * Version 2.0 (the 'License'). You may not use this file except in
 * compliance with the License. Please obtain a copy of the License at
 * http://opensource.org/licenses/Apache-2.0/ and read it before using this
 * file.
 *
 * The Original Code and all software distributed under the License are
 * distributed on an 'AS IS' basis, WITHOUT WARRANTY OF ANY KIND, EITHER
 * EXPRESS OR IMPLIED, AND APPLE HEREBY DISCLAIMS ALL SUCH WARRANTIES,
 * INCLUDING WITHOUT LIMITATION, ANY WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE, QUIET ENJOYMENT OR NON-INFRINGEMENT.
 * Please see the License for the specific language governing rights and
 * limitations under the License.
 *
 * @APPPLANT_LICENSE_HEADER_END@
 */

package com.commontime.plugin.notification;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.commontime.plugin.notification.notification.Manager;
import com.commontime.plugin.notification.notification.NotificationWrapper;
import com.google.android.gcm.GCMRegistrar;

/**
 * This plugin utilizes the Android AlarmManager in combination with local
 * notifications. When a local notification is scheduled the alarm manager takes
 * care of firing the event. When the event is processed, a notification is put
 * in the Android notification center and status bar.
 */
public class Notification extends CordovaPlugin {

    public static final String EXIT = "exit";

    private static String gSenderID;

    // Reference to the web view for static access
    private static CordovaWebView webView = null;

    // Indicates if the device is ready (to receive events)
    private static Boolean deviceready = false;

    // To inform the user about the state of the app in callbacks
    protected static Boolean isInBackground = true;

    // Queues all events before deviceready
    private static ArrayList<String> eventQueue = new ArrayList<String>();

    private static CallbackContext tmpCommand;
    private BroadcastReceiver idleReceiver;

    /**
     * Called after plugin construction and fields have been initialized.
     * Prefer to use pluginInitialize instead since there is no value in
     * having parameters on the initialize() function.
     * <p/>
     * pluginInitialize is not available for cordova 3.0-3.5 !
     */
    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        Notification.webView = super.webView;
        PushSingleton.getInstance().setActivity(cordova.getActivity());

        IntentFilter filter = new IntentFilter();
        filter.addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED);
        filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        idleReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                onDeviceIdleChanged();
            }
        };
        System.out.println("Registering idleReceiver");       
        cordova.getActivity().registerReceiver(idleReceiver, filter);
        
        tick();
    }
    
    private void tick() {
        final PowerManager pm = (PowerManager) cordova.getActivity().getSystemService(Context.POWER_SERVICE);
        new Thread( new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(5000);
                } catch (Exception e) {
                }                
                System.out.println("DeviceIdleMode: " + pm.isDeviceIdleMode());
                System.out.println("PowerSaveMode: " + pm.isPowerSaveMode());
                tick();
            }
        }).start();
    }

    private void onDeviceIdleChanged() {
        PowerManager pm = (PowerManager) cordova.getActivity().getSystemService(Context.POWER_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            System.out.println("DeviceIdleMode: " + pm.isDeviceIdleMode());
            System.out.println("PowerSaveMode: " + pm.isPowerSaveMode());
        }
    }

    /**
     * Called when the system is about to start resuming a previous activity.
     *
     * @param multitasking Flag indicating if multitasking is turned on for app
     */
    @Override
    public void onPause(boolean multitasking) {
        super.onPause(multitasking);
        isInBackground = true;
    }

    /**
     * Called when the activity will start interacting with the user.
     *
     * @param multitasking Flag indicating if multitasking is turned on for app
     */
    @Override
    public void onResume(boolean multitasking) {
        super.onResume(multitasking);
        isInBackground = false;
        deviceready();
    }

    /**
     * The final call you receive before your activity is destroyed.
     */
    @Override
    public void onDestroy() {
        deviceready = false;
        isInBackground = true;
        Notification.webView = null;
        PushSingleton.getInstance().setActivity(null);
        cordova.getActivity().unregisterReceiver(idleReceiver);
    }

    /**
     * Executes the request.
     * <p/>
     * This method is called from the WebView thread. To do a non-trivial
     * amount of work, use:
     * cordova.getThreadPool().execute(runnable);
     * <p/>
     * To run on the UI thread, use:
     * cordova.getActivity().runOnUiThread(runnable);
     *
     * @param action  The action to execute.
     * @param args    The exec() arguments in JSON form.
     * @param command The callback context used when calling back into JavaScript.
     * @return Whether the action was valid.
     */
    @Override
    public boolean execute(final String action, final JSONArray args,
                           final CallbackContext command) throws JSONException {

        NotificationWrapper.setDefaultTriggerReceiver(TriggerReceiver.class);

        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                if (action.equals("schedule")) {
                    schedule(args);
                    command.success();
                } else if (action.equals("update")) {
                    update(args);
                    command.success();
                } else if (action.equals("cancel")) {
                    cancel(args);
                    command.success();
                } else if (action.equals("cancelAll")) {
                    cancelAll();
                    command.success();
                } else if (action.equals("clear")) {
                    clear(args);
                    command.success();
                } else if (action.equals("clearAll")) {
                    clearAll();
                    command.success();
                } else if (action.equals("isPresent")) {
                    isPresent(args.optInt(0), command);
                } else if (action.equals("isScheduled")) {
                    isScheduled(args.optInt(0), command);
                } else if (action.equals("isTriggered")) {
                    isTriggered(args.optInt(0), command);
                } else if (action.equals("getAllIds")) {
                    getAllIds(command);
                } else if (action.equals("getScheduledIds")) {
                    getScheduledIds(command);
                } else if (action.equals("getTriggeredIds")) {
                    getTriggeredIds(command);
                } else if (action.equals("getSingle")) {
                    getSingle(args, command);
                } else if (action.equals("getSingleScheduled")) {
                    getSingleScheduled(args, command);
                } else if (action.equals("getSingleTriggered")) {
                    getSingleTriggered(args, command);
                } else if (action.equals("getAll")) {
                    getAll(args, command);
                } else if (action.equals("getScheduled")) {
                    getScheduled(args, command);
                } else if (action.equals("getTriggered")) {
                    getTriggered(args, command);
                } else if (action.equals("registerForPush")) {
                    registerForPush(args, command);
                } else if (action.equals("unregisterForPush")) {
                    unregisterForPush(command);
                } else if (action.equals("deviceready")) {
                    deviceready();
                }
            }
        });

        return true;
    }

    /**
     * Schedule multiple local notifications.
     *
     * @param notifications Properties for each local notification
     */
    private void schedule(JSONArray notifications) {
        for (int i = 0; i < notifications.length(); i++) {
            JSONObject options = notifications.optJSONObject(i);

            NotificationWrapper notification = getNotificationMgr().schedule(options, TriggerReceiver.class);

            fireEvent("schedule", notification);
        }
    }

    /**
     * Update multiple local notifications.
     *
     * @param updates NotificationWrapper properties including their IDs
     */
    private void update(JSONArray updates) {
        for (int i = 0; i < updates.length(); i++) {
            JSONObject update = updates.optJSONObject(i);
            int id = update.optInt("id", 0);

            NotificationWrapper notification = getNotificationMgr().update(id, update, TriggerReceiver.class);

            fireEvent("update", notification);
        }
    }

    /**
     * Cancel multiple local notifications.
     *
     * @param ids Set of local notification IDs
     */
    private void cancel(JSONArray ids) {
        for (int i = 0; i < ids.length(); i++) {
            int id = ids.optInt(i, 0);

            NotificationWrapper notification = getNotificationMgr().cancel(id);

            if (notification != null) {
                fireEvent("cancel", notification);
            }
        }
    }

    /**
     * Cancel all scheduled notifications.
     */
    private void cancelAll() {
        getNotificationMgr().cancelAll();
        fireEvent("cancelall");
    }

    /**
     * Clear multiple local notifications without canceling them.
     *
     * @param ids Set of local notification IDs
     */
    private void clear(JSONArray ids) {
        for (int i = 0; i < ids.length(); i++) {
            int id = ids.optInt(i, 0);

            NotificationWrapper notification = getNotificationMgr().clear(id);

            if (notification != null) {
                fireEvent("clear", notification);
            }
        }
    }

    /**
     * Clear all triggered notifications without canceling them.
     */
    private void clearAll() {
        getNotificationMgr().clearAll();
        fireEvent("clearall");
    }

    /**
     * If a notification with an ID is present.
     *
     * @param id      NotificationWrapper ID
     * @param command The callback context used when calling back into JavaScript.
     */
    private void isPresent(int id, CallbackContext command) {
        boolean exist = getNotificationMgr().exist(id);

        PluginResult result = new PluginResult(
                PluginResult.Status.OK, exist);

        command.sendPluginResult(result);
    }

    /**
     * If a notification with an ID is scheduled.
     *
     * @param id      NotificationWrapper ID
     * @param command The callback context used when calling back into JavaScript.
     */
    private void isScheduled(int id, CallbackContext command) {
        boolean exist = getNotificationMgr().exist(
                id, NotificationWrapper.Type.SCHEDULED);

        PluginResult result = new PluginResult(
                PluginResult.Status.OK, exist);

        command.sendPluginResult(result);
    }

    /**
     * If a notification with an ID is triggered.
     *
     * @param id      NotificationWrapper ID
     * @param command The callback context used when calling back into JavaScript.
     */
    private void isTriggered(int id, CallbackContext command) {
        boolean exist = getNotificationMgr().exist(
                id, NotificationWrapper.Type.TRIGGERED);

        PluginResult result = new PluginResult(
                PluginResult.Status.OK, exist);

        command.sendPluginResult(result);
    }

    /**
     * Set of IDs from all existent notifications.
     *
     * @param command The callback context used when calling back into JavaScript.
     */
    private void getAllIds(CallbackContext command) {
        List<Integer> ids = getNotificationMgr().getIds();

        command.success(new JSONArray(ids));
    }

    /**
     * Set of IDs from all scheduled notifications.
     *
     * @param command The callback context used when calling back into JavaScript.
     */
    private void getScheduledIds(CallbackContext command) {
        List<Integer> ids = getNotificationMgr().getIdsByType(
                NotificationWrapper.Type.SCHEDULED);

        command.success(new JSONArray(ids));
    }

    /**
     * Set of IDs from all triggered notifications.
     *
     * @param command The callback context used when calling back into JavaScript.
     */
    private void getTriggeredIds(CallbackContext command) {
        List<Integer> ids = getNotificationMgr().getIdsByType(
                NotificationWrapper.Type.TRIGGERED);

        command.success(new JSONArray(ids));
    }

    /**
     * Options from local notification.
     *
     * @param ids     Set of local notification IDs
     * @param command The callback context used when calling back into JavaScript.
     */
    private void getSingle(JSONArray ids, CallbackContext command) {
        getOptions(ids.optString(0), NotificationWrapper.Type.ALL, command);
    }

    /**
     * Options from scheduled notification.
     *
     * @param ids     Set of local notification IDs
     * @param command The callback context used when calling back into JavaScript.
     */
    private void getSingleScheduled(JSONArray ids, CallbackContext command) {
        getOptions(ids.optString(0), NotificationWrapper.Type.SCHEDULED, command);
    }

    /**
     * Options from triggered notification.
     *
     * @param ids     Set of local notification IDs
     * @param command The callback context used when calling back into JavaScript.
     */
    private void getSingleTriggered(JSONArray ids, CallbackContext command) {
        getOptions(ids.optString(0), NotificationWrapper.Type.TRIGGERED, command);
    }

    /**
     * Set of options from local notification.
     *
     * @param ids     Set of local notification IDs
     * @param command The callback context used when calling back into JavaScript.
     */
    private void getAll(JSONArray ids, CallbackContext command) {
        getOptions(ids, NotificationWrapper.Type.ALL, command);
    }

    /**
     * Set of options from scheduled notifications.
     *
     * @param ids     Set of local notification IDs
     * @param command The callback context used when calling back into JavaScript.
     */
    private void getScheduled(JSONArray ids, CallbackContext command) {
        getOptions(ids, NotificationWrapper.Type.SCHEDULED, command);
    }

    /**
     * Set of options from triggered notifications.
     *
     * @param ids     Set of local notification IDs
     * @param command The callback context used when calling back into JavaScript.
     */
    private void getTriggered(JSONArray ids, CallbackContext command) {
        getOptions(ids, NotificationWrapper.Type.TRIGGERED, command);
    }

    /**
     * Options from local notification.
     *
     * @param id      Set of local notification IDs
     * @param type    The local notification life cycle type
     * @param command The callback context used when calling back into JavaScript.
     */
    private void getOptions(String id, NotificationWrapper.Type type,
                            CallbackContext command) {

        JSONArray ids = new JSONArray().put(id);

        JSONObject options =
                getNotificationMgr().getOptionsBy(type, toList(ids)).get(0);

        command.success(options);
    }

    /**
     * Set of options from local notifications.
     *
     * @param ids     Set of local notification IDs
     * @param type    The local notification life cycle type
     * @param command The callback context used when calling back into JavaScript.
     */
    private void getOptions(JSONArray ids, NotificationWrapper.Type type,
                            CallbackContext command) {

        List<JSONObject> options;

        if (ids.length() == 0) {
            options = getNotificationMgr().getOptionsByType(type);
        } else {
            options = getNotificationMgr().getOptionsBy(type, toList(ids));
        }

        command.success(new JSONArray(options));
    }

    private void registerForPush(JSONArray data, CallbackContext command) {
        try {
            tmpCommand = command;

            JSONObject jo = data.getJSONObject(0);
            gSenderID = (String) jo.get("senderID");

            GCMRegistrar.register(cordova.getActivity().getApplicationContext(), gSenderID);
            //command.success();
        } catch (JSONException e) {
            command.error(e.getMessage());
        }
    }

    public static void deviceRegisteredForPush(String token)
    {
        if (tmpCommand == null)
            return;
            
        tmpCommand.success(token);
        tmpCommand = null;
    }

    private void unregisterForPush(CallbackContext command)
    {
        GCMRegistrar.unregister(cordova.getActivity().getApplicationContext());
        command.success();
    }

    /**
     * Call all pending callbacks after the deviceready event has been fired.
     */
    private static synchronized void deviceready () {
        isInBackground = false;
        deviceready = true;

        for (String js : eventQueue) {
            sendJavascript(js);
        }

        eventQueue.clear();
    }

    /**
     * Fire given event on JS side. Does inform all event listeners.
     *
     * @param event
     *      The event name
     */
    private void fireEvent (String event) {
        fireEvent(event, null);
    }

    /**
     * Fire given event on JS side. Does inform all event listeners.
     *
     * @param event
     *      The event name
     * @param notification
     *      Optional notification to pass the id and properties.
     */
    public static void fireEvent (String event, NotificationWrapper notification) {
        String state = getApplicationState();
        String params = "\"" + state + "\"";

        if (notification != null) {
            params = notification.toString() + "," + params;
        }

        String js = "cordova.plugins.notification.core.fireEvent(" +
                "\"" + event + "\"," + params + ")";

        sendJavascript(js);
    }

     /**
     * Fire given event on JS side. Does inform all event listeners.
     *
     * @param data
     *      Optional local notification to pass the id and properties.
     */
    public static void firePushReceivedEvent (Bundle data) {
        String state = getApplicationState();
        String params = "\"" + state + "\"";

        if (data != null) {
            params = convertBundleToJson(data).toString() + "," + params;
        }

        String js = "cordova.plugins.notification.core.fireEvent(\"pushReceived\"," + params + ")";

        sendJavascript(js);
    }

    /**
     * Use this instead of deprecated sendJavascript
     *
     * @param js
     *       JS code snippet as string
     */
    private static synchronized void sendJavascript(final String js) {

        if (!deviceready) {
            eventQueue.add(js);
            return;
        }
        Runnable jsLoader = new Runnable() {
            public void run() {
                webView.loadUrl("javascript:" + js);
            }
        };
        try {
            Method post = webView.getClass().getMethod("post",Runnable.class);
            post.invoke(webView,jsLoader);
        } catch(Exception e) {

            ((Activity)(webView.getContext())).runOnUiThread(jsLoader);
        }
    }

    /**
     * Convert JSON array of integers to List.
     *
     * @param ary
     *      Array of integers
     */
    private List<Integer> toList (JSONArray ary) {
        ArrayList<Integer> list = new ArrayList<Integer>();

        for (int i = 0; i < ary.length(); i++) {
            list.add(ary.optInt(i));
        }

        return list;
    }

    /**
     * Current application state.
     *
     * @return
     *      "background" or "foreground"
     */
    static String getApplicationState () {
        return isInBackground ? "background" : "foreground";
    }

    /**
     * NotificationWrapper manager instance.
     */
    private Manager getNotificationMgr() {
        return Manager.getInstance(cordova.getActivity());
    }

    public static boolean isActive() {
        return webView != null;
    }

    public static boolean isInBackground() {
        return isInBackground;
    }

    /*
     * serializes a bundle to JSON.
     */
    private static JSONObject convertBundleToJson(Bundle extras)
    {
        try
        {
            JSONObject json;
            json = new JSONObject().put("event", "message");

            Iterator<String> it = extras.keySet().iterator();
            while (it.hasNext())
            {
                String key = it.next();
                Object value = extras.get(key);

                // System data from Android
                if (key.equals("from") || key.equals("collapse_key"))
                {
                    json.put(key, value);
                }
                else if (key.equals("foreground"))
                {
                    json.put(key, extras.getBoolean("foreground"));
                }
                else if (key.equals("coldstart"))
                {
                    json.put(key, extras.getBoolean("coldstart"));
                }
                else if (key.equals("title"))
                {
                    json.put(key, extras.getString("title"));
                }
                else if (key.equals("message"))
                {
                    json.put(key, extras.getString("message"));
                }
                else if (key.equals("payload"))
                {
                    createPayloadObject(json, (String) value);
                }
                else if (key.equals("action"))
                {
                    createActionObject(json, (String) value);
                }
                else if (key.equals("actions"))
                {
                    createJSON(json, key, (String) value);
                }
                else if (key.equals("sound"))
                {
                    createJSON(json, key, (String) value);
                }
                else if (key.equals("vibrate"))
                {
                    createJSON(json, key, (String) value);
                }
                else if (key.equals("deleted"))
                {
                    createBoolean(json, key, (Boolean) value);
                }
                else
                {
                    if (value instanceof String)
                    {
                        createPayloadObject(json, (String) value);
                    }
                }
            } // while

            json.put("service","GCM");

            return json;
        }
        catch( JSONException e)
        {
        }
        return null;
    }

    private static void createBoolean(JSONObject json, String key, boolean value) {
        try {
            json.put(key, value);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private static void createJSON(JSONObject json, String key, String value) {
        try {
            if( value.trim().startsWith("[")) {
                JSONArray array = new JSONArray(value);
                json.put(key, array);
            } else {
                JSONObject object = new JSONObject(value);
                json.put(key, object);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private static void createActionObject(JSONObject json, String value) {
        try {
            json.put("actionResponseIdentifier", value);
        } catch (JSONException e) {
        }
    }

    private static void createPayloadObject(JSONObject json, String value)
    {
        String strValue = value;
        if (strValue.startsWith("{")) {
            try {
                JSONObject json2 = new JSONObject(strValue);
                json.put("payload", json2);
            }
            catch (Exception e) {
            }
        }
        else if (strValue.startsWith("["))
        {
            try
            {
                JSONArray json2 = new JSONArray(strValue);
                json.put("payload", json2);
            }
            catch (Exception e) {
            }
        }
    }
}
