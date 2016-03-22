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

#import "Notification.h"
#import "NotificationOptions.h"
#import "UIApplication+Notification.h"
#import "UILocalNotification+Notification.h"
#import "AppDelegate+RegisterUserNotificationSettings.h"
#import "AppDelegate+HandleActionWithIdentifier.h"
#import "AppDelegate+RemoteNotifications.h"

@interface Notification ()

// Retrieves the application state
@property (readonly, getter=applicationState) NSString* applicationState;
// All events will be queued until deviceready has been fired
@property (readwrite, assign) BOOL deviceready;
// Event queue
@property (readonly, nonatomic, retain) NSMutableArray* eventQueue;
// Needed when calling `registerPermission`
@property (nonatomic, retain) CDVInvokedUrlCommand* command;

@end

@implementation Notification

@synthesize deviceready, eventQueue;

@synthesize isInline;

@synthesize callbackId;
@synthesize notificationCallbackId;

#pragma mark -
#pragma mark Interface

/**
 * Execute all queued events.
 */
- (void) deviceready:(CDVInvokedUrlCommand*)command
{
    deviceready = YES;

    for (NSString* js in eventQueue) {
        [self.commandDelegate evalJs:js];
    }

    [eventQueue removeAllObjects];
}

/**
 * Schedule a set of notifications.
 *
 * @param properties
 *      A dict of properties for each notification
 */
- (void) schedule:(CDVInvokedUrlCommand*)command
{
    NSArray* notifications = command.arguments;

    [self.commandDelegate runInBackground:^{
        for (NSDictionary* options in notifications) {
            UILocalNotification* notification;
            
            if([options objectForKey:@"category"] != nil) {
                [self registerCategory:[options objectForKey:@"category"]];
            }

            notification = [[UILocalNotification alloc] initWithOptions:options];
            
            [self scheduleLocalNotification:[notification copy]];
            [self fireEvent:@"schedule" notification:notification];

            if (notifications.count > 1) {
                [NSThread sleepForTimeInterval:0.01];
            }
        }

        [self execCallback:command];
    }];
}

/**
 * Update a set of notifications.
 *
 * @param properties
 *      A dict of properties for each notification
 */
- (void) update:(CDVInvokedUrlCommand*)command
{
    NSArray* notifications = command.arguments;

    [self.commandDelegate runInBackground:^{
        for (NSDictionary* options in notifications) {
            NSNumber* id = [options objectForKey:@"id"];
            UILocalNotification* notification;

            notification = [self.app localNotificationWithId:id];

            if (!notification)
                continue;

            [self updateLocalNotification:[notification copy]
                              withOptions:options];

            [self fireEvent:@"update" notification:notification];

            if (notifications.count > 1) {
                [NSThread sleepForTimeInterval:0.01];
            }
        }

        [self execCallback:command];
    }];
}

/**
 * Cancel a set of notifications.
 *
 * @param ids
 *      The IDs of the notifications
 */
- (void) cancel:(CDVInvokedUrlCommand*)command
{
    [self.commandDelegate runInBackground:^{
        for (NSNumber* id in command.arguments) {
            UILocalNotification* notification;

            notification = [self.app localNotificationWithId:id];

            if (!notification)
                continue;

            [self.app cancelLocalNotification:notification];
            [self fireEvent:@"cancel" notification:notification];
        }

        [self execCallback:command];
    }];
}

/**
 * Cancel all local notifications.
 */
- (void) cancelAll:(CDVInvokedUrlCommand*)command
{
    [self.commandDelegate runInBackground:^{
        [self cancelAllLocalNotifications];
        [self fireEvent:@"cancelall"];
        [self execCallback:command];
    }];
}

/**
 * Clear a set of notifications.
 *
 * @param ids
 *      The IDs of the notifications
 */
- (void) clear:(CDVInvokedUrlCommand*)command
{
    [self.commandDelegate runInBackground:^{
        for (NSNumber* id in command.arguments) {
            UILocalNotification* notification;

            notification = [self.app localNotificationWithId:id];

            if (!notification)
                continue;

            [self.app clearLocalNotification:notification];
            [self fireEvent:@"clear" notification:notification];
        }

        [self execCallback:command];
    }];
}

