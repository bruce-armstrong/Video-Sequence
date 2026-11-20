# Video Sequence

Video Sequence is an offline Android app for playing your own videos in grouped order. It runs on Android 6.0 or newer.

## Install and use

Tap **Playlists** to create a named playlist, switch between playlists, or rename the current one. Each playlist has its own groups and video order. If you're updating from an earlier version, your existing sequence appears as **My playlist**.

1. Open `VideoSequence.apk` on your Android device and follow the installation prompt.
2. Tap **New group**, give it a name, then tap **Add videos** inside the group. Choose **Browse albums**, open an album in Android's media picker, and select one or more videos. On some devices you may need to tap the picker's **Albums** tab first. Use **Browse files** to navigate by folder. Devices without the system media picker open the file picker directly. The albums available depend on what Android's picker can see.
3. In **Review selected videos**, tap **Preview** beside a clip to play it. Go back to the review, uncheck any clips you want to leave out, then tap **Add**. You can also tap a thumbnail in the editor to preview an already-added video.
4. Tap **Reorder groups** at the top of the editor, use the arrows, and tap **Save order**. Use the arrows beside each video's thumbnail and name to change its position within a group. Use a video's three-dot menu to move it to a different group.
5. Tap **Play sequence**. Videos advance automatically, including across group boundaries. At the end you can replay from the start. Tap **Edit** in the player, change the order, and tap **Play sequence** to start with the revised order.

The app saves the arrangement on your device and keeps references to the selected files. If a file becomes unavailable, the player offers to skip it or return to the editor.

## Build from source on Windows

Install Android SDK Platform 36 or newer, Build-Tools 36.0.0, and a JDK such as the one bundled with Android Studio. From PowerShell in this folder, run `./build.ps1`. Override `-Sdk` and `-Jdk` if their locations differ from the defaults. The script creates a debug-signed APK in the parent directory and verifies its signature and alignment. Keep the generated debug keystore in the build directory to produce installable updates with the same signature.
