using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Runtime.Serialization;
using System.Windows;
using System.Net;

using Microsoft.Phone.Controls;
using Microsoft.Phone.Notification;
using Microsoft.Phone.Scheduler;
using Microsoft.Phone.Shell;

using Newtonsoft.Json;

namespace WPCordovaClassLib.Cordova.Commands
{
  public class Notification : BaseCommand
  {
    public const string PushReceived = "pushReceived";

    private const string InvalidRegistrationError = "Unable to open a channel with the specified name. The most probable cause is that you have already registered a channel with a different name. Call unregister(old-channel-name) or uninstall and redeploy your application.";
    private const string MissingChannelError = "Couldn't find a channel with the specified name.";

    private static readonly DateTime Epoch = new DateTime(1970, 1, 1, 0, 0, 0);
    private static readonly IList<Event> pendingEvents = new List<Event>();

    private readonly IDictionary<string, ChannelListener> channelListeners = new Dictionary<string, ChannelListener>();

    #region Encoding/Decoding paramaters into a URI

    private static Uri EncodeParameterUri(string identifier)
    {
      NotificationParameters parameters = new NotificationParameters();

      parameters.Identifier = identifier;

      return EncodeParameterUri(parameters);
    }

    private static Uri EncodeParameterUri(NotificationParameters parameters)
    {
      return new Uri(string.Format("/Plugins/com.commontime.cordova.notification/NotificationPage.xaml?id={0}", parameters.Identifier), UriKind.Relative);
    }

    private static NotificationParameters DecodeParameterUri(Uri uri)
    {
      NotificationParameters parameters = new NotificationParameters();

      string uriString = uri.ToString();
      string idKey = "id=";
      int idStart = uriString.IndexOf(idKey);

      if (idStart >= 0)
      {
        parameters.Identifier = HttpUtility.UrlDecode(uriString.Substring(idStart + idKey.Length));
      }

      return parameters;
    }

    #endregion

    #region Events

    private class Event
    {
      public Event(string name, object args)
      {
        this.Name = name;
        this.Args = args;
      }

      public string Name
      {
        get;
        private set;
      }

      public object Args
      {
        get;
        private set;
      }
    }

    private static void FireEvent(string name, object args)
    {
      Deployment.Current.Dispatcher.BeginInvoke(() =>
      {
        PhoneApplicationFrame frame;
        PhoneApplicationPage page;
        CordovaView cordovaView;

        if (TryCast(Application.Current.RootVisual, out frame) &&
            TryCast(frame.Content, out page) &&
            TryCast(page.FindName("CordovaView"), out cordovaView))
        {
          cordovaView.Browser.Dispatcher.BeginInvoke(() =>
          {
            try
            {
              string script = string.Format("cordova.plugins.notification.core.fireEvent('{0}', {1})", name, JsonConvert.SerializeObject(args));

#if DEBUG
              Debug.WriteLine("Firing event: " + script);
#endif

              cordovaView.Browser.InvokeScript("execScript", script);
            }
            catch (Exception e)
            {
              Debug.WriteLine("Cannot fire event " + name + ": " + e.Message);
            }
          });
        }
      });
    }

    private static void QueueEvent(string name, object args)
    {
      lock (pendingEvents)
      {
        pendingEvents.Add(new Event(name, args));
      }
    }

    public static void QueueNotificationEvent(Uri uri)
    {
      NotificationParameters parameters = DecodeParameterUri(uri);

      if (!string.IsNullOrEmpty(parameters.Identifier))
      {
        QueueEvent(PushReceived, parameters);
      }
    }

    private static void FireAllPendingEvents()
    {
      lock (pendingEvents)
      {
        foreach (Event e in pendingEvents)
        {
          FireEvent(e.Name, e.Args);
        }

        pendingEvents.Clear();
      }
    }

    #endregion

    public void deviceready(string args)
    {
    }

    public void pause(string args)
    {
    }

    public void resume(string args)
    {
    }