/**
 * Clear all local notifications.
 */
- (void) clearAll:(CDVInvokedUrlCommand*)command
{
    [self.commandDelegate runInBackground:^{
        [self clearAllLocalNotifications];
        [self fireEvent:@"clearall"];
        [self execCallback:command];
    }];
}

/**
 * If a notification by ID is present.
 *
 * @param id
 *      The ID of the notification
 */
- (void) isPresent:(CDVInvokedUrlCommand *)command
{
    [self isPresent:command type:NotifcationTypeAll];
}

/**
 * If a notification by ID is scheduled.
 *
 * @param id
 *      The ID of the notification
 */
- (void) isScheduled:(CDVInvokedUrlCommand*)command
{
    [self isPresent:command type:NotifcationTypeScheduled];
}

/**
 * Check if a notification with an ID is triggered.
 *
 * @param id
 *      The ID of the notification
 */
- (void) isTriggered:(CDVInvokedUrlCommand*)command
{
    [self isPresent:command type:NotifcationTypeTriggered];
}

/**
 * Check if a notification with an ID exists.
 *
 * @param type
 *      The notification life cycle type
 */
- (void) isPresent:(CDVInvokedUrlCommand*)command
              type:(NotificationType)type;
{
    [self.commandDelegate runInBackground:^{
        NSNumber* id = [command argumentAtIndex:0];
        BOOL exist;

        CDVPluginResult* result;

        if (type == NotifcationTypeAll) {
            exist = [self.app localNotificationExist:id];
        } else {
            exist = [self.app localNotificationExist:id type:type];
        }

        result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                                     messageAsBool:exist];

        [self.commandDelegate sendPluginResult:result
                                    callbackId:command.callbackId];
    }];
}

/**
 * List all ids from all local notifications.
 */
- (void) getAllIds:(CDVInvokedUrlCommand*)command
{
    [self getIds:command byType:NotifcationTypeAll];
}

/**
 * List all ids from all pending notifications.
 */
- (void) getScheduledIds:(CDVInvokedUrlCommand*)command
{
    [self getIds:command byType:NotifcationTypeScheduled];
}

/**
 * List all ids from all triggered notifications.
 */
- (void) getTriggeredIds:(CDVInvokedUrlCommand*)command
{
    [self getIds:command byType:NotifcationTypeTriggered];
}

/**
 * List of ids for given local notifications.
 *
 * @param type
 *      Notification life cycle type
 * @param ids
 *      The IDs of the notifications
 */
- (void) getIds:(CDVInvokedUrlCommand*)command
         byType:(NotificationType)type;
{
    [self.commandDelegate runInBackground:^{
        CDVPluginResult* result;
        NSArray* ids;

        if (type == NotifcationTypeAll) {
            ids = [self.app localNotificationIds];
        } else {
            ids = [self.app localNotificationIdsByType:type];
        }

        result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                                    messageAsArray:ids];

        [self.commandDelegate sendPluginResult:result
                                    callbackId:command.callbackId];
    }];
}

/**
 * Propertys for given local notification.
 */
- (void) getSingle:(CDVInvokedUrlCommand*)command
{
    [self getOption:command byType:NotifcationTypeAll];
}

/**
 * Propertya for given scheduled notification.
 */
- (void) getSingleScheduled:(CDVInvokedUrlCommand*)command
{
    [self getOption:command byType:NotifcationTypeScheduled];
}

// Propertys for given triggered notification
- (void) getSingleTriggered:(CDVInvokedUrlCommand*)command
{
    [self getOption:command byType:NotifcationTypeTriggered];
}

/**
 * Property list for given local notifications.
 *
 * @param ids
 *      The IDs of the notifications
 */
- (void) getAll:(CDVInvokedUrlCommand*)command
{
    [self getOptions:command byType:NotifcationTypeAll];
}

/**
 * Property list for given scheduled notifications.
 *
 * @param ids
 *      The IDs of the notifications
 */
- (void) getScheduled:(CDVInvokedUrlCommand*)command
{
    [self getOptions:command byType:NotifcationTypeScheduled];
}

/**
 * Property list for given triggered notifications.
 *
 * @param ids
 *      The IDs of the notifications
 */
