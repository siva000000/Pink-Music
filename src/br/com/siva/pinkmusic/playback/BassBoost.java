//
// Pink Music Android is distributed under the FreeBSD License
//
// Copyright (c) 2013-2015, Siva Prasad
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice, this
//    list of conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright notice,
//    this list of conditions and the following disclaimer in the documentation
//    and/or other materials provided with the distribution.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
// ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
// WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
// DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
// ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
// (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
// LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
// ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
//
// The views and conclusions contained in the software and documentation are those
// of the authors and should not be interpreted as representing official policies,
// either expressed or implied, of the FreeBSD Project.
//

package br.com.siva.pinkmusic.playback;

import br.com.siva.pinkmusic.util.SerializableMap;

public final class BassBoost {
	private static int sessionId = Integer.MIN_VALUE, strength, strength_wire, strength_bt;
	private static boolean enabled, enabled_wire, enabled_bt, strengthSupported, supported;
	private static android.media.audiofx.BassBoost theBooster;

	public static void deserialize(SerializableMap opts, int audioSink) {
		//use OPTBIT_BASSBOOST_ENABLED and OPT_BASSBOOST_STRENGTH for all audio sinks!!!
		switch (audioSink) {
		case Player.AUDIO_SINK_WIRE:
			enabled_wire = opts.getBit(Player.OPTBIT_BASSBOOST_ENABLED);
			strength_wire = opts.getInt(Player.OPT_BASSBOOST_STRENGTH);
			break;
		case Player.AUDIO_SINK_BT:
			enabled_bt = opts.getBit(Player.OPTBIT_BASSBOOST_ENABLED);
			strength_bt = opts.getInt(Player.OPT_BASSBOOST_STRENGTH);
			break;
		default:
			enabled = opts.getBit(Player.OPTBIT_BASSBOOST_ENABLED);
			strength = opts.getInt(Player.OPT_BASSBOOST_STRENGTH);
			break;
		}
	}

	public static void serialize(SerializableMap opts, int audioSink) {
		//use OPTBIT_BASSBOOST_ENABLED and OPT_BASSBOOST_STRENGTH for all audio sinks!!!
		switch (audioSink) {
		case Player.AUDIO_SINK_WIRE:
			opts.putBit(Player.OPTBIT_BASSBOOST_ENABLED, enabled_wire);
			opts.put(Player.OPT_BASSBOOST_STRENGTH, strength_wire);
			break;
		case Player.AUDIO_SINK_BT:
			opts.putBit(Player.OPTBIT_BASSBOOST_ENABLED, enabled_bt);
			opts.put(Player.OPT_BASSBOOST_STRENGTH, strength_bt);
			break;
		default:
			opts.putBit(Player.OPTBIT_BASSBOOST_ENABLED, enabled);
			opts.put(Player.OPT_BASSBOOST_STRENGTH, strength);
			break;
		}
	}

	static void loadConfig(SerializableMap opts) {
		enabled = opts.getBit(Player.OPTBIT_BASSBOOST_ENABLED);
		//use the regular enabled flag as the default for the new presets
		enabled_wire = opts.getBit(Player.OPTBIT_BASSBOOST_ENABLED_WIRE, enabled);
		enabled_bt = opts.getBit(Player.OPTBIT_BASSBOOST_ENABLED_BT, enabled);
		strength = opts.getInt(Player.OPT_BASSBOOST_STRENGTH);
		//use the regular strength as the default for the new presets
		strength_wire = opts.getInt(Player.OPT_BASSBOOST_STRENGTH_WIRE, strength);
		strength_bt = opts.getInt(Player.OPT_BASSBOOST_STRENGTH_BT, strength);
	}

	static void saveConfig(SerializableMap opts) {
		opts.putBit(Player.OPTBIT_BASSBOOST_ENABLED, enabled);
		opts.putBit(Player.OPTBIT_BASSBOOST_ENABLED_WIRE, enabled_wire);
		opts.putBit(Player.OPTBIT_BASSBOOST_ENABLED_BT, enabled_bt);
		opts.put(Player.OPT_BASSBOOST_STRENGTH, strength);
		opts.put(Player.OPT_BASSBOOST_STRENGTH_WIRE, strength_wire);
		opts.put(Player.OPT_BASSBOOST_STRENGTH_BT, strength_bt);
	}

