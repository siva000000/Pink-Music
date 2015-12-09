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

package br.com.siva.pinkmusic.list;

import android.content.Context;
import android.os.Message;
import android.view.View;
import android.view.ViewGroup;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Random;

import br.com.siva.pinkmusic.activity.MainHandler;
import br.com.siva.pinkmusic.playback.Player;
import br.com.siva.pinkmusic.ui.SongView;
import br.com.siva.pinkmusic.util.ArraySorter;
import br.com.siva.pinkmusic.util.ArraySorter.Comparer;
import br.com.siva.pinkmusic.util.Serializer;

//All methods of this class MUST BE called from the main thread, except those otherwise noted!!!
public final class SongList extends BaseList<Song> implements Comparer<Song> {
	public static final int SORT_BY_TITLE = 0;
	public static final int SORT_BY_ARTIST = 1;
	public static final int SORT_BY_ALBUM = 2;
	public static final int HOW_CURRENT = -4;
	public static final int HOW_PREVIOUS = -3;
	public static final int HOW_NEXT_MANUAL = -2;
	public static final int HOW_NEXT_AUTO = -1;
	public static final int REPEAT_ALL = 0;
	public static final int REPEAT_ONE = 1;
	public static final int REPEAT_NONE = 2;

	private static final int MAX_COUNT = 2048;

	private static final int MSG_ADD_SONGS = 0x0700;
	private static final int MSG_FINISHED_ADDING = 0x0701;

	private volatile int adding;
	private int currentShuffledItemIndex, shuffledItemsAlreadyPlayed, indexOfPreviouslyDeletedCurrentShuffledItem, sortMode, repeatMode;
	public boolean selecting, moving, okToTurnOffAfterReachingTheEnd;
	private Song[] shuffledList;
	public Song possibleNextSong;
	private static final SongList theSongList = new SongList();
	
	private SongList() {
		super(Song.class, MAX_COUNT);
		this.adding = 0;
		this.indexOfPreviouslyDeletedCurrentShuffledItem = -1;
	}
	
	public static SongList getInstance() {
		return theSongList;
	}
	
	//--------------------------------------------------------------------------------------------
	//These six methods are the only ones that can be called from any thread (all the others must be called from the main thread)
	public void startDeserializing(final Context context, final String path, final boolean entireListBeingLoaded, final boolean append, final boolean play) {
		try {
			addingStarted();
			(new Thread("List Deserializer Thread") {
				private boolean done;
				private int current;
				private Song[] songs;
				private Throwable ex;
				@Override
				public void run() {
					if (done) {
						if (songs == null)
							songs = new Song[0];
						deserializationEnded(songs, current, entireListBeingLoaded, append, play, ex);
						songs = null;
						ex = null;
					} else {
						FileInputStream fs = null;
						BufferedInputStream bs = null;
						try {
							fs = context.openFileInput((path == null) ? "_List" : path);
							bs = new BufferedInputStream(fs, 4096);
							current = Serializer.deserializeInt(bs);
							final int version = Serializer.deserializeInt(bs);
							final int count = Serializer.deserializeInt(bs);
							if (version == 0x0100 && count > 0) {
								songs = new Song[count];
								for (int i = 0; i < count; i++)
									songs[i] = Song.deserialize(bs);
							}
							done = true;
							MainHandler.postToMainThread(this);
						} catch (Throwable ex) {
							songs = null;
							done = true;
							if (!(ex instanceof FileNotFoundException))
								this.ex = ex;
							MainHandler.postToMainThread(this);
						} finally {
							addingEnded();
							try {
								if (bs != null)
									bs.close();
							} catch (Throwable ex) {
								ex.printStackTrace();
							}
							try {
								if (fs != null)
									fs.close();
							} catch (Throwable ex) {
								ex.printStackTrace();
							}
						}
					}
				}
			}).start();
		} catch (Throwable ex) {
			addingEnded();
		}
	}
	
	public void addingStarted() {
		synchronized (currentAndCountMutex) {
			adding++;
		}
	}
	
