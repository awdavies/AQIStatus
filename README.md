AQI Status Checker
==================

Ever been too lazy to open PurpleAir, then navigate to where you are, then sort
of take a stab at guessing what the air quality is right now?

Ever wanted to see at a glance whether the wildfires in your area are preventing
you from being able to go outside for a run?

Ever wanted to know if it's safe to open the windows without taking a whiff of
smokey air into your fragile pink lungs?

Then this cheesy app is for you!

## What Does It Do?

This is a (very) simple app that goes to [PurpleAir](https://www.purpleair.com)
once every minute or so, fetches the data from all _outdoor_ sensors within 4
square miles of you (this will be configurable eventually), then gives you an
average of all sensors. The info will be available for viewing in a persistent
notification.

This applies the [LRAPA correction](http://lrapa.org/DocumentCenter/View/4147/PurpleAir-Correction-Summary)
to all PM2.5 readings before getting the AQI (as PurpleAir sensors tend to read
on the higher side).

That's it! That's all it does.

## How do I install it?

### Gradle

I'm too lazy right now to figure out how to install this thing on the play
store, so for now you'll have to install this thing yourself.

1. [Install Gradle](https://gradle.org/install/)
2. [Setup ADB](https://developer.android.com/studio/command-line/adb)
3. Connect your phone to ADB

Then after connecting to your phone via adb, run the following in the root of
the repo:

```shell
gradle installDebug
```

Then you should have `AQI Status Checker` installed on your phone.

Once you run it, enable all location permissions. You'll then be presented with
some simple options for updating the update frequency.

Starting the app will open a notification that will get the AQI in your
immediate area once per minute (ish). Then just leave the app in the background
and you're good to go.

### Release APK

This will create a lot of noise from your phone, but it is now possible to
install a release binary from under `app/release` in this very codebase! You can
also navigate through the releases list.

You can download it on your phone and then go through a bunch of security hoops
(at your own risk, of course).

## Caveats

* There is no warranty for this software. Use at your own risk.
* This is mostly a toy project, so this may never make it to the play store, and
  I may never add any of the fun features I've outlined.
* In the release binary tapping on the notification appears to remove the
  foreground service and force the user to close and reopen the app. Not sure
  why this is the case.
* If you and a friend are both running the app under the same WiFi, there is no
  mechanism in place to ensure both of you handle PurpleAir's rate limiter
  correctly, as both of you will be seen by PurpleAir's servers as the same IP
  address. This means it might be possible that one of you always has the latest
  AQI data and the other doesn't. The fix for this would be prohibitively time
  consuming for me to implement, so for now I just suggest one person turn off
  their WiFi and use mobile networking if they are in dire need of constant AQI
  updates.
* See issues section.

## Troubleshooting

* AQI is just sitting on pending:
  * This might be something to do with your update frequency being quite long,
    which is resulting in the location updater sitting around for a long time.
    If it's been like this for a few minutes, try updating the update interval
    to be longer/shorter than the one you have right now, which will restart
    the persistent notification foreground service. If this still doesn't work,
    there might be some other machine on your WiFi contact PurpleAir frequently
    (see caveats for more information on why this might happen), so it might be
    necessary to switch your phone to mobile networking instead of using WiFi.
    To force the foreground service to restart and immediately try to speak to
    PurpleAir, just change the update interval in your settings to a different
    number.

## Future Plans

* More options:
  * Radius of sensors from which to check.
  * Graphs and such.
* Alarms so you know when to open the windows or go joggin.
* Color coding for scary reds and spooky purples.
