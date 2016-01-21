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
package br.com.siva.pinkmusic;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Build;
import android.text.InputType;
import android.text.TextUtils.TruncateAt;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.RelativeLayout;
import android.widget.TextView;

import br.com.siva.pinkmusic.activity.ActivityHost;
import br.com.siva.pinkmusic.activity.ClientActivity;
import br.com.siva.pinkmusic.activity.MainHandler;
import br.com.siva.pinkmusic.list.Song;
import br.com.siva.pinkmusic.playback.Player;
import br.com.siva.pinkmusic.ui.BackgroundActivityMonitor;
import br.com.siva.pinkmusic.ui.BgButton;
import br.com.siva.pinkmusic.ui.BgListView;
import br.com.siva.pinkmusic.ui.ColorPickerView;
import br.com.siva.pinkmusic.ui.CustomContextMenu;
import br.com.siva.pinkmusic.ui.ObservableScrollView;
import br.com.siva.pinkmusic.ui.SettingView;
import br.com.siva.pinkmusic.ui.UI;
import br.com.siva.pinkmusic.ui.drawable.ColorDrawable;
import br.com.siva.pinkmusic.ui.drawable.TextIconDrawable;
import br.com.siva.pinkmusic.util.BluetoothConnectionManager;
import br.com.siva.pinkmusic.util.ColorUtils;
import br.com.siva.pinkmusic.visualizer.BluetoothVisualizerControllerJni;

public final class ActivitySettings extends ClientActivity implements Player.PlayerTurnOffTimerObserver, View.OnClickListener, DialogInterface.OnClickListener, ColorPickerView.OnColorPickerViewListener, ObservableScrollView.OnScrollListener, Runnable {
	private static final double MIN_THRESHOLD = 1.5; //waaaaaaaaaayyyyyyyy below W3C recommendations, so no one should complain about the app being "boring"
	private final boolean colorMode, bluetoothMode;
	private boolean changed, checkingReturn, configsChanged, lblTitleOk, startTransmissionOnConnection;
	private BgButton btnGoBack, btnBluetooth, btnAbout;
	private EditText txtCustomMinutes;
	private ObservableScrollView list;
	private TextView lblTitle;
	private RelativeLayout panelControls;
	private LinearLayout panelSettings;
	private SettingView firstViewAdded, lastViewAdded, optLoadCurrentTheme, optUseAlternateTypeface, optAutoTurnOff, optAutoIdleTurnOff, optAutoTurnOffPlaylist, optKeepScreenOn, optTheme, optFlat, optBorders, optExpandSeekBar, optVolumeControlType, optDoNotAttenuateVolume, opt3D, optIsDividerVisible, optIsVerticalMarginLarge, optExtraSpacing, optPlaceTitleAtTheBottom, optForcedLocale, optPlacePlaylistToTheRight, optScrollBarToTheLeft, optScrollBarSongList, optScrollBarBrowser, optWidgetTransparentBg, optWidgetTextColor, optWidgetIconColor, optHandleCallKey, optHeadsetHook1, optHeadsetHook2, optHeadsetHook3, optPlayWhenHeadsetPlugged, optBlockBackKey, optBackKeyAlwaysReturnsToPlayerWhenBrowsing, optWrapAroundList, optDoubleClickMode, optMarqueeTitle, optPrepareNext, optClearListWhenPlayingFolders, optGoBackWhenPlayingFolders, optExtraInfoMode, optForceOrientation, optTransition, optAnimations, optNotFullscreen, optFadeInFocus, optFadeInPause, optFadeInOther, optBtMessage, optBtConnect, optBtStart, optBtFramesToSkip, optBtSize, optBtVUMeter, optBtSpeed, optAnnounceCurrentSong, optFollowCurrentSong, optBytesBeforeDecoding, optSecondsBeforePlayback, lastMenuView;
	private SettingView[] colorViews;
	private int lastColorView, currentHeader, btMessageText, btErrorMessage, btConnectText, btStartText;
	private TextView[] headers;

	public ActivitySettings(boolean colorMode, boolean bluetoothMode) {
		this.colorMode = colorMode;
		this.bluetoothMode = bluetoothMode;
	}