- (void) getTriggered:(CDVInvokedUrlCommand *)command
{
    [self getOptions:command byType:NotifcationTypeTriggered];
}

/**
 * Propertys for given triggered notification.
 *
 * @param type
 *      Notification life cycle type
 * @param ids
 *      The ID of the notification
 */
- (void) getOption:(CDVInvokedUrlCommand*)command
            byType:(NotificationType)type;
{
    [self.commandDelegate runInBackground:^{
        NSArray* ids = command.arguments;
        NSArray* notifications;
        CDVPluginResult* result;

        if (type == NotifcationTypeAll) {
            notifications = [self.app localNotificationOptionsById:ids];
        }
        else {
            notifications = [self.app localNotificationOptionsByType:type
                                                               andId:ids];
        }

        result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                                    messageAsDictionary:notifications[0]];

        [self.commandDelegate sendPluginResult:result
                                    callbackId:command.callbackId];
    }];
}

/**
 * Property list for given triggered notifications.
 *
 * @param type
 *      Notification life cycle type
 * @param ids
 *      The IDs of the notifications
 */
- (void) getOptions:(CDVInvokedUrlCommand*)command
             byType:(NotificationType)type;
{
    [self.commandDelegate runInBackground:^{
        NSArray* ids = command.arguments;
        NSArray* notifications;
        CDVPluginResult* result;

        if (type == NotifcationTypeAll && ids.count == 0) {
            notifications = [self.app localNotificationOptions];
        }
        else if (type == NotifcationTypeAll) {
            notifications = [self.app localNotificationOptionsById:ids];
        }
        else if (ids.count == 0) {
            notifications = [self.app localNotificationOptionsByType:type];
        }
        else {
            notifications = [self.app localNotificationOptionsByType:type
                                                               andId:ids];
        }

        result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                                    messageAsArray:notifications];

        [self.commandDelegate sendPluginResult:result
                                    callbackId:command.callbackId];
    }];
}

/**
 * Inform if the app has the permission to show
 * badges and local notifications.
 */
- (void) hasPermission:(CDVInvokedUrlCommand*)command
{
    [self.commandDelegate runInBackground:^{
        CDVPluginResult* result;
        BOOL hasPermission;

        hasPermission = [self.app hasPermissionToScheduleLocalNotifications];

        result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                                     messageAsBool:hasPermission];

        [self.commandDelegate sendPluginResult:result
                                    callbackId:command.callbackId];
    }];
}

/**
 * Ask for permission to show badges.
 */
- (void) registerPermission:(CDVInvokedUrlCommand*)command
{
    if ([[UIApplication sharedApplication]
         respondsToSelector:@selector(registerUserNotificationSettings:)])
    {
        _command = command;

        [self.commandDelegate runInBackground:^{
            [self.app registerPermissionToScheduleLocalNotifications];
        }];
    } else {
        [self hasPermission:command];
    }
}

#pragma mark -
#pragma mark Core Logic

/**
 * Schedule the local notification.
 */
- (void) scheduleLocalNotification:(UILocalNotification*)notification
{
    [self cancelForerunnerLocalNotification:notification];
    [self.app scheduleLocalNotification:notification];
}

/**
 * Update the local notification.
 */
- (void) updateLocalNotification:(UILocalNotification*)notification
                     withOptions:(NSDictionary*)newOptions
{
    NSMutableDictionary* options = [notification.userInfo mutableCopy];

    [options addEntriesFromDictionary:newOptions];
    [options setObject:[NSDate date] forKey:@"updatedAt"];

    notification = [[UILocalNotification alloc]
                    initWithOptions:options];

    [self scheduleLocalNotification:notification];
}

/**
 * Cancel all local notifications.
 */
- (void) cancelAllLocalNotifications
{
    [self.app cancelAllLocalNotifications];
    [self.app setApplicationIconBadgeNumber:0];
}

/**
 * Clear all local notifications.
 */
- (void) clearAllLocalNotifications
{
    [self.app clearAllLocalNotifications];
    [self.app setApplicationIconBadgeNumber:0];
}

/**
 * Cancel a maybe given forerunner with the same ID.
 */
