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

import android.content.Context;
import android.graphics.Paint.FontMetrics;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import br.com.siva.pinkmusic.activity.ClientActivity;
import br.com.siva.pinkmusic.list.FileSt;
import br.com.siva.pinkmusic.list.Song;
import br.com.siva.pinkmusic.playback.BassBoost;
import br.com.siva.pinkmusic.playback.Equalizer;
import br.com.siva.pinkmusic.playback.Player;
import br.com.siva.pinkmusic.playback.Virtualizer;
import br.com.siva.pinkmusic.ui.BgButton;
import br.com.siva.pinkmusic.ui.BgSeekBar;
import br.com.siva.pinkmusic.ui.CustomContextMenu;
import br.com.siva.pinkmusic.ui.UI;
import br.com.siva.pinkmusic.ui.drawable.ColorDrawable;
import br.com.siva.pinkmusic.ui.drawable.TextIconDrawable;
import br.com.siva.pinkmusic.util.SerializableMap;

public final class ActivityEffects extends ClientActivity implements Runnable, View.OnClickListener, BgSeekBar.OnBgSeekBarChangeListener, ActivityFileSelection.OnFileSelectionListener, Player.PlayerObserver {
	private static final int LevelThreshold = 100, MNU_ZEROPRESET = 100, MNU_LOADPRESET = 101, MNU_SAVEPRESET = 102, MNU_AUDIOSINK_DEVICE = 103, MNU_AUDIOSINK_WIRE = 104, MNU_AUDIOSINK_BT = 105;
	private LinearLayout panelControls, panelEqualizer, panelBars;
	private ViewGroup panelSecondary;
	private BgButton chkEqualizer, chkBass, chkVirtualizer;
	private BgButton btnGoBack, btnAudioSink, btnMenu, btnChangeEffect;
	private int min, max, audioSink, storedAudioSink;
	private int[] frequencies;
	private boolean enablingEffect, screenNotSoLarge;
	private BgSeekBar[] bars;
	private BgSeekBar barBass, barVirtualizer;
	private StringBuilder txtBuilder;

	@Override
	public CharSequence getTitle() {
		return getText(R.string.audio_effects);
	}

	private String format(int frequency, int level) {
		if (txtBuilder == null)
			return "";
		txtBuilder.delete(0, txtBuilder.length());
		if (frequency < 1000) {
			txtBuilder.append(frequency);
		} else {
			UI.formatIntAsFloat(txtBuilder, frequency / 100, false, true);
			txtBuilder.append('k');
		}
		txtBuilder.append("Hz ");
		if (level > 0)
			txtBuilder.append('+');
		UI.formatIntAsFloat(txtBuilder, level / 10, false, false);
		txtBuilder.append("dB");
		return txtBuilder.toString();
	}
	
	private String format(int strength) {
		if (txtBuilder == null)
			return "";
		txtBuilder.delete(0, txtBuilder.length());
		UI.formatIntAsFloat(txtBuilder, strength, false, false);
		txtBuilder.append("%");
		return txtBuilder.toString();
	}

	private String getAudioSinkDescription(int audioSink, boolean ellipsize) {
		String description = ((Player.localAudioSinkUsedInEffects == audioSink) ? "» " : "");
		switch (audioSink) {
		case Player.AUDIO_SINK_WIRE:
			description += getText(R.string.earphones).toString();
			break;
		case Player.AUDIO_SINK_BT:
			description += getText(R.string.bluetooth).toString();
			break;
		default:
			description += getText(R.string.loudspeaker).toString();
			break;
		}
		if (Player.localAudioSinkUsedInEffects == audioSink)
			description += " «";
		if (ellipsize) {
			final int availWidth = UI.usableScreenWidth -
				(UI.extraSpacing ? (UI.controlMargin << 1) : 0) - //extra spacing in the header
				(UI.defaultControlSize << 1) - //btnGoBack and btnMenu
				(UI.controlMargin << 1) - //btnAudioSink's padding
				UI.defaultControlContentsSize - //btnAudioSink's TextIconDrawable
				UI.controlMargin; //bonus :)
			description = UI.ellipsizeText(description, UI.isLargeScreen ? UI._22sp : UI._18sp, availWidth, false);
		}
		return description;
	}