	static void _initialize(int newSessionId) {
		if (newSessionId != Integer.MIN_VALUE)
			sessionId = newSessionId;
		try {
			theBooster = new android.media.audiofx.BassBoost(0, sessionId);
			strengthSupported = theBooster.getStrengthSupported();
			supported = true;
		} catch (Throwable ex) {
			ex.printStackTrace();
			supported = false;
		}
	}

	static void _release() {
		if (theBooster != null) {
			try {
				theBooster.release();
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
			theBooster = null;
		}
	}

	public static boolean isSupported() {
		return supported;
	}

	public static boolean isEnabled(int audioSink) {
		return ((audioSink == Player.AUDIO_SINK_WIRE) ? enabled_wire : ((audioSink == Player.AUDIO_SINK_BT) ? enabled_bt : enabled));
	}

	public static int getMaxStrength() {
		return 1000;
	}

	public static int getStrength(int audioSink) {
		return ((audioSink == Player.AUDIO_SINK_WIRE) ? strength_wire : ((audioSink == Player.AUDIO_SINK_BT) ? strength_bt : strength));
	}

	public static void setStrength(int strength, int audioSink) {
		if (strength > 1000)
			strength = 1000;
		else if (strength < 0)
			strength = 0;
		switch (audioSink) {
		case Player.AUDIO_SINK_WIRE:
			BassBoost.strength_wire = strength;
			break;
		case Player.AUDIO_SINK_BT:
			BassBoost.strength_bt = strength;
			break;
		default:
			BassBoost.strength = strength;
			break;
		}
	}

	static void _setEnabled(boolean enabled, int audioSink) {
		switch (audioSink) {
		case Player.AUDIO_SINK_WIRE:
			BassBoost.enabled_wire = enabled;
			break;
		case Player.AUDIO_SINK_BT:
			BassBoost.enabled_bt = enabled;
			break;
		default:
			BassBoost.enabled = enabled;
			break;
		}
		if (theBooster == null || audioSink != Player.audioSinkUsedInEffects)
			return;
		try {
			if (!enabled) {
				theBooster.setEnabled(false);
			} else if (sessionId != Integer.MIN_VALUE) {
				if (!strengthSupported) {
					switch (audioSink) {
					case Player.AUDIO_SINK_WIRE:
						strength_wire = 1000;
						break;
					case Player.AUDIO_SINK_BT:
						strength_bt = 1000;
						break;
					default:
						strength = 1000;
						break;
					}
				}
				_commit(audioSink);
				theBooster.setEnabled(true);
			}
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
		enabled = theBooster.getEnabled();
		switch (audioSink) {
		case Player.AUDIO_SINK_WIRE:
			BassBoost.enabled_wire = enabled;
			break;
		case Player.AUDIO_SINK_BT:
			BassBoost.enabled_bt = enabled;
			break;
		default:
			BassBoost.enabled = enabled;
			break;
		}
	}

	static void _commit(int audioSink) {
		if (theBooster == null || audioSink != Player.audioSinkUsedInEffects)
			return;
		try {
			switch (audioSink) {
			case Player.AUDIO_SINK_WIRE:
				theBooster.setStrength(strengthSupported ? (short)strength_wire : (short)((strength_wire == 0) ? 0 : 1000));
				strength_wire = theBooster.getRoundedStrength();
				break;
			case Player.AUDIO_SINK_BT:
				theBooster.setStrength(strengthSupported ? (short)strength_bt : (short)((strength_bt == 0) ? 0 : 1000));
				strength_bt = theBooster.getRoundedStrength();
				break;
			default:
				theBooster.setStrength(strengthSupported ? (short)strength : (short)((strength == 0) ? 0 : 1000));
				strength = theBooster.getRoundedStrength();
				break;
			}
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
	}
}
