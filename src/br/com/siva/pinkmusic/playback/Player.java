// Pink Music Android is distributed under the FreeBSD License
//
// Copyright (c) 2013-2016, Siva Prasad												
// All rights reserved.																
// ****************************************************************************************
//*******************************************************************************************
//**	Redistribution and use in source and binary forms, with or without					**
//**	modification, are permitted provided that the following conditions are met:			**
//**																						**
//**	 1. Redistributions of source code must retain the above copyright notice, this		**
//**     list of conditions and the following disclaimer.									**
//**	 2. Redistributions in binary form must reproduce the above copyright notice		**
//**     this list of conditions and the following disclaimer in the documentation			**
//**     and/or other materials provided with the distribution.							    **
//**																						**
//**	THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND		**
//**   	ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED		**
//**	WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE				**
//**    DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR		**
//**    ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES		**
//**    (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;		**
//**    LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND			**
//**    ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT			**
//**    (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS		**
//**     SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.						**
//**																						**
//**    The views and conclusions contained in the software and documentation are those		**
//**    of the authors and should not be interpreted as representing official policies,		**
//**    either expressed or implied, of the FreeBSD Project.								**
//********************************************************************************************
// ******************************************************************************************
package br.com.siva.pinkmusic.playback;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.MediaRouter;
import android.media.RemoteControlClient;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.telephony.TelephonyManager;
import android.view.KeyEvent;
import android.widget.RemoteViews;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;

import br.com.siva.pinkmusic.ExternalReceiver;
import br.com.siva.pinkmusic.R;
import br.com.siva.pinkmusic.WidgetMain;
import br.com.siva.pinkmusic.activity.ActivityHost;
import br.com.siva.pinkmusic.activity.ClientActivity;
import br.com.siva.pinkmusic.activity.MainHandler;
import br.com.siva.pinkmusic.list.FileSt;
import br.com.siva.pinkmusic.list.Song;
import br.com.siva.pinkmusic.list.SongList;
import br.com.siva.pinkmusic.ui.BgListView;
import br.com.siva.pinkmusic.ui.UI;
import br.com.siva.pinkmusic.util.ArraySorter;
import br.com.siva.pinkmusic.util.SerializableMap;
import br.com.siva.pinkmusic.util.TypedRawArrayList;
import br.com.siva.pinkmusic.visualizer.BluetoothVisualizerControllerJni;

//
//MediaPlayer CALLBACKS ARE CALLED ON THE THREAD WHERE THE PLAYER IS CREATED (WHICH
//IS THE MAIN THREAD, IN OUR CASE) THEREFORE, THERE IS NO NEED TO SYNCHRONIZE CALLS
//AS LONG AS previous(), play(), playPause() AND next() ARE ALWAYS CALLED FROM THE
//MAIN THREAD!!!
//http://stackoverflow.com/questions/3919089/do-callbacks-occur-on-the-main-ui-thread
//http://stackoverflow.com/questions/11066186/how-does-android-perform-callbacks-on-my-thread
//

//
//Android Audio: Play an MP3 file on an AudioTrack
//http://mindtherobot.com/blog/624/android-audio-play-an-mp3-file-on-an-audiotrack/
//
//Android Audio: Play a WAV file on an AudioTrack
//http://mindtherobot.com/blog/580/android-audio-play-a-wav-file-on-an-audiotrack/
//
//Equalizer
//http://developer.android.com/reference/android/media/audiofx/Equalizer.html
//
//Media Playback
//http://developer.android.com/guide/topics/media/mediaplayer.html
//
//MediaPlayer (including state diagram)
//http://developer.android.com/reference/android/media/MediaPlayer.html
//
//AudioTrack
//http://developer.android.com/reference/android/media/AudioTrack.html
//
//Allowing applications to play nice(r) with each other: Handling remote control buttons
//http://android-developers.blogspot.com.br/2010/06/allowing-applications-to-play-nicer.html
//

//Many properties used to be private, but I decided to make them public, even though some of them
//still have their setters, after I found out ProGuard does not inline static setters (or at least
//I have not been able to figure out a way to do so....)

//************************************************************************************
//In a few devices, error -38 will happen on the MediaPlayer currently being played
//when adding breakpoints to Player Core Thread
//************************************************************************************

@SuppressWarnings("deprecation")
@TargetApi(23)
public final class Player extends Service implements AudioManager.OnAudioFocusChangeListener, MediaPlayer.OnErrorListener, MediaPlayer.OnSeekCompleteListener, MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener, MediaPlayer.OnInfoListener, ArraySorter.Comparer<FileSt> {
	public interface PlayerObserver {
		void onPlayerChanged(Song currentSong, boolean songHasChanged, boolean preparingHasChanged, Throwable ex);
		void onPlayerMetadataChanged(Song currentSong);
		void onPlayerControlModeChanged(boolean controlMode);
		void onPlayerGlobalVolumeChanged(int volume);
		void onPlayerAudioSinkChanged();
		void onPlayerMediaButtonPrevious();
		void onPlayerMediaButtonNext();
	}

	public interface PlayerTurnOffTimerObserver {
		void onPlayerTurnOffTimerTick();
		void onPlayerIdleTurnOffTimerTick();
	}

	public interface PlayerDestroyedObserver {
		void onPlayerDestroyed();
	}

	private static final class TimeoutException extends Exception {
		private static final long serialVersionUID = 4571328670214281144L;
	}

	private static final class MediaServerDiedException extends Exception {
		private static final long serialVersionUID = -902099312236606175L;
	}

	private static final class FocusException extends Exception {
		private static final long serialVersionUID = 6158088015763157546L;
	}

	private static final class PermissionDeniedException extends SecurityException {
		private static final long serialVersionUID = 8743650824658438278L;
	}

	private static final class UnsupportedFormatException extends IOException {
		private static final long serialVersionUID = 7845932937323727492L;
	}

	private static final int MSG_UPDATE_STATE = 0x0100;
	private static final int MSG_PAUSE = 0x0101;
	private static final int MSG_PLAYPAUSE = 0x0102;
	private static final int MSG_SEEKTO = 0x0103;
	private static final int MSG_SYNC_VOLUME = 0x0104;
	private static final int MSG_PREPARE_NEXT_SONG = 0x0105;
	private static final int MSG_OVERRIDE_VOLUME_MULTIPLIER = 0x0106;
	private static final int MSG_BECOMING_NOISY = 0x0107;
	private static final int MSG_AUDIO_SINK_CHANGED = 0x0108;
	private static final int MSG_INITIALIZATION_STEP = 0x0109;
	private static final int MSG_NEXT_MAY_HAVE_CHANGED = 0x010A;
	private static final int MSG_LIST_CLEARED = 0x010B;
	private static final int MSG_FOCUS_GAIN_TIMER = 0x010C;
	private static final int MSG_FADE_IN_VOLUME_TIMER = 0x010D;
	private static final int MSG_HEADSET_HOOK_TIMER = 0x010E;
	private static final int MSG_TURN_OFF_TIMER = 0x010F;
	private static final int MSG_IDLE_TURN_OFF_TIMER = 0x0110;
	private static final int MSG_REGISTER_MEDIA_BUTTON_EVENT_RECEIVER = 0x0111;
	private static final int MSG_SONG_LIST_DESERIALIZED = 0x0112;
	private static final int MSG_PRE_PLAY = 0x0113;
	private static final int MSG_POST_PLAY = 0x0114;
	private static final int MSG_ENABLE_EFFECTS = 0x0115;
	private static final int MSG_COMMIT_EQUALIZER = 0x0116;
	private static final int MSG_COMMIT_BASS_BOOST = 0x0117;
	private static final int MSG_COMMIT_VIRTUALIZER = 0x0118;
	private static final int MSG_COMMIT_ALL_EFFECTS = 0x0119;
	private static final int MSG_TURN_OFF_NOW = 0x011A;
	private static final int MSG_HTTP_STREAM_RECEIVER_ERROR = 0x011B;
	private static final int MSG_HTTP_STREAM_RECEIVER_PREPARED = 0x011C;
	private static final int MSG_HTTP_STREAM_RECEIVER_METADATA_UPDATE = 0x011D;
	private static final int MSG_HTTP_STREAM_RECEIVER_URL_UPDATED = 0x011E;

	public static final int STATE_NEW = 0;
	public static final int STATE_INITIALIZING = 1;
	public static final int STATE_INITIALIZING_STEP2 = 2;
	public static final int STATE_ALIVE = 3;
	public static final int STATE_TERMINATING = 4;
	public static final int STATE_DEAD = 5;

	public static final int PLAYER_STATE_NEW = 0;
	public static final int PLAYER_STATE_PREPARING = 1;
	public static final int PLAYER_STATE_LOADED = 2;

	public static final int AUDIO_SINK_DEVICE = 1;
	public static final int AUDIO_SINK_WIRE = 2;
	public static final int AUDIO_SINK_BT = 4;

	public static final int VOLUME_MIN_DB = -4000;
	public static final int VOLUME_CONTROL_DB = 0;
	public static final int VOLUME_CONTROL_STREAM = 1;
	public static final int VOLUME_CONTROL_NONE = 2;
	public static final int VOLUME_CONTROL_PERCENT = 3;

	private static final int SILENCE_NORMAL = 0;
	private static final int SILENCE_FOCUS = 1;
	private static final int SILENCE_NONE = -1;

	public static final int ERROR_NOT_FOUND = 1001; //1001 = internal constant (not used by original MediaPlayer class) used to indicate that the file has not been found
	public static final int ERROR_UNSUPPORTED_FORMAT = -1010; //MediaPlayer.MEDIA_ERROR_UNSUPPORTED
	public static final int ERROR_IO = -1004; //MediaPlayer.MEDIA_ERROR_IO
	public static final int ERROR_TIMED_OUT = -110; //MediaPlayer.MEDIA_ERROR_TIMED_OUT

	public static final String ACTION_PREVIOUS = "br.com.siva.pinkmusic.PREVIOUS";
	public static final String ACTION_PLAY_PAUSE = "br.com.siva.pinkmusic.PLAY_PAUSE";
	public static final String ACTION_NEXT = "br.com.siva.pinkmusic.NEXT";
	public static final String ACTION_EXIT = "br.com.siva.pinkmusic.EXIT";
	public static String pathToPlayWhenStarting;
	private static String startCommand;

	public static int state;
	private static Thread thread;
	private static Looper looper;
	private static Player thePlayer;
	private static Handler handler, localHandler;
	private static AudioManager audioManager;
	private static NotificationManager notificationManager;
	private static TelephonyManager telephonyManager;
	public static final SongList songs = SongList.getInstance();

	//keep these instances here to prevent UI, MainHandler, Equalizer, BassBoost,
	//and Virtualizer classes from being garbage collected...
	public static MainHandler theMainHandler;
	public static final UI theUI = new UI();
	public static final Equalizer theEqualizer = new Equalizer();
	public static final BassBoost theBassBoost = new BassBoost();
	public static final Virtualizer theVirtualizer = new Virtualizer();

	public static boolean hasFocus;
	public static int volumeStreamMax = 15, volumeControlType;
	private static boolean volumeDimmed;
	private static int volumeDB, volumeDBFading, silenceMode;
	private static float volumeMultiplier;

	private static int audioSink, audioSinkBeforeFocusLoss;
	static int audioSinkUsedInEffects;
	public static int localAudioSinkUsedInEffects;

	private static int storedSongTime, howThePlayerStarted, playerState, nextPlayerState;
	private static boolean resumePlaybackAfterFocusGain, postPlayPending, playing, playerBuffering, playAfterSeeking, prepareNextAfterSeeking, nextAlreadySetForPlaying, reviveAlreadyTried, httpStreamReceiverActsLikePlayer;
	private static Song song, nextSong, songScheduledForPreparation, nextSongScheduledForPreparation, songWhenFirstErrorHappened;
	private static MediaPlayer player, nextPlayer;

	//keep these three fields here, instead of in ActivityMain/ActivityBrowser,
	//so they will survive their respective activity's destruction
	//(and even the class garbage collection)
	public static int lastCurrent = -1, listFirst = -1, listTop = 0, positionToSelect = -1;
	public static boolean isMainActiveOnTop, alreadySelected;

	//These are only set in the main thread
	private static int localVolumeStream, localPlayerState;
	public static int localVolumeDB;
	public static boolean localPlaying;
	public static Song localSong;
	public static MediaPlayer localPlayer;

	private static class CoreHandler extends Handler {
		@Override
		public void dispatchMessage(@NonNull Message msg) {
			switch (msg.what) {
			case MSG_POST_PLAY:
				_postPlay(msg.arg1, (Song[])msg.obj);
				break;
			case MSG_BECOMING_NOISY:
				if (playing)
					_playPause();
				//this cleanup must be done, as sometimes, when changing between two output types,
				//the effects are lost...
				_fullCleanup();
				_checkAudioSink(false, false, true);
				break;
			case MSG_FADE_IN_VOLUME_TIMER:
				_processFadeInVolumeTimer(msg.arg1);
				break;
			case MSG_AUDIO_SINK_CHANGED:
				_checkAudioSink(msg.arg1 != 0, true, true);
				break;
			case MSG_PAUSE:
				if (playing)
					_playPause();
				break;
			case MSG_PLAYPAUSE:
				_playPause();
				break;
			case MSG_SEEKTO:
				_seekTo(msg.arg1);
				break;
			case MSG_SYNC_VOLUME:
				_syncVolume();
				break;
			case MSG_PREPARE_NEXT_SONG:
				_prepareNextPlayer((Song)msg.obj);
				break;
			case MSG_FOCUS_GAIN_TIMER:
				_processFocusGainTimer();
				break;
			case MSG_OVERRIDE_VOLUME_MULTIPLIER:
				_overrideVolumeMultiplier(msg.arg1 != 0);
				break;
			case MSG_LIST_CLEARED:
				_fullCleanup();
				song = null;
				storedSongTime = -1;
				_updateState(false, null);
				break;
			case MSG_NEXT_MAY_HAVE_CHANGED:
				_nextMayHaveChanged((Song)msg.obj);
				break;
			case MSG_ENABLE_EFFECTS:
				_enableEffects(msg.arg1, msg.arg2, (Runnable)msg.obj);
				break;
			case MSG_COMMIT_EQUALIZER:
				Equalizer._commit(msg.arg1, msg.arg2);
				break;
			case MSG_COMMIT_BASS_BOOST:
				BassBoost._commit(msg.arg2);
				break;
			case MSG_COMMIT_VIRTUALIZER:
				Virtualizer._commit(msg.arg2);
				break;
			case MSG_COMMIT_ALL_EFFECTS:
				Equalizer._commit(-1, msg.arg2);
				BassBoost._commit(msg.arg2);
				Virtualizer._commit(msg.arg2);
				break;
			case MSG_SONG_LIST_DESERIALIZED:
				_songListDeserialized(msg.obj);
				break;
			case MSG_HTTP_STREAM_RECEIVER_ERROR:
				_httpStreamReceiverError(msg.arg1, msg.arg2);
				break;
			case MSG_HTTP_STREAM_RECEIVER_PREPARED:
				_httpStreamReceiverPrepared(msg.arg1);
				break;
			case MSG_HTTP_STREAM_RECEIVER_METADATA_UPDATE:
				if (localHandler != null)
					localHandler.sendMessageAtTime(Message.obtain(localHandler, MSG_HTTP_STREAM_RECEIVER_METADATA_UPDATE, msg.arg1, 0, msg.obj), SystemClock.uptimeMillis());
				break;
			case MSG_HTTP_STREAM_RECEIVER_URL_UPDATED:
				if (msg.obj != null)
					_httpStreamReceiverUrlUpdated(msg.arg1, msg.obj.toString());
				break;
			}
		}
	}

	private static class CoreLocalHandler extends Handler {
		@Override
		public void dispatchMessage(@NonNull Message msg) {
			if (thePlayer == null || state > STATE_ALIVE)
				return;
			switch (msg.what) {
			case MSG_PRE_PLAY:
				prePlay(msg.arg1);
				break;
			case MSG_AUDIO_SINK_CHANGED:
				localAudioSinkUsedInEffects = audioSink;
				if (observer != null)
					observer.onPlayerAudioSinkChanged();
				break;
			case MSG_UPDATE_STATE:
				updateState(msg.arg1, (Object[])msg.obj);
				break;
			case MSG_REGISTER_MEDIA_BUTTON_EVENT_RECEIVER:
				registerMediaButtonEventReceiver();
				break;
			case MSG_INITIALIZATION_STEP:
				switch (state) {
				case STATE_INITIALIZING:
					state = STATE_INITIALIZING_STEP2;
					break;
				case STATE_INITIALIZING_STEP2:
					state = STATE_ALIVE;
					handler.sendMessageAtTime(Message.obtain(handler, MSG_OVERRIDE_VOLUME_MULTIPLIER, (volumeControlType != VOLUME_CONTROL_DB && volumeControlType != VOLUME_CONTROL_PERCENT) ? 1 : 0, 0), SystemClock.uptimeMillis());
					setTurnOffTimer(turnOffTimerSelectedMinutes);
					setIdleTurnOffTimer(idleTurnOffTimerSelectedMinutes);
					executeStartCommand();
					break;
				}
				break;
			case MSG_HEADSET_HOOK_TIMER:
				processHeadsetHookTimer();
				break;
			case MSG_TURN_OFF_TIMER:
				processTurnOffTimer();
				break;
			case MSG_IDLE_TURN_OFF_TIMER:
				idleTurnOffTimerSent = false;
				processIdleTurnOffTimer();
				break;
			case MSG_TURN_OFF_NOW:
				stopService(false);
				break;
			case MSG_HTTP_STREAM_RECEIVER_METADATA_UPDATE:
				if (msg.obj != null)
					httpStreamReceiverMetadataUpdate(msg.arg1, msg.obj.toString());
				break;
			}
		}
	}

	@Override
	public void onCreate() {
		thePlayer = this;
		startService(this);
		startForeground(1, getNotification());
		super.onCreate();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		System.exit(0);
	}

