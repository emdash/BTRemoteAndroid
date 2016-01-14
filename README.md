This project started out life as an example app for the Blend Micro, a
small, BLE-enabled, Arduino-based micro controller. I used this
devices as the starting point for my remote control, and so it was
natural to use their sample code as the starting point for the android
connectivity.

It has been heavily re-worked.

In particular:

- All of the BLE logic has been refactored into a stand-alone service.
- The disconnect / reconnect logic is properly handled to the best of
  my knowledge (it was not in the original app).
- The main screens have been re-worked to eliminate the unecessary
  serial emulation. In fact, the main screen is only used for choosing
  a device. All else is handled by the service.
- A bunch of application-specific code has been added (parsing the
  notification stream, encoding / decoding the ASCII bluetooth
  protocol, etc).

Things I would do if I had more time:
- Define custom BLE characteristics for each message, and ditch the ASCII protocol.
- Re-work the main screen, so it doesn't use Red Bear Labs' styling.
- Eliminate the need to explicitly start the app. I don't know if this actually possible.
- Send all notifications to the device, rather than just Spotify.
- Find a way to skip tracks, play, pause, etc. that doesn't send spotify-specific intents.

Known Issues:
- Notification parsing (and thus the current artist and song) seems to be broken on more recent android.
- App is limited to working specifically with Spotify (except for volume adjustment)
- Volume adjustment is very slow.