- (void) cancelForerunnerLocalNotification:(UILocalNotification*)notification
{
    NSNumber* id = notification.options.id;
    UILocalNotification* forerunner;

    forerunner = [self.app localNotificationWithId:id];

    if (!forerunner)
        return;

    [self.app cancelLocalNotification:forerunner];
}

/**
 * Cancels all non-repeating local notification older then
 * a specific amount of seconds
 */
- (void) cancelAllNotificationsWhichAreOlderThen:(float)seconds
{
    NSArray* notifications;

    notifications = [self.app localNotifications];

    for (UILocalNotification* notification in notifications)
    {
        if (![notification isRepeating]
            && notification.timeIntervalSinceFireDate > seconds)
        {
            [self.app cancelLocalNotification:notification];
            [self fireEvent:@"cancel" notification:notification];
        }
    }
}

#pragma mark -
#pragma mark Delegates

/**
 * Calls the cancel or trigger event after a local notification was received.
 * Cancels the local notification if autoCancel was set to true.
 */
- (void) didReceiveLocalNotification:(NSNotification*)notification
{
    UILocalNotification* localNotification = [notification object];

    if ([localNotification wasUpdated])
        return;
    
    NSTimeInterval timeInterval = [localNotification timeIntervalSinceLastTrigger];
    
    NSString* event = (timeInterval <= 1 && deviceready) ? @"trigger" : @"interactedWith";
    
    [self fireEvent:event notification:localNotification];
    
    if (![event isEqualToString:@"interactedWith"])
        return;
    
    if ([localNotification isRepeating]) {
        [self fireEvent:@"clear" notification:localNotification];
    } else {
        [self.app cancelLocalNotification:localNotification];
        [self fireEvent:@"cancel" notification:localNotification];
    }
}

/**
 * Calls the cancel or trigger event after a local notification was received.
 * Cancels the local notification if autoCancel was set to true.
 */
- (void) didReceiveLocalNotificationWithAction:(UILocalNotification*)localNotification
{
    [self fireEvent:@"interactedWith" notification:localNotification];
}

/**
 * Called when app has started
 * (by clicking on a local notification).
 */
- (void) didFinishLaunchingWithOptions:(NSNotification*)notification
{
    NSDictionary* launchOptions = [notification userInfo];

    UILocalNotification* localNotification;

    localNotification = [launchOptions objectForKey:
                         UIApplicationLaunchOptionsLocalNotificationKey];

    if (localNotification) {
        [self didReceiveLocalNotification:
         [NSNotification notificationWithName:CDVLocalNotification
                                       object:localNotification]];
    }
}

/**
 * Called on otification settings registration is completed.
 */
- (void) didRegisterUserNotificationSettings:(UIUserNotificationSettings*)settings
{
    if (_command)
    {
        [self hasPermission:_command];
        _command = NULL;
    }
}

/**
 * Called when a notification action has been recieved
 */
- (void) handleActionWithIdentifier:(UILocalNotification *)notification
{
    [self fireEvent:@"interactedWith" data:notification.userInfo];
}

- (void)registerCategory:(NSDictionary *)categoryJson
{
    NSMutableArray *categories = [[NSMutableArray alloc] init];
    
    UIUserNotificationType types;
    UIUserNotificationSettings *settings;
    
    [self removeRegisteredCategoriesPrivate:@[[categoryJson valueForKey:@"identifier"]]];
    
    settings = [[UIApplication sharedApplication]
                currentUserNotificationSettings];
    
    types = settings.types|UIUserNotificationTypeAlert|UIUserNotificationTypeBadge|UIUserNotificationTypeSound;
    
    NSSet *existingCategories = settings.categories;
    
    NSMutableArray *actions = [[NSMutableArray alloc] init];
    for (NSDictionary *action in [categoryJson valueForKey:@"actions"])
    {
        UIMutableUserNotificationAction *newAction = [[UIMutableUserNotificationAction alloc] init];
        if ([[action valueForKey:@"mode"] isEqualToString:@"foreground"])
        {
            [newAction setActivationMode:UIUserNotificationActivationModeForeground];
        }
        else
        {
            [newAction setActivationMode:UIUserNotificationActivationModeBackground];
        }
        [newAction setTitle:[action valueForKey:@"title"]];
        [newAction setIdentifier:[action valueForKey:@"identifier"]];
        if([[action valueForKey:@"textInput"] boolValue])
            newAction.behavior = UIUserNotificationActionBehaviorTextInput;
        [newAction setDestructive:NO];
        [newAction setAuthenticationRequired:NO];
        [actions addObject:newAction];
    }
        
    UIMutableUserNotificationCategory *newCategory = [[UIMutableUserNotificationCategory alloc] init];
        
    [newCategory setIdentifier:[categoryJson valueForKey:@"identifier"]];
        
    [newCategory setActions:actions forContext:UIUserNotificationActionContextDefault];
        
    [newCategory setActions:actions forContext:UIUserNotificationActionContextMinimal];
        
    [categories addObject:newCategory];
    
    [categories addObjectsFromArray:[existingCategories allObjects]];
    
    NSSet *categoriesSet = [NSSet setWithArray:categories];
    
    settings = [UIUserNotificationSettings settingsForTypes:types categories:categoriesSet];
    
    [[UIApplication sharedApplication] registerUserNotificationSettings:settings];

}

