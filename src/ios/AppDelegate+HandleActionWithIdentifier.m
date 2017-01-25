#import "AppDelegate+HandleActionWithIdentifier.h"
#import "UILocalNotification+APPLocalNotification.h"
#import "Notification.h"
#import <Availability.h>

@implementation AppDelegate (HandleActionWithIdentifier)

- (id) getCommandInstance:(NSString*)className
{
    return [self.viewController getCommandInstance:className];
}

- (void)application:(UIApplication *)application handleActionWithIdentifier:(NSString *)identifier forLocalNotification:(UILocalNotification *)notification withResponseInfo:(NSDictionary *)responseInfo completionHandler:(void (^)())completionHandler
{
    Notification *notificationInstance = [self getCommandInstance:@"Notification"];
    
    NSMutableDictionary* options = [notification.userInfo mutableCopy];
    [options setValue:identifier forKey:@"actionResponseIdentifier"];
    if(responseInfo.count > 0)
        [options setObject:[[responseInfo allValues] objectAtIndex:0] forKey:@"actionResponse"];
    
    UILocalNotification *localNotification = [[UILocalNotification alloc] initWithOptions:options];
    
    [notificationInstance didReceiveLocalNotificationWithAction:localNotification];
}

@end