    public void registerForPush(string options)
    {
      PushOptions pushOptions;

      if (!TryDeserializeOptions(options, out pushOptions))
      {
        DispatchCommandResult(new PluginResult(PluginResult.Status.JSON_EXCEPTION));

        return;
      }

#if DEBUG
      Debug.WriteLine("Registering for push on channel " + pushOptions.ChannelName);
#endif

      HttpNotificationChannel pushChannel = HttpNotificationChannel.Find(pushOptions.ChannelName);
      ChannelListener channelListener = null;

      if (!channelListeners.TryGetValue(pushOptions.ChannelName, out channelListener))
      {
        channelListener = new ChannelListener(pushOptions.ChannelName, this, this.CurrentCommandCallbackId);
        channelListeners[pushOptions.ChannelName] = channelListener;
      }

      if (pushChannel == null)
      {
        pushChannel = new HttpNotificationChannel(pushOptions.ChannelName);
        channelListener.Subscribe(pushChannel);

        try
        {
          for (int tries = 0; tries < 3 && pushChannel.ChannelUri == null; ++tries)
          {
            pushChannel.Open();
          }
        }
        catch (InvalidOperationException)
        {
          DispatchCommandResult(new PluginResult(PluginResult.Status.ERROR, InvalidRegistrationError));

          return;
        }

        pushChannel.BindToShellToast();
        pushChannel.BindToShellTile();
      }
      else
      {
        channelListener.Subscribe(pushChannel);
      }

      if (pushChannel.ChannelUri != null)
      {
#if DEBUG
        Debug.WriteLine("Already registered for push on channel " + pushChannel.ChannelName + " with URI " + pushChannel.ChannelUri);
#endif

        PluginResult result = new PluginResult(PluginResult.Status.OK, pushChannel.ChannelUri.ToString());

        result.KeepCallback = true;
        DispatchCommandResult(result);
      }

      FireAllPendingEvents();
    }

    public void unregisterForPush(string options)
    {
      PushOptions unregisterOptions;

      if (!TryDeserializeOptions(options, out unregisterOptions))
      {
        DispatchCommandResult(new PluginResult(PluginResult.Status.JSON_EXCEPTION));

        return;
      }

      HttpNotificationChannel pushChannel = HttpNotificationChannel.Find(unregisterOptions.ChannelName);

      if (pushChannel != null)
      {
        ChannelListener channelListener;

        if (channelListeners.TryGetValue(unregisterOptions.ChannelName, out channelListener))
        {
          channelListener.Unsubscribe(pushChannel);
          channelListeners.Remove(unregisterOptions.ChannelName);
        }

        pushChannel.UnbindToShellTile();
        pushChannel.UnbindToShellToast();
        pushChannel.Close();

#if DEBUG
        Debug.WriteLine("Unregistered for push on channel " + pushChannel.ChannelName);
#endif

        DispatchCommandResult(new PluginResult(PluginResult.Status.OK));
      }
      else
      {
        DispatchCommandResult(new PluginResult(PluginResult.Status.ERROR, MissingChannelError));
      }
    }

    #region Scheduled and Toast Notifications

    public void schedule(string args)
    {
      try
      {
        ScheduledNotification notification;

        if (!TryDeserializeOptions(args, out notification))
        {
          DispatchCommandResult(new PluginResult(PluginResult.Status.JSON_EXCEPTION));

          return;
        }

        Reminder reminder = new Reminder(notification.Identifier);
        DateTime beginTime = Epoch + TimeSpan.FromSeconds(notification.At);

#if DEBUG
        Debug.WriteLine("Attempting to scheduled notification for {0}; time is now {1}", beginTime, DateTime.Now);
#endif

        DateTime cutoff = DateTime.Now + TimeSpan.FromSeconds(30);

        if (beginTime < cutoff)
        {
          beginTime = cutoff;
        }

        reminder.Title = notification.Title;
        reminder.Content = notification.Text;
        reminder.BeginTime = beginTime;
        reminder.NavigationUri = EncodeParameterUri(reminder.Name);

        if (ScheduledActionService.Find(reminder.Name) != null)
        {
          ScheduledActionService.Remove(reminder.Name);
        }

        ScheduledActionService.Add(reminder);

#if DEBUG
        Debug.WriteLine("Scheduled notification for {0}", beginTime);
#endif

        DispatchCommandResult(new PluginResult(PluginResult.Status.OK));
      }
      catch (Exception e)
      {
#if DEBUG
        Debug.WriteLine("Could not schedule notification: {0}", e.Message);
#endif

        DispatchCommandResult(new PluginResult(PluginResult.Status.ERROR, e.Message));
      }
    }