- (void)getRegisteredCategories:(CDVInvokedUrlCommand *)command
{
    [self.commandDelegate runInBackground:^{
        UIUserNotificationType types;
        UIUserNotificationSettings *settings;
    
        settings = [[UIApplication sharedApplication]
                currentUserNotificationSettings];
    
        types = settings.types|UIUserNotificationTypeAlert|UIUserNotificationTypeBadge|UIUserNotificationTypeSound;
    
        NSSet *existingCategories = settings.categories;
        
        NSMutableArray *categoriesArray = [[NSMutableArray alloc] init];
        
        for(id category in existingCategories)
        {
            NSString* categoryId = [category identifier];
            [categoriesArray addObject:categoryId];
        }
    
        CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:[categoriesArray componentsJoinedByString:@","]];
    
        [self.commandDelegate sendPluginResult:result
                                callbackId:command.callbackId];
    }];
}

- (void)removeRegisteredCategories:(CDVInvokedUrlCommand *)command
{
    [self.commandDelegate runInBackground:^{
        [self removeRegisteredCategoriesPrivate:command.arguments];
    }];
}

- (void)removeRegisteredCategoriesPrivate:(NSArray *)categories
{
    UIUserNotificationType types;
    UIUserNotificationSettings *settings;
    
    UIUserNotificationSettings *exitingSettings = [[UIApplication sharedApplication]
                currentUserNotificationSettings];
    
    types = settings.types|UIUserNotificationTypeAlert|UIUserNotificationTypeBadge|UIUserNotificationTypeSound;
    
    NSMutableArray *existingCategories = [exitingSettings.categories allObjects];
    NSMutableArray *newCategories = [[NSMutableArray alloc] init];
    
    for(id category in existingCategories)
    {
        bool addToNewList = true;
        
        NSString* existingCategoryId = [category identifier];
        for(NSString *categoryIdentifierToDelete in categories)
        {
            if([categoryIdentifierToDelete isEqualToString:existingCategoryId])
            {
                addToNewList = false;
                break;
            }
        }
        
        if(addToNewList)
            [newCategories addObject:category];
    }
    
    NSSet *newCategoriesSet = [NSSet setWithArray:newCategories];
    
    settings = [UIUserNotificationSettings settingsForTypes:types categories:newCategoriesSet];
    
    [[UIApplication sharedApplication] registerUserNotificationSettings:settings];
}

- (void)clearRegisteredCategories:(CDVInvokedUrlCommand *)command
{
    [self.commandDelegate runInBackground:^{
        UIUserNotificationType types;
        UIUserNotificationSettings *settings;
        
        types = settings.types|UIUserNotificationTypeAlert|UIUserNotificationTypeBadge|UIUserNotificationTypeSound;
        
        settings = [UIUserNotificationSettings settingsForTypes:types categories:nil];
        
        [[UIApplication sharedApplication] registerUserNotificationSettings:settings];
    }];
}


#pragma mark -
#pragma mark Life Cycle

/**
 * Registers obervers after plugin was initialized.
 */
