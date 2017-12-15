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

package com.commontime.plugin.notification.notification;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;

import org.json.JSONObject;

import java.util.Random;

/**
 * Builder class for local notifications. Build fully configured local
 * notification specified by JSON object passed from JS side.
 */
public class Builder {

    // Application context passed by constructor
    private final Context context;

    // NotificationWrapper options passed by JS
    private final Options options;

    // Receiver to handle the trigger event
    private Class<?> triggerReceiver;

    // Receiver to handle the clear event
    private Class<?> clearReceiver = ClearReceiver.class;

    // Activity to handle the click event
    private Class<?> clickActivity = ClickActivity.class;

    // Activity to handle the action click event
    private Class<?> actionClickActivity = ActionClickActivity.class;

    /**
     * Constructor
     *
     * @param context
     *      Application context
     * @param options
     *      NotificationWrapper options
     */
    public Builder(Context context, JSONObject options) {
        this.context = context;
        this.options = new Options(context).parse(options);
    }

    /**
     * Constructor
     *
     * @param options
     *      NotificationWrapper options
     */
    public Builder(Options options) {
        this.context = options.getContext();
        this.options = options;
    }

    /**
     * Set trigger receiver.
     *
     * @param receiver
     *      Broadcast receiver
     */
    public Builder setTriggerReceiver(Class<?> receiver) {
        this.triggerReceiver = receiver;
        return this;
    }

    /**
     * Set clear receiver.
     *
     * @param receiver
     *      Broadcast receiver
     */
    public Builder setClearReceiver(Class<?> receiver) {
        this.clearReceiver = receiver;
        return this;
    }

    /**
     * Set click activity.
     *
     * @param activity
     *      Activity
     */
    public Builder setClickActivity(Class<?> activity) {
        this.clickActivity = activity;
        return this;
    }

    /**
     * Set action click activity.
     *
     * @param activity
     *      Activity
     */
    public Builder setActionClickActivity(Class<?> activity) {
        this.actionClickActivity = activity;
        return this;
    }

    /**
     * Creates the notification with all its options passed through JS.
     */
    public NotificationWrapper build() {
        Uri sound = options.getSoundUri();
        NotificationCompat.BigTextStyle style;
        NotificationCompat.Builder builder;

        style = new NotificationCompat.BigTextStyle()
                .bigText(options.getText());

        builder = new NotificationCompat.Builder(context)
                .setDefaults(0)
                .setContentTitle(options.getTitle())
                .setContentText(options.getText())
                .setNumber(options.getBadgeNumber())
                .setTicker(options.getText())                
                .setAutoCancel(options.isAutoClear())
                .setOngoing(options.isOngoing())
                .setStyle(style)
                .setLights(options.getLedColor(), 500, 500);

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                Class<?> cls  = Class.forName(context.getPackageName() + ".R$drawable");
                int resId = (Integer) cls.getDeclaredField("icontransparent").get(Integer.class);
                builder.setSmallIcon(resId);                
            } catch (Exception ignore) {
                builder.setSmallIcon(options.getSmallIcon());
            }
            builder.setLargeIcon(options.getIconBitmap());
        } else { 
            builder.setSmallIcon(options.getSmallIcon());
            builder.setLargeIcon(options.getIconBitmap());
        } 

        if (sound != null)
        {
            builder.setSound(sound);
        }

        if(!TextUtils.isEmpty(options.getCategoryIdentifier()))
            applyActionReceiver(builder);

        applyDeleteReceiver(builder);
        applyContentReceiver(builder);

        return new NotificationWrapper(context, options, builder, triggerReceiver);
    }

    /**
     * Set intent to handle the delete event. Will clean up some persisted
     * preferences.
     *
     * @param builder
     *      Local notification builder instance
     */
    private void applyDeleteReceiver(NotificationCompat.Builder builder) {

        if (clearReceiver == null)
            return;

        Intent deleteIntent = new Intent(context, clearReceiver)
                .setAction(options.getIdStr())
                .putExtra(Options.EXTRA, options.toString());

        PendingIntent dpi = PendingIntent.getBroadcast(
                context, 0, deleteIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        builder.setDeleteIntent(dpi);
    }

    /**
     * Set intent to handle the click event. Will bring the app to
     * foreground.
     *
     * @param builder
     *      Local notification builder instance
     */
    private void applyContentReceiver(NotificationCompat.Builder builder) {

        if (clickActivity == null)
            return;

        Intent intent = new Intent(context, clickActivity)
                .putExtra(Options.EXTRA, options.toString())
                .setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);

        int requestCode = new Random().nextInt();

        PendingIntent contentIntent = PendingIntent.getActivity(
                context, requestCode, intent, PendingIntent.FLAG_CANCEL_CURRENT);

        builder.setContentIntent(contentIntent);
    }

    /**
     * Set intent to handle the action click event. Will bring the app to
     * foreground.
     *
     * @param builder
     *      Local notification builder instance
     */
    private void applyActionReceiver(NotificationCompat.Builder builder) {

        if (actionClickActivity == null)
            return;

        try
        {
            for(int i = 0; i < options.getCategoryActionCount(); i++)
            {
                JSONObject action = options.getCategoryAction(i);

                Intent intent = new Intent(context, actionClickActivity)
                        .putExtra(Options.EXTRA, options.toString())
                        .putExtra(ActionClickActivity.ACTION_PARAM, action.toString())
                        .setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);

                int requestCode = new Random().nextInt();

                PendingIntent actionIntent = PendingIntent.getActivity(
                        context, requestCode, intent, PendingIntent.FLAG_CANCEL_CURRENT);

                builder.addAction(new NotificationCompat.Action(context.getResources().getIdentifier("action_hand", "drawable", context.getPackageName()), action.getString("title"), actionIntent));
            }
        }
        catch(Exception e)
        {
        }
    }
}