	public void addingEnded() {
		synchronized (currentAndCountMutex) {
			adding = ((adding <= 1) ? 0 : (adding - 1));
		}
	}
	
	public boolean isAdding() {
		return (adding > 0);
	}

	//addFiles MUST NOT be called from the main thread!
	public void addFiles(FileSt[] files, Iterator<FileSt> iterator, int count, boolean play, boolean isAddingFolder, boolean addAsURL, boolean forceScrollIntoView) {
		if (((files == null || files.length == 0) && (iterator == null)) || count <= 0 || Player.state >= Player.STATE_TERMINATING)
			return;
		if (files != null && count > files.length)
			count = files.length;
		class Closure implements MainHandler.Callback {
			public Song[] songs;
			private int idx, positionToSelect;
			private boolean firstTime;
			private final boolean clearList, playAfterwards, forceScrollIntoView;
			Closure(int count, boolean clearList, boolean playAfterwards, boolean forceScrollIntoView) {
				songs = new Song[count];
				idx = 0;
				firstTime = true;
				this.clearList = clearList;
				this.playAfterwards = playAfterwards;
				this.forceScrollIntoView = forceScrollIntoView;
			}
			@Override
			public boolean handleMessage(Message msg) {
				if (Player.state >= Player.STATE_TERMINATING)
					return true;
				if (firstTime) {
					if (clearList)
						clear();
					positionToSelect = getCount();
				}
				int localCount = msg.arg1;
				if (localCount > songs.length)
					localCount = songs.length;
				if (idx < localCount) {
					add(-1, songs, idx, localCount - idx);
					idx = localCount;
				}
				if (firstTime) {
					firstTime = false;
					Player.setSelectionAfterAdding(positionToSelect);
					if (playAfterwards)
						Player.play(positionToSelect);
					if (forceScrollIntoView && listObserver != null)
						listObserver.centerItem(positionToSelect);
				}
				if (msg.what == MSG_FINISHED_ADDING) {
					for (int i = localCount - 1; i >= 0; i--)
						songs[i] = null;
					songs = null;
					System.gc();
				}
				return true;
			}
		}
		if (count > 0) {
			final Closure c = new Closure(count, play && isAddingFolder && Player.clearListWhenPlayingFolders, play, forceScrollIntoView);
			int f = 0;
			if (addAsURL) {
				if (files != null) {
					for (int i = 0; i < count; i++) {
						if (!files[i].isDirectory) {
							c.songs[f] = new Song(files[i].path, files[i].name);
							f++;
							if ((f & 3) == 1) {
								if (Player.state >= Player.STATE_TERMINATING || SongList.this.count >= MAX_COUNT)
									break;
								MainHandler.sendMessage(c, MSG_ADD_SONGS, f, 0);
							}
						}
					}
				} else {
					while (iterator.hasNext()) {
						final FileSt file = iterator.next();
						if (!file.isDirectory) {
							c.songs[f] = new Song(file.path, file.name);
							f++;
							if ((f & 3) == 1) {
								if (Player.state >= Player.STATE_TERMINATING || SongList.this.count >= MAX_COUNT)
									break;
								MainHandler.sendMessage(c, MSG_ADD_SONGS, f, 0);
							}
						}
					}
				}
			} else {
				final byte[][] tmpPtr = new byte[][] { new byte[256] };
				if (files != null) {
					for (int i = 0; i < count; i++) {
						if (!files[i].isDirectory) {
							c.songs[f] = new Song(files[i], tmpPtr);
							f++;
							if ((f & 3) == 1) {
								if (Player.state >= Player.STATE_TERMINATING || SongList.this.count >= MAX_COUNT)
									break;
								MainHandler.sendMessage(c, MSG_ADD_SONGS, f, 0);
							}
						}
					}
				} else {
					while (iterator.hasNext()) {
						final FileSt file = iterator.next();
						if (!file.isDirectory) {
							c.songs[f] = new Song(file, tmpPtr);
							f++;
							if ((f & 3) == 1) {
								if (Player.state >= Player.STATE_TERMINATING || SongList.this.count >= MAX_COUNT)
									break;
								MainHandler.sendMessage(c, MSG_ADD_SONGS, f, 0);
							}
						}
					}
				}
			}
			if (Player.state < Player.STATE_TERMINATING)
				MainHandler.sendMessage(c, MSG_FINISHED_ADDING, f, 0);
		}
	}