- (void) pluginInitialize
{
    NSNotificationCenter* center = [NSNotificationCenter
                                    defaultCenter];

    eventQueue = [[NSMutableArray alloc] init];

    [center addObserver:self
               selector:@selector(didReceiveLocalNotification:)
                   name:CDVLocalNotification
                 object:nil];

    [center addObserver:self
               selector:@selector(didFinishLaunchingWithOptions:)
                   name:UIApplicationDidFinishLaunchingNotification
                 object:nil];

    [center addObserver:self
               selector:@selector(didRegisterUserNotificationSettings:)
                   name:UIApplicationRegisterUserNotificationSettings
                 object:nil];

}

/**
 * Clears all single repeating notifications which are older then 5 days
 * before the app terminates.
 */
- (void) onAppTerminate
{
    [self cancelAllNotificationsWhichAreOlderThen:432000];
}

#pragma mark -
#pragma mark Helper

/**
 * Retrieves the application state
 *
 * @return
 *      Either "background" or "foreground"
 */
- (NSString*) applicationState
{
    UIApplicationState state = [self.app applicationState];

    bool isActive = state == UIApplicationStateActive;

    return isActive ? @"foreground" : @"background";
}

/**
 * Simply invokes the callback without any parameter.
 */
- (void) execCallback:(CDVInvokedUrlCommand*)command
{
    CDVPluginResult *result = [CDVPluginResult
                               resultWithStatus:CDVCommandStatus_OK];

    [self.commandDelegate sendPluginResult:result
                                callbackId:command.callbackId];
}

/**
 * Short hand for shared application instance.
 */
- (UIApplication*) app
{
    return [UIApplication sharedApplication];
}

/**
 * Fire general event.
 */
- (void) fireEvent:(NSString*)event
{
    [self fireEvent:event notification:NULL];
}

/**
 * Fire event for local notification.
 */
- (void) fireEvent:(NSString*)event notification:(UILocalNotification*)notification
{
    NSString* js;
    NSString* params = [NSString stringWithFormat:
                        @"\"%@\"", self.applicationState];

    if (notification) {
        NSString* args = [notification encodeToJSON];

        params = [NSString stringWithFormat:
                  @"%@,'%@'",
                  args, self.applicationState];
    }

    js = [NSString stringWithFormat:
          @"cordova.plugins.notification.core.fireEvent('%@', %@)",
          event, params];

    if (deviceready) {
        [self.commandDelegate evalJs:js];
    } else {
        [self.eventQueue addObject:js];
    }
}

/**
 * Fire event for local notification..
 
 */
- (void) fireEvent:(NSString*)event data:(NSDictionary*)dictionary
{
    NSString* js;
    NSString* params = [NSString stringWithFormat:
                        @"\"%@\"", self.applicationState];
    
    if (dictionary) {
        NSData *jsonData = [NSJSONSerialization dataWithJSONObject:dictionary options:0 error:nil];
        NSString* args = [[NSString alloc] initWithBytes:[jsonData bytes] length:[jsonData length] encoding:NSUTF8StringEncoding];
        
        params = [NSString stringWithFormat:
                  @"%@,'%@'",
                  args, self.applicationState];
    }
    
    js = [NSString stringWithFormat:
          @"cordova.plugins.notification.core.fireEvent('%@', %@)",
          event, params];
    
    if (deviceready) {
        [self.commandDelegate evalJs:js];
    } else {
        [self.eventQueue addObject:js];
    }
}

// --------------------------------------------------------------------------------------

- (void)unregisterForPush:(CDVInvokedUrlCommand*)command;
{
    self.callbackId = command.callbackId;

    [[UIApplication sharedApplication] unregisterForRemoteNotifications];
    [self successWithMessage:@"unregistered"];
}

