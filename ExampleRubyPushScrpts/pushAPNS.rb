require 'rubygems'
require 'pushmeup'


APNS.host = 'gateway.sandbox.push.apple.com' 
APNS.port = 2195 
APNS.pem  = 'Path to ck.pem File Here'
APNS.pass = 'ck.pem Password Here'

device_token = 'Device Token Here'
# APNS.send_notification(device_token, 'Hello iPhone!' )
APNS.send_notification(device_token,  :alert => 'Anything', :badge => 1, :sound => 'beep.wav', :other => {:key => 'Elephant', :colour => "grey"})