    public void clear(string args)
    {
      try
      {
        string[] identifiers = null;

        if (!TryDeserializeOptions(args, out identifiers))
        {
          DispatchCommandResult(new PluginResult(PluginResult.Status.JSON_EXCEPTION));

          return;
        }

        CancelScheduledNotifications(identifiers);

        DispatchCommandResult(new PluginResult(PluginResult.Status.OK));
      }
      catch (Exception e)
      {
#if DEBUG
        Debug.WriteLine("Could not clear notifications: {0}", e.Message);
#endif

        DispatchCommandResult(new PluginResult(PluginResult.Status.ERROR, e.Message));
      }
    }

    public void clearAll(string args)
    {
      try
      {
        CancelAllScheduleNotifications();

        DispatchCommandResult(new PluginResult(PluginResult.Status.OK));
      }
      catch (Exception e)
      {
#if DEBUG
        Debug.WriteLine("Could not clear notifications: {0}", e.Message);
#endif

        DispatchCommandResult(new PluginResult(PluginResult.Status.ERROR, e.Message));
      }
    }

    public void cancel(string args)
    {
      try
      {
        string[] identifiers = null;

        if (!TryDeserializeOptions(args, out identifiers))
        {
          DispatchCommandResult(new PluginResult(PluginResult.Status.JSON_EXCEPTION));

          return;
        }

        CancelScheduledNotifications(identifiers);

        DispatchCommandResult(new PluginResult(PluginResult.Status.OK));
      }
      catch (Exception e)
      {
#if DEBUG
        Debug.WriteLine("Could not cancel notifications: {0}", e.Message);
#endif

        DispatchCommandResult(new PluginResult(PluginResult.Status.ERROR, e.Message));
      }
    }

    private void CancelScheduledNotifications(string[] names)
    {
      foreach (string name in names)
      {
        if (ScheduledActionService.Find(name) != null)
        {
          ScheduledActionService.Remove(name);
        }
      }
    }

    private void CancelAllScheduleNotifications()
    {
      IEnumerable<ScheduledAction> actions = ScheduledActionService.GetActions<ScheduledAction>();

      foreach (ScheduledAction action in actions)
      {
        ScheduledActionService.Remove(action.Name);
      }
    }

    public void showToastNotification(string options)
    {
      ShellToast toast;

      if (!TryDeserializeOptions(options, out toast))
      {
        DispatchCommandResult(new PluginResult(PluginResult.Status.JSON_EXCEPTION));

        return;
      }

      Deployment.Current.Dispatcher.BeginInvoke(toast.Show);
    }

    #endregion

    private sealed class ChannelListener
    {
      private readonly string name;
      private readonly Notification parent;
      private readonly string callbackId;

      public ChannelListener(string name, Notification parent, string callbackId)
      {
        this.name = name;
        this.parent = parent;
        this.callbackId = callbackId;
      }

      public void Subscribe(HttpNotificationChannel channel)
      {
        channel.ChannelUriUpdated += OnChannelUriUpdated;
        channel.ErrorOccurred += OnErrorOccurred;
        channel.ShellToastNotificationReceived += OnShellToastNotificationReceived;
        channel.HttpNotificationReceived += OnHttpNotificationReceived;
      }

      public void Unsubscribe(HttpNotificationChannel channel)
      {
        channel.ChannelUriUpdated -= OnChannelUriUpdated;
        channel.ErrorOccurred -= OnErrorOccurred;
        channel.ShellToastNotificationReceived -= OnShellToastNotificationReceived;
        channel.HttpNotificationReceived -= OnHttpNotificationReceived;
      }

      private void OnChannelUriUpdated(object sender, NotificationChannelUriEventArgs e)
      {
#if DEBUG
        Debug.WriteLine("Push channel URI changed to " + e.ChannelUri);
#endif
        PluginResult result = new PluginResult(PluginResult.Status.OK, e.ChannelUri.ToString());

        result.KeepCallback = true;
        parent.DispatchCommandResult(result, callbackId);
      }

      private void OnErrorOccurred(object sender, NotificationChannelErrorEventArgs e)
      {
#if DEBUG
        Debug.WriteLine("Received error on push channel: " + e.Message);
#endif

        PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.Message);

