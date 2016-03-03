using System;
using System.Net;
using System.Windows.Navigation;

using Microsoft.Phone.Controls;

namespace WPCordovaClassLib.Cordova.Commands
{
  public partial class NotificationPage : PhoneApplicationPage
  {
    public NotificationPage()
    {
      InitializeComponent();
    }

    protected override void OnNavigatedTo(NavigationEventArgs e)
    {
      base.OnNavigatedTo(e);

      Notification.QueueNotificationEvent(e.Uri);

      this.NavigationService.Navigate(new Uri("/MainPage.xaml", UriKind.Relative));
    }

    protected override void OnNavigatedFrom(NavigationEventArgs e)
    {
      base.OnNavigatedFrom(e);

      // Make sure we can't navigate back here.
      NavigationService.RemoveBackEntry();
    }
  }
}