- (void)registerForPush:(CDVInvokedUrlCommand*)command;
{
    self.callbackId = command.callbackId;

    NSMutableDictionary* options = [command.arguments objectAtIndex:0];

#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 80000
        UIUserNotificationType UserNotificationTypes = UIUserNotificationTypeNone;
#endif
    UIRemoteNotificationType notificationTypes = UIRemoteNotificationTypeNone;

    id badgeArg = [options objectForKey:@"badge"];
    id soundArg = [options objectForKey:@"sound"];
    id alertArg = [options objectForKey:@"alert"];

    if ([badgeArg isKindOfClass:[NSString class]])
    {
        if ([badgeArg isEqualToString:@"true"]) {
            notificationTypes |= UIRemoteNotificationTypeBadge;
#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 80000
            UserNotificationTypes |= UIUserNotificationTypeBadge;
#endif
        }
    }
    else if ([badgeArg boolValue]) {
        notificationTypes |= UIRemoteNotificationTypeBadge;
#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 80000
        UserNotificationTypes |= UIUserNotificationTypeBadge;
#endif
    }

    if ([soundArg isKindOfClass:[NSString class]])
    {
        if ([soundArg isEqualToString:@"true"]) {
            notificationTypes |= UIRemoteNotificationTypeSound;
#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 80000
            UserNotificationTypes |= UIUserNotificationTypeSound;
#endif
    }
    }
    else if ([soundArg boolValue]) {
        notificationTypes |= UIRemoteNotificationTypeSound;
#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 80000
        UserNotificationTypes |= UIUserNotificationTypeSound;
#endif
    }

    if ([alertArg isKindOfClass:[NSString class]])
    {
        if ([alertArg isEqualToString:@"true"]) {
            notificationTypes |= UIRemoteNotificationTypeAlert;
#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 80000
            UserNotificationTypes |= UIUserNotificationTypeAlert;
#endif
    }
    }
    else if ([alertArg boolValue]) {
        notificationTypes |= UIRemoteNotificationTypeAlert;
#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 80000
        UserNotificationTypes |= UIUserNotificationTypeAlert;
#endif
    }

    notificationTypes |= UIRemoteNotificationTypeNewsstandContentAvailability;
#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 80000
    UserNotificationTypes |= UIUserNotificationActivationModeBackground;
#endif

    //self.callback = [options objectForKey:@"ecb"];

    if (notificationTypes == UIRemoteNotificationTypeNone)
        NSLog(@"Notifications.register: Push notification type is set to none");

    isInline = NO;

#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 80000
    if ([[UIApplication sharedApplication]respondsToSelector:@selector(registerUserNotificationSettings:)]) {
        NSSet *existingCategories = [[UIApplication sharedApplication] currentUserNotificationSettings].categories;
        UIUserNotificationSettings *settings = [UIUserNotificationSettings settingsForTypes:UserNotificationTypes categories:existingCategories];
        [[UIApplication sharedApplication] registerUserNotificationSettings:settings];
        [[UIApplication sharedApplication] registerForRemoteNotifications];
    } else {
            [[UIApplication sharedApplication] registerForRemoteNotificationTypes:notificationTypes];
    }
#else
        [[UIApplication sharedApplication] registerForRemoteNotificationTypes:notificationTypes];
#endif

    //if (notificationMessage)            // if there is a pending startup notification
    //    [self notificationReceived];    // go ahead and process it
}

- (void)didRegisterForRemoteNotificationsWithDeviceToken:(NSData *)deviceToken {

    NSMutableDictionary *results = [NSMutableDictionary dictionary];
    NSString *token = [[[[deviceToken description] stringByReplacingOccurrencesOfString:@"<"withString:@""]
                        stringByReplacingOccurrencesOfString:@">" withString:@""]
                       stringByReplacingOccurrencesOfString: @" " withString: @""];
    [results setValue:token forKey:@"deviceToken"];

    #if !TARGET_IPHONE_SIMULATOR
        // Get Bundle Info for Remote Registration (handy if you have more than one app)
        [results setValue:[[[NSBundle mainBundle] infoDictionary] objectForKey:@"CFBundleDisplayName"] forKey:@"appName"];
        [results setValue:[[[NSBundle mainBundle] infoDictionary] objectForKey:@"CFBundleVersion"] forKey:@"appVersion"];

        // Check what Notifications the user has turned on.  We registered for all three, but they may have manually disabled some or all of them.
        NSUInteger rntypes = [[UIApplication sharedApplication] enabledRemoteNotificationTypes];

        // Set the defaults to disabled unless we find otherwise...
        NSString *pushBadge = @"disabled";
        NSString *pushAlert = @"disabled";
        NSString *pushSound = @"disabled";

        // Check what Registered Types are turned on. This is a bit tricky since if two are enabled, and one is off, it will return a number 2... not telling you which
        // one is actually disabled. So we are literally checking to see if rnTypes matches what is turned on, instead of by number. The "tricky" part is that the
        // single notification types will only match if they are the ONLY one enabled.  Likewise, when we are checking for a pair of notifications, it will only be
        // true if those two notifications are on.  This is why the code is written this way
        if(rntypes & UIRemoteNotificationTypeBadge){
            pushBadge = @"enabled";
        }
        if(rntypes & UIRemoteNotificationTypeAlert) {
            pushAlert = @"enabled";
        }
        if(rntypes & UIRemoteNotificationTypeSound) {
            pushSound = @"enabled";
        }

        [results setValue:pushBadge forKey:@"pushBadge"];
        [results setValue:pushAlert forKey:@"pushAlert"];
        [results setValue:pushSound forKey:@"pushSound"];

        // Get the users Device Model, Display Name, Token & Version Number
        UIDevice *dev = [UIDevice currentDevice];
        [results setValue:dev.name forKey:@"deviceName"];
        [results setValue:dev.model forKey:@"deviceModel"];
        [results setValue:dev.systemVersion forKey:@"deviceSystemVersion"];
    
        [self successWithMessage:token];
#endif
}