	@Override
	public CharSequence getTitle() {
		return (bluetoothMode ? "Bluetooth + Arduino" : getText(colorMode ? R.string.custom_color_theme : R.string.settings));
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
		if (view == optAutoTurnOff || view == optAutoIdleTurnOff) {
			lastMenuView = (SettingView)view;
			UI.prepare(menu);
			final int s = ((view == optAutoTurnOff) ? Player.turnOffTimerSelectedMinutes : Player.idleTurnOffTimerSelectedMinutes);
			final int c = ((view == optAutoTurnOff) ? Player.turnOffTimerCustomMinutes : Player.idleTurnOffTimerCustomMinutes);
			menu.add(0, 0, 0, R.string.never)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable(s <= 0 ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			UI.separator(menu, 0, 1);
			menu.add(1, c, 0, getMinuteString(c))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable(s == c ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			UI.separator(menu, 1, 1);
			menu.add(2, 60, 0, getMinuteString(60))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable(s == 60 ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			menu.add(2, 90, 1, getMinuteString(90))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable(s == 90 ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			menu.add(2, 120, 2, getMinuteString(120))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable(s == 120 ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			UI.separator(menu, 2, 4);
			menu.add(3, -2, 0, R.string.custom)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable(s != c && s != 60 && s != 90 && s != 120 && s > 0 ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		} else if (view == optForcedLocale) {
			final Context ctx = getApplication();
			final int o = UI.forcedLocale;
			lastMenuView = optForcedLocale;
			UI.prepare(menu);
			menu.add(0, UI.LOCALE_NONE, 0, R.string.standard_language)
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable((o == UI.LOCALE_NONE) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		UI.separator(menu, 0, 1);
  			menu.add(1, UI.LOCALE_US, 1, UI.getLocaleDescriptionFromCode(ctx, UI.LOCALE_US))
			.setOnMenuItemClickListener(this)
			.setIcon(new TextIconDrawable((o == UI.LOCALE_US) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			menu.add(1, UI.LOCALE_TAL, 2, UI.getLocaleDescriptionFromCode(ctx, UI.LOCALE_TAL))
			.setOnMenuItemClickListener(this)
		   .setIcon(new TextIconDrawable((o == UI.LOCALE_TAL) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		
		} else if (view == optTheme) {
			final Context ctx = getApplication();
			final int o = UI.theme;
			lastMenuView = optTheme;
			UI.prepare(menu);
			menu.add(0, UI.THEME_CUSTOM, 0, UI.getThemeString(ctx, UI.THEME_CUSTOM))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.THEME_CUSTOM) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			UI.separator(menu, 0, 1);
			menu.add(1, UI.THEME_pinkmusic, 0, UI.getThemeString(ctx, UI.THEME_pinkmusic))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.THEME_pinkmusic) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			menu.add(1, UI.THEME_CREAMY, 1, UI.getThemeString(ctx, UI.THEME_CREAMY))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.THEME_CREAMY) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			menu.add(1, UI.THEME_DARK_LIGHT, 2, UI.getThemeString(ctx, UI.THEME_DARK_LIGHT))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.THEME_DARK_LIGHT) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			menu.add(1, UI.THEME_BLUE_ORANGE, 3, UI.getThemeString(ctx, UI.THEME_BLUE_ORANGE))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.THEME_BLUE_ORANGE) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			menu.add(1, UI.THEME_BLUE, 4, UI.getThemeString(ctx, UI.THEME_BLUE))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.THEME_BLUE) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			menu.add(1, UI.THEME_ORANGE, 5, UI.getThemeString(ctx, UI.THEME_ORANGE))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.THEME_ORANGE) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			menu.add(1, UI.THEME_LIGHT, 6, UI.getThemeString(ctx, UI.THEME_LIGHT))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((o == UI.THEME_LIGHT) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		} else if (view == optVolumeControlType) {
			lastMenuView = optVolumeControlType;
			UI.prepare(menu);
			menu.add(0, Player.VOLUME_CONTROL_STREAM, 0, R.string.volume_control_type_integrated)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((Player.volumeControlType == Player.VOLUME_CONTROL_STREAM) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			UI.separator(menu, 0, 1);
			menu.add(1, Player.VOLUME_CONTROL_DB, 0, R.string.volume_control_type_decibels)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((Player.volumeControlType == Player.VOLUME_CONTROL_DB) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			menu.add(1, Player.VOLUME_CONTROL_PERCENT, 1, R.string.volume_control_type_percentage)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((Player.volumeControlType == Player.VOLUME_CONTROL_PERCENT) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			UI.separator(menu, 1, 2);
			menu.add(2, Player.VOLUME_CONTROL_NONE, 0, R.string.noneM)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((Player.volumeControlType == Player.VOLUME_CONTROL_NONE) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		} else if (view == optExtraInfoMode) {
			lastMenuView = optExtraInfoMode;
			UI.prepare(menu);
			final int o = Song.extraInfoMode;
			for (int i = Song.EXTRA_ARTIST; i <= Song.EXTRA_ARTIST_ALBUM; i++) {
				menu.add(0, i, i, getExtraInfoModeString(i))
					.setOnMenuItemClickListener(this)
					.setIcon(new TextIconDrawable((o == i) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			}
		} else if (view == optForceOrientation) {
			lastMenuView = optForceOrientation;
			UI.prepare(menu);
			final int o = UI.forcedOrientation;
			menu.add(0, 0, 0, R.string.none)
					.setOnMenuItemClickListener(this)
					.setIcon(new TextIconDrawable((o == 0) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			UI.separator(menu, 0, 1);
			menu.add(1, -1, 0, R.string.landscape)
					.setOnMenuItemClickListener(this)
					.setIcon(new TextIconDrawable((o < 0) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			menu.add(1, 1, 1, R.string.portrait)
					.setOnMenuItemClickListener(this)
					.setIcon(new TextIconDrawable((o > 0) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		} else if (view == optTransition) {
			lastMenuView = optTransition;
			UI.prepare(menu);
			final Context ctx = getApplication();
			final int o = UI.transition;
			menu.add(0, UI.TRANSITION_NONE, 0, UI.getTransitionString(ctx, UI.TRANSITION_NONE))
					.setOnMenuItemClickListener(this)
					.setIcon(new TextIconDrawable((o == UI.TRANSITION_NONE) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			UI.separator(menu, 0, 1);
			menu.add(1, UI.TRANSITION_FADE, 0, UI.getTransitionString(ctx, UI.TRANSITION_FADE))
					.setOnMenuItemClickListener(this)
					.setIcon(new TextIconDrawable((o == UI.TRANSITION_FADE) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			menu.add(1, UI.TRANSITION_ZOOM, 1, UI.getTransitionString(ctx, UI.TRANSITION_ZOOM))
					.setOnMenuItemClickListener(this)
					.setIcon(new TextIconDrawable((o == UI.TRANSITION_ZOOM) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			menu.add(2, UI.TRANSITION_DISSOLVE, 2, UI.getTransitionString(ctx, UI.TRANSITION_DISSOLVE))
					.setOnMenuItemClickListener(this)
					.setIcon(new TextIconDrawable((o == UI.TRANSITION_DISSOLVE) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		} else if (view == optScrollBarSongList || view == optScrollBarBrowser) {
			lastMenuView = (SettingView)view;
			UI.prepare(menu);
			final int d = ((view == optScrollBarSongList) ? UI.songListScrollBarType : UI.browserScrollBarType);
			if (view == optScrollBarBrowser)
				menu.add(0, BgListView.SCROLLBAR_INDEXED, 0, R.string.indexed_if_possible)
					.setOnMenuItemClickListener(this)
					.setIcon(new TextIconDrawable((d == BgListView.SCROLLBAR_INDEXED) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			menu.add(0, BgListView.SCROLLBAR_LARGE, 1, R.string.large)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((d == BgListView.SCROLLBAR_LARGE) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			menu.add(0, BgListView.SCROLLBAR_SYSTEM, 2, R.string.system_integrated)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((d != BgListView.SCROLLBAR_INDEXED && d != BgListView.SCROLLBAR_LARGE && d != BgListView.SCROLLBAR_NONE) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			UI.separator(menu, 0, 3);
			menu.add(1, BgListView.SCROLLBAR_NONE, 0, R.string.none)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((d == BgListView.SCROLLBAR_NONE) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		} else if (view == optFadeInFocus || view == optFadeInPause || view == optFadeInOther) {
			lastMenuView = (SettingView)view;
			UI.prepare(menu);
			final int d = ((view == optFadeInFocus) ? Player.fadeInIncrementOnFocus : ((view == optFadeInPause) ? Player.fadeInIncrementOnPause : Player.fadeInIncrementOnOther));
			menu.add(0, 0, 0, R.string.none)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((d <= 0) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			UI.separator(menu, 0, 1);
			menu.add(1, 250, 0, R.string.dshort)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((d >= 250) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			menu.add(1, 125, 1, R.string.dmedium)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable(((d >= 125) && (d < 250)) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			menu.add(1, 62, 2, R.string.dlong)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable(((d > 0) && (d < 125)) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		} else if (view == optHeadsetHook1 || view == optHeadsetHook2 || view == optHeadsetHook3) {
			lastMenuView = (SettingView)view;
			UI.prepare(menu);
			final int pressCount = ((view == optHeadsetHook1) ? 1 : ((view == optHeadsetHook2) ? 2 : 3));
			final int action = Player.getHeadsetHookAction(pressCount);
			menu.add(0, 0, 0, R.string.nothing)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((action == 0) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			UI.separator(menu, 0, 1);
			menu.add(1, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0, getText(R.string.play) + "/" + getText(R.string.pause))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((action == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			menu.add(1, KeyEvent.KEYCODE_MEDIA_NEXT, 1, R.string.next)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((action == KeyEvent.KEYCODE_MEDIA_NEXT) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			menu.add(1, KeyEvent.KEYCODE_MEDIA_PREVIOUS, 2, R.string.previous)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((action == KeyEvent.KEYCODE_MEDIA_PREVIOUS) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		} else if (view == optBtSize) {
			lastMenuView = optBtSize;
			UI.prepare(menu);
			final int size = Player.getBluetoothVisualizerSize();
			for (int i = 0; i <= 6; i++) {
				menu.add(0, i, i, Integer.toString(1 << (2 + i)))
					.setOnMenuItemClickListener(this)
					.setIcon(new TextIconDrawable((size == i) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			}
		} else if (view == optBtSpeed) {
			lastMenuView = optBtSpeed;
			UI.prepare(menu);
			final int speed = Player.getBluetoothVisualizerSpeed();
			for (int i = 0; i <= 2; i++) {
				menu.add(0, i, i, Integer.toString(3 - i))
					.setOnMenuItemClickListener(this)
					.setIcon(new TextIconDrawable((speed == i) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			}
		} else if (view == optBtFramesToSkip) {
			lastMenuView = optBtFramesToSkip;
			UI.prepare(menu);
			final int framesToSkip = Player.getBluetoothVisualizerFramesToSkipIndex();
			for (int i = 0; i <= 11; i++) {
				menu.add(0, i, i, Integer.toString(Player.getBluetoothVisualizerFramesPerSecond(i)))
					.setOnMenuItemClickListener(this)
					.setIcon(new TextIconDrawable((framesToSkip == i) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			}
		} else if (view == optBytesBeforeDecoding) {
			lastMenuView = optBytesBeforeDecoding;
			UI.prepare(menu);
			final int bytesBeforeDecodingIndex = Player.getBytesBeforeDecodingIndex();
			for (int i = 0; i <= 7; i++) {
				menu.add(0, i, i, getBytesBeforeDecodingString(i))
					.setOnMenuItemClickListener(this)
					.setIcon(new TextIconDrawable((bytesBeforeDecodingIndex == i) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			}
		} else if (view == optSecondsBeforePlayback) {
			lastMenuView = optSecondsBeforePlayback;
			UI.prepare(menu);
			final int secondsBeforePlaybackIndex = Player.getSecondsBeforePlaybackIndex();
			for (int i = 0; i <= 4; i++) {
				menu.add(0, i, i, getSecondsBeforePlaybackString(i))
					.setOnMenuItemClickListener(this)
					.setIcon(new TextIconDrawable((secondsBeforePlaybackIndex == i) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			}
		}
	}
	
	@Override
	public boolean onMenuItemClick(MenuItem item) {
		//NullPointerException reported at Play Store.... :/
		final ActivityHost ctx = getHostActivity();
		final Application app = (ctx == null ? null : ctx.getApplication());
		if (item == null || lastMenuView == null || ctx == null || app == null)
			return true;
		if (lastMenuView == optAutoTurnOff || lastMenuView == optAutoIdleTurnOff) {
			if (item.getItemId() >= 0) {
				if (lastMenuView == optAutoTurnOff) {
					Player.setTurnOffTimer(item.getItemId());
					optAutoTurnOff.setSecondaryText(getAutoTurnOffString());
				} else {
					Player.setIdleTurnOffTimer(item.getItemId());
					optAutoIdleTurnOff.setSecondaryText(getAutoIdleTurnOffString());
				}
			} else {
				final LinearLayout l = (LinearLayout)UI.createDialogView(ctx, null);
				
				TextView lbl = new TextView(ctx);
				lbl.setText(R.string.msg_turn_off);
				lbl.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI.dialogTextSize);
				l.addView(lbl);
				
				txtCustomMinutes = new EditText(ctx);
				txtCustomMinutes.setContentDescription(ctx.getText(R.string.msg_turn_off));
				txtCustomMinutes.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI.dialogTextSize);
				txtCustomMinutes.setInputType(InputType.TYPE_CLASS_NUMBER);
				txtCustomMinutes.setSingleLine();
				final LayoutParams p = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
				p.topMargin = UI.dialogMargin;
				txtCustomMinutes.setLayoutParams(p);
				txtCustomMinutes.setText(Integer.toString((lastMenuView == optAutoTurnOff) ? Player.turnOffTimerCustomMinutes : Player.idleTurnOffTimerCustomMinutes));
				l.addView(txtCustomMinutes);
				
				UI.prepareDialogAndShow((new AlertDialog.Builder(ctx))
				.setTitle(R.string.msg_turn_off_title)
				.setView(l)
				.setPositiveButton(R.string.ok, this)
				.setNegativeButton(R.string.cancel, this)
				.create());
			}
		} else if (lastMenuView == optForcedLocale) {
			if (item.getItemId() != UI.forcedLocale) {
				UI.setForcedLocale(app, ctx, item.getItemId(), true);
				WidgetMain.updateWidgets(app);
				onCleanupLayout();
				onCreateLayout(false);
				System.gc();
			}
		} else if (lastMenuView == optTheme) {
			if (item.getItemId() == UI.THEME_CUSTOM) {
				startActivity(new ActivitySettings(true, false), 0, null, false);
			} else {
				UI.setTheme(ctx, item.getItemId());
				onCleanupLayout();
				onCreateLayout(false);
				System.gc();
			}
		} else if (lastMenuView == optVolumeControlType) {
			Player.setVolumeControlType(item.getItemId());
			optVolumeControlType.setSecondaryText(getVolumeString());
		} else if (lastMenuView == optExtraInfoMode) {
			Song.extraInfoMode = item.getItemId();
			optExtraInfoMode.setSecondaryText(getExtraInfoModeString(Song.extraInfoMode));
			Player.songs.updateExtraInfo();
			WidgetMain.updateWidgets(app);
		} else if (lastMenuView == optForceOrientation) {
			UI.forcedOrientation = item.getItemId();
			ctx.setRequestedOrientation((UI.forcedOrientation == 0) ? ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED : ((UI.forcedOrientation < 0) ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
			optForceOrientation.setSecondaryText(getOrientationString());
		} else if (lastMenuView == optTransition) {
			UI.setTransition(item.getItemId());
			optTransition.setSecondaryText(UI.getTransitionString(app, item.getItemId()));
		} else if (lastMenuView == optScrollBarSongList) {
			UI.songListScrollBarType = item.getItemId();
			optScrollBarSongList.setSecondaryText(getScrollBarString(item.getItemId()));
		} else if (lastMenuView == optScrollBarBrowser) {
			UI.browserScrollBarType = item.getItemId();
			optScrollBarBrowser.setSecondaryText(getScrollBarString(item.getItemId()));
			list.updateVerticalScrollbar();
		} else if (lastMenuView == optFadeInFocus) {
			Player.fadeInIncrementOnFocus = item.getItemId();
			optFadeInFocus.setSecondaryText(getFadeInString(item.getItemId()));
		} else if (lastMenuView == optFadeInPause) {
			Player.fadeInIncrementOnPause = item.getItemId();
			optFadeInPause.setSecondaryText(getFadeInString(item.getItemId()));
		} else if (lastMenuView == optFadeInOther) {
			Player.fadeInIncrementOnOther = item.getItemId();
			optFadeInOther.setSecondaryText(getFadeInString(item.getItemId()));
		} else if (lastMenuView == optHeadsetHook1 || lastMenuView == optHeadsetHook2 || lastMenuView == optHeadsetHook3) {
			final int pressCount = ((lastMenuView == optHeadsetHook1) ? 1 : ((lastMenuView == optHeadsetHook2) ? 2 : 3));
			Player.setHeadsetHookAction(pressCount, item.getItemId());
			lastMenuView.setSecondaryText(getHeadsetHookString(pressCount));
		} else if (lastMenuView == optBtSize) {
			Player.setBluetoothVisualizerSize(item.getItemId());
			optBtSize.setSecondaryText(getSizeString());
			if (Player.bluetoothVisualizerController != null)
				((BluetoothVisualizerControllerJni)Player.bluetoothVisualizerController).syncSize();
		} else if (lastMenuView == optBtSpeed) {
			Player.setBluetoothVisualizerSpeed(item.getItemId());
			optBtSpeed.setSecondaryText(getSpeedString());
			if (Player.bluetoothVisualizerController != null)
				((BluetoothVisualizerControllerJni)Player.bluetoothVisualizerController).syncSpeed();
		} else if (lastMenuView == optBtFramesToSkip) {
			Player.setBluetoothVisualizerFramesToSkipIndex(item.getItemId());
			optBtFramesToSkip.setSecondaryText(getFramesToSkipString());
			if (Player.bluetoothVisualizerController != null)
				((BluetoothVisualizerControllerJni)Player.bluetoothVisualizerController).syncFramesToSkip();
		} else if (lastMenuView == optBytesBeforeDecoding) {
			Player.setBytesBeforeDecodingIndex(item.getItemId());
			optBytesBeforeDecoding.setSecondaryText(getBytesBeforeDecodingString(item.getItemId()));
		} else if (lastMenuView == optSecondsBeforePlayback) {
			Player.setSecondsBeforePlayingIndex(item.getItemId());
			optSecondsBeforePlayback.setSecondaryText(getSecondsBeforePlaybackString(item.getItemId()));
		}
		configsChanged = true;
		return true;
	}

	private String getBytesBeforeDecodingString(int index) {
		return (Player.getBytesBeforeDecoding(index) >> 10) + " kiB";
	}

	private String getSecondsBeforePlaybackString(int index) {
		final int sec = Player.getSecondsBeforePlayback(index);
		return UI.formatIntAsFloat(sec / 100, false, true) + " " + ((sec == 1000) ? getText(R.string.second) : getText(R.string.seconds));
	}

	private String getMinuteString(int minutes) {
		if (minutes <= 0)
			return getText(R.string.never).toString();
		if (minutes == 1)
			return "1 " + getText(R.string.minute).toString();
		return minutes + " " + getText(R.string.minutes).toString();
	}
	
	private String getAutoTurnOffString() {
		return getMinuteString(Player.getTurnOffTimerMinutesLeft());
	}
	
	private String getAutoIdleTurnOffString() {
		return getMinuteString(Player.getIdleTurnOffTimerMinutesLeft());
	}
	
	private String getVolumeString() {
		switch (Player.volumeControlType) {
		case Player.VOLUME_CONTROL_DB:
			return getText(R.string.volume_control_type_decibels).toString();
		case Player.VOLUME_CONTROL_PERCENT:
			return getText(R.string.volume_control_type_percentage).toString();
		case Player.VOLUME_CONTROL_NONE:
			return getText(R.string.noneM).toString();
		default:
			return getText(R.string.volume_control_type_integrated).toString();
		}
	}
	
	private String getExtraInfoModeString(int extraMode) {
		switch (extraMode) {
		case Song.EXTRA_ARTIST:
			return getText(R.string.artist).toString();
		case Song.EXTRA_ALBUM:
			return getText(R.string.album).toString();
		case Song.EXTRA_TRACK_ARTIST:
			return getText(R.string.track) + "/" + getText(R.string.artist);
		case Song.EXTRA_TRACK_ALBUM:
			return getText(R.string.track) + "/" + getText(R.string.album);
		case Song.EXTRA_TRACK_ARTIST_ALBUM:
			return getText(R.string.track) + "/" + getText(R.string.artist) + "/" + getText(R.string.album);
		default:
			return getText(R.string.artist) + "/" + getText(R.string.album);
		}
	}
	
	private String getOrientationString() {
		final int o = UI.forcedOrientation;
		return getText((o == 0) ? R.string.none : ((o < 0) ? R.string.landscape : R.string.portrait)).toString();
	}
	
	private String getFadeInString(int duration) {
		return getText((duration >= 250) ? R.string.dshort : ((duration >= 125) ? R.string.dmedium : ((duration > 0) ? R.string.dlong : R.string.none))).toString();
	}
	
	private String getScrollBarString(int scrollBarType) {
		return getText(((scrollBarType == BgListView.SCROLLBAR_INDEXED) ? R.string.indexed_if_possible : ((scrollBarType == BgListView.SCROLLBAR_LARGE) ? R.string.large : ((scrollBarType == BgListView.SCROLLBAR_NONE) ? R.string.none : R.string.system_integrated)))).toString();
	}

	private String getFramesToSkipString() {
		return Integer.toString(Player.getBluetoothVisualizerFramesPerSecond(Player.getBluetoothVisualizerFramesToSkipIndex()));
	}

	private String getSizeString() {
		return Integer.toString(1 << (2 + Player.getBluetoothVisualizerSize()));
	}

	private String getSpeedString() {
		return Integer.toString(3 - Player.getBluetoothVisualizerSpeed());
	}

	private String getHeadsetHookString(int pressCount) {
		switch (Player.getHeadsetHookAction(pressCount)) {
		case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
			return getText(R.string.play) + "/" + getText(R.string.pause);
		case KeyEvent.KEYCODE_MEDIA_NEXT:
			return getText(R.string.next).toString();
		case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
			return getText(R.string.previous).toString();
		}
		return getText(R.string.nothing).toString();
	}

	@SuppressWarnings("deprecation")
	private void prepareHeader(TextView hdr) {
		hdr.setMaxLines(2);
		hdr.setEllipsize(TruncateAt.END);
		hdr.setPadding(UI.controlMargin, UI.controlMargin, UI.controlMargin, UI.controlMargin);
		if (UI.isLargeScreen)
			UI.largeText(hdr);
		else
			UI.mediumText(hdr);
		hdr.setTextColor(UI.colorState_text_highlight_static);
		hdr.setBackgroundDrawable(new ColorDrawable(UI.color_highlight));
	}
	
	private void addHeader(Context ctx, int resId, SettingView previousControl, int index) {
		final TextView hdr = new TextView(ctx);
		hdr.setText(resId);
		hdr.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
		prepareHeader(hdr);
		headers[index] = hdr;
		hdr.setTag(index);
		panelSettings.addView(hdr);
		if (previousControl != null) {
			if (UI.is3D) {
				LinearLayout.LayoutParams params = (LinearLayout.LayoutParams)previousControl.getLayoutParams();
				if (params == null)
					params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
				params.setMargins(0, 0, 0, UI.controlSmallMargin);
				previousControl.setLayoutParams(params);
			} else {
				previousControl.setHidingSeparator(true);
			}
		}
	}

	private SettingView addOption(SettingView view) {
		if (firstViewAdded == null)
			firstViewAdded = view;
		lastViewAdded = view;
		panelSettings.addView(view);
		view.setOnClickListener(this);
		view.setNextFocusLeftId(R.id.btnAbout);
		view.setNextFocusRightId(R.id.btnGoBack);
		return view;
	}

	private boolean cancelGoBack() {
		if (colorMode && changed) {
			checkingReturn = true;
			UI.prepareDialogAndShow((new AlertDialog.Builder(getHostActivity()))
			.setTitle(R.string.oops)
			.setView(UI.createDialogView(getHostActivity(), getText(R.string.discard_theme)))
			.setPositiveButton(R.string.ok, this)
			.setNegativeButton(R.string.cancel, null)
			.create());
			return true;
		}
		return false;
	}
	
	private int validate() {
		//hard = -1
		//impossible = -2
		final int color_list = colorViews[UI.IDX_COLOR_LIST].getColor();
		final int color_text_listitem = colorViews[UI.IDX_COLOR_TEXT_LISTITEM].getColor();
		final int color_window = colorViews[UI.IDX_COLOR_WINDOW].getColor();
		final int color_text = colorViews[UI.IDX_COLOR_TEXT].getColor();
		final int color_selected_grad_lt = colorViews[UI.IDX_COLOR_SELECTED_GRAD_LT].getColor();
		final int color_selected_grad_dk = colorViews[UI.IDX_COLOR_SELECTED_GRAD_DK].getColor();
		final int color_text_selected = colorViews[UI.IDX_COLOR_TEXT_SELECTED].getColor();
		final int color_menu = colorViews[UI.IDX_COLOR_MENU].getColor();
		final int color_text_menu = colorViews[UI.IDX_COLOR_TEXT_MENU].getColor();
		final int color_highlight = colorViews[UI.IDX_COLOR_HIGHLIGHT].getColor();
		final int color_text_highlight = colorViews[UI.IDX_COLOR_TEXT_HIGHLIGHT].getColor();
		final int color_text_listitem_secondary = colorViews[UI.IDX_COLOR_TEXT_LISTITEM_SECONDARY].getColor();
		final double crList = ColorUtils.contrastRatio(color_list, color_text_listitem);
		final double crWindow = ColorUtils.contrastRatio(color_window, color_text);
		final double crSel = ColorUtils.contrastRatio(ColorUtils.blend(color_selected_grad_lt, color_selected_grad_dk, 0.5f), color_text_selected);
		final double crMenu = ColorUtils.contrastRatio(color_menu, color_text_menu);
		if (crList < MIN_THRESHOLD || crWindow < MIN_THRESHOLD || crSel < MIN_THRESHOLD || crMenu < MIN_THRESHOLD)
			return -2;
		if (crList < 6.5 || crWindow < 6.5 || crSel < 6.5 || crMenu < 6.5)
			return -1;
		//these colors are considered nonessential, therefore their thresholds are lower,
		//and only warnings are generated when they are violated
		final double crHighlight = ColorUtils.contrastRatio(color_highlight, color_text_highlight);
		final double crListSecondary = ColorUtils.contrastRatio(color_list, color_text_listitem_secondary);
		if (crHighlight < 5 || crListSecondary < 5)
			return -1;
		return 0;
	}
	
	private void applyTheme(View sourceView) {
		final byte[] colors = UI.serializeThemeToArray();
		for (int i = 0; i < colorViews.length; i++)
			UI.serializeThemeColor(colors, i * 3, colorViews[i].getColor());
		UI.customColors = colors;
		UI.setTheme(getHostActivity(), UI.THEME_CUSTOM);
		changed = false;
		finish(0, sourceView, true);
	}
	
	private void loadColors(boolean createControls, boolean forceCurrent) {
		final Context ctx = getHostActivity();
		final int[] colorOrder = new int[] {
				UI.IDX_COLOR_DIVIDER,
				UI.IDX_COLOR_TEXT_HIGHLIGHT,
				UI.IDX_COLOR_HIGHLIGHT,
				UI.IDX_COLOR_TEXT,
				UI.IDX_COLOR_TEXT_DISABLED,
				UI.IDX_COLOR_WINDOW,
				UI.IDX_COLOR_CONTROL_MODE,
				UI.IDX_COLOR_VISUALIZER,
				UI.IDX_COLOR_TEXT_LISTITEM,
				UI.IDX_COLOR_TEXT_LISTITEM_SECONDARY,
				UI.IDX_COLOR_LIST,
				UI.IDX_COLOR_TEXT_MENU,
				UI.IDX_COLOR_MENU_BORDER,
				UI.IDX_COLOR_MENU_ICON,
				UI.IDX_COLOR_MENU,
				UI.IDX_COLOR_TEXT_SELECTED,
				UI.IDX_COLOR_SELECTED_BORDER,
				UI.IDX_COLOR_SELECTED_GRAD_LT,
				UI.IDX_COLOR_SELECTED_GRAD_DK,
				UI.IDX_COLOR_SELECTED_PRESSED,
				UI.IDX_COLOR_FOCUSED_BORDER,
				UI.IDX_COLOR_FOCUSED_GRAD_LT,
				UI.IDX_COLOR_FOCUSED_GRAD_DK,
				UI.IDX_COLOR_FOCUSED_PRESSED };
		final byte[] colors = ((UI.customColors != null && UI.customColors.length >= 72 && !forceCurrent) ? UI.customColors : UI.serializeThemeToArray());
		if (createControls)
			colorViews = new SettingView[colorOrder.length];
		for (int i = 0; i < colorOrder.length; i++) {
			final int idx = colorOrder[i];
			if (createControls)
				colorViews[idx] = new SettingView(ctx, null, UI.getThemeColorDescription(ctx, idx).toString(), null, false, false, true);
			colorViews[idx].setColor(UI.deserializeThemeColor(colors, idx * 3));
		}
		if (createControls) {
			optLoadCurrentTheme = new SettingView(ctx, UI.ICON_THEME, getText(R.string.load_colors_from_current_theme).toString(), null, false, false, false);
			int hIdx = 0;
			headers = new TextView[6];
			addHeader(ctx, R.string.color_theme, optLoadCurrentTheme, hIdx++);
			addOption(optLoadCurrentTheme);
			for (int i = 0; i < colorOrder.length; i++) {
				final int idx = colorOrder[i];
				switch (i) {
				case 0:
					addHeader(ctx, R.string.general, optLoadCurrentTheme, hIdx++);
					break;
				case 8:
					addHeader(ctx, R.string.list2, colorViews[colorOrder[i - 1]], hIdx++);
					break;
				case 11:
					addHeader(ctx, R.string.menu, colorViews[colorOrder[i - 1]], hIdx++);
					break;
				case 15:
					addHeader(ctx, R.string.selection, colorViews[colorOrder[i - 1]], hIdx++);
					break;
				case 20:
					addHeader(ctx, R.string.keyboard_focus, colorViews[colorOrder[i - 1]], hIdx++);
					break;
				}
				//don't show this to the user as it is not atually being used...
				if (idx != UI.IDX_COLOR_TEXT_DISABLED)
					addOption(colorViews[idx]);
			}
			lblTitle.setVisibility(View.GONE);
			currentHeader = -1;
		}
	}
	
	@Override
	public boolean onBackPressed() {
		return cancelGoBack();
	}

	private void setListPadding() {
		//for lblTitle to look nice, we must have no paddings
		if (panelSettings != null)
			UI.prepareViewPaddingForLargeScreen(panelSettings, 0, 0);
		if (lblTitle != null) {
			final RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams)lblTitle.getLayoutParams();
			lp.leftMargin = UI.getViewPaddingForLargeScreen();
			lp.rightMargin = lp.leftMargin;
			lblTitle.setLayoutParams(lp);
		}
	}

	private void refreshBluetoothStatus(boolean postAgain) {
		if (optBtMessage == null)
			return;
		if (Player.bluetoothVisualizerLastErrorMessage != 0) {
			btErrorMessage = Player.bluetoothVisualizerLastErrorMessage;
			Player.bluetoothVisualizerLastErrorMessage = 0;
			BackgroundActivityMonitor.bluetoothEnded();
		}
		if (Player.bluetoothVisualizerController != null) {
			btErrorMessage = 0;
			if (Player.bluetoothVisualizerState == Player.BLUETOOTH_VISUALIZER_STATE_CONNECTING) {
				if (btMessageText != R.string.bt_scanning) {
					btMessageText = R.string.bt_scanning;
					optBtMessage.setText(getText(R.string.bt_scanning).toString());
				}
			} else {
				btMessageText = 0;
				optBtMessage.setText(getText(R.string.bt_packets_sent).toString() + " " + ((BluetoothVisualizerControllerJni)Player.bluetoothVisualizerController).getPacketsSent());
			}
		} else {
			if (btErrorMessage != 0) {
				if (btMessageText != btErrorMessage) {
					btMessageText = btErrorMessage;
					optBtMessage.setText(getText(btErrorMessage).toString());
				}
			} else if (btMessageText != R.string.bt_inactive) {
				btMessageText = R.string.bt_inactive;
				optBtMessage.setText(getText(R.string.bt_inactive).toString());
			}
		}
		if (optBtConnect != null && optBtStart != null) {
			switch (Player.bluetoothVisualizerState) {
			case Player.BLUETOOTH_VISUALIZER_STATE_CONNECTED:
			case Player.BLUETOOTH_VISUALIZER_STATE_TRANSMITTING:
				if (btConnectText != R.string.bt_disconnect) {
					btConnectText = R.string.bt_disconnect;
					optBtConnect.setText(getText(R.string.bt_disconnect).toString());
				}
				if (Player.bluetoothVisualizerState == Player.BLUETOOTH_VISUALIZER_STATE_TRANSMITTING) {
					if (btStartText != R.string.bt_stop) {
						btStartText = R.string.bt_stop;
						optBtStart.setText(getText(R.string.bt_stop).toString());
						optBtStart.setIcon(UI.ICON_PAUSE);
					}
				} else {
					if (btStartText != R.string.bt_start) {
						btStartText = R.string.bt_start;
						optBtStart.setText(getText(R.string.bt_start).toString());
						optBtStart.setIcon(UI.ICON_PLAY);
					}
				}
				break;
			default:
				if (btConnectText != R.string.bt_connect) {
					btConnectText = R.string.bt_connect;
					optBtConnect.setText(getText(R.string.bt_connect).toString());
				}
				if (btStartText != R.string.bt_start) {
					btStartText = R.string.bt_start;
					optBtStart.setText(getText(R.string.bt_start).toString());
					optBtStart.setIcon(UI.ICON_PLAY);
				}
				break;
			}
		}
		if (postAgain)
			MainHandler.postToMainThreadDelayed(this, 1000);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (!bluetoothMode || optBtConnect == null)
			return;
		if (requestCode == BluetoothConnectionManager.REQUEST_ENABLE && resultCode == Activity.RESULT_OK)
			onClick(startTransmissionOnConnection ? optBtStart : optBtConnect);
	}

	@SuppressWarnings("deprecation")
	@Override
	protected void onCreateLayout(boolean firstCreation) {
		setContentView(R.layout.activity_settings);
		btnGoBack = (BgButton)findViewById(R.id.btnGoBack);
		btnGoBack.setOnClickListener(this);
		btnGoBack.setIcon(UI.ICON_GOBACK);
		btnBluetooth = (BgButton)findViewById(R.id.btnBluetooth);
		btnBluetooth.setOnClickListener(this);
		btnAbout = (BgButton)findViewById(R.id.btnAbout);
		btnAbout.setOnClickListener(this);
		boolean showBluetooth = false;
		if (colorMode) {
			btnAbout.setText(R.string.apply_theme);
		} else if (bluetoothMode) {
			btnAbout.setText(R.string.tutorial);
			btnAbout.setCompoundDrawables(new TextIconDrawable(UI.ICON_LINK, UI.colorState_text_visualizer_reactive.getDefaultColor(), UI.defaultControlContentsSize), null, null, null);
		} else {
			try {
				showBluetooth = (BluetoothAdapter.getDefaultAdapter() != null);
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
			if (showBluetooth) {
				btnBluetooth.setIcon(UI.ICON_BLUETOOTH);
				final TextView sep = (TextView)findViewById(R.id.sep);
				final RelativeLayout.LayoutParams rp = new RelativeLayout.LayoutParams(UI.strokeSize, UI.defaultControlContentsSize);
				rp.addRule(RelativeLayout.CENTER_VERTICAL, RelativeLayout.TRUE);
				rp.addRule(RelativeLayout.LEFT_OF, R.id.btnAbout);
				rp.leftMargin = UI.controlMargin;
				rp.rightMargin = UI.controlMargin;
				sep.setLayoutParams(rp);
				sep.setBackgroundDrawable(new ColorDrawable(UI.color_highlight));
				sep.setVisibility(View.VISIBLE);
				btnBluetooth.setVisibility(View.VISIBLE);
			}
			btnAbout.setCompoundDrawables(new TextIconDrawable(UI.ICON_INFORMATION, UI.color_text, UI.defaultControlContentsSize), null, null, null);
		}

		btnGoBack.setNextFocusDownId(2);
		btnBluetooth.setNextFocusDownId(2);
		btnAbout.setNextFocusDownId(2);
		btnAbout.setNextFocusRightId(2);
		UI.setNextFocusForwardId(btnAbout, 2);

		btnGoBack.setNextFocusUpId(3);
		btnBluetooth.setNextFocusUpId(3);
		btnAbout.setNextFocusUpId(3);
		btnGoBack.setNextFocusLeftId(3);

		if (!showBluetooth) {
			UI.setNextFocusForwardId(btnGoBack, R.id.btnAbout);
			btnGoBack.setNextFocusRightId(R.id.btnAbout);
			btnAbout.setNextFocusLeftId(R.id.btnGoBack);
		} else {
			UI.setNextFocusForwardId(btnGoBack, R.id.btnBluetooth);
			btnGoBack.setNextFocusRightId(R.id.btnBluetooth);
			btnAbout.setNextFocusLeftId(R.id.btnBluetooth);
		}

		lastColorView = -1;
		
		final Context ctx = getHostActivity();
		
		list = (ObservableScrollView)findViewById(R.id.list);
		list.setHorizontalFadingEdgeEnabled(false);
		list.setVerticalFadingEdgeEnabled(false);
		list.setFadingEdgeLength(0);
		if (!UI.isLargeScreen)
			UI.offsetTopEdgeEffect(list);
		//for lblTitle to look nice, we must have no paddings
		list.setBackgroundDrawable(new ColorDrawable(UI.color_list_bg));
		panelControls = (RelativeLayout)findViewById(R.id.panelControls);
		panelSettings = (LinearLayout)findViewById(R.id.panelSettings);
		lblTitle = (TextView)findViewById(R.id.lblTitle);
		prepareHeader(lblTitle);

		if (UI.isLargeScreen)
			setListPadding();

		list.setOnScrollListener(this);
		if (colorMode) {
			loadColors(true, false);
		} else if (bluetoothMode) {
			optBtMessage = new SettingView(ctx, UI.ICON_INFORMATION, "", null, false, false, false);
			optBtConnect = new SettingView(ctx, UI.ICON_BLUETOOTH, "", null, false, false, false);
			optBtStart = new SettingView(ctx, Player.bluetoothVisualizerState == Player.BLUETOOTH_VISUALIZER_STATE_TRANSMITTING ? UI.ICON_PAUSE : UI.ICON_PLAY, "", null, false, false, false);
			optBtFramesToSkip = new SettingView(ctx, UI.ICON_CLOCK, getText(R.string.bt_fps).toString(), getFramesToSkipString(), false, false, false);
			optBtSize = new SettingView(ctx, UI.ICON_VISUALIZER, getText(R.string.bt_sample_count).toString(), getSizeString(), false, false, false);
			optBtVUMeter = new SettingView(ctx, UI.ICON_VISUALIZER, getText(R.string.bt_vumeter).toString(), null, true, Player.isBluetoothUsingVUMeter(), false);
			optBtSpeed = new SettingView(ctx, UI.ICON_VISUALIZER, getText(R.string.sustain).toString(), getSpeedString(), false, false, false);
			refreshBluetoothStatus(true);

			headers = new TextView[3];
			addHeader(ctx, R.string.information, optBtMessage, 0);
			addOption(optBtMessage);
			addHeader(ctx, R.string.general, optBtMessage, 1);
			addOption(optBtConnect);
			addOption(optBtStart);
			addHeader(ctx, R.string.settings, optBtStart, 2);
			//addOption(optBtVUMeter);
			addOption(optBtFramesToSkip);
			addOption(optBtSize);
			addOption(optBtSpeed);
			currentHeader = -1;
		} else {
			if (!UI.isCurrentLocaleCyrillic()) {
				optUseAlternateTypeface = new SettingView(ctx, UI.ICON_DYSLEXIA, getText(R.string.opt_use_alternate_typeface).toString(), null, true, UI.isUsingAlternateTypeface, false);
			}
			optAutoTurnOff = new SettingView(ctx, UI.ICON_CLOCK, getText(R.string.opt_auto_turn_off).toString(), getAutoTurnOffString(), false, false, false);
			optAutoIdleTurnOff = new SettingView(ctx, UI.ICON_CLOCK, getText(R.string.opt_auto_idle_turn_off).toString(), getAutoIdleTurnOffString(), false, false, false);
			optAutoTurnOffPlaylist = new SettingView(ctx, UI.ICON_REPEATNONE, getText(R.string.opt_auto_turn_off_playlist).toString(), null, true, Player.turnOffWhenPlaylistEnds, false);
			optKeepScreenOn = new SettingView(ctx, UI.ICON_SCREEN, getText(R.string.opt_keep_screen_on).toString(), null, true, UI.keepScreenOn, false);
			optTheme = new SettingView(ctx, UI.ICON_THEME, getText(R.string.color_theme).toString() + ":", UI.getThemeString(ctx, UI.theme), false, false, false);
			optFlat = new SettingView(ctx, UI.ICON_FLAT, getText(R.string.flat_details).toString(), null, true, UI.isFlat, false);
			optBorders = new SettingView(ctx, UI.ICON_TRANSPARENT, getText(R.string.borders).toString(), null, true, UI.hasBorders, false);
			optExpandSeekBar = new SettingView(ctx, UI.ICON_SEEKBAR, getText(R.string.expand_seek_bar).toString(), null, true, UI.expandSeekBar, false);
			optVolumeControlType = new SettingView(ctx, UI.ICON_VOLUME4, getText(R.string.opt_volume_control_type).toString(), getVolumeString(), false, false, false);
			optDoNotAttenuateVolume = new SettingView(ctx, UI.ICON_INFORMATION, getText(R.string.opt_do_not_attenuate_volume).toString(), null, true, Player.doNotAttenuateVolume, false);
			opt3D = new SettingView(ctx, UI.ICON_DIVIDER, "3D", null, true, UI.is3D, false);
			optIsDividerVisible = new SettingView(ctx, UI.ICON_DIVIDER, getText(R.string.opt_is_divider_visible).toString(), null, true, UI.isDividerVisible, false);
			optIsVerticalMarginLarge = new SettingView(ctx, UI.ICON_SPACELIST, getText(R.string.opt_is_vertical_margin_large).toString(), null, true, UI.isVerticalMarginLarge, false);
			optExtraSpacing = new SettingView(ctx, UI.ICON_SPACEHEADER, getText(R.string.opt_extra_spacing).toString(), null, true, UI.extraSpacing, false);
			if (!UI.isLargeScreen)
				optPlaceTitleAtTheBottom = new SettingView(ctx, UI.ICON_SPACEHEADER, getText(R.string.place_title_at_the_bottom).toString(), null, true, UI.placeTitleAtTheBottom, false);
			optForcedLocale = new SettingView(ctx, UI.ICON_LANGUAGE, getText(R.string.opt_language).toString(), UI.getLocaleDescriptionFromCode(ctx, UI.forcedLocale), false, false, false);
			if (UI.isLargeScreen)
				optPlacePlaylistToTheRight = new SettingView(ctx, UI.ICON_HAND, getText(R.string.place_the_playlist_to_the_right).toString(), null, true, UI.controlsToTheLeft, false);
			optScrollBarToTheLeft = new SettingView(ctx, UI.ICON_HAND, getText(R.string.scrollbar_to_the_left).toString(), null, true, UI.scrollBarToTheLeft, false);
			optScrollBarSongList = new SettingView(ctx, UI.ICON_SCROLLBAR, getText(R.string.scrollbar_playlist).toString(), getScrollBarString(UI.songListScrollBarType), false, false, false);
			optScrollBarBrowser = new SettingView(ctx, UI.ICON_SCROLLBAR, getText(R.string.scrollbar_browser_type).toString(), getScrollBarString(UI.browserScrollBarType), false, false, false);
			optWidgetTransparentBg = new SettingView(ctx, UI.ICON_TRANSPARENT, getText(R.string.transparent_background).toString(), null, true, UI.widgetTransparentBg, false);
			optWidgetTextColor = new SettingView(ctx, UI.ICON_PALETTE, getText(R.string.text_color).toString(), null, false, false, true);
			optWidgetTextColor.setColor(UI.widgetTextColor);
			optWidgetIconColor = new SettingView(ctx, UI.ICON_PALETTE, getText(R.string.icon_color).toString(), null, false, false, true);
			optWidgetIconColor.setColor(UI.widgetIconColor);
			optHandleCallKey = new SettingView(ctx, UI.ICON_DIAL, getText(R.string.opt_handle_call_key).toString(), null, true, Player.handleCallKey, false);
			optHeadsetHook1 = new SettingView(ctx, UI.ICON_HEADSETHOOK1, getText(R.string.headset_hook_1).toString(), getHeadsetHookString(1), false, false, false);
			optHeadsetHook2 = new SettingView(ctx, UI.ICON_HEADSETHOOK2, getText(R.string.headset_hook_2).toString(), getHeadsetHookString(2), false, false, false);
			optHeadsetHook3 = new SettingView(ctx, UI.ICON_HEADSETHOOK3, getText(R.string.headset_hook_3).toString(), getHeadsetHookString(3), false, false, false);
			optPlayWhenHeadsetPlugged = new SettingView(ctx, UI.ICON_HEADSET, getText(R.string.opt_play_when_headset_plugged).toString(), null, true, Player.playWhenHeadsetPlugged, false);
			optBlockBackKey = new SettingView(ctx, UI.ICON_SETTINGS, getText(R.string.opt_block_back_key).toString(), null, true, UI.blockBackKey, false);
			optBackKeyAlwaysReturnsToPlayerWhenBrowsing = new SettingView(ctx, UI.ICON_SETTINGS, getText(R.string.opt_back_key_always_returns_to_player_when_browsing).toString(), null, true, UI.backKeyAlwaysReturnsToPlayerWhenBrowsing, false);
			optWrapAroundList = new SettingView(ctx, UI.ICON_SETTINGS, getText(R.string.opt_wrap_around_list).toString(), null, true, UI.wrapAroundList, false);
			optDoubleClickMode = new SettingView(ctx, UI.ICON_SETTINGS, getText(R.string.opt_double_click_mode).toString(), null, true, UI.doubleClickMode, false);
			optMarqueeTitle = new SettingView(ctx, UI.ICON_SETTINGS, getText(R.string.opt_marquee_title).toString(), null, true, UI.marqueeTitle, false);
			optPrepareNext = new SettingView(ctx, UI.ICON_SETTINGS, getText(R.string.opt_prepare_next).toString(), null, true, Player.nextPreparationEnabled, false);
			optClearListWhenPlayingFolders = new SettingView(ctx, UI.ICON_SETTINGS, getText(R.string.opt_clear_list_when_playing_folders).toString(), null, true, Player.clearListWhenPlayingFolders, false);
			optGoBackWhenPlayingFolders = new SettingView(ctx, UI.ICON_SETTINGS, getText(R.string.opt_go_back_when_playing_folders).toString(), null, true, Player.goBackWhenPlayingFolders, false);
			optExtraInfoMode = new SettingView(ctx, UI.ICON_SETTINGS, getText(R.string.secondary_line_of_text).toString(), getExtraInfoModeString(Song.extraInfoMode), false, false, false);
			optForceOrientation = new SettingView(ctx, UI.ICON_ORIENTATION, getText(R.string.opt_force_orientation).toString(), getOrientationString(), false, false, false);
			optTransition = new SettingView(ctx, UI.ICON_TRANSITION, getText(R.string.transition).toString(), UI.getTransitionString(ctx, UI.transition), false, false, false);
			optAnimations = new SettingView(ctx, UI.ICON_TRANSITION, getText(R.string.animations).toString(), null, true, UI.animationEnabled, false);
			optNotFullscreen = new SettingView(ctx, UI.ICON_SCREEN, getText(R.string.fullscreen).toString(), null, true, !UI.notFullscreen, false);
			optFadeInFocus = new SettingView(ctx, UI.ICON_FADE, getText(R.string.opt_fade_in_focus).toString(), getFadeInString(Player.fadeInIncrementOnFocus), false, false, false);
			optFadeInPause = new SettingView(ctx, UI.ICON_FADE, getText(R.string.opt_fade_in_pause).toString(), getFadeInString(Player.fadeInIncrementOnPause), false, false, false);
			optFadeInOther = new SettingView(ctx, UI.ICON_FADE, getText(R.string.opt_fade_in_other).toString(), getFadeInString(Player.fadeInIncrementOnOther), false, false, false);
			optAnnounceCurrentSong = new SettingView(ctx, UI.ICON_MIC, getText(R.string.announce_current_song).toString(), null, true, Player.announceCurrentSong, false);
			optFollowCurrentSong = new SettingView(ctx, UI.ICON_SCROLLBAR, getText(R.string.follow_current_song).toString(), null, true, Player.followCurrentSong, false);
			optBytesBeforeDecoding = new SettingView(ctx, UI.ICON_RADIO, getText(R.string.bytes_before_decoding).toString(), getBytesBeforeDecodingString(Player.getBytesBeforeDecodingIndex()), false, false, false);
			optSecondsBeforePlayback = new SettingView(ctx, UI.ICON_RADIO, getText(R.string.seconds_before_playback).toString(), getSecondsBeforePlaybackString(Player.getSecondsBeforePlaybackIndex()), false, false, false);

			int hIdx = 0;
			headers = new TextView[8];
			addHeader(ctx, R.string.msg_turn_off_title, optAutoTurnOffPlaylist, hIdx++);
			addOption(optAutoTurnOff);
			addOption(optAutoIdleTurnOff);
			addOption(optAutoTurnOffPlaylist);
			addHeader(ctx, R.string.hdr_display, optAutoTurnOffPlaylist, hIdx++);
			addOption(optKeepScreenOn);
			addOption(optTheme);
			addOption(opt3D);
			addOption(optFlat);
			addOption(optBorders);
			if (!UI.is3D)
				addOption(optIsDividerVisible);
			addOption(optExtraInfoMode);
			addOption(optForceOrientation);
			addOption(optTransition);
			addOption(optAnimations);
			addOption(optNotFullscreen);
			addOption(optIsVerticalMarginLarge);
			addOption(optExtraSpacing);
			if (!UI.isLargeScreen)
				addOption(optPlaceTitleAtTheBottom);
			if (!UI.isCurrentLocaleCyrillic())
				addOption(optUseAlternateTypeface);
			addOption(optForcedLocale);
			addHeader(ctx, R.string.accessibility, optForcedLocale, hIdx++);
			if (UI.isLargeScreen)
				addOption(optPlacePlaylistToTheRight);
			addOption(optAnnounceCurrentSong);
			addOption(optScrollBarToTheLeft);
			addHeader(ctx, R.string.scrollbar, optScrollBarToTheLeft, hIdx++);
			addOption(optFollowCurrentSong);
			addOption(optScrollBarSongList);
			addOption(optScrollBarBrowser);
			addHeader(ctx, R.string.widget, optScrollBarBrowser, hIdx++);
			addOption(optWidgetTransparentBg);
			addOption(optWidgetTextColor);
			addOption(optWidgetIconColor);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
				addHeader(ctx, R.string.radio, optWidgetIconColor, hIdx++);
				addOption(optBytesBeforeDecoding);
				addOption(optSecondsBeforePlayback);
			}
			addHeader(ctx, R.string.hdr_playback, (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) ? optSecondsBeforePlayback : optWidgetIconColor, hIdx++);
			addOption(optPlayWhenHeadsetPlugged);
			addOption(optHandleCallKey);
			addOption(optHeadsetHook1);
			addOption(optHeadsetHook2);
			addOption(optHeadsetHook3);
			addOption(optExpandSeekBar);
			addOption(optVolumeControlType);
			addOption(optDoNotAttenuateVolume);
			addOption(optFadeInFocus);
			addOption(optFadeInPause);
			addOption(optFadeInOther);
			addHeader(ctx, R.string.hdr_behavior, optFadeInOther, hIdx);
			addOption(optBackKeyAlwaysReturnsToPlayerWhenBrowsing);
			addOption(optClearListWhenPlayingFolders);
			addOption(optGoBackWhenPlayingFolders);
			addOption(optBlockBackKey);
			addOption(optWrapAroundList);
			addOption(optDoubleClickMode);
			addOption(optMarqueeTitle);
			addOption(optPrepareNext);
			lblTitle.setVisibility(View.GONE);
			currentHeader = -1;
		}
		firstViewAdded.setId(2);
		firstViewAdded.setNextFocusUpId(R.id.btnAbout);
		firstViewAdded = null;
		lastViewAdded.setId(3);
		lastViewAdded.setNextFocusDownId(R.id.btnGoBack);
		lastViewAdded = null;

		UI.prepareControlContainer(panelControls, false, UI.isLargeScreen);
		if (UI.isLargeScreen)
			btnAbout.setTextSize(TypedValue.COMPLEX_UNIT_PX, UI._22sp);
		btnAbout.setDefaultHeight();
	}

	@Override
	protected void onPause() {
		if (!colorMode && !bluetoothMode)
			Player.turnOffTimerObserver = null;
	}
	
	@Override
	protected void onResume() {
		if (!colorMode && !bluetoothMode) {
			Player.turnOffTimerObserver = this;
			if (optAutoTurnOff != null)
				optAutoTurnOff.setSecondaryText(getAutoTurnOffString());
			if (optAutoIdleTurnOff != null)
				optAutoIdleTurnOff.setSecondaryText(getAutoIdleTurnOffString());
		}
	}
	
	@Override
	protected void onOrientationChanged() {
		if (UI.isLargeScreen)
			setListPadding();
	}
	
	@Override
	protected void onCleanupLayout() {
		btnGoBack = null;
		btnBluetooth = null;
		btnAbout = null;
		list = null;
		lblTitle = null;
		panelControls = null;
		panelSettings = null;
		if (headers != null) {
			for (int i = headers.length - 1; i >= 0; i--)
				headers[i] = null;
			headers = null;
		}
		optLoadCurrentTheme = null;
		optUseAlternateTypeface = null;
		optAutoTurnOff = null;
		optAutoIdleTurnOff = null;
		optAutoTurnOffPlaylist = null;
		optKeepScreenOn = null;
		optTheme = null;
		optFlat = null;
		optBorders = null;
		optExpandSeekBar = null;
		optVolumeControlType = null;
		optDoNotAttenuateVolume = null;
		opt3D = null;
		optIsDividerVisible = null;
		optIsVerticalMarginLarge = null;
		optExtraSpacing = null;
		optPlaceTitleAtTheBottom = null;
		optForcedLocale = null;
		optPlacePlaylistToTheRight = null;
		optScrollBarToTheLeft = null;
		optScrollBarSongList = null;
		optScrollBarBrowser = null;
		optWidgetTransparentBg = null;
		optWidgetTextColor = null;
		optWidgetIconColor = null;
		optHandleCallKey = null;
		optHeadsetHook1 = null;
		optHeadsetHook2 = null;
		optHeadsetHook3 = null;
		optPlayWhenHeadsetPlugged = null;
		optBlockBackKey = null;
		optBackKeyAlwaysReturnsToPlayerWhenBrowsing = null;
		optWrapAroundList = null;
		optDoubleClickMode = null;
		optMarqueeTitle = null;
		optPrepareNext = null;
		optClearListWhenPlayingFolders = null;
		optGoBackWhenPlayingFolders = null;
		optExtraInfoMode = null;
		optForceOrientation = null;
		optTransition = null;
		optAnimations = null;
		optNotFullscreen = null;
		optFadeInFocus = null;
		optFadeInPause = null;
		optFadeInOther = null;
		optBtMessage = null;
		optBtConnect = null;
		optBtStart = null;
		optBtFramesToSkip = null;
		optBtSize = null;
		optBtVUMeter = null;
		optBtSpeed = null;
		optAnnounceCurrentSong = null;
		optFollowCurrentSong = null;
		optBytesBeforeDecoding = null;
		optSecondsBeforePlayback = null;
		lastMenuView = null;
		if (colorViews != null) {
			for (int i = colorViews.length - 1; i >= 0; i--)
				colorViews[i] = null;
			colorViews = null;
		}
	}
	
	@Override
	protected void onDestroy() {
		if (!colorMode && !bluetoothMode && configsChanged)
			MainHandler.postToMainThread(this);
	}
	
	@Override
	public void onClick(View view) {
		if (view == btnGoBack) {
			if (!cancelGoBack())
				finish(0, view, true);
			return;
		}
		if (view == btnBluetooth && !colorMode && !bluetoothMode) {
			try {
				getHostActivity().startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
			return;
		}
		if (colorViews != null) {
			for (int i = colorViews.length - 1; i >= 0; i--) {
				if (view == colorViews[i]) {
					lastColorView = i;
					ColorPickerView.showDialog(getHostActivity(), colorViews[i].getColor(), null, this);
					return;
				}
			}
		} else if (bluetoothMode) {
			if (view == btnAbout) {
				try {
					getHostActivity().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/carlosrafaelgn/FPlayArduino")));
				} catch (Throwable ex) {
					ex.printStackTrace();
				}
			} else if (view == optBtConnect) {
				if (Player.bluetoothVisualizerController == null) {
					startTransmissionOnConnection = false;
					Player.startBluetoothVisualizer(getHostActivity(), false);
					refreshBluetoothStatus(false);
				} else if (Player.bluetoothVisualizerState != Player.BLUETOOTH_VISUALIZER_STATE_CONNECTING) {
					Player.stopBluetoothVisualizer();
					refreshBluetoothStatus(false);
				}
			} else if (view == optBtStart) {
				if (Player.bluetoothVisualizerController != null) {
					switch (Player.bluetoothVisualizerState) {
					case Player.BLUETOOTH_VISUALIZER_STATE_CONNECTED:
						((BluetoothVisualizerControllerJni)Player.bluetoothVisualizerController).startTransmission();
						refreshBluetoothStatus(false);
						break;
					case Player.BLUETOOTH_VISUALIZER_STATE_TRANSMITTING:
						((BluetoothVisualizerControllerJni)Player.bluetoothVisualizerController).stopTransmission();
						refreshBluetoothStatus(false);
						break;
					}
				} else if (Player.bluetoothVisualizerState != Player.BLUETOOTH_VISUALIZER_STATE_CONNECTING) {
					startTransmissionOnConnection = true;
					Player.startBluetoothVisualizer(getHostActivity(), true);
					refreshBluetoothStatus(false);
				}
			} else if (view == optBtVUMeter) {
				Player.setBluetoothUsingVUMeter(optBtVUMeter.isChecked());
				if (Player.bluetoothVisualizerController != null)
					((BluetoothVisualizerControllerJni)Player.bluetoothVisualizerController).syncDataType();
			} else if (view == optBtSize || view == optBtSpeed || view == optBtFramesToSkip) {
				CustomContextMenu.openContextMenu(view, this);
			}
			return;
		}
		if (view == optLoadCurrentTheme) {
			if (colorMode)
				loadColors(false, true);
		} else if (view == btnAbout) {
			if (colorMode) {
				checkingReturn = false;
				switch (validate()) {
				case -1:
					UI.prepareDialogAndShow((new AlertDialog.Builder(getHostActivity()))
					.setTitle(R.string.oops)
					.setView(UI.createDialogView(getHostActivity(), getText(R.string.hard_theme)))
					.setPositiveButton(R.string.ok, this)
					.setNegativeButton(R.string.cancel, null)
					.create());
					return;
				case -2:
					UI.prepareDialogAndShow((new AlertDialog.Builder(getHostActivity()))
					.setTitle(R.string.oops)
					.setView(UI.createDialogView(getHostActivity(), getText(R.string.unreadable_theme)))
					.setPositiveButton(R.string.got_it, null)
					.create());
					return;
				}
				applyTheme(view);
			} else {
				startActivity(new ActivityAbout(), 0, view, true);
			}
		} else if (!UI.isCurrentLocaleCyrillic() && view == optUseAlternateTypeface) {
			final boolean desired = optUseAlternateTypeface.isChecked();
			UI.setUsingAlternateTypeface(getHostActivity(), desired);
			if (UI.isUsingAlternateTypeface != desired) {
				optUseAlternateTypeface.setChecked(UI.isUsingAlternateTypeface);
			} else {
				onCleanupLayout();
				onCreateLayout(false);
				System.gc();
			}
		} else if (view == optKeepScreenOn) {
			UI.keepScreenOn = optKeepScreenOn.isChecked();
			if (UI.keepScreenOn)
				addWindowFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
			else
				clearWindowFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		} else if (view == optNotFullscreen) {
			UI.notFullscreen = !optNotFullscreen.isChecked();
			if (UI.notFullscreen)
				clearWindowFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
			else
				addWindowFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		} else if (view == opt3D) {
			UI.is3D = opt3D.isChecked();
			UI.updateColorListBg();
			onCleanupLayout();
			onCreateLayout(false);
			System.gc();
		} else if (view == optIsDividerVisible) {
			UI.isDividerVisible = optIsDividerVisible.isChecked();
			if (!UI.is3D) {
				for (int i = panelSettings.getChildCount() - 1; i >= 0; i--) {
					final View v = panelSettings.getChildAt(i);
					if (v != null && (v instanceof SettingView))
						v.invalidate();
				}
			}
		} else if (view == optIsVerticalMarginLarge) {
			final int oldVerticalMargin = UI.verticalMargin;
			UI.setVerticalMarginLarge(optIsVerticalMarginLarge.isChecked());
			for (int i = panelSettings.getChildCount() - 1; i >= 0; i--) {
				final View v = panelSettings.getChildAt(i);
				if (v != null && (v instanceof SettingView))
					((SettingView)v).updateVerticalMargin(oldVerticalMargin);
			}
		} else if (view == optExtraSpacing) {
			UI.extraSpacing = optExtraSpacing.isChecked();
			onCleanupLayout();
			onCreateLayout(false);
			System.gc();
		} else if (view == optPlaceTitleAtTheBottom) {
			UI.placeTitleAtTheBottom = optPlaceTitleAtTheBottom.isChecked();
		} else if (view == optFlat) {
			UI.setFlat(optFlat.isChecked());
			//onCleanupLayout();
			//onCreateLayout(false);
			System.gc();
		} else if (view == optBorders) {
			UI.hasBorders = optBorders.isChecked();
		} else if (view == optHandleCallKey) {
			Player.handleCallKey = optHandleCallKey.isChecked();
		} else if (view == optPlayWhenHeadsetPlugged) {
			Player.playWhenHeadsetPlugged = optPlayWhenHeadsetPlugged.isChecked();
		} else if (view == optPlacePlaylistToTheRight) {
			UI.controlsToTheLeft = optPlacePlaylistToTheRight.isChecked();
		} else if (view == optScrollBarToTheLeft) {
			UI.scrollBarToTheLeft = optScrollBarToTheLeft.isChecked();
			list.updateVerticalScrollbar();
		} else if (view == optWidgetTransparentBg) {
			UI.widgetTransparentBg = optWidgetTransparentBg.isChecked();
			WidgetMain.updateWidgets(getApplication());
		} else if (view == optWidgetTextColor) {
			ColorPickerView.showDialog(getHostActivity(), UI.widgetTextColor, view, this);
		} else if (view == optWidgetIconColor) {
			ColorPickerView.showDialog(getHostActivity(), UI.widgetIconColor, view, this);
		} else if (view == optBlockBackKey) {
			UI.blockBackKey = optBlockBackKey.isChecked();
		} else if (view == optBackKeyAlwaysReturnsToPlayerWhenBrowsing) {
			UI.backKeyAlwaysReturnsToPlayerWhenBrowsing = optBackKeyAlwaysReturnsToPlayerWhenBrowsing.isChecked();
		} else if (view == optWrapAroundList) {
			UI.wrapAroundList = optWrapAroundList.isChecked();
		} else if (view == optDoubleClickMode) {
			UI.doubleClickMode = optDoubleClickMode.isChecked();
		} else if (view == optDoNotAttenuateVolume) {
			Player.doNotAttenuateVolume = optDoNotAttenuateVolume.isChecked();
		} else if (view == optMarqueeTitle) {
			UI.marqueeTitle = optMarqueeTitle.isChecked();
		} else if (view == optPrepareNext) {
			Player.nextPreparationEnabled = optPrepareNext.isChecked();
		} else if (view == optClearListWhenPlayingFolders) {
			Player.clearListWhenPlayingFolders = optClearListWhenPlayingFolders.isChecked();
		} else if (view == optGoBackWhenPlayingFolders) {
			Player.goBackWhenPlayingFolders = optGoBackWhenPlayingFolders.isChecked();
		} else if (view == optExpandSeekBar) {
			UI.expandSeekBar = optExpandSeekBar.isChecked();
		} else if (view == optAnimations) {
			UI.animationEnabled = optAnimations.isChecked();
		} else if (view == optAutoTurnOffPlaylist) {
			Player.turnOffWhenPlaylistEnds = optAutoTurnOffPlaylist.isChecked();
		} else if (view == optAnnounceCurrentSong) {
			Player.announceCurrentSong = optAnnounceCurrentSong.isChecked();
		} else if (view == optFollowCurrentSong) {
			Player.followCurrentSong = optFollowCurrentSong.isChecked();
		} else if (view == optAutoTurnOff || view == optAutoIdleTurnOff || view == optTheme || view == optForcedLocale || view == optVolumeControlType || view == optExtraInfoMode || view == optForceOrientation || view == optTransition || view == optFadeInFocus || view == optFadeInPause || view == optFadeInOther || view == optScrollBarSongList || view == optScrollBarBrowser || view == optHeadsetHook1 || view == optHeadsetHook2 || view == optHeadsetHook3 || view == optBytesBeforeDecoding || view == optSecondsBeforePlayback) {
			lastMenuView = null;
			CustomContextMenu.openContextMenu(view, this);
			return;
		}
		configsChanged = true;
	}
	
	@Override
	public void onClick(DialogInterface dialog, int which) {
		if (which == AlertDialog.BUTTON_POSITIVE) {
			if (colorMode) {
				if (checkingReturn) {
					changed = false;
					finish(0, null, false);
				} else {
					applyTheme(null);
				}
			} else if (txtCustomMinutes != null) {
				try {
					int m = Integer.parseInt(txtCustomMinutes.getText().toString());
					configsChanged = true;
					if (lastMenuView == optAutoTurnOff) {
						Player.setTurnOffTimer(m);
						optAutoTurnOff.setSecondaryText(getAutoTurnOffString());
					} else {
						Player.setIdleTurnOffTimer(m);
						optAutoIdleTurnOff.setSecondaryText(getAutoIdleTurnOffString());
					}
				} catch (Throwable ex) {
					ex.printStackTrace();
				}
				txtCustomMinutes = null;
			}
		}
	}
	
	@Override
	public void onPlayerTurnOffTimerTick() {
		if (optAutoTurnOff != null)
			optAutoTurnOff.setSecondaryText(getAutoTurnOffString());
	}
	
	@Override
	public void onPlayerIdleTurnOffTimerTick() {
		if (optAutoIdleTurnOff != null)
			optAutoIdleTurnOff.setSecondaryText(getAutoIdleTurnOffString());
	}
	
	private void validateColor(int idx1, int idx2) {
		final boolean err = (ColorUtils.contrastRatio((idx1 == UI.IDX_COLOR_SELECTED_GRAD_LT) ? ColorUtils.blend(colorViews[UI.IDX_COLOR_SELECTED_GRAD_LT].getColor(), colorViews[UI.IDX_COLOR_SELECTED_GRAD_DK].getColor(), 0.5f) : colorViews[idx1].getColor(), colorViews[idx2].getColor()) < MIN_THRESHOLD);
		colorViews[idx1].showErrorView(err);
		colorViews[idx2].showErrorView(err);
		if (idx1 == UI.IDX_COLOR_SELECTED_GRAD_LT)
			colorViews[UI.IDX_COLOR_SELECTED_GRAD_DK].showErrorView(err);
	}
	
	@Override
	public void onColorPicked(ColorPickerView picker, View parentView, int color) {
		if (colorMode && lastColorView >= 0) {
			if (colorViews[lastColorView].getColor() != color) {
				changed = true;
				colorViews[lastColorView].setColor(color);
				switch (lastColorView) {
				case UI.IDX_COLOR_WINDOW:
				case UI.IDX_COLOR_TEXT:
					validateColor(UI.IDX_COLOR_WINDOW, UI.IDX_COLOR_TEXT);
					break;
				case UI.IDX_COLOR_LIST:
				case UI.IDX_COLOR_TEXT_LISTITEM:
					validateColor(UI.IDX_COLOR_LIST, UI.IDX_COLOR_TEXT_LISTITEM);
					break;
				case UI.IDX_COLOR_SELECTED_GRAD_LT:
				case UI.IDX_COLOR_SELECTED_GRAD_DK:
				case UI.IDX_COLOR_TEXT_SELECTED:
					validateColor(UI.IDX_COLOR_SELECTED_GRAD_LT, UI.IDX_COLOR_TEXT_SELECTED);
					break;
				case UI.IDX_COLOR_MENU:
				case UI.IDX_COLOR_TEXT_MENU:
					validateColor(UI.IDX_COLOR_MENU, UI.IDX_COLOR_TEXT_MENU);
					break;
				}
			}
			lastColorView = -1;
		} else if (parentView == optWidgetTextColor) {
			UI.widgetTextColor = color;
			optWidgetTextColor.setColor(color);
			WidgetMain.updateWidgets(getApplication());
		} else if (parentView == optWidgetIconColor) {
			UI.widgetIconColor = color;
			optWidgetIconColor.setColor(color);
			WidgetMain.updateWidgets(getApplication());
		}
	}
	
	@Override
	public void onScroll(ObservableScrollView view, int l, int t, int oldl, int oldt) {
		if (headers == null || panelSettings == null || lblTitle == null || oldt == t)
			return;
		if (t < 0)
			t = 0;
		int i = view.getPreviousChildIndexWithClass(TextView.class, t);
		if (i <= 0) {
			if (t == 0) {
				currentHeader = -1;
				lblTitle.setVisibility(View.GONE);
				return;
			} else {
				i = 0;
			}
		}
		i = (Integer)panelSettings.getChildAt(i).getTag();
		if (currentHeader < 0) {
			lblTitle.setVisibility(View.VISIBLE);
			lblTitle.bringToFront();
			//neat workaround to partially hide lblTitle ;)
			panelControls.bringToFront();
		}
		if (currentHeader != i) {
			currentHeader = i;
			lblTitle.setText(headers[i].getText());
		}
		RelativeLayout.LayoutParams lp;
		if (i < (headers.length - 1) && headers[i + 1].getTop() < (t + lblTitle.getHeight())) {
			lp = (RelativeLayout.LayoutParams)lblTitle.getLayoutParams();
			lp.topMargin = headers[i + 1].getTop() - (t + lblTitle.getHeight());
			lblTitle.setLayoutParams(lp);
			lblTitleOk = false;
		} else if (!lblTitleOk) {
			lp = (RelativeLayout.LayoutParams)lblTitle.getLayoutParams();
			lp.topMargin = 0;
			lblTitle.setLayoutParams(lp);
			lblTitleOk = true;
		}
	}
	
	@Override
	public void run() {
		if (bluetoothMode)
			refreshBluetoothStatus(true);
		else if (Player.state < Player.STATE_TERMINATING && Player.getService() != null)
			Player.saveConfig(Player.getService(), false);
	}
}
