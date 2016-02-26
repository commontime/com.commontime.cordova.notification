
Cordova Notification Plugin
=================================

A modified version of Katzer local-notification plugin found at https://github.com/katzer/cordova-common-registerusernotificationsettings/blob/master/plugin.xml. This adds in the ability to include actions for both iOS and Android and hook into the user's response in JavaScript.

The essential purpose of local notifications is to enable an application to inform its users that it has something for them — for example, a message or an upcoming appointment — when the application isn’t running in the foreground.<br>
They are scheduled by an application and delivered on the same device.

<img width="35%" align="right" hspace="19" vspace="12" src="https://github.com/katzer/cordova-plugin-local-notifications/blob/example/images/android.png"></img>

### How they appear to the user
Users see notifications in the following ways:
- Displaying an alert or banner
- Badging the app’s icon
- Playing a sound


### Examples of Notification Usage
Local notifications are ideally suited for applications with time-based behaviors, such as calendar and to-do list applications. Applications that run in the background for the limited period allowed by iOS might also find local notifications useful.<br>
For example, applications that depend on servers for messages or data can poll their servers for incoming items while running in the background; if a message is ready to view or an update is ready to download, they can then present a local notification immediately to inform their users.


## Supported Platforms
The plugin supports the following platforms:
- __iOS__ _(including iOS8)_<br>
- __Android__ _(SDK >=7)_
- __Windows 8.1__
- __Windows Phone 8.1__

## Sample
The sample demonstrates how to schedule a local notification which repeats every week. The listener will be called when the user has clicked on the local notification.

```javascript
cordova.plugins.notification.schedule({
    id: 1,
    title: "Production Jour fixe",
    text: "Duration 1h",
    firstAt: monday_9_am,
    every: "week",
    sound: "file://sounds/reminder.mp3",
    icon: "http://icons.com/?cal_id=1",
    data: { meetingId:"123#fg8" }
});

cordova.plugins.notification.on("interactedWith", function (notification) {
    joinMeeting(notification.data.meetingId);
});
```

Below shows how to set actions on notifications and the function to listener for a reponse.

```javascript
cordova.plugins.notification.schedule({
	id: 1,
	title: "Scheduled notification with delay",
	text: "Message",
	at: _3_sec_from_now,
	sound: null,
	category: {
		identifier: "AWSOME_CATEGORY",
		actions: [{
			title: "Awsome Action",
			identifier: "AWSOME_ACTION", 
			textInput: true/false
		}]
	}
});

cordova.plugins.notification.on("interactedWith", function(notification) {
    var identifier = notification.actionResponseIdentifier;
    var responseText = notification.actionResponse; // If textInput is not set to true this will not exist
}
```

As well as the local notifications there is also the ability to register and listen for push notifications. The code that has been merged into this plugin to enable this functionality is from the PushPlugin project at https://github.com/phonegap-build/PushPlugin.

[cordova]: https://cordova.apache.org
[apache2_license]: http://opensource.org/licenses/Apache-2.0