- (void)didFailToRegisterForRemoteNotificationsWithError:(NSError *)error
{
    [self failWithMessage:@"" withError:error];
}

- (void)didReceiveRemoteNotification:(NSDictionary*)pushData {
    NSLog(@"Push notification received");
    
    NSMutableDictionary *newPushData = [[NSMutableDictionary alloc] init];
    
    NSMutableDictionary *payload = [[NSMutableDictionary alloc] init];
    
    NSMutableDictionary *other = [[NSMutableDictionary alloc] init];
    
    for(NSString *key in pushData)
    {
        id object = [pushData objectForKey:key];

        if([key compare:@"aps"] == NSOrderedSame)
        {
            NSDictionary *apsDictonary = object;
            
            for(NSString *apsKey in apsDictonary)
            {
                id apsObject = [apsDictonary objectForKey:apsKey];
                
                if([apsKey compare:@"alert"] == NSOrderedSame)
                    [newPushData setObject:apsObject forKey:@"message"];
                else
                    [newPushData setObject:apsObject forKey:apsKey];
            }
            continue;
        }
        else if([key compare:@"payload"] == NSOrderedSame)
        {
            NSDictionary *payloadDictonary = object;
            
            for(NSString *payloadKey in payloadDictonary)
            {
                id payloadObject = [payloadDictonary objectForKey:payloadKey];
                [payload setObject:payloadObject forKey:payloadKey];
            }
            continue;
        }
        else
        {
            [other setObject:object forKey:key];
        }
    }
    
    if([payload count] > 0)
        [newPushData setObject:other forKey:@"payload"];
    
    if([other count] > 0)
        [newPushData setObject:other forKey:@"other"];
    
    [newPushData setObject:@"APNS" forKey:@"service"];
    
    [self fireEvent:@"pushReceived" data:newPushData];
}

- (void)setApplicationIconBadgeNumber:(CDVInvokedUrlCommand *)command {

    self.callbackId = command.callbackId;

    NSMutableDictionary* options = [command.arguments objectAtIndex:0];
    int badge = [[options objectForKey:@"badge"] intValue] ?: 0;

    [[UIApplication sharedApplication] setApplicationIconBadgeNumber:badge];

    [self successWithMessage:[NSString stringWithFormat:@"app badge count set to %d", badge]];
}

-(void)successWithMessage:(NSString *)message
{
    if (self.callbackId != nil)
    {
        CDVPluginResult *commandResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:message];
        [self.commandDelegate sendPluginResult:commandResult callbackId:self.callbackId];
    }
}

-(void)failWithMessage:(NSString *)message withError:(NSError *)error
{
    NSString *errorMessage = (error) ? [NSString stringWithFormat:@"%@ - %@", message, [error localizedDescription]] : message;
    CDVPluginResult *commandResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:errorMessage];

    [self.commandDelegate sendPluginResult:commandResult callbackId:self.callbackId];
}

@end
