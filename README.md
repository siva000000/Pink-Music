PinkMusic
============
PinkMusic for Android is a simple and lightweight music player, with clean interface, equalizer, bass boost and customizable colors.

The main goal of this project is to provide the users with a fully functional music player based on lists and folders. The player itself does not consume too much memory, and respects WCAG 2.0 accessibility guidelines for colors and contrast, being friendly for color blind people as well as for people with mild visual impairments.

Screenshots
============
<img src="http://i.imgur.com/bPn8PBC.jpg" width=761 height=480>
<img src="http://i.imgur.com/gCRVQpZ.jpg" width=761 height=480>

Features
=========
- Browse through Icecast Directory
- Extract ID3v1 and ID3v2 information from MP3 and AAC files without android.media.MediaMetadataRetriever
- Reduce the latency between consecutive tracks using setNextMediaPlayer()
- Apply audio effects to music (equalizer and bass boost)
- Use android.media.audiofx.Visualizer and getFft()
- Call native code from within Java
- Acess the surface's bytes directly using the Native Window API
- Use NEON intrinsics to optimize native ARM code written in C++ (no need for Assembly)
- Use keyboard for navigation and control
- Handle different screen sizes and densities
- Handle physical media buttons as well as Bluetooth commands
- Send track information via A2DP Bluetooth (using com.android.music.playbackcomplete, com.android.music.playstatechanged and com.android.music.metachanged intents)
- Use custom fonts as vector icons instead of bitmap files
- Detect different display sizes (such as tablets and handhelds)
- Create custom menus
- Create custom views
- Create custom ListView with support for multiple selection and item reordering
- Create resizable widgets
- Control and monitor device's volume changes

The player is still in a "beta stage", so there may be unknown bugs yet ;)



* PinkMusic for Android is somewhat based on my old J2ME player PinkMusic

----

PinkMusic
============

PinkMusic can be integrated to Arduino, or other electronic board, via Bluetooth SPP - Serial Port Profile :D

----

Very special thanks to the people who helped translating PinkMusic:

LENIN RETINA

ALEX

Thanks a lot!! :D

The typeface used as the dyslexia-friendly typeface is OpenDyslexic Regular, by Abelardo Gonzalez, available at http://dyslexicfonts.com and licensed under a Creative Commons Attribution 3.0 Unported License.

The font used to draw the scalable icons, icons.ttf, was created using IcoMoon App, by Keyamoon, available at: http://icomoon.io/app

Some of the scalable icons were created by me and some came from the IcoMoon Free icon set, by Keyamoon, licensed under the Creative Commons License 3.0.