	@Override
	public View getNullContextMenuView() {
		return btnMenu;
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
		UI.prepare(menu);
		if (view == btnAudioSink) {
			menu.add(0, MNU_AUDIOSINK_DEVICE, 0, getAudioSinkDescription(Player.AUDIO_SINK_DEVICE, false))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((audioSink == Player.AUDIO_SINK_DEVICE) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			menu.add(0, MNU_AUDIOSINK_WIRE, 1, getAudioSinkDescription(Player.AUDIO_SINK_WIRE, false))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((audioSink == Player.AUDIO_SINK_WIRE) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
			menu.add(0, MNU_AUDIOSINK_BT, 2, getAudioSinkDescription(Player.AUDIO_SINK_BT, false))
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable((audioSink == Player.AUDIO_SINK_BT) ? UI.ICON_RADIOCHK : UI.ICON_RADIOUNCHK));
		} else {
			menu.add(0, MNU_ZEROPRESET, 0, R.string.zero_preset)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable(UI.ICON_REMOVE));
			menu.add(0, MNU_LOADPRESET, 1, R.string.load_preset)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable(UI.ICON_LOAD));
			menu.add(0, MNU_SAVEPRESET, 2, R.string.save_preset)
				.setOnMenuItemClickListener(this)
				.setIcon(new TextIconDrawable(UI.ICON_SAVE));
		}
	}
	
	@Override
	public boolean onMenuItemClick(MenuItem item) {
		switch (item.getItemId()) {
		case MNU_ZEROPRESET:
			for (int i = Equalizer.getBandCount() - 1; i >= 0; i--)
				Equalizer.setBandLevel(i, 0, audioSink);
			BassBoost.setStrength(0, audioSink);
			Virtualizer.setStrength(0, audioSink);
			Player.commitAllEffects(audioSink);
			updateEffects();
			break;
		case MNU_LOADPRESET:
			startActivity(ActivityFileSelection.createPresetSelector(getHostActivity(), getText(R.string.load_preset), MNU_LOADPRESET, false, false, this), 0, null, false);
			break;
		case MNU_SAVEPRESET:
			startActivity(ActivityFileSelection.createPresetSelector(getHostActivity(), getText(R.string.save_preset), MNU_SAVEPRESET, true, false, this), 0, null, false);
			break;
		case MNU_AUDIOSINK_DEVICE:
			audioSink = Player.AUDIO_SINK_DEVICE;
			storedAudioSink = audioSink;
			updateEffects();
			break;
		case MNU_AUDIOSINK_WIRE:
			audioSink = Player.AUDIO_SINK_WIRE;
			storedAudioSink = audioSink;
			updateEffects();
			break;
		case MNU_AUDIOSINK_BT:
			audioSink = Player.AUDIO_SINK_BT;
			storedAudioSink = audioSink;
			updateEffects();
			break;
		}
		return true;
	}
	
	@Override
	public void onClick(View view) {
		if (view == btnGoBack) {
			finish(0, view, true);
		} else if (view == btnAudioSink) {
			CustomContextMenu.openContextMenu(btnAudioSink, this);
		} else if (view == btnMenu) {
			CustomContextMenu.openContextMenu(btnMenu, this);
		} else if (view == btnChangeEffect) {
			Player.bassBoostMode = !Player.bassBoostMode;
			prepareViewForMode(false);
		} else if (view == chkEqualizer) {
			if (enablingEffect)
				return;
			if (!Equalizer.isSupported()) {
				chkEqualizer.setChecked(false);
				UI.toast(getApplication(), R.string.effect_not_supported);
				return;
			}
			enablingEffect = true;
			Player.enableEffects(chkEqualizer.isChecked(), chkBass.isChecked(), chkVirtualizer.isChecked(), audioSink, this);
		} else if (view == chkBass) {
			if (enablingEffect)
				return;
			if (!BassBoost.isSupported()) {
				chkBass.setChecked(false);
				UI.toast(getApplication(), R.string.effect_not_supported);
				return;
			}
			enablingEffect = true;
			Player.enableEffects(chkEqualizer.isChecked(), chkBass.isChecked(), chkVirtualizer.isChecked(), audioSink, this);
		} else if (view == chkVirtualizer) {
			if (enablingEffect)
				return;
			if (!Virtualizer.isSupported()) {
				chkVirtualizer.setChecked(false);
				UI.toast(getApplication(), R.string.effect_not_supported);
				return;
			}
			enablingEffect = true;
			Player.enableEffects(chkEqualizer.isChecked(), chkBass.isChecked(), chkVirtualizer.isChecked(), audioSink, this);
		}
	}
	
	@Override
	public void run() {
		//the effects have just been reset!
		enablingEffect = false;
		chkEqualizer.setChecked(Equalizer.isEnabled(audioSink));
		chkBass.setChecked(BassBoost.isEnabled(audioSink));
		chkVirtualizer.setChecked(Virtualizer.isEnabled(audioSink));
	}
	
	private void initBarsAndFrequencies(int bandCount) {
		clearBarsAndFrequencies();
		min = Equalizer.getMinBandLevel();
		max = Equalizer.getMaxBandLevel();
		frequencies = new int[bandCount];
		bars = new BgSeekBar[bandCount];
		for (int i = bandCount - 1; i >= 0; i--)
			frequencies[i] = Equalizer.getBandFrequency(i);
	}
	
	private void clearBarsAndFrequencies() {
		frequencies = null;
		if (bars != null) {
			for (int i = bars.length - 1; i >= 0; i--)
				bars[i] = null;
			bars = null;
		}
	}
	
	@Override
	protected void onCreate() {
		txtBuilder = new StringBuilder(32);
		final int _600dp = UI.dpToPxI(600);
		screenNotSoLarge = ((UI.screenWidth < _600dp) || (UI.screenHeight < _600dp));
	}
	
	@SuppressWarnings("deprecation")
	@Override
	protected void onCreateLayout(boolean firstCreation) {
		setContentView(R.layout.activity_effects);
		audioSink = (storedAudioSink <= 0 ? Player.localAudioSinkUsedInEffects : storedAudioSink);
		storedAudioSink = audioSink;
		panelControls = (LinearLayout)findViewById(R.id.panelControls);
		panelControls.setBackgroundDrawable(new ColorDrawable(UI.color_list_original));
		panelEqualizer = (LinearLayout)findViewById(R.id.panelEqualizer);
		panelSecondary = (ViewGroup)findViewById(R.id.panelSecondary);
		if (panelSecondary instanceof ScrollView) {
			panelSecondary.setHorizontalFadingEdgeEnabled(false);
			panelSecondary.setVerticalFadingEdgeEnabled(false);
			panelSecondary.setFadingEdgeLength(0);
		}
		btnGoBack = (BgButton)findViewById(R.id.btnGoBack);
		btnGoBack.setOnClickListener(this);
		btnGoBack.setIcon(UI.ICON_GOBACK);
		btnAudioSink = (BgButton)findViewById(R.id.btnAudioSink);
		btnAudioSink.setOnClickListener(this);
		btnAudioSink.setDefaultHeight();
		btnAudioSink.setCompoundDrawables(new TextIconDrawable(UI.ICON_SETTINGS, UI.color_text, UI.defaultControlContentsSize), null, null, null);
		chkEqualizer = (BgButton)findViewById(R.id.chkEqualizer);
		chkEqualizer.setOnClickListener(this);
		chkEqualizer.setTextColor(UI.colorState_text_listitem_reactive);
		chkEqualizer.formatAsLabelledCheckBox();
		chkBass = (BgButton)findViewById(R.id.chkBass);
		chkBass.setOnClickListener(this);
		chkBass.setTextColor(UI.colorState_text_listitem_reactive);
		chkBass.formatAsLabelledCheckBox();
		chkVirtualizer = (BgButton)findViewById(R.id.chkVirtualizer);
		chkVirtualizer.setOnClickListener(this);
		chkVirtualizer.setTextColor(UI.colorState_text_listitem_reactive);
		chkVirtualizer.formatAsLabelledCheckBox();
		btnMenu = (BgButton)findViewById(R.id.btnMenu);
		btnMenu.setOnClickListener(this);
		btnMenu.setIcon(UI.ICON_MENU);
		btnChangeEffect = (BgButton)findViewById(R.id.btnChangeEffect);
		if (btnChangeEffect != null) {
			btnChangeEffect.setOnClickListener(this);
			btnChangeEffect.setCompoundDrawables(new TextIconDrawable(UI.ICON_EQUALIZER, UI.color_text_listitem, UI.defaultControlContentsSize), null, null, null);
			btnChangeEffect.setMinimumHeight(UI.defaultControlSize);
			btnChangeEffect.setTextColor(UI.colorState_text_listitem_reactive);
		} else {
			UI.isLargeScreen = true;
			Player.bassBoostMode = false;
		}
		barBass = (BgSeekBar)findViewById(R.id.barBass);
		barBass.setMax(BassBoost.getMaxStrength());
		barBass.setValue(BassBoost.getStrength(audioSink));
		barBass.setKeyIncrement(BassBoost.getMaxStrength() / 50);
		barBass.setOnBgSeekBarChangeListener(this);
		barBass.setInsideList(true);
		barBass.setAdditionalContentDescription(getText(R.string.bass_boost).toString());
		barVirtualizer = (BgSeekBar)findViewById(R.id.barVirtualizer);
		barVirtualizer.setMax(Virtualizer.getMaxStrength());
		barVirtualizer.setValue(Virtualizer.getStrength(audioSink));
		barVirtualizer.setKeyIncrement(Virtualizer.getMaxStrength() / 50);
		barVirtualizer.setOnBgSeekBarChangeListener(this);
		barVirtualizer.setInsideList(true);
		barVirtualizer.setAdditionalContentDescription(getText(R.string.virtualization).toString());
		LinearLayout.LayoutParams lp;
		if (!UI.isLargeScreen && UI.isLandscape) {
			if (btnChangeEffect != null) {
				lp = (LinearLayout.LayoutParams)btnChangeEffect.getLayoutParams();
				lp.topMargin = 0;
				btnChangeEffect.setLayoutParams(lp);
			}
			lp = (LinearLayout.LayoutParams)chkEqualizer.getLayoutParams();
			lp.bottomMargin = 0;
			chkEqualizer.setLayoutParams(lp);
			lp = (LinearLayout.LayoutParams)chkBass.getLayoutParams();
			lp.bottomMargin = 0;
			chkBass.setLayoutParams(lp);
			lp = (LinearLayout.LayoutParams)chkVirtualizer.getLayoutParams();
			lp.bottomMargin = 0;
			chkVirtualizer.setLayoutParams(lp);
			lp = (LinearLayout.LayoutParams)barBass.getLayoutParams();
			lp.bottomMargin = UI.controlMargin;
			barBass.setLayoutParams(lp);
			lp = (LinearLayout.LayoutParams)barVirtualizer.getLayoutParams();
			lp.bottomMargin = UI.controlMargin;
			barVirtualizer.setLayoutParams(lp);
			panelControls.setPadding(UI.controlLargeMargin, UI.thickDividerSize, UI.controlLargeMargin, 0);
		} else if (UI.isLargeScreen) {
			UI.prepareViewPaddingForLargeScreen(panelControls, 0, (screenNotSoLarge && !UI.isLandscape) ? UI.controlMargin : UI.controlLargeMargin);
			if (!UI.isLandscape && (panelControls instanceof LinearLayout)) {
				panelControls.setOrientation(LinearLayout.VERTICAL);
				panelControls.setWeightSum(0);
				final int margin = (screenNotSoLarge ? UI.controlMargin : (UI.controlLargeMargin << 1));
				lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
				lp.leftMargin = margin;
				lp.topMargin = margin;
				lp.rightMargin = margin;
				lp.bottomMargin = margin;
				panelSecondary.setLayoutParams(lp);
				lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0);
				lp.weight = 1;
				lp.leftMargin = margin;
				lp.topMargin = (screenNotSoLarge ? UI.controlLargeMargin : margin);
				lp.rightMargin = margin;
				lp.bottomMargin = margin;
				panelEqualizer.setLayoutParams(lp);
				if (screenNotSoLarge) {
					lp = (LinearLayout.LayoutParams)barBass.getLayoutParams();
					lp.bottomMargin = UI.controlLargeMargin;
					barBass.setLayoutParams(lp);
					lp = (LinearLayout.LayoutParams)barVirtualizer.getLayoutParams();
					lp.bottomMargin = UI.controlLargeMargin;
					barVirtualizer.setLayoutParams(lp);
				}
			}
		} else {
			panelControls.setPadding(UI.controlMargin, UI.thickDividerSize, UI.controlMargin, 0);
		}
		UI.prepareControlContainer(findViewById(R.id.panelTop), false, true);
		prepareViewForMode(true);
	}

	@Override
	protected void onResume() {
		Player.observer = this;
	}

	@Override
	protected void onPause() {
		Player.observer = null;
	}

	@Override
	protected void onOrientationChanged() {
		onCleanupLayout();
		onCreateLayout(false);
	}

	@Override
	protected void onCleanupLayout() {
		UI.animationReset();
		panelControls = null;
		panelEqualizer = null;
		panelSecondary = null;
		panelBars = null;
		chkEqualizer = null;
		chkBass = null;
		chkVirtualizer = null;
		btnGoBack = null;
		btnMenu = null;
		btnChangeEffect = null;
		if (bars != null) {
			for (int i = bars.length - 1; i >= 0; i--)
				bars[i] = null;
		}
	}
	
	@Override
	protected void onDestroy() {
	}
	
	@Override
	public void onValueChanged(BgSeekBar seekBar, int value, boolean fromUser, boolean usingKeys) {
		if (!fromUser)
			return;
		if (seekBar == barBass) {
			BassBoost.setStrength(value, audioSink);
			seekBar.setText(format(BassBoost.getStrength(audioSink)));
		} else if (seekBar == barVirtualizer) {
			Virtualizer.setStrength(value, audioSink);
			seekBar.setText(format(Virtualizer.getStrength(audioSink)));
		} else if (bars != null && frequencies != null) {
			for (int i = bars.length - 1; i >= 0; i--) {
				if (seekBar == bars[i]) {
					int level = (10 * value) + min;
					if (!usingKeys && (level <= LevelThreshold) && (level >= -LevelThreshold)) {
						level = 0;
						seekBar.setValue(-min / 10);
					}
					Equalizer.setBandLevel(i, level, audioSink);
					seekBar.setText(format(frequencies[i], Equalizer.getBandLevel(i, audioSink)));
					return;
				}
			}
		}
	}
	
	@Override
	public boolean onStartTrackingTouch(BgSeekBar seekBar) {
		return true;
	}
	
	@Override
	public void onStopTrackingTouch(BgSeekBar seekBar, boolean cancelled) {
		if (seekBar == barBass) {
			Player.commitBassBoost(audioSink);
		} else if (seekBar == barVirtualizer) {
			Player.commitVirtualizer(audioSink);
		} else if (bars != null) {
			for (int i = bars.length - 1; i >= 0; i--) {
				if (seekBar == bars[i]) {
					Player.commitEqualizer(i, audioSink);
					return;
				}
			}
		}
	}
	
	private void updateEffects() {
		if (chkEqualizer != null)
			chkEqualizer.setChecked(Equalizer.isEnabled(audioSink));
		if (bars != null && frequencies != null) {
			for (int i = bars.length - 1; i >= 0; i--) {
				final BgSeekBar bar = bars[i];
				if (bar != null) {
					final int level = Equalizer.getBandLevel(i, audioSink);
					bars[i].setText(format(frequencies[i], level));
					bars[i].setValue(((level <= LevelThreshold) && (level >= -LevelThreshold)) ? (-min / 10) : ((level - min) / 10));
				}
			}
		}
		if (chkBass != null)
			chkBass.setChecked(BassBoost.isEnabled(audioSink));
		if (barBass != null) {
			barBass.setValue(BassBoost.getStrength(audioSink));
			barBass.setText(format(BassBoost.getStrength(audioSink)));
		}
		if (chkVirtualizer != null)
			chkVirtualizer.setChecked(Virtualizer.isEnabled(audioSink));
		if (barVirtualizer != null) {
			barVirtualizer.setValue(Virtualizer.getStrength(audioSink));
			barVirtualizer.setText(format(Virtualizer.getStrength(audioSink)));
		}
		if (btnAudioSink != null)
			btnAudioSink.setText(getAudioSinkDescription(audioSink, true));
	}
	
	private void prepareViewForMode(boolean isCreatingLayout) {
		LinearLayout.LayoutParams lp;
		final Context ctx = getApplication();
		updateEffects();
		if (Player.bassBoostMode) {
			UI.animationReset();
			UI.animationAddViewToHide(panelEqualizer);
			UI.animationAddViewToShow(panelSecondary);
			UI.animationCommit(isCreatingLayout, null);
			btnGoBack.setNextFocusDownId(R.id.chkBass);
			btnAudioSink.setNextFocusDownId(R.id.chkBass);
			btnMenu.setNextFocusRightId(R.id.chkBass);
			btnMenu.setNextFocusDownId(R.id.chkBass);
			UI.setNextFocusForwardId(btnMenu, R.id.chkBass);
			btnChangeEffect.setNextFocusUpId(R.id.barVirtualizer);
			btnChangeEffect.setNextFocusLeftId(R.id.barVirtualizer);
		} else {
			if (btnChangeEffect != null) {
				UI.animationReset();
				UI.animationAddViewToHide(panelSecondary);
				UI.animationAddViewToShow(panelEqualizer);
				UI.animationCommit(isCreatingLayout, null);
			}
			chkEqualizer.setChecked(Equalizer.isEnabled(audioSink));
			
			final int bandCount = Equalizer.getBandCount();
			if (bars == null || frequencies == null || bars.length < bandCount || frequencies.length < bandCount)
				initBarsAndFrequencies(bandCount);
			int availableScreenW = getDecorViewWidth();
			int hMargin = getDecorViewHeight();
			//some times, when rotating the device, the decorview takes a little longer to be updated
			if (UI.isLandscape != (availableScreenW >= hMargin))
				availableScreenW = hMargin;
			hMargin = ((UI.isLandscape || UI.isLargeScreen) ? UI.spToPxI(32) : UI.spToPxI(16));
			if (UI.usableScreenWidth > 0 && availableScreenW > UI.usableScreenWidth)
				availableScreenW = UI.usableScreenWidth;
			if (UI.isLargeScreen) {
				availableScreenW -= ((UI.getViewPaddingForLargeScreen() << 1) + (UI.controlLargeMargin << 1));
				if (UI.isLandscape)
					availableScreenW >>= 1;
			} else {
				availableScreenW -= (UI.controlMargin << 1);
			}
			while (hMargin > UI._1dp && ((bandCount * UI.defaultControlSize) + ((bandCount - 1) * hMargin)) > availableScreenW)
				hMargin--;
			int size = 0, textSize = 0, bgY = 0, y = 0;
			if (hMargin <= UI._1dp) {
				//oops... the bars didn't fit inside the screen... we must adjust everything!
				hMargin = ((bandCount >= 10) ? UI._1dp : UI.controlSmallMargin);
				size = UI.defaultControlSize - 1;
				while (size > UI._4dp && ((bandCount * size) + ((bandCount - 1) * hMargin)) > availableScreenW)
					size--;
				textSize = size - (UI.controlMargin << 1) - (UI.strokeSize << 1) - (UI._1dp << 1);
				if (textSize < UI._4dp)
					textSize = UI._4dp;
				UI.textPaint.setTextSize(textSize);
				final FontMetrics fm = UI.textPaint.getFontMetrics();
				final int box = (int)(fm.descent - fm.ascent + 0.5f);
				final int yInBox = box - (int)(fm.descent);
				bgY = (size - box) >> 1;
				y = bgY + yInBox;
			}
			if (panelBars == null) {
				panelBars = new LinearLayout(ctx);
				lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
				panelBars.setLayoutParams(lp);
				panelBars.setOrientation(LinearLayout.HORIZONTAL);
				
				for (int i = 0; i < bandCount; i++) {
					final int level = Equalizer.getBandLevel(i, audioSink);
					final BgSeekBar bar = new BgSeekBar(ctx);
					bar.setVertical(true);
					final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT);
					if (i > 0)
						p.leftMargin = hMargin;
					bar.setLayoutParams(p);
					bar.setMax((max - min) / 10);
					bar.setText(format(frequencies[i], level));
					bar.setKeyIncrement(10);
					bar.setValue(((level <= LevelThreshold) && (level >= -LevelThreshold)) ? (-min / 10) : ((level - min) / 10));
					bar.setOnBgSeekBarChangeListener(this);
					bar.setInsideList(true);
					if (size != 0)
						bar.setSize(size, textSize, bgY, y);
					bars[i] = bar;
					bar.setId(i + 1);
					bar.setNextFocusLeftId(i);
					bar.setNextFocusRightId(i + 2);
					UI.setNextFocusForwardId(bar, i + 2);
					bar.setNextFocusDownId(R.id.btnChangeEffect);
					bar.setNextFocusUpId(R.id.chkEqualizer);
					
					panelBars.addView(bar);
				}
				if (bars != null && bars.length > 0)
					bars[0].setNextFocusLeftId(R.id.chkEqualizer);
				panelEqualizer.addView(panelBars);
			}
			if (btnChangeEffect != null) {
				if (bars != null && bars.length > 0) {
					bars[bandCount - 1].setNextFocusRightId(R.id.btnChangeEffect);
					UI.setNextFocusForwardId(bars[bandCount - 1], R.id.btnChangeEffect);
				}
				btnGoBack.setNextFocusDownId(R.id.chkEqualizer);
				btnAudioSink.setNextFocusDownId(R.id.chkEqualizer);
				btnMenu.setNextFocusRightId(R.id.chkEqualizer);
				btnMenu.setNextFocusDownId(R.id.chkEqualizer);
				UI.setNextFocusForwardId(btnMenu, R.id.chkEqualizer);
				btnChangeEffect.setNextFocusUpId(bandCount);
				btnChangeEffect.setNextFocusLeftId(bandCount);
			} else {
				if (bars != null && bars.length > 0) {
					bars[bandCount - 1].setNextFocusRightId(R.id.chkBass);
					UI.setNextFocusForwardId(bars[bandCount - 1], R.id.chkBass);
				}
				chkBass.setNextFocusLeftId(bandCount);
			}
			chkEqualizer.setNextFocusRightId(1);
			chkEqualizer.setNextFocusDownId(1);
			UI.setNextFocusForwardId(chkEqualizer, 1);
		}
	}
	
	@Override
	public void onFileSelected(int id, FileSt file) {
		if (id == MNU_LOADPRESET) {
			final SerializableMap opts = SerializableMap.deserialize(getApplication(), file.path);
			if (opts != null) {
				Equalizer.deserialize(opts, audioSink);
				BassBoost.deserialize(opts, audioSink);
				Virtualizer.deserialize(opts, audioSink);
				Player.commitAllEffects(audioSink);
				updateEffects();
			}
		} else {
			final SerializableMap opts = new SerializableMap(Equalizer.getBandCount() << 1);
			Equalizer.serialize(opts, audioSink);
			BassBoost.serialize(opts, audioSink);
			Virtualizer.serialize(opts, audioSink);
			opts.serialize(getApplication(), file.path);
		}
	}

	@Override
	public void onAddClicked(int id, FileSt file) {
	}

	@Override
	public void onPlayClicked(int id, FileSt file) {
	}

	@Override
	public boolean onDeleteClicked(int id, FileSt file) {
		return false;
	}

	@Override
	public void onPlayerChanged(Song currentSong, boolean songHasChanged, boolean preparingHasChanged, Throwable ex) {
	}

	@Override
	public void onPlayerMetadataChanged(Song currentSong) {
	}

	@Override
	public void onPlayerControlModeChanged(boolean controlMode) {
	}

	@Override
	public void onPlayerGlobalVolumeChanged(int volume) {
	}

	@Override
	public void onPlayerAudioSinkChanged() {
		audioSink = Player.localAudioSinkUsedInEffects;
		storedAudioSink = audioSink;
		updateEffects();
	}

	@Override
	public void onPlayerMediaButtonPrevious() {
	}

	@Override
	public void onPlayerMediaButtonNext() {
	}
}