	private static void executeStartCommand() {
		if (state == STATE_ALIVE) {
			if (pathToPlayWhenStarting != null) {
				if (songs.addPathAndForceScrollIntoView(pathToPlayWhenStarting, true))
					startCommand = null;
				pathToPlayWhenStarting = null;
			}
			if (startCommand != null) {
				switch (startCommand) {
				case ACTION_PREVIOUS:
					previous();
					break;
				case ACTION_PLAY_PAUSE:
					playPause();
					break;
				case ACTION_NEXT:
					next();
					break;
				case ACTION_EXIT:
					stopService(false);
					break;
				}
				startCommand = null;
			}
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null) {
			final String command = intent.getAction();
			if (command != null) {
				startCommand = command;
				executeStartCommand();
			}
		}
		return START_STICKY;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	public static Service getService() {
		return thePlayer;
	}

	public static boolean startService(Context context) {
		final boolean stateNew = (state == STATE_NEW);
		if (stateNew) {
			MainHandler.initialize();
			alreadySelected = true; //fix the initial selection when the app is started from the widget
			state = STATE_INITIALIZING;
			context.startService(new Intent(context, Player.class));
			theMainHandler = MainHandler.initialize();
			localHandler = new CoreLocalHandler();
			notificationManager = (NotificationManager)context.getSystemService(NOTIFICATION_SERVICE);
			audioManager = (AudioManager)context.getSystemService(AUDIO_SERVICE);
			telephonyManager = (TelephonyManager)context.getSystemService(TELEPHONY_SERVICE);
			destroyedObservers = new TypedRawArrayList<>(PlayerDestroyedObserver.class, 4);
			stickyBroadcast = new Intent();
			loadConfig(context);
		}
		//when the player starts from the widget, startService is not called twice, it is
		//called only once, from within onCreate, when thePlayer is already != null
		if (thePlayer != null && thread == null) {
			createIntents(context);

			//These broadcast actions are registered here, instead of in the manifest file,
			//because a few tests showed that some devices will produce the notifications
			//faster this way, specially AUDIO_BECOMING_NOISY. Moreover, by registering here,
			//only one BroadcastReceiver is instantiated and used throughout the entire
			//application's lifecycle, whereas when the manifest is used, a different instance
			//is created to handle every notification! :)
			//The only exception is the MEDIA_BUTTON broadcast action, which MUST BE declared
			//in the application manifest according to the documentation of the method
			//registerMediaButtonEventReceiver!!! :(
			final IntentFilter filter = new IntentFilter("android.media.AUDIO_BECOMING_NOISY");
			//filter.addAction("android.intent.action.MEDIA_BUTTON");
			filter.addAction("android.intent.action.CALL_BUTTON");
			filter.addAction("android.intent.action.HEADSET_PLUG");
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
				filter.addAction("android.media.ACTION_SCO_AUDIO_STATE_UPDATED");
			else
				filter.addAction("android.media.SCO_AUDIO_STATE_CHANGED");
			filter.addAction("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED");
			filter.addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED");
			//HEADSET_STATE_CHANGED is based on: https://groups.google.com/forum/#!topic/android-developers/pN2k5_kFo4M
			filter.addAction("android.bluetooth.intent.action.HEADSET_STATE_CHANGED");
			externalReceiver = new ExternalReceiver();
			thePlayer.getApplicationContext().registerReceiver(externalReceiver, filter);

			registerMediaButtonEventReceiver();
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
				registerMediaRouter(thePlayer);

			thread = new Thread("Player Core Thread") {
				@Override
				public void run() {
					Looper.prepare();
					looper = Looper.myLooper();
					handler = new CoreHandler();
					_initializePlayers();
					Equalizer._initialize(player.getAudioSessionId());
					Equalizer._release();
					BassBoost._initialize(player.getAudioSessionId());
					BassBoost._release();
					Virtualizer._initialize(player.getAudioSessionId());
					Virtualizer._release();
					_checkAudioSink(false, false, false);
					audioSinkUsedInEffects = audioSink;
					if (Equalizer.isEnabled(audioSinkUsedInEffects)) {
						Equalizer._initialize(player.getAudioSessionId());
						Equalizer._setEnabled(true, audioSinkUsedInEffects);
					}
					if (BassBoost.isEnabled(audioSinkUsedInEffects)) {
						BassBoost._initialize(player.getAudioSessionId());
						BassBoost._setEnabled(true, audioSinkUsedInEffects);
					}
					if (Virtualizer.isEnabled(audioSinkUsedInEffects)) {
						Virtualizer._initialize(player.getAudioSessionId());
						Virtualizer._setEnabled(true, audioSinkUsedInEffects);
					}
					localHandler.sendEmptyMessageAtTime(MSG_INITIALIZATION_STEP, SystemClock.uptimeMillis());
					Looper.loop();
					if (song != null) {
						_storeSongTime();
						song = null;
					} else {
						storedSongTime = -1;
					}
					_fullCleanup();
					hasFocus = false;
					if (audioManager != null && thePlayer != null)
						audioManager.abandonAudioFocus(thePlayer);
				}
			};
			thread.start();

			while (handler == null)
				Thread.yield();

			songs.startDeserializingOrImportingFrom(thePlayer, null, true, false, false);
		}
		//fix the initial selection when the app is started from the widget
		alreadySelected = false;
		positionToSelect = songs.getCurrentPosition();
		return stateNew;
	}

	public static void stopService(boolean restart) {
		if (state != STATE_ALIVE)
			return;
		state = STATE_TERMINATING;

		_releaseInternetObjects();

		looper.quit();

		try {
			thread.join();
		} catch (Throwable ex) {
			ex.printStackTrace();
		}

		if (bluetoothVisualizerController != null)
			stopBluetoothVisualizer();

		unregisterMediaButtonEventReceiver();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && thePlayer != null)
			unregisterMediaRouter(thePlayer);

		if (destroyedObservers != null) {
			for (int i = destroyedObservers.size() - 1; i >= 0; i--)
				destroyedObservers.get(i).onPlayerDestroyed();
			destroyedObservers.clear();
			destroyedObservers = null;
		}

		if (thePlayer != null) {
			if (externalReceiver != null)
				thePlayer.getApplicationContext().unregisterReceiver(externalReceiver);
			saveConfig(thePlayer, true);
		}

		updateState(~0x27, new Object[] { null, null, null });

		if (restart && intentActivityHost != null) {
			//http://stackoverflow.com/questions/6609414/howto-programatically-restart-android-app
			((AlarmManager)thePlayer.getSystemService(ALARM_SERVICE)).set(AlarmManager.RTC, System.currentTimeMillis() + 300, intentActivityHost);
		}

		thread = null;
		observer = null;
		turnOffTimerObserver = null;
		theMainHandler = null;
		looper = null;
		handler = null;
		localHandler = null;
		notification = null;
		notificationRemoteViews = null;
		notificationManager = null;
		audioManager = null;
		telephonyManager = null;
		externalReceiver = null;
		stickyBroadcast = null;
		intentActivityHost = null;
		intentPrevious = null;
		intentPlayPause = null;
		intentNext = null;
		intentExit = null;
		favoriteFolders.clear();
		localSong = null;
		localPlayer = null;
		stateLastSong = null;
		state = STATE_DEAD;
		if (thePlayer != null) {
			thePlayer.stopForeground(true);
			thePlayer.stopSelf();
			thePlayer = null;
		} else {
			System.exit(0);
		}
	}

	private static boolean prePlay(int how) {
		if (state != STATE_ALIVE || postPlayPending)
			return false;
		localSong = songs.getSongAndSetCurrent(how);
		final Song[] songArray = new Song[] { localSong, songs.possibleNextSong };
		songs.possibleNextSong = null;
		postPlayPending = true;
		handler.sendMessageAtTime(Message.obtain(handler, MSG_POST_PLAY, how, 0, songArray), SystemClock.uptimeMillis());
		return true;
	}

	public static void previous() {
		prePlay(SongList.HOW_PREVIOUS);
	}

	public static boolean play(int index) {
		return prePlay(index);
	}

	public static void pause() {
		if (state != STATE_ALIVE)
			return;
		handler.sendMessageAtTime(Message.obtain(handler, MSG_PAUSE), SystemClock.uptimeMillis());
	}

	public static void playPause() {
		if (state != STATE_ALIVE)
			return;
		handler.sendMessageAtTime(Message.obtain(handler, MSG_PLAYPAUSE), SystemClock.uptimeMillis());
	}

	public static void next() {
		prePlay(SongList.HOW_NEXT_MANUAL);
	}

	public static void seekTo(int timeMS) {
		if (state != STATE_ALIVE)
			return;
		handler.sendMessageAtTime(Message.obtain(handler, MSG_SEEKTO, timeMS, 0), SystemClock.uptimeMillis());
	}

	public static void showStreamVolumeUI() {
		try {
			if (audioManager != null)
				audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI);
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
	}

	public static int setVolume(int volume) {
		if (state != STATE_ALIVE)
			return Integer.MIN_VALUE;
		switch (volumeControlType) {
		case VOLUME_CONTROL_STREAM:
			if (volume < 0)
				volume = 0;
			else if (volume > volumeStreamMax)
				volume = volumeStreamMax;
			localVolumeStream = volume;
			//apparently a few devices don't like to have the streamVolume changed from another thread....
			//maybe there is another reason for why it fails... I just haven't found yet :(
			try {
				if (audioManager != null)
					audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0);
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
			return volume;
		case VOLUME_CONTROL_DB:
		case VOLUME_CONTROL_PERCENT:
			if (volume <= VOLUME_MIN_DB)
				volume = VOLUME_MIN_DB;
			else if (volume > 0)
				volume = 0;
			if (localVolumeDB == volume)
				return Integer.MIN_VALUE;
			localVolumeDB = volume;
			break;
		default:
			showStreamVolumeUI();
			return Integer.MIN_VALUE;
		}
		handler.sendEmptyMessageAtTime(MSG_SYNC_VOLUME, SystemClock.uptimeMillis());
		return volume;
	}