        result.KeepCallback = false;
        parent.DispatchCommandResult(result, callbackId);
      }

      private void OnShellToastNotificationReceived(object sender, NotificationEventArgs e)
      {
        string param = null;
        NotificationParameters notificationParameters = new NotificationParameters();

        if (e.Collection.TryGetValue("wp:Param", out param))
        {
          try
          {
            notificationParameters = DecodeParameterUri(new Uri(param, UriKind.Relative));          
          }
          catch (Exception)
          {
          }
        }

        string text1;

        if (e.Collection.TryGetValue("wp:Text1", out text1))
        {
          notificationParameters.Title = text1;
        
        }

        string text2;

        if (e.Collection.TryGetValue("wp:Text2", out text2))
        {
          notificationParameters.Text = text2;
        }

        Notification.FireEvent(PushReceived, notificationParameters);
      }

      private void OnHttpNotificationReceived(object sender, HttpNotificationEventArgs e)
      {
        var raw = new PushNotification
        {
          Type = "raw"
        };

        using (var reader = new StreamReader(e.Notification.Body))
        {
          string body = reader.ReadToEnd();

          Debug.WriteLine("Received " + body);

          raw.JsonContent.Add("Body", body);
        }

        Notification.FireEvent(PushReceived, raw);
      }
    }

    #region Parse Helpers

    private static bool TryDeserializeOptions<T>(string options, out T result) where T : class
    {
      result = null;

      try
      {
        var args = JsonConvert.DeserializeObject<string[]>(options);

        result = JsonConvert.DeserializeObject<T>(args[0]);

        return true;
      }
      catch
      {
        return false;
      }
    }

    private static bool TryCast<T>(object obj, out T result) where T : class
    {
      result = obj as T;

      return result != null;
    }

    #endregion

    #region Argument/Option classes

    [DataContract]
    private class PushOptions
    {
      [DataMember(Name = "channelName", IsRequired = true)]
      public string ChannelName
      {
        get;
        set;
      }
    }

    [DataContract]
    private class RegisterResult
    {
      [DataMember(Name = "uri", IsRequired = true)]
      public string Uri
      {
        get;
        set;
      }

      [DataMember(Name = "channel", IsRequired = true)]
      public string ChannelName
      {
        get;
        set;
      }
    }

    [DataContract]
    private class PushNotification
    {
      public PushNotification()
      {
        this.JsonContent = new Dictionary<string, object>();
      }

      [DataMember(Name = "jsonContent", IsRequired = true)]
      public IDictionary<string, object> JsonContent
      {
        get;
        set;
      }

      [DataMember(Name = "type", IsRequired = true)]
      public string Type
      {
        get;
        set;
      }
    }

    [DataContract]
    private class RegisterError
    {
      [DataMember(Name = "code", IsRequired = true)]
      public string Code
      {
        get;
        set;
      }

      [DataMember(Name = "message", IsRequired = true)]
      public string Message
      {
        get;
        set;
      }
    }

    [DataContract]
    private class NotificationParameters
    {
      public NotificationParameters()
      {
      }

      [DataMember(Name = "id", IsRequired = false)]
      public string Identifier
      {
        get;
        set;
      }

      [DataMember(Name = "title", IsRequired = false)]
      public string Title
      {
        get;
        set;
      }

      [DataMember(Name = "text", IsRequired = false)]
      public string Text
      {
        get;
        set;
      }
    }

    [DataContract]
    private class ScheduledNotification
    {
      public ScheduledNotification()
      {
      }

      [DataMember(Name = "id", IsRequired = false)]
      public string Identifier
      {
        get;
        set;
      }

      [DataMember(Name = "title", IsRequired = false)]
      public string Title
      {
        get;
        set;
      }

      [DataMember(Name = "text", IsRequired = false)]
      public string Text
      {
        get;
        set;
      }

      [DataMember(Name = "at", IsRequired = false)]
      public double At
      {
        get;
        set;
      }

      [DataMember(Name = "behaviour", IsRequired = false)]
      public string Behaviour
      {
        get;
        set;
      }

      [DataMember(Name = "sound", IsRequired = false)]
      public string Sound
      {
        get;
        set;
      }

      [DataMember(Name = "badge", IsRequired = false)]
      public int Badge
      {
        get;
        set;
      }
    }

    #endregion
  }
}