	public boolean addPathAndForceScrollIntoView(final String path, final boolean play) {
		if (!FileFetcher.isFileAcceptable(path))
			return false;
		addingStarted();
		try {
			(new Thread("Path Adder Thread") {
				@Override
				public void run() {
					try {
						if (Player.state >= Player.STATE_TERMINATING)
							return;
						addFiles(new FileSt[] { new FileSt(new File(path)) }, null, 1, play, false, false, true);
					} catch (Throwable ex) {
						MainHandler.toast(ex);
					} finally {
						addingEnded();
					}
				}
			}).start();
			return true;
		} catch (Throwable ex) {
			ex.printStackTrace();
			addingEnded();
			return false;
		}
	}

	//--------------------------------------------------------------------------------------------

	private void fullSerialization(Context context, String path) throws IOException {
		FileOutputStream fs = null;
		BufferedOutputStream bs = null;
		try {
			fs = context.openFileOutput((path == null) ? "_List" : path, 0);
			bs = new BufferedOutputStream(fs, 4096);
			Serializer.serializeInt(bs, current);
			Serializer.serializeInt(bs, 0x0100);
			Serializer.serializeInt(bs, count);
			for (int i = 0; i < count; i++)
				items[i].serialize(bs);
			bs.flush();
		} finally {
			try {
				if (bs != null)
					bs.close();
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
			try {
				if (fs != null)
					fs.close();
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
		}
	}
	
	public boolean serialize(Context context, String path) {
		RandomAccessFile rf = null;
		try {
			if (modificationVersion > 1 || path != null) {
				fullSerialization(context, path);
			} else {
				try {
					final File f = context.getFileStreamPath("_List");
					rf = new RandomAccessFile(f, "rw");
					rf.seek(0);
					final byte[] buf = new byte[4];
					Serializer.serializeInt(buf, 0, current);
					rf.write(buf);
				} catch (Throwable ex) {
					try {
						if (rf != null)
							rf.close();
					} catch (Throwable ex2) {
						ex2.printStackTrace();
					}
					rf = null;
					fullSerialization(context, null);
					ex.printStackTrace();
				}
			}
		} catch (Throwable ex) {
			return false;
		} finally {
			try {
				if (rf != null)
					rf.close();
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
		}
		if (path == null)
			modificationVersion = 1;
		return true;
	}
	
	private void deserializationEnded(Song[] songs, int current, boolean entireListBeingLoaded, boolean append, boolean play, Throwable ex) {
		final int originalCount = count;
		int positionToSelect = -1;
		if (ex == null) {
			if (entireListBeingLoaded) {
				if (current >= 0)
					positionToSelect = originalCount + current;
			} else if (songs.length > 0) {
				if (append) {
					positionToSelect = originalCount;
				} else {
					positionToSelect = 0;
					clear();
				}
			}
			if (songs.length == 0)
				modificationVersion++; //increment here, since add() does not increment when count is 0
			else
				add(-1, songs, 0, songs.length);
			if (positionToSelect >= this.count) {
				positionToSelect = -1;
			} else if (positionToSelect >= 0) {
				if (entireListBeingLoaded || play) {
					this.current = positionToSelect;
					this.firstSel = positionToSelect;
					this.lastSel = positionToSelect;
					this.originalSel = positionToSelect;
				}
				if (listObserver != null)
					listObserver.centerItem(positionToSelect);
			}
		}
		Player.songListDeserialized((!append && (positionToSelect >= 0)) ? items[positionToSelect] : null, ((play && positionToSelect >= 0) ? positionToSelect : -1), positionToSelect, ex);
	}

	private Song getRandomSongAndSetCurrentInternal(int how) {
		if (shuffledItemsAlreadyPlayed >= count)
			setRandomModeInternal(true);
		int i;
		Song s = null;
		if (how == HOW_CURRENT) {
			how = ((indexOfPreviouslyDeletedCurrentShuffledItem >= 0) ? indexOfPreviouslyDeletedCurrentShuffledItem : currentShuffledItemIndex);
			if (how < 0) {
				how = current;
				if (how < 0 || how >= count) {
					how = 0;
				} else {
					s = items[how];
					for (i = count - 1; i >= 0; i--) {
						if (shuffledList[i] == s) {
							current = how;
							currentShuffledItemIndex = i;
							break;
						}
					}
				}
			} else if (how >= count) {
				how = count - 1;
			}
		} else if (how == HOW_PREVIOUS) {
			how = ((indexOfPreviouslyDeletedCurrentShuffledItem >= 0) ? indexOfPreviouslyDeletedCurrentShuffledItem : currentShuffledItemIndex) - 1;
			if (how < 0 || how >= count)
				how = count - 1;
		} else if (how == HOW_NEXT_AUTO || how == HOW_NEXT_MANUAL) {
			how = ((indexOfPreviouslyDeletedCurrentShuffledItem >= 0) ? indexOfPreviouslyDeletedCurrentShuffledItem : (currentShuffledItemIndex + 1));
			if (how < 0 || how >= count)
				how = 0;
		} else if (how >= 0 && how < count) {
			s = items[how];
			for (i = count - 1; i >= 0; i--) {
				if (shuffledList[i] == s) {
					current = how;
					currentShuffledItemIndex = i;
					break;
				}
			}
		}
		indexOfPreviouslyDeletedCurrentItem = -1;
		indexOfPreviouslyDeletedCurrentShuffledItem = -1;
		if (how < 0 || how >= count) {
			current = -1;
			s = null;
		} else {
			if (s == null) {
				s = shuffledList[how];
				for (i = count - 1; i >= 0; i--) {
					if (items[i] == s) {
						current = i;
						currentShuffledItemIndex = how;
						break;
					}
				}
			}
			if (!s.alreadyPlayed) {
				s.alreadyPlayed = true;
				shuffledItemsAlreadyPlayed++;
			}
		}
		possibleNextSong = null;
		return s;
	}

	public Song getSongAndSetCurrent(int how) {
		final Song s;
		//synchronized (currentAndCountMutex) {
			okToTurnOffAfterReachingTheEnd = false;
			if (shuffledList != null) {
				s = getRandomSongAndSetCurrentInternal(how);
			} else {
				if (how == HOW_CURRENT) {
					how = ((indexOfPreviouslyDeletedCurrentItem >= 0) ? indexOfPreviouslyDeletedCurrentItem : current);
					if (how < 0)
						how = 0;
					else if (how >= count)
						how = count - 1;
				} else if (how == HOW_PREVIOUS) {
					how = ((indexOfPreviouslyDeletedCurrentItem >= 0) ? indexOfPreviouslyDeletedCurrentItem : current) - 1;
					if (how < 0 || how >= count)
						how = count - 1;
				} else if (how == HOW_NEXT_AUTO || how == HOW_NEXT_MANUAL) {
					if (how == HOW_NEXT_AUTO && repeatMode == REPEAT_NONE) {
						how = ((indexOfPreviouslyDeletedCurrentItem >= 0) ? indexOfPreviouslyDeletedCurrentItem : (current + 1));
						if (how < 0)
							how = 0;
						else if (how >= count)
							how = -2;
					} else {
						if (repeatMode == REPEAT_ONE && how == HOW_NEXT_AUTO)
							how = ((indexOfPreviouslyDeletedCurrentItem >= 0) ? indexOfPreviouslyDeletedCurrentItem : current);
						else
							how = ((indexOfPreviouslyDeletedCurrentItem >= 0) ? indexOfPreviouslyDeletedCurrentItem : (current + 1));
						if (how < 0 || how >= count)
							how = 0;
					}
				}
				indexOfPreviouslyDeletedCurrentItem = -1;
				indexOfPreviouslyDeletedCurrentShuffledItem = -1;
				if (how < 0 || how >= count) {
					okToTurnOffAfterReachingTheEnd = (count > 0 && repeatMode == REPEAT_NONE && how == -2);
					current = ((count > 0 && repeatMode == REPEAT_NONE) ? (count - 1) : -1);
					s = null;
					possibleNextSong = null;
				} else {
					current = how;
					s = items[how];
					possibleNextSong = ((repeatMode == REPEAT_ONE || (repeatMode == REPEAT_NONE && how == (count - 1))) ? null : items[((how == (count - 1)) ? 0 : (how + 1))]);
				}
			}
			if (!selecting && !moving) {
				firstSel = current;
				lastSel = current;
				originalSel = current;
			}
		//}
		notifyDataSetChanged(-1, SELECTION_CHANGED);
		return s;
	}

	public int getRepeatMode() {
		return repeatMode;
	}

	public void setRepeatMode(int repeatMode) {
		//synchronized (currentAndCountMutex) {
			this.repeatMode = repeatMode;
			setRandomModeInternal(false);
		//}
	}

	public boolean isInRandomMode() {
		return (shuffledList != null);
	}

	private void setShuffledCapacity(int capacity) {
		//synchronized (currentAndCountMutex) {
			if (capacity >= count) {
				if (shuffledList == null)
					shuffledList = new Song[capacity + LIST_DELTA];
				else if (capacity > shuffledList.length || capacity <= (shuffledList.length - (2 * LIST_DELTA)))
					shuffledList = Arrays.copyOf(shuffledList, capacity + LIST_DELTA);
			}
		//}
	}

	private void setRandomModeInternal(boolean randomMode) {
		if ((shuffledList != null) == randomMode && !randomMode)
			return;
		int i;
		if (!randomMode) {
			//when randomMode == false, shuffledList != null!!
			for (i = count - 1; i >= 0; i--)
				shuffledList[i] = null;
			shuffledList = null;
		} else {
			repeatMode = REPEAT_ALL;
			setShuffledCapacity(count);
			for (i = count - 1; i >= 0; i--) {
				final Song s = items[i];
				s.alreadyPlayed = false;
				shuffledList[i] = s;
			}
			final Random r = new Random();
			for (i = (count - 1) >> 1; i >= 0; i--) {
				final int a = r.nextInt(count);
				final int b = r.nextInt(count);
				final Song s = shuffledList[a];
				shuffledList[a] = shuffledList[b];
				shuffledList[b] = s;
			}
		}
		currentShuffledItemIndex = -1;
		shuffledItemsAlreadyPlayed = 0;
	}

	public void setRandomMode(boolean randomMode) {
		//synchronized (currentAndCountMutex) {
			setRandomModeInternal(randomMode);
		//}
	}
	
	public void updateExtraInfo() {
		//synchronized (currentAndCountMutex) {
			//don't mess up with modificationVersion as it is not affected by this operation
			for (int i = count - 1; i >= 0; i--)
				items[i].updateExtraInfo();
			//don't mess up with suffling as it is not affected by this operation
		//}
		notifyCheckedChanged();
	}
	
	public void sort(int mode) {
		//synchronized (currentAndCountMutex) {
			sortMode = mode;
			modificationVersion++;
			final Song s = ((current >= 0 && current < count) ? items[current] : null);
			ArraySorter.sort(items, 0, count, this);
			current = -1;
			firstSel = -1;
			lastSel = -1;
			originalSel = -1;
			if (s != null) {
				for (int i = count - 1; i >= 0; i--) {
					if (items[i] == s) {
						current = i;
						firstSel = i;
						lastSel = i;
						originalSel = i;
						break;
					}
				}
			}
			//don't mess up with suffling as it is not affected by this sorting
		//}
		notifyDataSetChanged(current, CONTENT_MOVED);
	}
	
	@Override
	public int compare(Song a, Song b) {
		int r;
		switch (sortMode) {
		case SORT_BY_ALBUM:
			r = a.album.compareToIgnoreCase(b.album);
			if (r == 0)
				r = a.track - b.track;
			if (r == 0)
				return a.title.compareToIgnoreCase(b.title);
			return r;
		case SORT_BY_ARTIST:
			r = a.artist.compareToIgnoreCase(b.artist);
			if (r == 0)
				r = a.album.compareToIgnoreCase(b.album);
			if (r == 0)
				r = a.track - b.track;
			if (r == 0)
				return a.title.compareToIgnoreCase(b.title);
			return r;
		}
		r = a.title.compareToIgnoreCase(b.title);
		if (r == 0)
			return a.track - b.track;
		return r;
	}
	
	@Override
	protected void addingItems(int position, int count) {
		if (shuffledList == null)
			return;
		setShuffledCapacity(this.count);
		final int initial = this.count - count;
		System.arraycopy(items, position, shuffledList, initial, count);
		final Random r = new Random();
		for (int i = initial + ((count - 1) >> 1); i >= initial; i--) {
			final int a = initial + r.nextInt(count);
			final int b = initial + r.nextInt(count);
			final Song s = shuffledList[a];
			shuffledList[a] = shuffledList[b];
			shuffledList[b] = s;
		}
	}
	
	@Override
	protected void removingItems(int position, int count) {
		if (shuffledList == null)
			return;
		int shuffledCount = this.count;
		count += position;
		while (position < count) {
			final Song s = items[position];
			for (int i = shuffledCount - 1; i >= 0; i--) {
				if (shuffledList[i] == s) {
					shuffledCount--;
					if (indexOfPreviouslyDeletedCurrentShuffledItem >= 0) {
						if (indexOfPreviouslyDeletedCurrentShuffledItem == i)
							indexOfPreviouslyDeletedCurrentShuffledItem = i;
						else if (indexOfPreviouslyDeletedCurrentShuffledItem > i)
							indexOfPreviouslyDeletedCurrentShuffledItem--;
						if (indexOfPreviouslyDeletedCurrentShuffledItem >= shuffledCount)
							indexOfPreviouslyDeletedCurrentShuffledItem = -1;
					}
					if (currentShuffledItemIndex == i) {
						indexOfPreviouslyDeletedCurrentShuffledItem = i;
						currentShuffledItemIndex = -1;
					} else if (currentShuffledItemIndex > i) {
						currentShuffledItemIndex--;
					}
					if (currentShuffledItemIndex >= shuffledCount)
						currentShuffledItemIndex = -1;
					System.arraycopy(shuffledList, i + 1, shuffledList, i, shuffledCount - i);
					shuffledList[shuffledCount] = null;
					if (s.alreadyPlayed)
						shuffledItemsAlreadyPlayed--;
					break;
				}
			}
			position++;
		}
	}
	
	@Override
	protected void clearingItems() {
		if (shuffledList == null)
			return;
		for (int i = count - 1; i >= 0; i--)
			shuffledList[i] = null;
		currentShuffledItemIndex = -1;
		shuffledItemsAlreadyPlayed = 0;
		indexOfPreviouslyDeletedCurrentShuffledItem = -1;
	}
	
	@Override
	protected void notifyDataSetChanged(int gotoPosition, int whatHappened) {
		super.notifyDataSetChanged(gotoPosition, whatHappened);
		if (whatHappened == LIST_CLEARED) {
			Player.listCleared();
		} else if (whatHappened != SELECTION_CHANGED && shuffledList == null && current >= 0 && current < count) {
			int n = current + 1;
			if (n < 0 || n >= count)
				n = 0;
			if (n < count)
				Player.nextMayHaveChanged(items[n]);
		}
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		final SongView view = ((convertView != null) ? (SongView)convertView : new SongView(Player.getService()));
		view.setItemState(items[position], position, getItemState(position));
		return view;
	}
	
	@Override
	public int getViewHeight() {
		return SongView.getViewHeight();
	}
}