	public static int decreaseVolume() {
		switch (volumeControlType) {
		case VOLUME_CONTROL_STREAM:
			return setVolume(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) - 1);
		case VOLUME_CONTROL_DB:
		case VOLUME_CONTROL_PERCENT:
			//http://stackoverflow.com/a/4413073/3569421
			//(a/b)*b+(a%b) is equal to a
			//-350 % 200 = -150
			final int leftover = (Player.volumeDB % 200);
			return setVolume(Player.volumeDB + ((leftover == 0) ? -200 : (-200 - leftover)));
		}
		showStreamVolumeUI();
		return Integer.MIN_VALUE;
	}

	public static int increaseVolume() {
		switch (volumeControlType) {
		case VOLUME_CONTROL_STREAM:
			return setVolume(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) + 1);
		case VOLUME_CONTROL_DB:
		case VOLUME_CONTROL_PERCENT:
			//http://stackoverflow.com/a/4413073/3569421
			//(a/b)*b+(a%b) is equal to a
			//-350 % 200 = -150
			final int leftover = (Player.volumeDB % 200);
			return setVolume(Player.volumeDB + ((leftover == 0) ? 200 : -leftover));
		}
		showStreamVolumeUI();
		return Integer.MIN_VALUE;
	}

	public static int getSystemStreamVolume() {
		try {
			if (audioManager != null)
				return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
		return 0;
	}

	public static void setVolumeInPercentage(int percentage) {
		switch (volumeControlType) {
		case VOLUME_CONTROL_STREAM:
			setVolume((percentage * volumeStreamMax) / 100);
			break;
		case VOLUME_CONTROL_DB:
		case VOLUME_CONTROL_PERCENT:
			setVolume((percentage >= 100) ? 0 : ((percentage <= 0) ? VOLUME_MIN_DB : (((100 - percentage) * VOLUME_MIN_DB) / 100)));
			break;
		}
	}

	public static int getVolumeInPercentage() {
		switch (volumeControlType) {
		case VOLUME_CONTROL_STREAM:
			final int vol = getSystemStreamVolume();
			return ((vol <= 0) ? 0 : ((vol >= volumeStreamMax) ? 100 : ((vol * 100) / volumeStreamMax)));
		case VOLUME_CONTROL_DB:
		case VOLUME_CONTROL_PERCENT:
			return ((localVolumeDB >= 0) ? 100 : ((localVolumeDB <= VOLUME_MIN_DB) ? 0 : (((VOLUME_MIN_DB - localVolumeDB) * 100) / VOLUME_MIN_DB)));
		}
		return 0;
	}

	public static void setVolumeControlType(int volumeControlType) {
		switch (volumeControlType) {
		case VOLUME_CONTROL_DB:
		case VOLUME_CONTROL_PERCENT:
		case VOLUME_CONTROL_NONE:
			Player.volumeControlType = volumeControlType;
			if (handler != null)
				handler.sendMessageAtTime(Message.obtain(handler, MSG_OVERRIDE_VOLUME_MULTIPLIER, (volumeControlType == VOLUME_CONTROL_NONE) ? 1 : 0, 0), SystemClock.uptimeMillis());
			break;
		default:
			try {
				if (audioManager != null)
					volumeStreamMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
				if (volumeStreamMax < 1)
					volumeStreamMax = 1;
				if (handler != null)
					handler.sendMessageAtTime(Message.obtain(handler, MSG_OVERRIDE_VOLUME_MULTIPLIER, 1, 0), SystemClock.uptimeMillis());
				Player.volumeControlType = VOLUME_CONTROL_STREAM;
			} catch (Throwable ex) {
				Player.volumeControlType = VOLUME_CONTROL_NONE;
				ex.printStackTrace();
			}
			break;
		}
	}

	public static void enableEffects(boolean equalizer, boolean bassBoost, boolean virtualizer, int audioSink, Runnable callback) {
		if (state != STATE_ALIVE)
			return;
		handler.sendMessageAtTime(Message.obtain(handler, MSG_ENABLE_EFFECTS, (equalizer ? 1 : 0) | (bassBoost ? 2 : 0) | (virtualizer ? 4 : 0), audioSink, callback), SystemClock.uptimeMillis());
	}

	public static void commitEqualizer(int band, int audioSink) {
		if (state != STATE_ALIVE)
			return;
		handler.sendMessageAtTime(Message.obtain(handler, MSG_COMMIT_EQUALIZER, band, audioSink), SystemClock.uptimeMillis());
	}

	public static void commitBassBoost(int audioSink) {
		if (state != STATE_ALIVE)
			return;
		handler.sendMessageAtTime(Message.obtain(handler, MSG_COMMIT_BASS_BOOST, 0, audioSink), SystemClock.uptimeMillis());
	}

	public static void commitVirtualizer(int audioSink) {
		if (state != STATE_ALIVE)
			return;
		handler.sendMessageAtTime(Message.obtain(handler, MSG_COMMIT_VIRTUALIZER, 0, audioSink), SystemClock.uptimeMillis());
	}

	public static void commitAllEffects(int audioSink) {
		if (state != STATE_ALIVE)
			return;
		handler.sendMessageAtTime(Message.obtain(handler, MSG_COMMIT_ALL_EFFECTS, 0, audioSink), SystemClock.uptimeMillis());
	}

	public static boolean isPreparing() {
		return (localPlayerState == PLAYER_STATE_PREPARING || playerBuffering);
	}

	public static String getCurrentTitle(Context context, boolean preparing) {
		return ((context == null) ? "" :
			((localSong == null) ? context.getText(R.string.nothing_playing).toString() :
				(!preparing ? localSong.title :
					(context.getText(R.string.loading) + " " + localSong.title))));
	}

	public static int getPosition() {
		try {
			return ((httpStreamReceiver != null) ? -1 :
				((localPlayer != null && playerState == PLAYER_STATE_LOADED) ? localPlayer.getCurrentPosition() :
					((localSong == null) ? -1 : storedSongTime)));
		} catch (Throwable ex) {
			//localPlayer could throw a InvalidStateException (*very, very* rarely)
			return -1;
		}
	}

	public static int getHttpPosition() {
		//by doing like this, we do not need to synchronize the access to httpStreamReceiver
		final HttpStreamReceiver receiver = httpStreamReceiver;
		return ((receiver != null) ? receiver.bytesReceivedSoFar : -1);
	}

	public static int getAudioSessionId() {
		return ((localPlayer == null) ? -1 : localPlayer.getAudioSessionId());
	}

	private static MediaPlayer _createPlayer() {
		MediaPlayer mp = new MediaPlayer();
		mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
		mp.setOnErrorListener(thePlayer);
		mp.setOnPreparedListener(thePlayer);
		mp.setOnSeekCompleteListener(thePlayer);
		mp.setOnCompletionListener(thePlayer);
		mp.setOnInfoListener(thePlayer);
		mp.setWakeMode(thePlayer, PowerManager.PARTIAL_WAKE_LOCK);
		return mp;
	}

	private static void _startPlayer() {
		playAfterSeeking = false;
		playing = true;
		//when dimmed, decreased the volume by 20dB
		float multiplier = (volumeDimmed ? (volumeMultiplier * 0.1f) : volumeMultiplier);
		if (silenceMode != SILENCE_NONE) {
			final int increment = ((silenceMode == SILENCE_FOCUS) ? fadeInIncrementOnFocus : ((howThePlayerStarted == SongList.HOW_CURRENT || (song != null && song.isHttp)) ? fadeInIncrementOnPause : fadeInIncrementOnOther));
			if (increment > 30) {
				volumeDBFading = VOLUME_MIN_DB;
				multiplier = 0;
				handler.sendMessageAtTime(Message.obtain(handler, MSG_FADE_IN_VOLUME_TIMER, increment, 0), SystemClock.uptimeMillis() + 50);
			}
			silenceMode = SILENCE_NONE;
		}
		if (httpStreamReceiverActsLikePlayer) {
			httpStreamReceiver.setVolume(multiplier, multiplier);
			//httpStreamReceiver starts playing automatically (we just need to fix the volume, as
			//it always starts playing muted)
		} else if (player != null) {
			player.setVolume(multiplier, multiplier);
			player.start();
		}
		reviveAlreadyTried = false;
	}

	private static void _releasePlayer(MediaPlayer mediaPlayer) {
		mediaPlayer.setOnErrorListener(null);
		mediaPlayer.setOnPreparedListener(null);
		mediaPlayer.setOnSeekCompleteListener(null);
		mediaPlayer.setOnCompletionListener(null);
		mediaPlayer.setOnInfoListener(null);
		mediaPlayer.reset();
		mediaPlayer.release();
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	private static void _clearNextPlayer() {
		//there is no need to call setNextMediaPlayer(null) after calling reset()
		try {
			player.setNextMediaPlayer(null);
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	private static void _setNextPlayer() {
		try {
			if (player != null) {
				player.setNextMediaPlayer(nextPlayer);
				nextAlreadySetForPlaying = true;
			}
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
	}

	private static void _partialCleanup() {
		playerState = PLAYER_STATE_NEW;
		playerBuffering = false;
		if (player != null) {
			_releasePlayer(player);
			player = null;
		}
		nextAlreadySetForPlaying = false;
		nextPlayerState = PLAYER_STATE_NEW;
		if (nextPlayer != null) {
			_releasePlayer(nextPlayer);
			nextPlayer = null;
		}
		playing = false;
		playAfterSeeking = false;
		prepareNextAfterSeeking = false;
		resumePlaybackAfterFocusGain = false;
		songScheduledForPreparation = null;
		nextSongScheduledForPreparation = null;
		_releaseInternetObjects();
		if (handler != null) {
			handler.removeMessages(MSG_FOCUS_GAIN_TIMER);
			handler.removeMessages(MSG_FADE_IN_VOLUME_TIMER);
		}
		resetHeadsetHook();
	}

	private static void _fullCleanup() {
		_partialCleanup();
		silenceMode = SILENCE_NORMAL;
		nextSong = null;
		postPlayPending = false;
		Equalizer._release();
		BassBoost._release();
		Virtualizer._release();
	}

	private static void _initializePlayers() {
		if (player == null) {
			player = _createPlayer();
			if (nextPlayer != null) {
				player.setAudioSessionId(nextPlayer.getAudioSessionId());
			} else {
				nextPlayer = _createPlayer();
				nextPlayer.setAudioSessionId(player.getAudioSessionId());
			}
		} else if (nextPlayer == null) {
			nextPlayer = _createPlayer();
			nextPlayer.setAudioSessionId(player.getAudioSessionId());
		}
	}

	private static boolean _requestFocus() {
		if (thePlayer == null || audioManager == null || audioManager.requestAudioFocus(thePlayer, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
			hasFocus = false;
			return false;
		}
		localHandler.sendEmptyMessageAtTime(MSG_REGISTER_MEDIA_BUTTON_EVENT_RECEIVER, SystemClock.uptimeMillis());
		hasFocus = true;
		volumeDimmed = false;
		return true;
	}

	private static void _storeSongTime() {
		try {
			if (song == null || song.isHttp)
				storedSongTime = -1;
			else if (player != null && playerState == PLAYER_STATE_LOADED)
				storedSongTime = player.getCurrentPosition();
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
	}

	private static void _prepareNextPlayer(Song song) {
		try {
			if (song == nextSongScheduledForPreparation && nextPlayer != null) {
				//Even though it happens very rarely, a few devices will freeze and produce an ANR
				//when calling setDataSource from the main thread :(
				nextPlayer.setDataSource(nextSongScheduledForPreparation.path);
				nextPlayer.prepareAsync();
				nextPlayerState = PLAYER_STATE_PREPARING;
			}
		} catch (Throwable ex) {
			nextPlayerState = PLAYER_STATE_NEW;
			ex.printStackTrace();
		}
	}

	private static void _scheduleNextPlayerForPreparation() {
		nextAlreadySetForPlaying = false;
		prepareNextAfterSeeking = false;
		nextPlayerState = PLAYER_STATE_NEW;
		if (handler != null && song != null && nextSong != null && !nextSong.isHttp && nextPreparationEnabled && song.lengthMS > 10000 && nextSong.lengthMS > 10000) {
			handler.removeMessages(MSG_PREPARE_NEXT_SONG);
			nextSongScheduledForPreparation = nextSong;
			handler.sendMessageAtTime(Message.obtain(handler, MSG_PREPARE_NEXT_SONG, nextSong), SystemClock.uptimeMillis() + 5000);
		}
	}

	private static void _handleFailure(Throwable ex, boolean checkForPermission) {
		if (checkForPermission) {
			checkForPermission = false;
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				if (thePlayer.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
					checkForPermission = true;
			}
		}
		if (songWhenFirstErrorHappened == song || howThePlayerStarted == SongList.HOW_CURRENT || howThePlayerStarted >= 0 || checkForPermission) {
			songWhenFirstErrorHappened = null;
			_updateState(false, checkForPermission ? new PermissionDeniedException() : ex);
		} else {
			//this used to be called only when howThePlayerStarted == SongList.HOW_NEXT_AUTO
			if (songWhenFirstErrorHappened == null)
				songWhenFirstErrorHappened = song;
			_updateState(false, null);
			localHandler.sendMessageAtTime(Message.obtain(localHandler, MSG_PRE_PLAY, howThePlayerStarted, 0), SystemClock.uptimeMillis());
		}
	}

	private static void _postPlay(int how, Song[] songArray) {
		if (state != STATE_ALIVE)
			return;
		song = songArray[0];
		if (!hasFocus && !_requestFocus()) {
			if (how != SongList.HOW_CURRENT)
				storedSongTime = -1;
			_fullCleanup();
			_updateState(false, new FocusException());
			return;
		}
		//we must set this to false here, as the user could have manually
		//started playback before the focus timer had the chance to trigger
		resumePlaybackAfterFocusGain = false;
		if (song == null) {
			storedSongTime = -1;
			songWhenFirstErrorHappened = null;
			_fullCleanup();
			_updateState(false, null);
			return;
		}

		try {
			howThePlayerStarted = how;
			playerBuffering = false;
			if (playerState == PLAYER_STATE_PREPARING)
				//probably the user is changing songs too fast!
				_partialCleanup();
			if (player == null || nextPlayer == null || audioSinkUsedInEffects != audioSink) {
				_initializePlayers();
				_reinitializeEffects();
			}
			player.reset();
			postPlayPending = false;
			if (nextSong == song && how != SongList.HOW_CURRENT) {
				storedSongTime = -1;
				final MediaPlayer p = player;
				switch (nextPlayerState) {
				case PLAYER_STATE_LOADED:
					playerState = PLAYER_STATE_LOADED;
					player = nextPlayer;
					nextPlayer = p;
					nextSong = songArray[1];
					if (!nextAlreadySetForPlaying || how != SongList.HOW_NEXT_AUTO) {
						_startPlayer();
					} else {
						playing = true;
						//when dimmed, decreased the volume by 20dB
						final float multiplier = (volumeDimmed ? (volumeMultiplier * 0.1f) : volumeMultiplier);
						//do not call httpStreamReceiver.setVolume() here
						player.setVolume(multiplier, multiplier);
					}
					songWhenFirstErrorHappened = null;
					_releaseInternetObjects();
					_scheduleNextPlayerForPreparation();
					_updateState(false, null);
					return;
				case PLAYER_STATE_PREPARING:
					//just wait for the next song to finish preparing
					playing = false;
					playerState = PLAYER_STATE_PREPARING;
					nextPlayerState = PLAYER_STATE_NEW;
					player = nextPlayer;
					nextPlayer = p;
					nextSong = songArray[1];
					_releaseInternetObjects();
					_updateState(false, null);
					return;
				}
			}
			playing = false;
			playerState = PLAYER_STATE_PREPARING;

			_releaseInternetObjects();

			if (how != SongList.HOW_CURRENT)
				storedSongTime = -1;

			if (song.path == null || song.path.length() == 0)
				throw new IOException();
			songScheduledForPreparation = song;

			if (!song.isHttp) {
				//Even though it happens very rarely, a few devices will freeze and produce an ANR
				//when calling setDataSource from the main thread :(
				player.setDataSource(song.path);
				player.prepareAsync();
			} else {
				_createInternetObjects();
			}

			nextPlayerState = PLAYER_STATE_NEW;
			nextPlayer.reset();
			nextSong = songArray[1];
			nextSongScheduledForPreparation = null;
			if (nextPreparationEnabled)
				handler.removeMessages(MSG_PREPARE_NEXT_SONG);

			_updateState(false, null);
		} catch (Throwable ex) {
			_fullCleanup();
			//clear the flag here to allow _handleFailure to move to the next song
			_handleFailure(ex, true);
		}
	}

	private static void _playPause() {
		if (state != STATE_ALIVE || playerState == PLAYER_STATE_PREPARING)
			return;
		try {
			//we must set this to false here, as the user could have manually
			//started playback before the focus timer had the chance to trigger
			resumePlaybackAfterFocusGain = false;
			if (playing) {
				playing = false;
				if (httpStreamReceiverActsLikePlayer)
					httpStreamReceiver.pause();
				else
					player.pause();
				silenceMode = SILENCE_NORMAL;
				_storeSongTime();
				_updateState(false, null);
			} else {
				if (song == null || playerState == PLAYER_STATE_NEW || !hasFocus) {
					localHandler.sendMessageAtTime(Message.obtain(localHandler, MSG_PRE_PLAY, SongList.HOW_CURRENT, 0), SystemClock.uptimeMillis());
				} else {
					howThePlayerStarted = SongList.HOW_CURRENT;
					if (httpStreamReceiverActsLikePlayer) {
						//every time httpStreamReceiver starts, it is a preparation!
						playerBuffering = true;
						songScheduledForPreparation = song;
						httpStreamReceiver.start();
					} else {
						_startPlayer();
					}
					_updateState(false, null);
				}
			}
		} catch (Throwable ex) {
			_fullCleanup();
			_updateState(false, ex);
		}
	}

	private static void _seekTo(int timeMS) {
		if (state != STATE_ALIVE || playerState == PLAYER_STATE_PREPARING)
			return;
		storedSongTime = timeMS;
		if (playerState == PLAYER_STATE_LOADED) {
			playAfterSeeking = playing;
			playerState = PLAYER_STATE_PREPARING;
			try {
				if (playing) {
					playing = false;
					player.pause();
				}
				player.seekTo(timeMS);
				_updateState(false, null);
			} catch (Throwable ex) {
				_fullCleanup();
				_updateState(false, ex);
			}
		}
	}

	private static void _processFadeInVolumeTimer(int increment) {
		volumeDBFading += increment;
		//magnitude = 10 ^ (dB/20)
		//x^p = a ^ (p * log a (x))
		//10^p = e ^ (p * log e (10))
		float multiplier = (float)Math.exp((double)volumeDBFading * 2.3025850929940456840179914546844 / 2000.0);
		boolean send = true;
		if (multiplier >= volumeMultiplier) {
			multiplier = volumeMultiplier;
			send = false;
		}
		//when dimmed, decreased the volume by 20dB
		if (volumeDimmed)
			multiplier *= 0.1f;
		if (handler != null && hasFocus && player != null && playerState == PLAYER_STATE_LOADED) {
			if (httpStreamReceiverActsLikePlayer) {
				httpStreamReceiver.setVolume(multiplier, multiplier);
			} else {
				try {
					player.setVolume(multiplier, multiplier);
				} catch (Throwable ex) {
					ex.printStackTrace();
				}
			}
			if (send)
				handler.sendMessageAtTime(Message.obtain(handler, MSG_FADE_IN_VOLUME_TIMER, increment, 0), SystemClock.uptimeMillis() + 50);
		}
	}

	private static void _syncVolume() {
		if (state != STATE_ALIVE)
			return;
		if (volumeControlType == VOLUME_CONTROL_STREAM) {
			final int volumeStream = localVolumeStream;
			try {
				if (audioManager != null)
					audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, ((volumeStream <= 0) ? 0 : ((volumeStream >= volumeStreamMax) ? volumeStreamMax : volumeStream)), 0);
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
			return;
		}
		if (volumeDB == localVolumeDB)
			return;
		volumeDB = localVolumeDB;
		volumeMultiplier = ((volumeDB <= VOLUME_MIN_DB) ? 0.0f : ((volumeDB >= 0) ? 1.0f : (float)Math.exp((double)volumeDB * 2.3025850929940456840179914546844 / 2000.0)));
		//when dimmed, decreased the volume by 20dB
		final float multiplier = (volumeDimmed ? (volumeMultiplier * 0.1f) : volumeMultiplier);
		if (httpStreamReceiverActsLikePlayer)
			httpStreamReceiver.setVolume(multiplier, multiplier);
		if (player != null) {
			try {
				player.setVolume(multiplier, multiplier);
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
		}
		if (nextPlayer != null) {
			try {
				nextPlayer.setVolume(multiplier, multiplier);
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
		}
	}

	private static void _overrideVolumeMultiplier(boolean toMax) {
		if (state != STATE_ALIVE)
			return;
		volumeMultiplier = ((toMax || (volumeDB >= 0)) ? 1.0f : ((volumeDB <= VOLUME_MIN_DB) ? 0.0f : (float)Math.exp((double)volumeDB * 2.3025850929940456840179914546844 / 2000.0)));
		//when dimmed, decreased the volume by 20dB
		final float multiplier = (volumeDimmed ? (volumeMultiplier * 0.1f) : volumeMultiplier);
		if (httpStreamReceiverActsLikePlayer)
			httpStreamReceiver.setVolume(multiplier, multiplier);
		if (player != null) {
			try {
				player.setVolume(multiplier, multiplier);
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
		}
		if (nextPlayer != null) {
			try {
				nextPlayer.setVolume(multiplier, multiplier);
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
		}
	}

	private static void _processFocusGainTimer() {
		if (state != STATE_ALIVE || !hasFocus)
			return;
		//someone else may have changed our values if the engine is shared
		localHandler.sendEmptyMessageAtTime(MSG_REGISTER_MEDIA_BUTTON_EVENT_RECEIVER, SystemClock.uptimeMillis());
		if (resumePlaybackAfterFocusGain) {
			//do not restart playback in scenarios like this (it really scares people!):
			//the person has answered a call, removed the headset, ended the call without
			//the headset plugged in, and then the focus came back to us
			if (audioSinkBeforeFocusLoss != AUDIO_SINK_DEVICE && audioSink == AUDIO_SINK_DEVICE) {
				resumePlaybackAfterFocusGain = false;
				_requestFocus();
			} else if (!playing) {
				localHandler.sendMessageAtTime(Message.obtain(localHandler, MSG_PRE_PLAY, SongList.HOW_CURRENT, 0), SystemClock.uptimeMillis());
			} else {
				resumePlaybackAfterFocusGain = false;
			}
		}
	}

	private static void _enableEffects(int enabledFlags, int audioSink, Runnable callback) {
		//don't even ask.......
		//(a few devices won't disable one effect while the other effect is enabled)
		audioSinkUsedInEffects = Player.audioSink;
		if (audioSinkUsedInEffects != audioSink) {
			//just change the state, as these settings will not be actually applied
			Equalizer._setEnabled((enabledFlags & 1) != 0, audioSink);
			BassBoost._setEnabled((enabledFlags & 2) != 0, audioSink);
			Virtualizer._setEnabled((enabledFlags & 4) != 0, audioSink);
			if (callback != null)
				MainHandler.postToMainThread(callback);
			return;
		}
		Equalizer._release();
		BassBoost._release();
		Virtualizer._release();
		if ((enabledFlags & 1) != 0) {
			if (player != null)
				Equalizer._initialize(player.getAudioSessionId());
			Equalizer._setEnabled(true, audioSink);
		} else {
			Equalizer._setEnabled(false, audioSink);
		}
		if ((enabledFlags & 2) != 0) {
			if (player != null)
				BassBoost._initialize(player.getAudioSessionId());
			BassBoost._setEnabled(true, audioSink);
		} else {
			BassBoost._setEnabled(false, audioSink);
		}
		if ((enabledFlags & 4) != 0) {
			if (player != null)
				Virtualizer._initialize(player.getAudioSessionId());
			Virtualizer._setEnabled(true, audioSink);
		} else {
			Virtualizer._setEnabled(false, audioSink);
		}
		if (callback != null)
			MainHandler.postToMainThread(callback);
	}

	private static void _reinitializeEffects() {
		//don't even ask.......
		//(a few devices won't disable one effect while the other effect is enabled)
		Equalizer._release();
		BassBoost._release();
		Virtualizer._release();
		final int sessionId = player.getAudioSessionId();
		audioSinkUsedInEffects = audioSink;
		if (Equalizer.isEnabled(audioSinkUsedInEffects)) {
			Equalizer._initialize(sessionId);
			Equalizer._setEnabled(true, audioSinkUsedInEffects);
		}
		if (BassBoost.isEnabled(audioSinkUsedInEffects)) {
			BassBoost._initialize(sessionId);
			BassBoost._setEnabled(true, audioSinkUsedInEffects);
		}
		if (Virtualizer.isEnabled(audioSinkUsedInEffects)) {
			Virtualizer._initialize(sessionId);
			Virtualizer._setEnabled(true, audioSinkUsedInEffects);
		}
	}

	@Override
	public void onAudioFocusChange(int focusChange) {
		if (state != STATE_ALIVE)
			return;
		if (handler != null) {
			handler.removeMessages(MSG_FOCUS_GAIN_TIMER);
			handler.removeMessages(MSG_FADE_IN_VOLUME_TIMER);
		}
		resetHeadsetHook();
		volumeDimmed = false;
		switch (focusChange) {
		case AudioManager.AUDIOFOCUS_GAIN:
			if (!hasFocus) {
				hasFocus = true;
				if (handler != null)
					handler.sendMessageAtTime(Message.obtain(handler, MSG_FOCUS_GAIN_TIMER), SystemClock.uptimeMillis() + 1500);
			} else {
				//came here from AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
				if (httpStreamReceiverActsLikePlayer) {
					httpStreamReceiver.setVolume(volumeMultiplier, volumeMultiplier);
				} else if (player != null && playerState == PLAYER_STATE_LOADED) {
					try {
						player.setVolume(volumeMultiplier, volumeMultiplier);
					} catch (Throwable ex) {
						ex.printStackTrace();
					}
				}
			}
			break;
		case AudioManager.AUDIOFOCUS_LOSS:
		case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
			//we just cannot replace resumePlaybackAfterFocusGain's value with
			//playing, because if a second focus loss occurred BEFORE _focusGain
			//had a chance to be called, then playing would be false, when in fact,
			//we want resumePlaybackAfterFocusGain to remain true (and the audio sink
			//must be untouched)
			//also because of that, we must take resumePlaybackAfterFocusGain into
			//consideration here, because _fullCleanup always sets it to false
			final boolean oldResumePlaybackAfterFocusGain = resumePlaybackAfterFocusGain;
			final boolean resume = (localPlaying || localPlayerState == PLAYER_STATE_PREPARING);
			hasFocus = false;
			_storeSongTime();
			_fullCleanup();
			silenceMode = SILENCE_FOCUS;
			if (resume || oldResumePlaybackAfterFocusGain)
				resumePlaybackAfterFocusGain = true;
			//change audioSinkBeforeFocusLoss only at the first time we lose focus!
			//(if we lose focus several times in row, before we actually have had
			//chance to play at least once, resume will be false)
			if (resume)
				audioSinkBeforeFocusLoss = audioSink;
			_updateState(false, null);
			break;
		case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
			hasFocus = true;
			if (!doNotAttenuateVolume) {
				//when dimmed, decreased the volume by 20dB
				final float multiplier = volumeMultiplier * 0.1f;
				volumeDimmed = true;
				if (httpStreamReceiverActsLikePlayer) {
					httpStreamReceiver.setVolume(multiplier, multiplier);
				} else if (player != null && playerState == PLAYER_STATE_LOADED) {
					try {
						player.setVolume(multiplier, multiplier);
					} catch (Throwable ex) {
						ex.printStackTrace();
					}
				}
			}
			break;
		}
	}

	@Override
	public void onCompletion(MediaPlayer mediaPlayer) {
		if (state != STATE_ALIVE)
			return;
		if (playing && player == mediaPlayer)
			localHandler.sendMessageAtTime(Message.obtain(localHandler, MSG_PRE_PLAY, SongList.HOW_NEXT_AUTO, 0), SystemClock.uptimeMillis());
	}

	@Override
	public boolean onError(MediaPlayer mediaPlayer, int what, int extra) {
		if (state != STATE_ALIVE)
			return true;
		if (mediaPlayer == nextPlayer || mediaPlayer == player) {
			if (what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
				_storeSongTime();
				_fullCleanup();
				if (reviveAlreadyTried) {
					reviveAlreadyTried = false;
					_updateState(false, new MediaServerDiedException());
				} else {
					reviveAlreadyTried = true;
					localHandler.sendMessageAtTime(Message.obtain(localHandler, MSG_PRE_PLAY, SongList.HOW_CURRENT, 0), SystemClock.uptimeMillis());
				}
			} else if (mediaPlayer == nextPlayer) {
				nextSong = null;
				nextPlayerState = PLAYER_STATE_NEW;
			} else {
				_fullCleanup();
				final Throwable result = ((extra == ERROR_NOT_FOUND) ? new FileNotFoundException() :
					((extra == ERROR_TIMED_OUT) ? new TimeoutException() :
						((extra == ERROR_UNSUPPORTED_FORMAT) ? new UnsupportedFormatException() :
							new IOException())));
				//_handleFailure used to be called only when howThePlayerStarted == SongList.HOW_NEXT_AUTO
				//and the song was being prepared
				if (howThePlayerStarted != SongList.HOW_CURRENT && howThePlayerStarted < 0)
					_handleFailure(result, false);
				else
					_updateState(false, result);
			}
		} else {
			_fullCleanup();
			_updateState(false, new Exception("Invalid MediaPlayer"));
		}
		return true;
	}

	@Override
	public boolean onInfo(MediaPlayer mediaPlayer, int what, int extra) {
		if (mediaPlayer == player) {
			switch (what) {
			case MediaPlayer.MEDIA_INFO_BUFFERING_START:
				if (!playerBuffering) {
					playerBuffering = true;
					_updateState(true, null);
				}
				break;
			case MediaPlayer.MEDIA_INFO_BUFFERING_END:
				if (playerBuffering) {
					playerBuffering = false;
					_updateState(true, null);
				}
				break;
			}
		}
		return false;
	}

	@Override
	public void onPrepared(MediaPlayer mediaPlayer) {
		if (state != STATE_ALIVE)
			return;
		if (mediaPlayer == nextPlayer) {
			if (nextSongScheduledForPreparation == nextSong && nextSong != null) {
				nextSongScheduledForPreparation = null;
				nextPlayerState = PLAYER_STATE_LOADED;
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
					_setNextPlayer();
			}
		} else if (mediaPlayer == player) {
			if (songScheduledForPreparation == song && song != null) {
				songScheduledForPreparation = null;
				playerState = PLAYER_STATE_LOADED;
				try {
					if (!hasFocus) {
						_fullCleanup();
						_updateState(false, null);
						return;
					}
					//when dimmed, decreased the volume by 20dB
					float multiplier = (volumeDimmed ? (volumeMultiplier * 0.1f) : volumeMultiplier);
					if (httpStreamReceiverActsLikePlayer)
						httpStreamReceiver.setVolume(multiplier, multiplier);
					else
						player.setVolume(multiplier, multiplier);
					if (storedSongTime < 0 || song.isHttp) {
						storedSongTime = -1;
						_startPlayer();
						_scheduleNextPlayerForPreparation();
					} else {
						playAfterSeeking = true;
						prepareNextAfterSeeking = true;
						playerState = PLAYER_STATE_PREPARING;
						player.seekTo(storedSongTime);
					}
					songWhenFirstErrorHappened = null;
					_updateState(false, null);
				} catch (Throwable ex) {
					_fullCleanup();
					_handleFailure(ex, false);
				}
			}
		} else {
			_fullCleanup();
			_updateState(false, new Exception("Invalid MediaPlayer"));
		}
	}

	@Override
	public void onSeekComplete(MediaPlayer mediaPlayer) {
		if (state != STATE_ALIVE)
			return;
		if (mediaPlayer == player) {
			try {
				playerState = PLAYER_STATE_LOADED;
				if (playAfterSeeking)
					_startPlayer();
				_updateState(false, null);
				if (prepareNextAfterSeeking)
					_scheduleNextPlayerForPreparation();
			} catch (Throwable ex) {
				_fullCleanup();
				_updateState(false, ex);
			}
		}
	}

	private static int httpStreamReceiverVersion, httpOptions;
	private static HttpStreamReceiver httpStreamReceiver;

	public static int getBytesBeforeDecoding(int index) {
		switch (index) {
		case 0:
			return (4 << 10);
		case 2:
			return (16 << 10);
		case 3:
			return (32 << 10);
		case 4:
			return (48 << 10);
		case 5:
			return (64 << 10);
		case 6:
			return (128 << 10);
		case 7:
			return (256 << 10);
		default:
			return (8 << 10);
		}
	}

	public static int getBytesBeforeDecodingIndex() {
		return (httpOptions & 0x0F);
	}

	public static void setBytesBeforeDecodingIndex(int bytesBeforeDecodingIndex) {
		httpOptions = (httpOptions & ~0x0F) | (bytesBeforeDecodingIndex & 0x0F);
	}

	public static int getSecondsBeforePlayback(int index) {
		switch (index) {
		case 0:
			return 500;
		case 1:
			return 1000;
		case 3:
			return 2000;
		case 4:
			return 2500;
		default:
			return 1500;
		}
	}

	public static int getSecondsBeforePlaybackIndex() {
		return ((httpOptions >>> 4) & 0x0F);
	}

	public static void setSecondsBeforePlayingIndex(int secondsBeforePlayingIndex) {
		httpOptions = (httpOptions & ~0xF0) | ((secondsBeforePlayingIndex & 0x0F) << 4);
	}

	private static void _httpStreamReceiverError(int version, int errorCode) {
		if (state != STATE_ALIVE || version != httpStreamReceiverVersion || song == null || player == null || thePlayer == null)
			return;
		_releaseInternetObjects();
		thePlayer.onError(player, MediaPlayer.MEDIA_ERROR_UNKNOWN, !isConnectedToTheInternet() ? ERROR_NOT_FOUND : errorCode);
	}

	private static void httpStreamReceiverMetadataUpdate(int version, String metadata) {
		if (state != STATE_ALIVE || version != httpStreamReceiverVersion || localSong == null || localPlayer == null || thePlayer == null)
			return;
		int i;
		if ((i = metadata.indexOf("StreamTitle")) < 0)
			return;
		if ((i = metadata.indexOf('=', i + 11)) < 0)
			return;
		if ((i = metadata.indexOf('\'', i + 1)) < 0)
			return;
		final int firstChar = i + 1;
		int loopCount = 0;
		do {
			loopCount++;
			if ((i = metadata.indexOf('\'', i + 1)) < 0)
				return;
		} while (metadata.charAt(i - 1) == '\\');
		metadata = metadata.substring(firstChar, i).trim();
		if (loopCount > 1)
			metadata = metadata.replace("\\'", "\'");
		if (metadata.length() > 0) {
			String name = null, url = null;
			//by doing like this, we do not need to synchronize the access to httpStreamReceiver
			final HttpStreamReceiver receiver = httpStreamReceiver;
			if (receiver != null) {
				name = receiver.getIcyName();
				url = receiver.getIcyUrl();
			}
			//****** NEVER update the song's path! we need the original title in order to be able to resolve it again, later!
			if (name != null && name.length() > 0) {
				localSong.artist = name;
				localSong.extraInfo = name;
			}
			if (url != null && url.length() > 0)
				localSong.album = url;
			localSong.title = metadata;
			broadcastStateChange(getCurrentTitle(thePlayer, isPreparing()), isPreparing(), true);
			//this will force a serialization when closing the app (saving this update)
			songs.markAsChanged();
			if (observer != null)
				observer.onPlayerMetadataChanged(localSong);
		}
	}

	private static void _httpStreamReceiverPrepared(int version) {
		if (state != STATE_ALIVE || version != httpStreamReceiverVersion || song == null || player == null || thePlayer == null)
			return;
		playerBuffering = false;
		thePlayer.onPrepared(player);
	}

	private static void _httpStreamReceiverUrlUpdated(int version, String newPath) {
		if (state != STATE_ALIVE || version != httpStreamReceiverVersion || song == null || player == null || thePlayer == null)
			return;
		song.path = newPath;
	}

	private static void _createInternetObjects() throws IOException {
		//force the player to always start playing as if coming from a pause
		silenceMode = SILENCE_NORMAL;
		playerBuffering = true;
		httpStreamReceiver = new HttpStreamReceiver(handler, MSG_HTTP_STREAM_RECEIVER_ERROR, MSG_HTTP_STREAM_RECEIVER_PREPARED, MSG_HTTP_STREAM_RECEIVER_METADATA_UPDATE, MSG_HTTP_STREAM_RECEIVER_URL_UPDATED, ++httpStreamReceiverVersion, getBytesBeforeDecoding(getBytesBeforeDecodingIndex()), getSecondsBeforePlayback(getSecondsBeforePlaybackIndex()), player.getAudioSessionId(), song.path);
		if (httpStreamReceiver.start()) {
			if ((httpStreamReceiverActsLikePlayer = httpStreamReceiver.isPerformingFullPlayback))
				return;
			playerBuffering = false;
			//Even though it happens very rarely, a few devices will freeze and produce an ANR
			//when calling setDataSource from the main thread :(
			player.setDataSource(httpStreamReceiver.getLocalURL());
			player.prepareAsync();
		} else {
			//when start() returns false, this means we were unable to create the local server
			_releaseInternetObjects();
			throw new PermissionDeniedException();
		}
	}

	private static void _releaseInternetObjects() {
		if (httpStreamReceiver != null) {
			httpStreamReceiverVersion++;
			httpStreamReceiver.release();
			httpStreamReceiverActsLikePlayer = false;
			httpStreamReceiver = null;
		}
	}

	//I know this is far from "organized"... but this is the only way to prevent
	//the class BluetoothVisualizerController from loading into memory without need!!!
	public static Object bluetoothVisualizerController;
	//bluetoothVisualizerConfig bits:
	//0 1 2 = size
	//3 4 = speed
	//5 6 7 8 = frames to skip (index)
	//9 = vu meter
	public static int bluetoothVisualizerLastErrorMessage, bluetoothVisualizerConfig, bluetoothVisualizerState;
	public static final int BLUETOOTH_VISUALIZER_STATE_INITIAL = 0;
	public static final int BLUETOOTH_VISUALIZER_STATE_CONNECTING = 1;
	public static final int BLUETOOTH_VISUALIZER_STATE_CONNECTED = 2;
	public static final int BLUETOOTH_VISUALIZER_STATE_TRANSMITTING = 3;

	public static boolean startBluetoothVisualizer(ActivityHost activity, boolean startTransmissionOnConnection) {
		if (state != STATE_ALIVE)
			return false;
		stopBluetoothVisualizer();
		final BluetoothVisualizerControllerJni b = new BluetoothVisualizerControllerJni(activity, startTransmissionOnConnection);
		if (bluetoothVisualizerLastErrorMessage == 0) {
			bluetoothVisualizerController = b;
			return true;
		}
		b.destroy();
		return false;
	}

	public static void stopBluetoothVisualizer() {
		bluetoothVisualizerState = BLUETOOTH_VISUALIZER_STATE_INITIAL;
		if (bluetoothVisualizerController != null) {
			final BluetoothVisualizerControllerJni b = (BluetoothVisualizerControllerJni)bluetoothVisualizerController;
			bluetoothVisualizerController = null;
			b.destroy();
		}
	}

	private static void updateBluetoothVisualizer(boolean songHasChanged) {
		if (bluetoothVisualizerController != null) {
			final BluetoothVisualizerControllerJni b = (BluetoothVisualizerControllerJni)bluetoothVisualizerController;
			if (!songHasChanged && playing)
				b.resetAndResume();
			else
				b.resume();
			b.playingChanged();
		}
	}

	public static int getBluetoothVisualizerSize() {
		final int size = (bluetoothVisualizerConfig & 7);
		return ((size <= 0) ? 0 : ((size >= 6) ? 6 : size));
	}

	public static void setBluetoothVisualizerSize(int size) {
		bluetoothVisualizerConfig = (bluetoothVisualizerConfig & (~7)) | ((size <= 0) ? 0 : ((size >= 6) ? 6 : size));
	}

	public static int getBluetoothVisualizerSpeed() {
		final int speed = ((bluetoothVisualizerConfig >> 3) & 3);
		return ((speed <= 0) ? 0 : ((speed >= 2) ? 2 : 1));
	}

	public static void setBluetoothVisualizerSpeed(int speed) {
		bluetoothVisualizerConfig = (bluetoothVisualizerConfig & (~(3 << 3))) | (((speed <= 0) ? 0 : ((speed >= 2) ? 2 : 1)) << 3);
	}

	public static int getBluetoothVisualizerFramesPerSecond(int framesToSkipIndex) {
		//index          0, 1, 2, 3, 4, 5, 6, 7 , 8 , 9 , 10, 11
		//frames to skip 0, 1, 2, 3, 4, 5, 9, 11, 14, 19, 29, 59
		//frames/second  60,30,20,15,12,10, 6, 5 , 4 , 3 , 2 , 1
		return ((framesToSkipIndex <= 5) ? (60 / (framesToSkipIndex + 1)) : (12 - framesToSkipIndex));
	}

	public static int getBluetoothVisualizerFramesToSkip() {
		return (getBluetoothVisualizerFramesPerSecond(11 - getBluetoothVisualizerFramesToSkipIndex()) - 1);
	}

	public static int getBluetoothVisualizerFramesToSkipIndex() {
		final int framesToSkipIndex = ((bluetoothVisualizerConfig >> 5) & 15);
		return ((framesToSkipIndex <= 0) ? 0 : ((framesToSkipIndex >= 11) ? 11 : framesToSkipIndex));
	}

	public static void setBluetoothVisualizerFramesToSkipIndex(int framesToSkipIndex) {
		bluetoothVisualizerConfig = (bluetoothVisualizerConfig & (~(15 << 5))) | (((framesToSkipIndex <= 0) ? 0 : ((framesToSkipIndex >= 11) ? 11 : framesToSkipIndex)) << 5);
	}

	public static boolean isBluetoothUsingVUMeter() {
		return ((bluetoothVisualizerConfig & (1 << 9)) != 0);
	}

	public static void setBluetoothUsingVUMeter(boolean usingVUMeter) {
		if (usingVUMeter)
			bluetoothVisualizerConfig |= (1 << 9);
		else
			bluetoothVisualizerConfig &= ~(1 << 9);
	}

	//these options were deprecated on version 74 in favor of their bit equivalents
	private static final int OPT_VOLUME = 0x0000;
	//private static final int OPT_CONTROLMODE = 0x0001;
	private static final int OPT_LASTTIME = 0x0002;
	private static final int OPT_PATH = 0x0003;
	private static final int OPT_ORIGINALPATH = 0x0004;
	private static final int OPT_FAVORITEFOLDERCOUNT = 0x0005;
	//private static final int OPT_BASSBOOSTMODE = 0x0006;
	private static final int OPT_FADEININCREMENTONFOCUS = 0x0007;
	private static final int OPT_FADEININCREMENTONPAUSE = 0x0008;
	private static final int OPT_FADEININCREMENTONOTHER = 0x0009;
	//private static final int OPT_NEXTPREPARATION = 0x000a;
	//private static final int OPT_PLAYFOLDERCLEARSLIST = 0x000b;
	//private static final int OPT_KEEPSCREENON = 0x000c;
	private static final int OPT_FORCEDORIENTATION = 0x000d;
	//private static final int OPT_DISPLAYVOLUMEINDB = 0x000e;
	//private static final int OPT_DOUBLECLICKMODE = 0x000f;
	//these 3 will no longer be used!
	//private static final int OPT_MSGADDSHOWN = 0x0010;
	//private static final int OPT_MSGPLAYSHOWN = 0x0011;
	//private static final int OPT_MSGSTARTUPSHOWN = 0x0012;
	//private static final int OPT_MARQUEETITLE = 0x0013;
	private static final int OPT_VOLUMECONTROLTYPE = 0x0014;
	//private static final int OPT_BLOCKBACKKEY = 0x0015;
	private static final int OPT_TURNOFFTIMERCUSTOMMINUTES = 0x0016;
	//private static final int OPT_ISDIVIDERVISIBLE = 0x0017;
	//private static final int OPT_ISVERTICALMARGINLARGE = 0x0018;
	//private static final int OPT_HANDLECALLKEY = 0x0019;
	//private static final int OPT_PLAYWHENHEADSETPLUGGED = 0x001a;
	//private static final int OPT_USEALTERNATETYPEFACE = 0x001b;
	//private static final int OPT_GOBACKWHENPLAYINGFOLDERS = 0x001c;
	//private static final int OPT_RANDOMMODE = 0x001d;
	private static final int OPT_FORCEDLOCALE = 0x001e;
	private static final int OPT_THEME = 0x001f;
	private static final int OPT_MSGS = 0x0020;
	private static final int OPT_MSGSTARTUP = 0x0021;
	//private static final int OPT_WIDGETTRANSPARENTBG = 0x0022;
	private static final int OPT_WIDGETTEXTCOLOR = 0x0023;
	private static final int OPT_WIDGETICONCOLOR = 0x0024;
	private static final int OPT_CUSTOMCOLORS = 0x0025;
	private static final int OPT_LASTVERSIONCODE = 0x0026;
	//private static final int OPT_BACKKEYALWAYSRETURNSTOPLAYERWHENBROWSING = 0x0027;
	//private static final int OPT_WRAPAROUNDLIST = 0x0028;
	//private static final int OPT_EXTRASPACING = 0x0029;
	//private static final int OPT_OLDBROWSERBEHAVIOR = 0x002A;
	//private static final int OPT_VISUALIZERORIENTATION = 0x002B;
	private static final int OPT_SONGEXTRAINFOMODE = 0x002C;
	private static final int OPT_TURNOFFTIMERSELECTEDMINUTES = 0x002D;
	private static final int OPT_IDLETURNOFFTIMERCUSTOMMINUTES = 0x002E;
	private static final int OPT_IDLETURNOFFTIMERSELECTEDMINUTES = 0x002F;
	//private static final int OPT_FLAT = 0x0030;
	//private static final int OPT_ALBUMART = 0x0031;
	private static final int OPT_RADIOSEARCHTERM = 0x0032;
	private static final int OPT_RADIOLASTGENRE = 0x0033;
	private static final int OPT_TRANSITION = 0x0034;
	private static final int OPT_BLUETOOTHVISUALIZERCONFIG = 0x0035;
	private static final int OPT_HEADSETHOOKACTIONS = 0x0036;
	private static final int OPT_RADIOLASTGENRESHOUTCAST = 0x0037;
	private static final int OPT_HTTPOPTIONS = 0x0038;

	//values 0x01xx are shared among all effects
	//static final int OPT_EQUALIZER_ENABLED = 0x0100;
	//static final int OPT_EQUALIZER_PRESET = 0x0101;
	static final int OPT_EQUALIZER_LEVELCOUNT = 0x0102;
	static final int OPT_EQUALIZER_LEVEL0 = 0x20000;
	static final int OPT_EQUALIZER_LEVEL0_WIRE = 0x21000;
	static final int OPT_EQUALIZER_LEVEL0_BT = 0x22000;
	//static final int OPT_BASSBOOST_ENABLED = 0x0110;
	static final int OPT_BASSBOOST_STRENGTH = 0x0111;
	//static final int OPT_VIRTUALIZER_ENABLED = 0x0112;
	static final int OPT_VIRTUALIZER_STRENGTH = 0x0113;
	static final int OPT_BASSBOOST_STRENGTH_WIRE = 0x0114;
	static final int OPT_VIRTUALIZER_STRENGTH_WIRE = 0x0115;
	static final int OPT_BASSBOOST_STRENGTH_BT = 0x0116;
	static final int OPT_VIRTUALIZER_STRENGTH_BT = 0x0117;

	private static final int OPTBIT_CONTROLMODE = 0;
	private static final int OPTBIT_BASSBOOSTMODE = 1;
	private static final int OPTBIT_NEXTPREPARATION = 2;
	private static final int OPTBIT_PLAYFOLDERCLEARSLIST = 3;
	private static final int OPTBIT_KEEPSCREENON = 4;
	//volume control changed on version 71
	private static final int OPTBIT_DISPLAYVOLUMEINDB = 5;
	private static final int OPTBIT_VOLUMECONTROLTYPE0 = 5;
	private static final int OPTBIT_DOUBLECLICKMODE = 6;
	private static final int OPTBIT_MARQUEETITLE = 7;
	private static final int OPTBIT_FLAT = 8;
	private static final int OPTBIT_ALBUMART = 9;
	private static final int OPTBIT_BLOCKBACKKEY = 10;
	private static final int OPTBIT_ISDIVIDERVISIBLE = 11;
	private static final int OPTBIT_ISVERTICALMARGINLARGE = 12;
	private static final int OPTBIT_HANDLECALLKEY = 13;
	private static final int OPTBIT_PLAYWHENHEADSETPLUGGED = 14;
	private static final int OPTBIT_USEALTERNATETYPEFACE = 15;
	private static final int OPTBIT_GOBACKWHENPLAYINGFOLDERS = 16;
	private static final int OPTBIT_RANDOMMODE = 17;
	private static final int OPTBIT_WIDGETTRANSPARENTBG = 18;
	private static final int OPTBIT_BACKKEYALWAYSRETURNSTOPLAYERWHENBROWSING = 19;
	private static final int OPTBIT_WRAPAROUNDLIST = 20;
	private static final int OPTBIT_EXTRASPACING = 21;
	//this bit has been recycled on version 71
	//private static final int OPTBIT_OLDBROWSERBEHAVIOR = 22;
	private static final int OPTBIT_VOLUMECONTROLTYPE1 = 22;
	static final int OPTBIT_EQUALIZER_ENABLED = 23;
	static final int OPTBIT_BASSBOOST_ENABLED = 24;
	static final int OPTBIT_VIRTUALIZER_ENABLED = 25;
	//this bit has been removed on version 83
	//private static final int OPTBIT_HEADSETHOOK_DOUBLE_PRESS_PAUSES = 26;
	private static final int OPTBIT_DO_NOT_ATTENUATE_VOLUME = 27;
	private static final int OPTBIT_SCROLLBAR_TO_THE_LEFT = 28;
	private static final int OPTBIT_SCROLLBAR_SONGLIST0 = 29;
	private static final int OPTBIT_SCROLLBAR_SONGLIST1 = 30;
	private static final int OPTBIT_SCROLLBAR_BROWSER0 = 31;
	private static final int OPTBIT_SCROLLBAR_BROWSER1 = 32;
	private static final int OPTBIT_LASTRADIOSEARCHWASBYGENRE = 33;
	private static final int OPTBIT_EXPANDSEEKBAR = 34;
	private static final int OPTBIT_REPEATONE = 35;
	private static final int OPTBIT_NOTFULLSCREEN = 36;
	private static final int OPTBIT_CONTROLS_TO_THE_LEFT = 37;
	private static final int OPTBIT_BORDERS = 38;
	private static final int OPTBIT_ANIMATIONS = 39;
	private static final int OPTBIT_VISUALIZER_PORTRAIT = 40;
	private static final int OPTBIT_REPEATNONE = 41;
	private static final int OPTBIT_TURNOFFPLAYLIST = 42;
	static final int OPTBIT_EQUALIZER_ENABLED_WIRE = 43;
	static final int OPTBIT_BASSBOOST_ENABLED_WIRE = 44;
	static final int OPTBIT_VIRTUALIZER_ENABLED_WIRE = 45;
	static final int OPTBIT_EQUALIZER_ENABLED_BT = 46;
	static final int OPTBIT_BASSBOOST_ENABLED_BT = 47;
	static final int OPTBIT_VIRTUALIZER_ENABLED_BT = 48;
	private static final int OPTBIT_3D = 49;
	private static final int OPTBIT_FOLLOW_CURRENT_SONG = 50;
	private static final int OPTBIT_ANNOUNCE_CURRENT_SONG = 51;
	private static final int OPTBIT_PLACE_TITLE_AT_THE_BOTTOM = 52;

	private static final int OPT_FAVORITEFOLDER0 = 0x10000;

	private static Notification notification;
	private static RemoteViews notificationRemoteViews;
	private static boolean appNotInForeground, idleTurnOffTimerSent;
	private static long turnOffTimerOrigin, idleTurnOffTimerOrigin;
	private static final HashSet<String> favoriteFolders = new HashSet<>();
	private static PendingIntent intentActivityHost, intentPrevious, intentPlayPause, intentNext, intentExit;
	private static int headsetHookActions, headsetHookPressCount;
	public static String path, originalPath, radioSearchTerm;
	public static boolean lastRadioSearchWasByGenre, nextPreparationEnabled, doNotAttenuateVolume, clearListWhenPlayingFolders, controlMode, bassBoostMode, handleCallKey, playWhenHeadsetPlugged, goBackWhenPlayingFolders, turnOffWhenPlaylistEnds, followCurrentSong, announceCurrentSong;
	public static int radioLastGenre, radioLastGenreShoutcast, fadeInIncrementOnFocus, fadeInIncrementOnPause, fadeInIncrementOnOther, turnOffTimerCustomMinutes, turnOffTimerSelectedMinutes, idleTurnOffTimerCustomMinutes, idleTurnOffTimerSelectedMinutes;

	public static SerializableMap loadConfigFromFile(Context context) {
		final SerializableMap opts = SerializableMap.deserialize(context, "_Player");
		return ((opts == null) ? new SerializableMap() : opts);
	}

	private static void loadConfig(Context context) {
		final SerializableMap opts = loadConfigFromFile(context);
		UI.lastVersionCode = opts.getInt(OPT_LASTVERSIONCODE, 0);
		volumeDB = opts.getInt(OPT_VOLUME);
		if (volumeDB < VOLUME_MIN_DB)
			volumeDB = VOLUME_MIN_DB;
		else if (volumeDB > 0)
			volumeDB = 0;
		localVolumeDB = volumeDB;
		volumeMultiplier = ((volumeDB <= VOLUME_MIN_DB) ? 0.0f : ((volumeDB >= 0) ? 1.0f : (float)Math.exp((double)volumeDB * 2.3025850929940456840179914546844 / 2000.0)));
		path = opts.getString(OPT_PATH);
		originalPath = opts.getString(OPT_ORIGINALPATH);
		storedSongTime = opts.getInt(OPT_LASTTIME, -1);
		fadeInIncrementOnFocus = opts.getInt(OPT_FADEININCREMENTONFOCUS, 125);
		fadeInIncrementOnPause = opts.getInt(OPT_FADEININCREMENTONPAUSE, 125);
		fadeInIncrementOnOther = opts.getInt(OPT_FADEININCREMENTONOTHER, 0);
		UI.forcedOrientation = opts.getInt(OPT_FORCEDORIENTATION);
		turnOffTimerCustomMinutes = opts.getInt(OPT_TURNOFFTIMERCUSTOMMINUTES, 30);
		if (turnOffTimerCustomMinutes < 1)
			turnOffTimerCustomMinutes = 1;
		turnOffTimerSelectedMinutes = opts.getInt(OPT_TURNOFFTIMERSELECTEDMINUTES, 0);
		if (turnOffTimerSelectedMinutes < 0)
			turnOffTimerSelectedMinutes = 0;
		idleTurnOffTimerCustomMinutes = opts.getInt(OPT_IDLETURNOFFTIMERCUSTOMMINUTES, 30);
		if (idleTurnOffTimerCustomMinutes < 1)
			idleTurnOffTimerCustomMinutes = 1;
		idleTurnOffTimerSelectedMinutes = opts.getInt(OPT_IDLETURNOFFTIMERSELECTEDMINUTES, 0);
		if (idleTurnOffTimerSelectedMinutes < 0)
			idleTurnOffTimerSelectedMinutes = 0;
		UI.customColors = opts.getBuffer(OPT_CUSTOMCOLORS);
		UI.is3D = opts.getBit(OPTBIT_3D, true);
		UI.setTheme(null, (UI.lastVersionCode < 74) ? UI.THEME_pinkmusic : opts.getInt(OPT_THEME, UI.THEME_pinkmusic));
		UI.msgs = opts.getInt(OPT_MSGS, 0);
		UI.msgStartup = opts.getInt(OPT_MSGSTARTUP, 0);
		UI.widgetTextColor = opts.getInt(OPT_WIDGETTEXTCOLOR, 0xff000000);
		UI.widgetIconColor = opts.getInt(OPT_WIDGETICONCOLOR, 0xff000000);
		bluetoothVisualizerConfig = opts.getInt(OPT_BLUETOOTHVISUALIZERCONFIG, 2 | (2 << 3) | (3 << 5));
		Song.extraInfoMode = opts.getInt(OPT_SONGEXTRAINFOMODE, Song.EXTRA_ARTIST);
		radioSearchTerm = opts.getString(OPT_RADIOSEARCHTERM);
		radioLastGenre = opts.getInt(OPT_RADIOLASTGENRE, 21);
		radioLastGenreShoutcast = opts.getInt(OPT_RADIOLASTGENRESHOUTCAST, 20);
		httpOptions = opts.getInt(OPT_HTTPOPTIONS, 0x00000021);
		UI.setTransition((UI.lastVersionCode < 76 && UI.deviceSupportsAnimations) ? UI.TRANSITION_FADE : opts.getInt(OPT_TRANSITION, UI.deviceSupportsAnimations ? UI.TRANSITION_FADE : UI.TRANSITION_NONE));
		headsetHookActions = opts.getInt(OPT_HEADSETHOOKACTIONS, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE | (KeyEvent.KEYCODE_MEDIA_NEXT << 8) | (KeyEvent.KEYCODE_MEDIA_PREVIOUS << 16));
		//the volume control types changed on version 71
		if (UI.lastVersionCode <= 70 && UI.lastVersionCode != 0) {
			final int volumeControlType = opts.getInt(OPT_VOLUMECONTROLTYPE, UI.isTV ? VOLUME_CONTROL_NONE : VOLUME_CONTROL_STREAM);
			if (volumeControlType == VOLUME_CONTROL_DB)
				setVolumeControlType(opts.getBit(OPTBIT_DISPLAYVOLUMEINDB) ? VOLUME_CONTROL_DB : VOLUME_CONTROL_PERCENT);
			else
				setVolumeControlType(volumeControlType);
		} else {
			//load the volume control type the new way
			final int defVolumeControlType = (UI.isTV ? VOLUME_CONTROL_NONE : VOLUME_CONTROL_STREAM);
			setVolumeControlType(opts.getBitI(OPTBIT_VOLUMECONTROLTYPE0, defVolumeControlType & 1) |
					(opts.getBitI(OPTBIT_VOLUMECONTROLTYPE1, defVolumeControlType >> 1) << 1));
		}
		//the concept of bit was added on version 38 (the old way was removed on version 74)
		//if (opts.hasBits() || UI.lastVersionCode == 0) {
			//load the bit flags the new way
			controlMode = opts.getBit(OPTBIT_CONTROLMODE);
			bassBoostMode = opts.getBit(OPTBIT_BASSBOOSTMODE);
			nextPreparationEnabled = opts.getBit(OPTBIT_NEXTPREPARATION, true);
			clearListWhenPlayingFolders = opts.getBit(OPTBIT_PLAYFOLDERCLEARSLIST);
			UI.keepScreenOn = opts.getBit(OPTBIT_KEEPSCREENON, true);
			UI.doubleClickMode = opts.getBit(OPTBIT_DOUBLECLICKMODE);
			UI.marqueeTitle = opts.getBit(OPTBIT_MARQUEETITLE, true);
			UI.setFlat((UI.lastVersionCode < 74) || opts.getBit(OPTBIT_FLAT, true));
			UI.hasBorders = ((UI.lastVersionCode >= 74) && opts.getBit(OPTBIT_BORDERS, false));
			UI.animationEnabled = ((UI.lastVersionCode < 76 && UI.deviceSupportsAnimations) || opts.getBit(OPTBIT_ANIMATIONS, UI.deviceSupportsAnimations));
			UI.albumArt = opts.getBit(OPTBIT_ALBUMART, true);
			UI.blockBackKey = opts.getBit(OPTBIT_BLOCKBACKKEY);
			UI.isDividerVisible = opts.getBit(OPTBIT_ISDIVIDERVISIBLE, true);
			UI.setVerticalMarginLarge(opts.getBit(OPTBIT_ISVERTICALMARGINLARGE, true)); //UI.isLargeScreen || !UI.isLowDpiScreen));
			handleCallKey = opts.getBit(OPTBIT_HANDLECALLKEY, true);
			playWhenHeadsetPlugged = opts.getBit(OPTBIT_PLAYWHENHEADSETPLUGGED, true);
			UI.setUsingAlternateTypefaceAndForcedLocale(context, opts.getBit(OPTBIT_USEALTERNATETYPEFACE), opts.getInt(OPT_FORCEDLOCALE, UI.LOCALE_NONE));
			goBackWhenPlayingFolders = opts.getBit(OPTBIT_GOBACKWHENPLAYINGFOLDERS);
			UI.widgetTransparentBg = opts.getBit(OPTBIT_WIDGETTRANSPARENTBG);
			UI.backKeyAlwaysReturnsToPlayerWhenBrowsing = opts.getBit(OPTBIT_BACKKEYALWAYSRETURNSTOPLAYERWHENBROWSING);
			UI.wrapAroundList = opts.getBit(OPTBIT_WRAPAROUNDLIST);
			UI.extraSpacing = opts.getBit(OPTBIT_EXTRASPACING, UI.isTV || (UI.screenWidth >= UI.dpToPxI(600)) || (UI.screenHeight >= UI.dpToPxI(600)));
			//new settings (cannot be loaded the old way)
			//headsetHookDoublePressPauses = opts.getBit(OPTBIT_HEADSETHOOK_DOUBLE_PRESS_PAUSES);
			doNotAttenuateVolume = opts.getBit(OPTBIT_DO_NOT_ATTENUATE_VOLUME);
			UI.scrollBarToTheLeft = opts.getBit(OPTBIT_SCROLLBAR_TO_THE_LEFT);
			UI.songListScrollBarType = (opts.getBitI(OPTBIT_SCROLLBAR_SONGLIST1, 0) << 1) | opts.getBitI(OPTBIT_SCROLLBAR_SONGLIST0, UI.isTV ? 0 : 1);
			if (UI.songListScrollBarType == BgListView.SCROLLBAR_INDEXED)
				UI.songListScrollBarType = BgListView.SCROLLBAR_LARGE;
			UI.browserScrollBarType = (opts.getBitI(OPTBIT_SCROLLBAR_BROWSER1, UI.isTV ? 0 : 1) << 1) | opts.getBitI(OPTBIT_SCROLLBAR_BROWSER0, 0);
			lastRadioSearchWasByGenre = opts.getBit(OPTBIT_LASTRADIOSEARCHWASBYGENRE, true);
			UI.expandSeekBar = opts.getBit(OPTBIT_EXPANDSEEKBAR, true);
			songs.setRepeatMode(opts.getBit(OPTBIT_REPEATONE) ? SongList.REPEAT_ONE : (opts.getBit(OPTBIT_REPEATNONE) ? SongList.REPEAT_NONE : SongList.REPEAT_ALL));
			songs.setRandomMode(opts.getBit(OPTBIT_RANDOMMODE));
			UI.notFullscreen = opts.getBit(OPTBIT_NOTFULLSCREEN);
			UI.controlsToTheLeft = opts.getBit(OPTBIT_CONTROLS_TO_THE_LEFT);
			UI.visualizerPortrait = opts.getBit(OPTBIT_VISUALIZER_PORTRAIT);
			turnOffWhenPlaylistEnds = opts.getBit(OPTBIT_TURNOFFPLAYLIST);
			followCurrentSong = opts.getBit(OPTBIT_FOLLOW_CURRENT_SONG, true);
			announceCurrentSong = opts.getBit(OPTBIT_ANNOUNCE_CURRENT_SONG);
			UI.placeTitleAtTheBottom = opts.getBit(OPTBIT_PLACE_TITLE_AT_THE_BOTTOM);
		/*} else {
			//load bit flags the old way
			controlMode = opts.getBoolean(OPT_CONTROLMODE);
			bassBoostMode = opts.getBoolean(OPT_BASSBOOSTMODE);
			nextPreparationEnabled = opts.getBoolean(OPT_NEXTPREPARATION, true);
			clearListWhenPlayingFolders = opts.getBoolean(OPT_PLAYFOLDERCLEARSLIST);
			UI.keepScreenOn = opts.getBoolean(OPT_KEEPSCREENON, true);
			UI.doubleClickMode = opts.getBoolean(OPT_DOUBLECLICKMODE);
			UI.marqueeTitle = opts.getBoolean(OPT_MARQUEETITLE, true);
			UI.setFlat(opts.getBoolean(OPT_FLAT, true));
			UI.albumArt = opts.getBoolean(OPT_ALBUMART, true);
			UI.blockBackKey = opts.getBoolean(OPT_BLOCKBACKKEY);
			UI.isDividerVisible = opts.getBoolean(OPT_ISDIVIDERVISIBLE, true);
			UI.setVerticalMarginLarge(opts.getBoolean(OPT_ISVERTICALMARGINLARGE, true)); //UI.isLargeScreen || !UI.isLowDpiScreen));
			handleCallKey = opts.getBoolean(OPT_HANDLECALLKEY, true);
			playWhenHeadsetPlugged = opts.getBoolean(OPT_PLAYWHENHEADSETPLUGGED, true);
			UI.setUsingAlternateTypefaceAndForcedLocale(context, opts.getBoolean(OPT_USEALTERNATETYPEFACE), opts.getInt(OPT_FORCEDLOCALE, UI.LOCALE_NONE));
			goBackWhenPlayingFolders = opts.getBoolean(OPT_GOBACKWHENPLAYINGFOLDERS);
			songs.setRandomMode(opts.getBoolean(OPT_RANDOMMODE));
			UI.widgetTransparentBg = opts.getBoolean(OPT_WIDGETTRANSPARENTBG);
			UI.backKeyAlwaysReturnsToPlayerWhenBrowsing = opts.getBoolean(OPT_BACKKEYALWAYSRETURNSTOPLAYERWHENBROWSING);
			UI.wrapAroundList = opts.getBoolean(OPT_WRAPAROUNDLIST);
			UI.extraSpacing = opts.getBoolean(OPT_EXTRASPACING, (UI.screenWidth >= UI.dpToPxI(600)) || (UI.screenHeight >= UI.dpToPxI(600)));
			//Load default values for new settings
			UI.songListScrollBarType = (UI.isTV ? BgListView.SCROLLBAR_SYSTEM : BgListView.SCROLLBAR_LARGE);
			UI.browserScrollBarType = (UI.isTV ? BgListView.SCROLLBAR_SYSTEM : BgListView.SCROLLBAR_INDEXED);
			lastRadioSearchWasByGenre = true;
			UI.expandSeekBar = true;
		}*/
		int count = opts.getInt(OPT_FAVORITEFOLDERCOUNT);
		if (count > 0) {
			if (count > 128)
				count = 128;
			favoriteFolders.clear();
			for (int i = count - 1; i >= 0; i--) {
				final String f = opts.getString(OPT_FAVORITEFOLDER0 + i);
				if (f != null && f.length() > 1)
					favoriteFolders.add(f);
			}
		}
		Equalizer.loadConfig(opts);
		BassBoost.loadConfig(opts);
		Virtualizer.loadConfig(opts);
		//PresetReverb.loadConfig(opts);
	}

	public static void saveConfig(Context context, boolean saveSongs) {
		final SerializableMap opts = new SerializableMap(96);
		opts.put(OPT_LASTVERSIONCODE, UI.VERSION_CODE);
		opts.put(OPT_VOLUME, volumeDB);
		opts.put(OPT_PATH, path);
		opts.put(OPT_ORIGINALPATH, originalPath);
		opts.put(OPT_LASTTIME, storedSongTime);
		opts.put(OPT_FADEININCREMENTONFOCUS, fadeInIncrementOnFocus);
		opts.put(OPT_FADEININCREMENTONPAUSE, fadeInIncrementOnPause);
		opts.put(OPT_FADEININCREMENTONOTHER, fadeInIncrementOnOther);
		opts.put(OPT_FORCEDORIENTATION, UI.forcedOrientation);
		opts.put(OPT_TURNOFFTIMERCUSTOMMINUTES, turnOffTimerCustomMinutes);
		opts.put(OPT_TURNOFFTIMERSELECTEDMINUTES, turnOffTimerSelectedMinutes);
		opts.put(OPT_IDLETURNOFFTIMERCUSTOMMINUTES, idleTurnOffTimerCustomMinutes);
		opts.put(OPT_IDLETURNOFFTIMERSELECTEDMINUTES, idleTurnOffTimerSelectedMinutes);
		opts.put(OPT_CUSTOMCOLORS, UI.customColors);
		opts.put(OPT_THEME, UI.theme);
		opts.put(OPT_FORCEDLOCALE, UI.forcedLocale);
		opts.put(OPT_MSGS, UI.msgs);
		opts.put(OPT_MSGSTARTUP, UI.msgStartup);
		opts.put(OPT_WIDGETTEXTCOLOR, UI.widgetTextColor);
		opts.put(OPT_WIDGETICONCOLOR, UI.widgetIconColor);
		opts.put(OPT_BLUETOOTHVISUALIZERCONFIG, bluetoothVisualizerConfig);
		opts.put(OPT_SONGEXTRAINFOMODE, Song.extraInfoMode);
		opts.put(OPT_RADIOSEARCHTERM, radioSearchTerm);
		opts.put(OPT_RADIOLASTGENRE, radioLastGenre);
		opts.put(OPT_RADIOLASTGENRESHOUTCAST, radioLastGenreShoutcast);
		opts.put(OPT_HTTPOPTIONS, httpOptions);
		opts.put(OPT_TRANSITION, UI.transition);
		opts.put(OPT_HEADSETHOOKACTIONS, headsetHookActions);
		opts.putBit(OPTBIT_CONTROLMODE, controlMode);
		opts.putBit(OPTBIT_BASSBOOSTMODE, bassBoostMode);
		opts.putBit(OPTBIT_NEXTPREPARATION, nextPreparationEnabled);
		opts.putBit(OPTBIT_PLAYFOLDERCLEARSLIST, clearListWhenPlayingFolders);
		opts.putBit(OPTBIT_KEEPSCREENON, UI.keepScreenOn);
		opts.putBit(OPTBIT_VOLUMECONTROLTYPE0, (volumeControlType & 1) != 0);
		opts.putBit(OPTBIT_VOLUMECONTROLTYPE1, (volumeControlType & 2) != 0);
		opts.putBit(OPTBIT_DOUBLECLICKMODE, UI.doubleClickMode);
		opts.putBit(OPTBIT_MARQUEETITLE, UI.marqueeTitle);
		opts.putBit(OPTBIT_FLAT, UI.isFlat);
		opts.putBit(OPTBIT_BORDERS, UI.hasBorders);
		opts.putBit(OPTBIT_ANIMATIONS, UI.animationEnabled);
		opts.putBit(OPTBIT_ALBUMART, UI.albumArt);
		opts.putBit(OPTBIT_BLOCKBACKKEY, UI.blockBackKey);
		opts.putBit(OPTBIT_3D, UI.is3D);
		opts.putBit(OPTBIT_ISDIVIDERVISIBLE, UI.isDividerVisible);
		opts.putBit(OPTBIT_ISVERTICALMARGINLARGE, UI.isVerticalMarginLarge);
		opts.putBit(OPTBIT_HANDLECALLKEY, handleCallKey);
		opts.putBit(OPTBIT_PLAYWHENHEADSETPLUGGED, playWhenHeadsetPlugged);
		opts.putBit(OPTBIT_USEALTERNATETYPEFACE, UI.isUsingAlternateTypeface);
		opts.putBit(OPTBIT_GOBACKWHENPLAYINGFOLDERS, goBackWhenPlayingFolders);
		opts.putBit(OPTBIT_RANDOMMODE, songs.isInRandomMode());
		opts.putBit(OPTBIT_WIDGETTRANSPARENTBG, UI.widgetTransparentBg);
		opts.putBit(OPTBIT_BACKKEYALWAYSRETURNSTOPLAYERWHENBROWSING, UI.backKeyAlwaysReturnsToPlayerWhenBrowsing);
		opts.putBit(OPTBIT_WRAPAROUNDLIST, UI.wrapAroundList);
		opts.putBit(OPTBIT_EXTRASPACING, UI.extraSpacing);
		//opts.putBit(OPTBIT_HEADSETHOOK_DOUBLE_PRESS_PAUSES, headsetHookDoublePressPauses);
		opts.putBit(OPTBIT_DO_NOT_ATTENUATE_VOLUME, doNotAttenuateVolume);
		opts.putBit(OPTBIT_SCROLLBAR_TO_THE_LEFT, UI.scrollBarToTheLeft);
		opts.putBit(OPTBIT_SCROLLBAR_SONGLIST0, (UI.songListScrollBarType & 1) != 0);
		opts.putBit(OPTBIT_SCROLLBAR_SONGLIST1, (UI.songListScrollBarType & 2) != 0);
		opts.putBit(OPTBIT_SCROLLBAR_BROWSER0, (UI.browserScrollBarType & 1) != 0);
		opts.putBit(OPTBIT_SCROLLBAR_BROWSER1, (UI.browserScrollBarType & 2) != 0);
		opts.putBit(OPTBIT_LASTRADIOSEARCHWASBYGENRE, lastRadioSearchWasByGenre);
		opts.putBit(OPTBIT_EXPANDSEEKBAR, UI.expandSeekBar);
		opts.putBit(OPTBIT_REPEATONE, songs.getRepeatMode() == SongList.REPEAT_ONE);
		opts.putBit(OPTBIT_REPEATNONE, songs.getRepeatMode() == SongList.REPEAT_NONE);
		opts.putBit(OPTBIT_NOTFULLSCREEN, UI.notFullscreen);
		opts.putBit(OPTBIT_CONTROLS_TO_THE_LEFT, UI.controlsToTheLeft);
		opts.putBit(OPTBIT_VISUALIZER_PORTRAIT, UI.visualizerPortrait);
		opts.putBit(OPTBIT_TURNOFFPLAYLIST, turnOffWhenPlaylistEnds);
		opts.putBit(OPTBIT_FOLLOW_CURRENT_SONG, followCurrentSong);
		opts.putBit(OPTBIT_ANNOUNCE_CURRENT_SONG, announceCurrentSong);
		opts.putBit(OPTBIT_PLACE_TITLE_AT_THE_BOTTOM, UI.placeTitleAtTheBottom);
		if (favoriteFolders.size() > 0) {
			opts.put(OPT_FAVORITEFOLDERCOUNT, favoriteFolders.size());
			int i = 0;
			for (String f : favoriteFolders) {
				opts.put(OPT_FAVORITEFOLDER0 + i, f);
				i++;
			}
		} else {
			opts.put(OPT_FAVORITEFOLDERCOUNT, 0);
		}
		Equalizer.saveConfig(opts);
		BassBoost.saveConfig(opts);
		Virtualizer.saveConfig(opts);
		//PresetReverb.saveConfig(opts);
		opts.serialize(context, "_Player");
		if (saveSongs)
			songs.serialize(context);
	}

	private static void createIntents(Context context) {
		if (intentActivityHost == null) {
			Intent intent = new Intent(context, ActivityHost.class);
			intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
			intentActivityHost = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
			intent = new Intent(context, Player.class);
			intent.setAction(Player.ACTION_PREVIOUS);
			intentPrevious = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
			intent = new Intent(context, Player.class);
			intent.setAction(Player.ACTION_PLAY_PAUSE);
			intentPlayPause = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
			intent = new Intent(context, Player.class);
			intent.setAction(Player.ACTION_NEXT);
			intentNext = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
			intent = new Intent(context, Player.class);
			intent.setAction(Player.ACTION_EXIT);
			intentExit = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		}
	}

	public static RemoteViews prepareRemoteViews(Context context, RemoteViews views, boolean prepareButtons, boolean notification, boolean notificationFirstTime) {
		createIntents(context);

		views.setTextViewText(R.id.lblTitle, getCurrentTitle(context, isPreparing()));

		if (prepareButtons) {
			if (notification) {
				UI.prepareNotificationPlaybackIcons(context);
				views.setImageViewBitmap(R.id.btnPlay, playing ? UI.icPauseNotif : UI.icPlayNotif);
				if (notificationFirstTime) {
					views.setImageViewBitmap(R.id.btnPrev, UI.icPrevNotif);
					views.setImageViewBitmap(R.id.btnNext, UI.icNextNotif);
					views.setImageViewBitmap(R.id.btnExit, UI.icExitNotif);

					views.setOnClickPendingIntent(R.id.btnPrev, intentPrevious);
					views.setOnClickPendingIntent(R.id.btnPlay, intentPlayPause);
					views.setOnClickPendingIntent(R.id.btnNext, intentNext);

					views.setOnClickPendingIntent(R.id.btnExit, intentExit);
				}
			} else {
				if (localSong == null)
					views.setTextViewText(R.id.lblArtist, "-");
				else
					views.setTextViewText(R.id.lblArtist, localSong.extraInfo);

				views.setTextColor(R.id.lblTitle, UI.widgetTextColor);
				views.setTextColor(R.id.lblArtist, UI.widgetTextColor);

				views.setOnClickPendingIntent(R.id.lblTitle, intentActivityHost);
				views.setOnClickPendingIntent(R.id.lblArtist, intentActivityHost);

				UI.prepareWidgetPlaybackIcons(context);
				views.setImageViewBitmap(R.id.btnPrev, UI.icPrev);
				views.setImageViewBitmap(R.id.btnPlay, playing ? UI.icPause : UI.icPlay);
				views.setImageViewBitmap(R.id.btnNext, UI.icNext);

				views.setOnClickPendingIntent(R.id.btnPrev, intentPrevious);
				views.setOnClickPendingIntent(R.id.btnPlay, intentPlayPause);
				views.setOnClickPendingIntent(R.id.btnNext, intentNext);
			}
		}
		return views;
	}

	private static Notification getNotification() {
		boolean firstTime = false;
		if (notification == null) {
			firstTime = true;
			notification = new Notification();
			notification.icon = R.drawable.ic_notification;
			notification.when = 0;
			notification.flags = Notification.FLAG_FOREGROUND_SERVICE;
			notification.contentIntent = intentActivityHost;
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
					//any need for this technique???
					//https://developer.android.com/about/versions/android-5.0-changes.html#BehaviorMediaControl
					//https://developer.android.com/reference/android/app/Notification.MediaStyle.html
					notification.visibility = Notification.VISIBILITY_PUBLIC;
					if (mediaSession != null)
						notification.extras.putParcelable(Notification.EXTRA_MEDIA_SESSION, mediaSession.getSessionToken());
				}
				notificationRemoteViews = new RemoteViews(thePlayer.getPackageName(), R.layout.notification);
			} else {
				notificationRemoteViews = new RemoteViews(thePlayer.getPackageName(), R.layout.notification_simple);
			}
			notification.contentView = notificationRemoteViews;
		}
		prepareRemoteViews(thePlayer, notificationRemoteViews, Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN, true, firstTime);
		return notification;
	}

	public static void setControlMode(boolean controlMode) {
		Player.controlMode = controlMode;
		if (observer != null)
			observer.onPlayerControlModeChanged(controlMode);
	}

	@Override
	public int compare(FileSt a, FileSt b) {
		return a.name.compareToIgnoreCase(b.name);
	}

	public static void addFavoriteFolder(String path) {
		synchronized (favoriteFolders) {
			favoriteFolders.add(path);
		}
	}

	public static void removeFavoriteFolder(String path) {
		synchronized (favoriteFolders) {
			favoriteFolders.remove(path);
		}
	}

	public static boolean isFavoriteFolder(String path) {
		synchronized (favoriteFolders) {
			return favoriteFolders.contains(path);
		}
	}

	public static FileSt[] getFavoriteFolders(int extra) {
		FileSt[] ffs;
		synchronized (favoriteFolders) {
			final int count = favoriteFolders.size();
			if (count == 0)
				return new FileSt[extra];
			ffs = new FileSt[count + extra];
			int i = 0;
			for (String f : favoriteFolders) {
				final int idx = f.lastIndexOf('/');
				ffs[i] = new FileSt(f, (idx >= 0 && idx < (f.length() - 1)) ? f.substring(idx + 1) : f, null, FileSt.TYPE_FAVORITE);
				i++;
			}
		}
		ArraySorter.sort(ffs, 0, ffs.length - extra, thePlayer);
		return ffs;
	}

	public static void setSelectionAfterAdding(int positionToSelect) {
		if (!alreadySelected) {
			alreadySelected = true;
			if (!songs.selecting && !songs.moving)
				songs.setSelection(positionToSelect, false);
			if (!isMainActiveOnTop)
				Player.positionToSelect = positionToSelect;
		}
	}

	public static void songListDeserialized(Song newCurrentSong, int forcePlayIdx, int positionToSelect, Throwable ex) {
		if (positionToSelect >= 0)
			setSelectionAfterAdding(positionToSelect);
		if (newCurrentSong != null)
			localSong = newCurrentSong;
		if (handler != null) {
			if (newCurrentSong != null)
				handler.sendMessageAtTime(Message.obtain(handler, MSG_SONG_LIST_DESERIALIZED, newCurrentSong), SystemClock.uptimeMillis());
			if (ex != null)
				handler.sendMessageAtTime(Message.obtain(handler, MSG_SONG_LIST_DESERIALIZED, ex), SystemClock.uptimeMillis());
		}
		switch (state) {
		case STATE_INITIALIZING:
		case STATE_INITIALIZING_STEP2:
			localHandler.sendEmptyMessageAtTime(MSG_INITIALIZATION_STEP, SystemClock.uptimeMillis());
			break;
		case STATE_ALIVE:
			if (ex == null && forcePlayIdx >= 0)
				play(forcePlayIdx);
			break;
		}
	}

	public static HttpURLConnection createConnection(String url) throws Throwable {
		HttpURLConnection urlConnection = (HttpURLConnection)(new URL(url)).openConnection();
		urlConnection.setInstanceFollowRedirects(false);
		urlConnection.setConnectTimeout(30000);
		urlConnection.setDoInput(true);
		urlConnection.setDoOutput(false);
		urlConnection.setReadTimeout(30000);
		urlConnection.setRequestMethod("GET");
		urlConnection.setUseCaches(false);
		return urlConnection;
	}

	public static boolean isConnectedToTheInternet() {
		if (thePlayer != null) {
			try {
				final ConnectivityManager mngr = (ConnectivityManager)thePlayer.getSystemService(Context.CONNECTIVITY_SERVICE);
				final NetworkInfo info = mngr.getActiveNetworkInfo();
				return (info != null && info.isConnected());
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
		}
		return false;
	}

	@SuppressWarnings({})
	public static boolean isInternetConnectedViaWiFi() {
		if (thePlayer != null) {
			try {
				final ConnectivityManager mngr = (ConnectivityManager)thePlayer.getSystemService(Context.CONNECTIVITY_SERVICE);
				final NetworkInfo infoMob = mngr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
				final NetworkInfo info = mngr.getActiveNetworkInfo();
				return (infoMob != null && info != null && !infoMob.isConnected() && info.isConnected());
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
		}
		return false;
	}

	private static void processTurnOffTimer() {
		if (state > STATE_ALIVE)
			return;
		localHandler.removeMessages(MSG_TURN_OFF_TIMER);
		if (turnOffTimerOrigin > 0) {
			final int secondsLeft = (turnOffTimerSelectedMinutes * 60) - (int)((SystemClock.elapsedRealtime() - turnOffTimerOrigin) / 1000);
			if (turnOffTimerObserver != null)
				turnOffTimerObserver.onPlayerTurnOffTimerTick();
			if (secondsLeft < 15) //less than half of our period
				stopService(false);
			else
				localHandler.sendEmptyMessageAtTime(MSG_TURN_OFF_TIMER, SystemClock.uptimeMillis() + 30000);
		}
	}

	public static void setTurnOffTimer(int minutes) {
		if (state > STATE_ALIVE)
			return;
		if (minutes > 0) {
			if (minutes != 60 && minutes != 90 && minutes != 120)
				turnOffTimerCustomMinutes = minutes;
			//we must use SystemClock.elapsedRealtime because uptimeMillis
			//does not take sleep time into account (and the user makes no
			//difference between the time spent during sleep and the one
			//while actually working)
			turnOffTimerOrigin = SystemClock.elapsedRealtime();
			turnOffTimerSelectedMinutes = minutes;
		} else {
			turnOffTimerOrigin = 0;
			turnOffTimerSelectedMinutes = 0;
		}
		processTurnOffTimer();
	}

	public static int getTurnOffTimerMinutesLeft() {
		if (turnOffTimerOrigin <= 0)
			return turnOffTimerSelectedMinutes;
		final int m = turnOffTimerSelectedMinutes - (int)((SystemClock.elapsedRealtime() - turnOffTimerOrigin) / 60000L);
		return ((m <= 0) ? 1 : m);
	}

	public static void setAppNotInForeground(boolean appNotInForeground) {
		if (state > STATE_ALIVE)
			return;
		if (Player.appNotInForeground != appNotInForeground) {
			Player.appNotInForeground = appNotInForeground;
			if (idleTurnOffTimerSelectedMinutes > 0)
				processIdleTurnOffTimer();
		}
	}

	private static void processIdleTurnOffTimer() {
		if (state > STATE_ALIVE)
			return;
		if (idleTurnOffTimerSelectedMinutes <= 0) {
			idleTurnOffTimerSent = false;
			localHandler.removeMessages(MSG_IDLE_TURN_OFF_TIMER);
			return;
		}
		boolean wasPlayingBeforeOngoingCall = false, sendMessage = false;
		final boolean idle = (!playing && appNotInForeground);
		if (idle && telephonyManager != null) {
			//check for ongoing call
			try {
				if (telephonyManager.getCallState() != TelephonyManager.CALL_STATE_IDLE)
					wasPlayingBeforeOngoingCall = resumePlaybackAfterFocusGain;
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
		}
		if (!idle || wasPlayingBeforeOngoingCall) {
			if (idle) {
				//consider time spent in calls as active time, but keep checking,
				//because when the call ends, the audio focus could go to someone
				//else, rendering us actually idle!
				idleTurnOffTimerOrigin = SystemClock.elapsedRealtime();
				sendMessage = true;
			} else {
				idleTurnOffTimerOrigin = 0; //it's safe to reset the origin
			}
			if (turnOffTimerObserver != null)
				turnOffTimerObserver.onPlayerIdleTurnOffTimerTick();
		} else {
			if (idleTurnOffTimerOrigin > 0) {
				final int secondsLeft = (idleTurnOffTimerSelectedMinutes * 60) - (int)((SystemClock.elapsedRealtime() - idleTurnOffTimerOrigin) / 1000);
				if (turnOffTimerObserver != null)
					turnOffTimerObserver.onPlayerIdleTurnOffTimerTick();
				if (secondsLeft < 15) //less than half of our period
					stopService(false);
				else
					sendMessage = true;
			} else {
				idleTurnOffTimerOrigin = SystemClock.elapsedRealtime();
				sendMessage = true;
			}
		}
		if (sendMessage) {
			if (!idleTurnOffTimerSent) {
				idleTurnOffTimerSent = true;
				localHandler.sendEmptyMessageAtTime(MSG_IDLE_TURN_OFF_TIMER, SystemClock.uptimeMillis() + 30000);
			}
		} else {
			if (idleTurnOffTimerSent) {
				idleTurnOffTimerSent = false;
				localHandler.removeMessages(MSG_IDLE_TURN_OFF_TIMER);
			}
		}
	}

	public static void setIdleTurnOffTimer(int minutes) {
		if (state > STATE_ALIVE)
			return;
		idleTurnOffTimerOrigin = 0;
		idleTurnOffTimerSent = false;
		localHandler.removeMessages(MSG_IDLE_TURN_OFF_TIMER);
		if (minutes > 0) {
			if (minutes != 60 && minutes != 90 && minutes != 120)
				idleTurnOffTimerCustomMinutes = minutes;
			idleTurnOffTimerSelectedMinutes = minutes;
			processIdleTurnOffTimer();
		} else {
			idleTurnOffTimerSelectedMinutes = 0;
		}
	}

	public static int getIdleTurnOffTimerMinutesLeft() {
		if (idleTurnOffTimerOrigin <= 0)
			return idleTurnOffTimerSelectedMinutes;
		final int m = idleTurnOffTimerSelectedMinutes - (int)((SystemClock.elapsedRealtime() - idleTurnOffTimerOrigin) / 60000L);
		return ((m <= 0) ? 1 : m);
	}

	private static void _songListDeserialized(Object obj) {
		if (obj instanceof Throwable) {
			_updateState(false, (Throwable)obj);
		} else {
			song = (Song)obj;
			_updateState(false, null);
		}
	}

	private static Intent stickyBroadcast;
	private static ExternalReceiver externalReceiver;
	private static ComponentName mediaButtonEventReceiver;
	private static RemoteControlClient remoteControlClient;
	private static MediaSession mediaSession;
	private static MediaMetadata.Builder mediaSessionMetadataBuilder;
	private static PlaybackState.Builder mediaSessionPlaybackStateBuilder;
	private static Object mediaRouterCallback;

	private static TypedRawArrayList<PlayerDestroyedObserver> destroyedObservers;
	public static PlayerTurnOffTimerObserver turnOffTimerObserver;
	public static PlayerObserver observer;

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	private static void registerMediaRouter(Context context) {
		final MediaRouter mr = (MediaRouter)context.getSystemService(MEDIA_ROUTER_SERVICE);
		if (mr != null) {
			mediaRouterCallback = new MediaRouter.Callback() {
				@Override
				public void onRouteVolumeChanged(MediaRouter router, MediaRouter.RouteInfo info) {
				}
				@Override
				public void onRouteUnselected(MediaRouter router, int type, MediaRouter.RouteInfo info) {
				}
				@Override
				public void onRouteUngrouped(MediaRouter router, MediaRouter.RouteInfo info, MediaRouter.RouteGroup group) {
				}
				@Override
				public void onRouteSelected(MediaRouter router, int type, MediaRouter.RouteInfo info) {
				}
				@Override
				public void onRouteRemoved(MediaRouter router, MediaRouter.RouteInfo info) {
				}
				@Override
				public void onRouteGrouped(MediaRouter router, MediaRouter.RouteInfo info, MediaRouter.RouteGroup group, int index) {
				}
				@Override
				public void onRouteChanged(MediaRouter router, MediaRouter.RouteInfo info) {
					if (info.getPlaybackStream() == AudioManager.STREAM_MUSIC) {
						//this actually works... nonetheless, I was not able to detected
						//which is the audio sink used by this route.... :(
						volumeStreamMax = info.getVolumeMax();
						if (volumeStreamMax < 1)
							volumeStreamMax = 1;
						audioSinkChanged(false);
					}
				}
				@Override
				public void onRouteAdded(MediaRouter router, MediaRouter.RouteInfo info) {
				}
			};
			mr.addCallback(MediaRouter.ROUTE_TYPE_LIVE_AUDIO | MediaRouter.ROUTE_TYPE_USER, (MediaRouter.Callback)mediaRouterCallback);
		}
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	private static void unregisterMediaRouter(Context context) {
		final MediaRouter mr = (MediaRouter)context.getSystemService(MEDIA_ROUTER_SERVICE);
		if (mediaRouterCallback != null && mr != null)
			mr.removeCallback((MediaRouter.Callback)mediaRouterCallback);
		mediaRouterCallback = null;
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
	private static void registerRemoteControlClientCallbacks() {
		remoteControlClient.setOnGetPlaybackPositionListener(new RemoteControlClient.OnGetPlaybackPositionListener() {
			@Override
			public long onGetPlaybackPosition() {
				return getPosition();
			}
		});
		remoteControlClient.setPlaybackPositionUpdateListener(new RemoteControlClient.OnPlaybackPositionUpdateListener() {
			@Override
			public void onPlaybackPositionUpdate(long pos) {
				seekTo((int)pos);
			}
		});
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
	private static void unregisterRemoteControlClientCallbacks() {
		remoteControlClient.setOnGetPlaybackPositionListener(null);
		remoteControlClient.setPlaybackPositionUpdateListener(null);
	}

	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	private static void registerRemoteControlClient() {
		try {
			if (remoteControlClient == null) {
				final Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
				mediaButtonIntent.setComponent(mediaButtonEventReceiver);
				final PendingIntent mediaPendingIntent = PendingIntent.getBroadcast(thePlayer, 0, mediaButtonIntent, 0);
				remoteControlClient = new RemoteControlClient(mediaPendingIntent);
				remoteControlClient.setTransportControlFlags(RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS | RemoteControlClient.FLAG_KEY_MEDIA_NEXT | RemoteControlClient.FLAG_KEY_MEDIA_PLAY | RemoteControlClient.FLAG_KEY_MEDIA_PAUSE | RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE | RemoteControlClient.FLAG_KEY_MEDIA_STOP);
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
					registerRemoteControlClientCallbacks();
			}
			audioManager.registerRemoteControlClient(remoteControlClient);
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
	}

	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	private static void unregisterRemoteControlClient() {
		try {
			if (remoteControlClient != null) {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
					unregisterRemoteControlClientCallbacks();
				audioManager.unregisterRemoteControlClient(remoteControlClient);
				remoteControlClient = null;
			}
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private static void registerMediaSession() {
		try {
			if (mediaSession == null && thePlayer != null) {
				mediaSessionMetadataBuilder = new MediaMetadata.Builder();
				mediaSessionPlaybackStateBuilder = new PlaybackState.Builder();
				mediaSessionPlaybackStateBuilder.setActions(PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_STOP | PlaybackState.ACTION_SEEK_TO);
				mediaSession = new MediaSession(thePlayer, "FPlay");
				mediaSession.setCallback(new MediaSession.Callback() {
					@Override
					public boolean onMediaButtonEvent(@NonNull Intent mediaButtonIntent) {
						final Object o = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
						if (o == null || !(o instanceof KeyEvent))
							return false;
						final KeyEvent e = (KeyEvent)o;
						if (e.getAction() == KeyEvent.ACTION_DOWN)
							handleMediaButton(e.getKeyCode());
						return true;
					}

					@Override
					public void onPlay() {
						handleMediaButton(KeyEvent.KEYCODE_MEDIA_PLAY);
					}

					@Override
					public void onPause() {
						handleMediaButton(KeyEvent.KEYCODE_MEDIA_PAUSE);
					}

					@Override
					public void onSkipToNext() {
						handleMediaButton(KeyEvent.KEYCODE_MEDIA_NEXT);
					}

					@Override
					public void onSkipToPrevious() {
						handleMediaButton(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
					}

					@Override
					public void onStop() {
						handleMediaButton(KeyEvent.KEYCODE_MEDIA_STOP);
					}

					@Override
					public void onSeekTo(long pos) {
						seekTo((int)pos);
					}
				});
				mediaSession.setSessionActivity(intentActivityHost);
				mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
				mediaSession.setPlaybackState(mediaSessionPlaybackStateBuilder.setState(PlaybackState.STATE_STOPPED, 0, 1, SystemClock.elapsedRealtime()).build());
			}
			if (mediaSession != null)
				mediaSession.setActive(true);
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private static void unregisterMediaSession() {
		try {
			if (mediaSession != null) {
				mediaSession.setActive(false);
				mediaSession.setCallback(null);
				mediaSession.release();
				mediaSession = null;
			}
			mediaSessionMetadataBuilder = null;
			mediaSessionPlaybackStateBuilder = null;
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
	}

	public static void registerMediaButtonEventReceiver() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			registerMediaSession();
		} else {
			if (mediaButtonEventReceiver == null)
				mediaButtonEventReceiver = new ComponentName("br.com.siva.pinkmusic", "br.com.siva.pinkmusic.ExternalReceiver");
			if (audioManager != null) {
				audioManager.registerMediaButtonEventReceiver(mediaButtonEventReceiver);
				if (thePlayer != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
					registerRemoteControlClient();
			}
		}
	}

	public static void unregisterMediaButtonEventReceiver() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			unregisterMediaSession();
		} else {
			if (mediaButtonEventReceiver != null && audioManager != null) {
				if (remoteControlClient != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
					unregisterRemoteControlClient();
				audioManager.unregisterMediaButtonEventReceiver(mediaButtonEventReceiver);
			}
			mediaButtonEventReceiver = null;
		}
	}

	public static void becomingNoisy() {
		if (handler != null)
			handler.sendEmptyMessageAtTime(MSG_BECOMING_NOISY, SystemClock.uptimeMillis());
	}

	public static void audioSinkChanged(boolean wiredHeadsetJustPlugged) {
		if (handler != null)
			handler.sendMessageAtTime(Message.obtain(handler, MSG_AUDIO_SINK_CHANGED, wiredHeadsetJustPlugged ? 1 : 0, 0), SystemClock.uptimeMillis());
	}

	public static void setHeadsetHookAction(int pressCount, int action) {
		switch (pressCount) {
		case 1:
			headsetHookActions = (headsetHookActions & ~0xFF) | (action & 0xFF);
			break;
		case 2:
			headsetHookActions = (headsetHookActions & ~0xFF00) | ((action & 0xFF) << 8);
			break;
		case 3:
			headsetHookActions = (headsetHookActions & ~0xFF0000) | ((action & 0xFF) << 16);
			break;
		}
	}

	public static int getHeadsetHookAction(int pressCount) {
		return ((headsetHookActions >> ((pressCount == 1) ? 0 : ((pressCount == 2) ? 8 : 16))) & 0xFF);
	}

	private static void resetHeadsetHook() {
		headsetHookPressCount = 0;
		if (localHandler != null)
			localHandler.removeMessages(MSG_HEADSET_HOOK_TIMER);
	}

	private static void processHeadsetHookTimer() {
		if (state != STATE_ALIVE)
			return;
		final int action = getHeadsetHookAction(headsetHookPressCount);
		resetHeadsetHook();
		switch (action) {
		case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
			playPause();
			break;
		case KeyEvent.KEYCODE_MEDIA_NEXT:
			next();
			break;
		case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
			previous();
			break;
		}
	}

	public static boolean isMediaButton(int keyCode) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_MEDIA_PLAY:
		case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
		case KeyEvent.KEYCODE_MEDIA_PAUSE:
		case KeyEvent.KEYCODE_BREAK:
		case KeyEvent.KEYCODE_HEADSETHOOK:
		case KeyEvent.KEYCODE_MEDIA_STOP:
		case KeyEvent.KEYCODE_CALL:
		case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
		case KeyEvent.KEYCODE_MEDIA_NEXT:
		case KeyEvent.KEYCODE_MEDIA_REWIND:
		case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
		case KeyEvent.KEYCODE_VOLUME_DOWN:
		case KeyEvent.KEYCODE_VOLUME_UP:
			return true;
		}
		return false;
	}

	public static boolean handleMediaButton(int keyCode) {
		switch (keyCode) {
		//There are a few weird bluetooth headsets that despite having only one physical
		//play/pause button, will try to simulate individual PLAY and PAUSE events,
		//instead of sending the proper event PLAY_PAUSE... The problem is, they are not
		//always synchronized with the actual player state, and therefore, the user ends
		//up having to press that button twice for something to happen! :(
		case KeyEvent.KEYCODE_MEDIA_PLAY:
		case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
		case KeyEvent.KEYCODE_MEDIA_PAUSE:
		case KeyEvent.KEYCODE_BREAK:
			playPause();
			break;
		case KeyEvent.KEYCODE_HEADSETHOOK:
			if (localHandler != null) {
				localHandler.removeMessages(MSG_HEADSET_HOOK_TIMER);
				headsetHookPressCount++;
				if (headsetHookPressCount >= 3)
					processHeadsetHookTimer();
				else
					localHandler.sendEmptyMessageAtTime(MSG_HEADSET_HOOK_TIMER, SystemClock.uptimeMillis() + 500);
			}
			break;
		case KeyEvent.KEYCODE_MEDIA_STOP:
			pause();
			break;
		case KeyEvent.KEYCODE_CALL:
			if (!handleCallKey)
				return false;
			playPause();
			break;
		case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
		case KeyEvent.KEYCODE_MEDIA_NEXT:
			next();
			if (observer != null)
				observer.onPlayerMediaButtonNext();
			break;
		case KeyEvent.KEYCODE_MEDIA_REWIND:
		case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
			previous();
			if (observer != null)
				observer.onPlayerMediaButtonPrevious();
			break;
		case KeyEvent.KEYCODE_VOLUME_DOWN:
			if (volumeControlType == VOLUME_CONTROL_STREAM) {
				keyCode = decreaseVolume();
				if (observer != null)
					observer.onPlayerGlobalVolumeChanged(keyCode);
				break;
			}
			break;
		case KeyEvent.KEYCODE_VOLUME_UP:
			if (volumeControlType == VOLUME_CONTROL_STREAM) {
				keyCode = increaseVolume();
				if (observer != null)
					observer.onPlayerGlobalVolumeChanged(keyCode);
				break;
			}
			break;
		default:
			return false;
		}
		return true;
	}

	public static void listCleared() {
		if (state != STATE_ALIVE)
			return;
		handler.sendEmptyMessageAtTime(MSG_LIST_CLEARED, SystemClock.uptimeMillis());
	}

	public static void nextMayHaveChanged(Song possibleNextSong) {
		if (state != STATE_ALIVE)
			return;
		handler.sendMessageAtTime(Message.obtain(handler, MSG_NEXT_MAY_HAVE_CHANGED, possibleNextSong), SystemClock.uptimeMillis());
	}

	public static void addDestroyedObserver(PlayerDestroyedObserver observer) {
		if (destroyedObservers != null && !destroyedObservers.contains(observer))
			destroyedObservers.add(observer);
	}

	public static void removeDestroyedObserver(PlayerDestroyedObserver observer) {
		if (destroyedObservers != null)
			destroyedObservers.remove(observer);
	}

	private static void _checkAudioSink(boolean wiredHeadsetJustPlugged, boolean triggerNoisy, boolean reinitializeEffects) {
		if (audioManager == null)
			return;
		final int oldAudioSink = audioSink;
		//let the guessing begin!!! really, it is NOT possible to rely solely on
		//these AudioManager.isXXX() methods, neither on MediaRouter...
		//I would really be happy if things were as easy as the doc says... :(
		//https://developer.android.com/training/managing-audio/audio-output.html
		audioSink = 0;
		try {
			//isSpeakerphoneOn() has not actually returned true on any devices
			//I have tested so far, anyway.... leaving this here won't hurt...
			if (audioManager.isSpeakerphoneOn())
				audioSink = AUDIO_SINK_DEVICE;
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
		try {
			//apparently, devices tend to use the wired headset over bluetooth headsets
			if (audioSink == 0 && (wiredHeadsetJustPlugged || audioManager.isWiredHeadsetOn()))
				audioSink = AUDIO_SINK_WIRE;
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
		try {
			//this whole A2dp thing is still not enough, as isA2dpPlaying()
			//will return false if there is nothing playing, even in scenarios
			//where A2dp will certainly be used for playback later...
			/*if (audioManager.isBluetoothA2dpOn()) {
				//the device being on is not enough! we must be sure
				//if it is actually being used for transmission...
				if (thePlayer == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
					if (audioSink == 0)
						audioSink = AUDIO_SINK_BT;
				} else {
					if (audioSink == 0 || isA2dpPlaying())
						audioSink = AUDIO_SINK_BT;
				}
			}*/
			if (audioSink == 0 && audioManager.isBluetoothA2dpOn())
				audioSink = AUDIO_SINK_BT;
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
		if (audioSink == 0)
			audioSink = AUDIO_SINK_DEVICE;
		if (oldAudioSink != audioSink && oldAudioSink != 0) {
			switch (audioSink) {
			case AUDIO_SINK_WIRE:
				if (!playing && playWhenHeadsetPlugged) {
					if (!hasFocus) {
						try {
							if (telephonyManager != null && telephonyManager.getCallState() != TelephonyManager.CALL_STATE_IDLE)
								break;
						} catch (Throwable ex) {
							ex.printStackTrace();
						}
					}
					if (reinitializeEffects && audioSinkUsedInEffects != audioSink && player != null)
						_reinitializeEffects();
					_playPause();
				}
				break;
			case AUDIO_SINK_DEVICE:
				if (triggerNoisy) {
					//this cleanup must be done, as sometimes, when changing between two output types,
					//the effects are lost...
					if (playing)
						_playPause();
					_fullCleanup();
				}
				break;
			}
		}
		if (reinitializeEffects && audioSinkUsedInEffects != audioSink && player != null)
			_reinitializeEffects();
		//I am calling the observer even if no changes have been detected, because
		//I myself don't trust this code will correctly work as expected on every device....
		if (localHandler != null)
			localHandler.sendMessageAtTime(Message.obtain(localHandler, MSG_AUDIO_SINK_CHANGED, audioSink, 0), SystemClock.uptimeMillis());
	}

	private static void _nextMayHaveChanged(Song possibleNextSong) {
		if (nextSong != possibleNextSong && nextPreparationEnabled) {
			nextSong = possibleNextSong;
			if (playerState == PLAYER_STATE_LOADED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
				_clearNextPlayer();
			if (nextPlayer != null)
				nextPlayer.reset();
			if (playerState == PLAYER_STATE_LOADED)
				_scheduleNextPlayerForPreparation();
		}
	}

	private static Song stateLastSong;
	private static boolean stateLastPlaying, stateLastPreparing;

	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	private static void broadcastStateChangeToRemoteControl(String title, boolean preparing, boolean titleOrSongHaveChanged) {
		try {
			if (localSong == null) {
				remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_STOPPED);
			} else {
				remoteControlClient.setPlaybackState(preparing ? RemoteControlClient.PLAYSTATE_BUFFERING : (playing ? RemoteControlClient.PLAYSTATE_PLAYING : RemoteControlClient.PLAYSTATE_PAUSED));
				if (titleOrSongHaveChanged) {
					final RemoteControlClient.MetadataEditor ed = remoteControlClient.editMetadata(true);
					ed.putString(MediaMetadataRetriever.METADATA_KEY_TITLE, title);
					ed.putString(MediaMetadataRetriever.METADATA_KEY_ARTIST, localSong.artist);
					ed.putString(MediaMetadataRetriever.METADATA_KEY_ALBUM, localSong.album);
					ed.putLong(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER, localSong.track);
					ed.putLong(MediaMetadataRetriever.METADATA_KEY_DURATION, localSong.lengthMS);
					//Oh!!!! METADATA_KEY_YEAR is only handled in API 19+ !!! :(
					//http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/4.4_r1/android/media/MediaMetadataEditor.java#MediaMetadataEditor.0METADATA_KEYS_TYPE
					//http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/4.1.2_r1/android/media/RemoteControlClient.java#RemoteControlClient.0METADATA_KEYS_TYPE_LONG
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
						ed.putLong(MediaMetadataRetriever.METADATA_KEY_YEAR, localSong.year);
					ed.apply();
				}
			}
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private static void broadcastStateChangeToMediaSession(String title, boolean preparing, boolean titleOrSongHaveChanged) {
		try {
			if (localSong == null) {
				mediaSession.setPlaybackState(mediaSessionPlaybackStateBuilder.setState(PlaybackState.STATE_STOPPED, 0, 1, SystemClock.elapsedRealtime()).build());
			} else {
				mediaSession.setPlaybackState(mediaSessionPlaybackStateBuilder.setState(preparing ? PlaybackState.STATE_BUFFERING : (playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED), getPosition(), 1, SystemClock.elapsedRealtime()).build());
				if (titleOrSongHaveChanged) {
					mediaSessionMetadataBuilder.putString(MediaMetadata.METADATA_KEY_TITLE, title);
					mediaSessionMetadataBuilder.putString(MediaMetadata.METADATA_KEY_ARTIST, localSong.artist);
					mediaSessionMetadataBuilder.putString(MediaMetadata.METADATA_KEY_ALBUM, localSong.album);
					mediaSessionMetadataBuilder.putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, localSong.track);
					mediaSessionMetadataBuilder.putLong(MediaMetadata.METADATA_KEY_DURATION, localSong.lengthMS);
					mediaSessionMetadataBuilder.putLong(MediaMetadata.METADATA_KEY_YEAR, localSong.year);
					mediaSession.setMetadata(mediaSessionMetadataBuilder.build());
				}
			}
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
	}

	private static void broadcastStateChange(String title, boolean preparing, boolean titleOrSongHaveChanged) {
		notificationManager.notify(1, getNotification());
		WidgetMain.updateWidgets(thePlayer);
		//
		//perhaps, one day we should implement RemoteControlClient for better Bluetooth support...?
		//http://developer.android.com/reference/android/media/RemoteControlClient.html
		//https://android.googlesource.com/platform/packages/apps/Music/+/master/src/com/android/music/MediaPlaybackService.java
		//
		//http://stackoverflow.com/questions/15527614/send-track-informations-via-a2dp-avrcp
		//http://stackoverflow.com/questions/14536597/how-does-the-android-lockscreen-get-playing-song
		//http://stackoverflow.com/questions/10510292/how-to-get-current-music-track-info
		//
		//https://android.googlesource.com/platform/packages/apps/Bluetooth/+/android-4.3_r0.9.1/src/com/android/bluetooth/a2dp/Avrcp.java
		//
		if (localSong == null) {
			stickyBroadcast.setAction("com.android.music.playbackcomplete");
			stickyBroadcast.removeExtra("id");
			stickyBroadcast.removeExtra("songid");
			stickyBroadcast.removeExtra("track");
			stickyBroadcast.removeExtra("artist");
			stickyBroadcast.removeExtra("album");
			stickyBroadcast.removeExtra("duration");
			//stickyBroadcast.removeExtra("position");
			stickyBroadcast.removeExtra("playing");
		} else {
			//apparently, a few 4.3 devices have an issue with com.android.music.metachanged....
			//stickyBroadcast.setAction(playbackHasChanged ? "com.android.music.playstatechanged" : "com.android.music.metachanged");
			stickyBroadcast.setAction("com.android.music.playstatechanged");
			stickyBroadcast.putExtra("id", localSong.id);
			stickyBroadcast.putExtra("songid", localSong.id);
			stickyBroadcast.putExtra("track", title);
			stickyBroadcast.putExtra("artist", localSong.artist);
			stickyBroadcast.putExtra("album", localSong.album);
			stickyBroadcast.putExtra("duration", (long)localSong.lengthMS);
			//stickyBroadcast.putExtra("position", (long)0);
			stickyBroadcast.putExtra("playing", playing);
		}
		//thePlayer.sendBroadcast(stickyBroadcast);
		//maybe check if api >= 23, and if so, use sendBroadcast instead.....???
		thePlayer.sendStickyBroadcast(stickyBroadcast);
		if (remoteControlClient != null)
			broadcastStateChangeToRemoteControl(title, preparing, titleOrSongHaveChanged);
		if (mediaSession != null)
			broadcastStateChangeToMediaSession(title, preparing, titleOrSongHaveChanged);
	}

	private static void updateState(int arg1, Object[] objs) {
		localPlaying = ((arg1 & 0x04) != 0);
		localPlayerState = (arg1 & 0x03);
		localSong = (Song)objs[0];
		objs[0] = null;
		localPlayer = (MediaPlayer)objs[1];
		objs[1] = null;
		if (songs.okToTurnOffAfterReachingTheEnd) {
			songs.okToTurnOffAfterReachingTheEnd = false;
			//turn off if requested
			if (turnOffWhenPlaylistEnds)
				localHandler.sendEmptyMessageAtTime(MSG_TURN_OFF_NOW, SystemClock.uptimeMillis() + 100);
		}
		final boolean songHasChanged = ((arg1 & 0x08) != 0);
		//final boolean playbackHasChanged = ((arg1 & 0x10) != 0);
		final boolean preparing = ((arg1 & 0x20) != 0);
		final boolean preparingHasChanged = ((arg1 & 0x40) != 0);
		final String title = ((localSong == null) ? null : getCurrentTitle(thePlayer, preparing));
		broadcastStateChange(title, preparing, songHasChanged | preparingHasChanged);
		if (idleTurnOffTimerSelectedMinutes > 0)
			processIdleTurnOffTimer();
		if (bluetoothVisualizerController != null)
			updateBluetoothVisualizer(songHasChanged);
		Throwable ex = null;
		if (songHasChanged && announceCurrentSong && UI.accessibilityManager != null && UI.accessibilityManager.isEnabled() && state == STATE_ALIVE)
			UI.announceAccessibilityText(title);
		if (objs[2] != null) {
			ex = (Throwable)objs[2];
			objs[2] = null;
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && (ex instanceof PermissionDeniedException) && observer != null && (observer instanceof ClientActivity))
				((ClientActivity)observer).getHostActivity().requestReadStoragePermission();
			final String msg = ex.getMessage();
			if (ex instanceof IllegalStateException) {
				UI.toast(thePlayer, R.string.error_state);
			} else if (ex instanceof UnsupportedFormatException) {
				UI.toast(thePlayer, R.string.error_unsupported_format);
			} else if (ex instanceof FileNotFoundException) {
				UI.toast(thePlayer, (localSong != null && localSong.isHttp) ?
					(!isConnectedToTheInternet() ? R.string.error_connection : R.string.error_server_not_found) :
						R.string.error_file_not_found);
			} else if (ex instanceof TimeoutException) {
				UI.toast(thePlayer, R.string.error_timeout);
			} else if (ex instanceof MediaServerDiedException) {
				UI.toast(thePlayer, R.string.error_server_died);
			} else if (ex instanceof SecurityException) {
				UI.toast(thePlayer, R.string.error_security);
			} else if (ex instanceof IOException) {
				int err = R.string.error_io;
				if (localSong != null) {
					if (localSong.isHttp) {
						err = (!isConnectedToTheInternet() ? R.string.error_connection : R.string.error_io);
					} else {
						try {
							if (!(new File(localSong.path)).exists())
								err = R.string.error_file_not_found;
						} catch (Throwable ex2) {
							err = R.string.error_file_not_found;
						}
					}
				}
				UI.toast(thePlayer, err);
			} else if (msg == null || msg.length() == 0) {
				UI.toast(thePlayer, R.string.error_playback);
			} else {
				final StringBuilder sb = new StringBuilder(thePlayer.getText(R.string.error_msg));
				sb.append(' ');
				sb.append(msg);
				UI.toast(thePlayer, sb);
			}
		}
		if (observer != null)
			observer.onPlayerChanged(localSong, songHasChanged, preparingHasChanged, ex);
	}

	private static void _updateState(boolean metaHasChanged, Throwable ex) {
		if (localHandler != null) {
			final boolean songHasChanged = (metaHasChanged || (stateLastSong != song));
			final boolean playbackHasChanged = (stateLastPlaying != playing);
			final boolean preparing = (playerState == PLAYER_STATE_PREPARING || playerBuffering);
			final boolean preparingHasChanged = (stateLastPreparing != preparing);
			if (!songHasChanged && !playbackHasChanged && !preparingHasChanged && ex == null)
				return;
			stateLastSong = song;
			stateLastPlaying = playing;
			stateLastPreparing = preparing;
			localHandler.sendMessageAtTime(Message.obtain(localHandler, MSG_UPDATE_STATE, playerState | (playing ? 0x04 : 0) | (songHasChanged ? 0x08 : 0) | (playbackHasChanged ? 0x10 : 0) | (preparing ? 0x20 : 0) | (preparingHasChanged ? 0x40 : 0), 0, new Object[] { song, player, ex }), SystemClock.uptimeMillis());
		}
	}
}
