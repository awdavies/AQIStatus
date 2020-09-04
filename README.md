AQI Status Checker
==================

Ever been too lazy to open PurpleAir, then navigate to where you are, then sort
of take a stab at guessing what the air quality is right now?

Ever wanted to see at a glance whether the wildfires in your area are preventing
you from being able to go outside for a run?

Ever wanted to know if it's safe to open the windows without opening your door?

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
some options that do _absolutely nothing_ right now. Starting the app will then
open a notification that will get the AQI in your immediate area once per
minute (ish). Then just leave the app in the background and you're good to go.

## Caveats

* There is no warranty for this software. Use at your own risk.
* This is mostly a toy project, so this may never make it to the play store, and
  I may never add any of the fun features I've outlined.
* None of the options do anything yet.
* The back button in the app does _nothing_. Just put the app in the background.
* If you and a friend are both running the app under the same WiFi, there is no
  mechanism in place to ensure both of you handle PurpleAir's rate limiter
  correctly, as both of you will be seen by PurpleAir's servers as the same IP
  address. This means it might be possible that one of you always has the latest
  AQI data and the other doesn't. The fix for this would be prohibitively time
  consuming for me to implement, so for now I just suggest one person turn off
  their WiFi and use mobile networking if they are in dire need of constant.

## Future Plans

* Actual options:
  * Polling frequency.
  * Radius of sensors from which to check.
* Alarms so you know when to open the windows or go joggin.
* Color coding for scary reds and spooky purples.