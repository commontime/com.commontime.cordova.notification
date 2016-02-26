require 'rubygems'
require 'pushmeup'
GCM.host = 'https://android.googleapis.com/gcm/send'
GCM.format = :json
GCM.key = "Server Key Here"
#destination = ["APA91bF3EgVFe86JoOUAQhNrswTfBKwTfcBuAGs-YHNl5pXD7cXrZ7vzikl55gr_YoFDin9q1umtfUbNY-fCOOUDTsZSPH6Pp26R6Mg41C_f6Udsqn7heAjYInPX0r3lXe6bVuA6iAAw"]
destination = ["Device Token Here"]
data = {:title => 'ANYTHING', :message => "Anything!", :msgcnt => "1", :soundname => "beep.wav", :other => '{"key":"Elephant","colour":"grey"}'}

GCM.send_notification( destination, data)
