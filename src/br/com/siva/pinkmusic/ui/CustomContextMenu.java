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
package br.com.siva.pinkmusic.ui;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.TypedValue;
import android.view.ActionProvider;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;

import java.lang.reflect.Constructor;

import br.com.siva.pinkmusic.R;
import br.com.siva.pinkmusic.activity.MainHandler;
import br.com.siva.pinkmusic.util.ArraySorter;
import br.com.siva.pinkmusic.util.ArraySorter.Comparer;
import br.com.siva.pinkmusic.util.TypedRawArrayList;

public final class CustomContextMenu implements SubMenu, ContextMenu, Runnable, Comparer<CustomContextMenu.Item>, View.OnClickListener, OnCancelListener, OnDismissListener {
	static final class Item implements MenuItem {
		Context context;
		OnMenuItemClickListener menuItemClickListener;
		boolean visible, enabled, checkable, checked;
		Drawable icon;
		int itemId, groupId, order;
		CharSequence title, condensedTitle;
		View actionView;
		CustomContextMenu subMenu;
		
		public Item(Context context, int groupId, int itemId, int order, CharSequence title) {
			this.visible = true;
			this.enabled = true;
			this.context = context;
			this.groupId = groupId;
			this.itemId = itemId;
			this.order = order;
			this.title = title;
		}
		
		@Override
		public boolean collapseActionView() {
			return false;
		}
		
		@Override
		public boolean expandActionView() {
			return false;
		}
		
		@Override
		public ActionProvider getActionProvider() {
			return null;
		}
		
		@Override
		public View getActionView() {
			return actionView;
		}
		
		@Override
		public char getAlphabeticShortcut() {
			return 0;
		}
		
		@Override
		public int getGroupId() {
			return groupId;
		}
		
		@Override
		public Drawable getIcon() {
			return icon;
		}
		
		@Override
		public Intent getIntent() {
			return null;
		}
		
		@Override
		public int getItemId() {
			return itemId;
		}
		
		@Override
		public ContextMenuInfo getMenuInfo() {
			return null;
		}
		
		@Override
		public char getNumericShortcut() {
			return 0;
		}
		
		@Override
		public int getOrder() {
			return order;
		}
		
		@Override
		public SubMenu getSubMenu() {
			return subMenu;
		}
		
		@Override
		public CharSequence getTitle() {
			return title;
		}
		
		@Override
		public CharSequence getTitleCondensed() {
			return condensedTitle;
		}
		
		@Override
		public boolean hasSubMenu() {
			return (subMenu != null);
		}
		
		@Override
		public boolean isActionViewExpanded() {
			return false;
		}
		
		@Override
		public boolean isCheckable() {
			return checkable;
		}
		
		@Override
		public boolean isChecked() {
			return checked;
		}
		
		@Override
		public boolean isEnabled() {
			return enabled;
		}
		
		@Override
		public boolean isVisible() {
			return visible;
		}
		
		@Override
		public MenuItem setActionProvider(ActionProvider actionProvider) {
			return this;
		}
		
		//level 10...
		public MenuItem setActionView_(View view) {
			this.actionView = view;
			return this;
		}
		
		@Override
		public MenuItem setActionView(View view) {
			this.actionView = view;
			return this;
		}
		
		@Override
		public MenuItem setActionView(int resId) {
			this.actionView = ((LayoutInflater)context.getSystemService(Application.LAYOUT_INFLATER_SERVICE)).inflate(resId, null);
			return this;
		}
		
		@Override
		public MenuItem setAlphabeticShortcut(char alphaChar) {
			return this;
		}
		
		@Override
		public MenuItem setCheckable(boolean checkable) {
			this.checkable = checkable;
			return this;
		}
		
		@Override
		public MenuItem setChecked(boolean checked) {
			this.checked = checked;
			return this;
		}
		
		@Override
		public MenuItem setEnabled(boolean enabled) {
			this.enabled = enabled;
			return this;
		}
		
		@Override
		public MenuItem setIcon(Drawable icon) {
			this.icon = icon;
			return this;
		}

		@SuppressWarnings("deprecation")
		@Override
		public MenuItem setIcon(int iconRes) {
			this.icon = context.getResources().getDrawable(iconRes);
			return this;
		}
		
		@Override
		public MenuItem setIntent(Intent intent) {
			return this;
		}
		
		@Override
		public MenuItem setNumericShortcut(char numericChar) {
			return this;
		}
		
		@Override
		public MenuItem setOnActionExpandListener(OnActionExpandListener listener) {
			return this;
		}
		
		@Override
		public MenuItem setOnMenuItemClickListener(OnMenuItemClickListener menuItemClickListener) {
			this.menuItemClickListener = menuItemClickListener;
			return this;
		}
		
		@Override
		public MenuItem setShortcut(char numericChar, char alphaChar) {
			return this;
		}
		
		@Override
		public void setShowAsAction(int actionEnum) {
		}
		
		@Override
		public MenuItem setShowAsActionFlags(int actionEnum) {
			return this;
		}
		
		@Override
		public MenuItem setTitle(CharSequence title) {
			this.title = title;
			return this;
		}
		
		@Override
		public MenuItem setTitle(int title) {
			this.title = context.getText(title);
			return this;
		}
		
		@Override
		public MenuItem setTitleCondensed(CharSequence title) {
			this.condensedTitle = title;
			return this;
		}
		
		@Override
		public MenuItem setVisible(boolean visible) {
			this.visible = visible;
			return this;
		}
	}
	
	static final class MenuDialog extends Dialog {
		public MenuDialog(Context context, CustomContextMenu menu, View contents) {
			super(context, R.style.MenuDialog);
			setContentView(contents);
			setCancelable(true);
			setCanceledOnTouchOutside(true);
			setOnCancelListener(menu);
			setOnDismissListener(menu);
			setTitle(context.getText(R.string.menu));
			UI.prepareDialogAnimations(this);
		}

		@Override
		public boolean dispatchPopulateAccessibilityEvent(@NonNull AccessibilityEvent event) {
			if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
				event.getText().add(getContext().getText(R.string.menu));
				return true;
			}
			return super.dispatchPopulateAccessibilityEvent(event);
		}
	}
	
	private Context context;
	private TypedRawArrayList<Item> items;
	private View.OnCreateContextMenuListener listener;
	private Activity closeListener;
	private View view;
	private int backgroundId, paddingL, paddingT, paddingR, paddingB, itemBackgroundId, itemPaddingL, itemPaddingT, itemPaddingR, itemPaddingB, itemTextAppearance, itemTextColor, itemTextSizePx, itemGravity;
	private Drawable backgroundDrawable, itemBackgroundDrawable;
	private Constructor<? extends TextView> itemClassConstructor;
	private ColorStateList itemTextColors;
	private boolean closed, closingByItemClick, hasItemPadding, hasBackground, hasItemBackground;
	private MenuDialog menu;
	private Item clickedItem, parentItem;
	private CustomContextMenu parentMenu;
	
	private CustomContextMenu(View view, View.OnCreateContextMenuListener listener, Activity closeListener, Item parentItem, CustomContextMenu parentMenu) {
		this.context = view.getContext();
		this.items = new TypedRawArrayList<>(Item.class, 8);
		this.listener = listener;
		this.closeListener = closeListener;
		this.view = view;
		this.closed = false;
		this.parentItem = parentItem;
		this.parentMenu = parentMenu;
	}
	
	/*public static void registerForContextMenu(View view, final View.OnCreateContextMenuListener listener) {
		if (!view.isLongClickable())
			view.setLongClickable(true);
		view.setOnLongClickListener(new View.OnLongClickListener() {
			@Override
			public boolean onLongClick(View view) {
				openContextMenu(view, listener);
				return true;
			}
		});
	}*/
	
	public static void openContextMenu(View view, View.OnCreateContextMenuListener listener) {
		final CustomContextMenu mnu = new CustomContextMenu(view, listener, null, null, null);
		if (MainHandler.isOnMainThread())
			mnu.run();
		else
			MainHandler.postToMainThread(mnu);
	}

	@Override
	public int compare(Item a, Item b) {
		if (a.groupId == b.groupId)
			return a.order - b.order;
		return a.groupId - b.groupId;
	}
	
	@SuppressWarnings("deprecation")
	@Override
	public void run() {
		closed = false;
		
		if (parentItem == null)
			listener.onCreateContextMenu(this, view, null);
		
		if (closed || this.items.size() == 0) {
			close();
			return;
		}
		
		if (menu == null) {
			final int itemsLength = this.items.size();
			final Item[] items = this.items.getRawArray();
			ArraySorter.sort(items, 0, itemsLength, this);
			
			final LinearLayout list = new LinearLayout(context);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
				UI.removeSplitTouch(list);
			list.setLayoutParams(new ViewGroup.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
			list.setOrientation(LinearLayout.VERTICAL);
			list.setBackgroundColor(0);
			list.setPadding(0, 0, 0, 0);
			
			final int minWidth = ((context instanceof Activity) ? (((Activity)context).getWindowManager().getDefaultDisplay().getWidth() >> ((UI.isLargeScreen || UI.isLandscape) ? 2 : 1)) : 0);
			int first = -1, last = -1;
			for (int i = 0; i < itemsLength; i++) {
				final Item it = items[i];
				if (it.visible) {
					if (it.actionView == null) {
						TextView btn;
						if (itemClassConstructor != null) {
							try {
								btn = itemClassConstructor.newInstance(context);
							} catch (Throwable e) {
								throw new RuntimeException(e);
							}
						} else {
							btn = new Button(context);
						}
						if (minWidth != 0)
							btn.setMinWidth(minWidth);
						btn.setMinHeight(UI.defaultControlSize);
						if (itemTextAppearance != 0)
							btn.setTextAppearance(context, itemTextAppearance);
						if (itemGravity != 0)
							btn.setGravity(itemGravity);
						if (hasItemBackground) {
							if (itemBackgroundDrawable != null)
								btn.setBackgroundDrawable(itemBackgroundDrawable);
							else
								btn.setBackgroundResource(itemBackgroundId);
						}
						if (itemTextSizePx != 0)
							btn.setTextSize(TypedValue.COMPLEX_UNIT_PX, itemTextSizePx);
						if (itemTextColor != 0)
							btn.setTextColor(itemTextColor);
						if (itemTextColors != null)
							btn.setTextColor(itemTextColors);
						if (hasItemPadding)
							btn.setPadding(itemPaddingL, itemPaddingT, itemPaddingR, itemPaddingB);
						if (it.icon != null)
							btn.setCompoundDrawables(it.icon, null, null, null);
						btn.setText(it.title);
						btn.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
						it.actionView = btn;
						it.actionView.setFocusableInTouchMode(!UI.hasTouch);
						it.actionView.setFocusable(true);
						it.actionView.setClickable(true);
						it.actionView.setOnClickListener(this);
					}
					if (it.enabled) {
						if (first < 0)
							first = i;
						last = i;
					}
					it.actionView.setEnabled(it.enabled);
					list.addView(it.actionView);
				}
			}
			if (first >= 0 && first != last) {
				items[first].actionView.setId(1);
				items[first].actionView.setNextFocusUpId(2);
				items[last].actionView.setId(2);
				items[last].actionView.setNextFocusDownId(1);
			}
			final ObservableScrollView scroll = new ObservableScrollView(context, UI.PLACEMENT_MENU);
			final FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
			fp.leftMargin = UI.controlMargin;
			fp.topMargin = UI.controlMargin;
			fp.rightMargin = UI.controlMargin;
			fp.bottomMargin = UI.controlMargin;
			fp.gravity = Gravity.CENTER;
			scroll.setLayoutParams(fp);
			if (hasBackground) {
				if (backgroundDrawable != null)
					scroll.setBackgroundDrawable(backgroundDrawable);
				else
					scroll.setBackgroundResource(backgroundId);
			}
			scroll.setPadding(paddingL, paddingT, paddingR, paddingB);
			scroll.addView(list);
			menu = new MenuDialog(context, this, scroll);
		} else {
			//we must manually reset the drawbles here, because they are always removed
			//from the views in their onDetachedFromWindow()
			final Item[] items = this.items.getRawArray();
			for (int i = this.items.size() - 1; i >= 0; i--) {
				final Item it = items[i];
				if (it.icon != null && it.actionView != null && (it.actionView instanceof TextView))
					((TextView)it.actionView).setCompoundDrawables(it.icon, null, null, null);
			}
		}
		menu.show();
		/*final Window w = menu.getWindow();
		final View decor = w.getDecorView();
		if (decor != null) {
			ViewGroup.LayoutParams p;
			ViewParent pa = scroll.getParent();
			while (pa != null && (pa instanceof View)) {
				final View v = (View)pa;
				p = v.getLayoutParams();
				if (pa == decor) {
					p.width = LayoutParams.MATCH_PARENT;
					p.height = LayoutParams.MATCH_PARENT;
				} else {
					p.width = LayoutParams.WRAP_CONTENT;
					p.height = LayoutParams.WRAP_CONTENT;
				}
				if (p instanceof MarginLayoutParams) {
					final MarginLayoutParams m = (MarginLayoutParams)p;
					m.leftMargin = 0;
					m.topMargin = 0;
					m.rightMargin = 0;
					m.bottomMargin = 0;
				}
				v.setLayoutParams(p);
				v.setPadding(0, 0, 0, 0);
				v.setBackgroundResource(0);
				pa = pa.getParent();
			}
			decor.setOnTouchListener(menu);
		}*/
	}
	
	@Override
	public void onClick(View view) {
		if (items == null)
			return;
		//as there are really few elements, and clicks happen only once in a while,
		//it was not worth keeping an extra HashMap in this class ;)
		Item it = null;
		final Item[] items = this.items.getRawArray();
		for (int i = this.items.size() - 1; i >= 0; i--) {
			final Item iti = items[i];
			if (iti != null && iti.actionView == view) {
				it = iti;
				break;
			}
		}
		if (it != null && it.enabled) {
			UI.storeViewCenterLocationForFade(it.actionView);
			if (it.subMenu != null) {
				it.subMenu.run();
				return;
			}
			clickedItem = it;
			closingByItemClick = true;
			close();
		}
	}
	
	@Override
	public void onCancel(DialogInterface dialog) {
		if (!closed) close();
	}
	
	@Override
	public void onDismiss(DialogInterface dialog) {
		if (!closed) close();
	}

	public View addSeparator(int groupId, int order, int color, int height, int marginLeft, int marginTop, int marginRight, int marginBottom) {
		final TextView t = new TextView(context);
		final LayoutParams p = new LayoutParams(LayoutParams.MATCH_PARENT, height);
		p.setMargins(marginLeft, marginTop, marginRight, marginBottom);
		t.setLayoutParams(p);
		t.setBackgroundColor(color);
		t.setEnabled(false);
		final Item i = (Item)add(groupId, 0, order, null);
		//level 10...
		i.setActionView_(t).setEnabled(false);
		return t;
	}
	
	public void setBackground(Drawable background) {
		hasBackground = true;
		backgroundDrawable = background;
		backgroundId = 0;
	}
	
	public void setBackground(int resId) {
		hasBackground = true;
		backgroundDrawable = null;
		backgroundId = resId;
	}
	
	public void setPadding(int padding) {
		paddingL = padding;
		paddingT = padding;
		paddingR = padding;
		paddingB = padding;
	}
	
	public void setPadding(int left, int top, int right, int bottom) {
		paddingL = left;
		paddingT = top;
		paddingR = right;
		paddingB = bottom;
	}
	
	public void setItemClassConstructor(Constructor<? extends TextView> classConstructor) {
		itemClassConstructor = classConstructor;
	}
	
	public void setItemBackground(Drawable background) {
		hasItemBackground = true;
		itemBackgroundDrawable = background;
		itemBackgroundId = 0;
	}
	
	public void setItemBackground(int resId) {
		hasItemBackground = true;
		itemBackgroundDrawable = null;
		itemBackgroundId = resId;
	}
	
	public void setItemPadding(int padding) {
		hasItemPadding = true;
		itemPaddingL = padding;
		itemPaddingT = padding;
		itemPaddingR = padding;
		itemPaddingB = padding;
	}
	
	public void setItemPadding(int left, int top, int right, int bottom) {
		hasItemPadding = true;
		itemPaddingL = left;
		itemPaddingT = top;
		itemPaddingR = right;
		itemPaddingB = bottom;
	}
	
	public void setItemTextAppearance(int resId) {
		itemTextAppearance = resId;
	}
	
	public void setItemTextSizeInPixels(int sizePx) {
		itemTextSizePx = sizePx;
	}
	
	public void setItemTextColor(int color) {
		itemTextColor = color;
		itemTextColors = null;
	}
	
	public void setItemTextColor(ColorStateList colors) {
		itemTextColor = 0;
		itemTextColors = colors;
	}
	
	public void setItemGravity(int gravity) {
		itemGravity = gravity;
	}
	
	@Override
	public MenuItem add(CharSequence title) {
		final Item i = new Item(context, 0, 0, 0, title);
		items.add(i);
		return i;
	}
	
	@Override
	public MenuItem add(int titleRes) {
		final Item i = new Item(context, 0, 0, 0, context.getText(titleRes));
		items.add(i);
		return i;
	}
	
	@Override
	public MenuItem add(int groupId, int itemId, int order, CharSequence title) {
		final Item i = new Item(context, groupId, itemId, order, title);
		items.add(i);
		return i;
	}
	
	@Override
	public MenuItem add(int groupId, int itemId, int order, int titleRes) {
		final Item i = new Item(context, groupId, itemId, order, context.getText(titleRes));
		items.add(i);
		return i;
	}
	
	@Override
	public int addIntentOptions(int groupId, int itemId, int order, ComponentName caller, Intent[] specifics, Intent intent, int flags, MenuItem[] outSpecificItems) {
		return 0;
	}
	
	@Override
	public SubMenu addSubMenu(CharSequence title) {
		final Item i = new Item(context, 0, 0, 0, title);
		final CustomContextMenu m = new CustomContextMenu(view, listener, closeListener, i, this);
		i.subMenu = m;
		items.add(i);
		return m;
	}
	
	@Override
	public SubMenu addSubMenu(int titleRes) {
		final Item i = new Item(context, 0, 0, 0, context.getText(titleRes));
		final CustomContextMenu m = new CustomContextMenu(view, listener, closeListener, i, this);
		i.subMenu = m;
		items.add(i);
		return m;
	}
	
	@Override
	public SubMenu addSubMenu(int groupId, int itemId, int order, CharSequence title) {
		final Item i = new Item(context, groupId, itemId, order, title);
		final CustomContextMenu m = new CustomContextMenu(view, listener, closeListener, i, this);
		i.subMenu = m;
		items.add(i);
		return m;
	}
	
	@Override
	public SubMenu addSubMenu(int groupId, int itemId, int order, int titleRes) {
		final Item i = new Item(context, groupId, itemId, order, context.getText(titleRes));
		final CustomContextMenu m = new CustomContextMenu(view, listener, closeListener, i, this);
		i.subMenu = m;
		items.add(i);
		return m;
	}
	
	@Override
	public void clear() {
		items.clear();
	}
	
	@Override
	public void close() {
		if (parentItem != null && !closingByItemClick) {
			//preserve everything for now, but call the listener
			if (!closed) {
				closed = true;
				if (closeListener != null)
					closeListener.onContextMenuClosed(this);
			}
			return;
		}
		if (!closed) {
			final Item clkIt = clickedItem;
			final android.view.MenuItem.OnMenuItemClickListener list = ((clkIt != null) ? clkIt.menuItemClickListener : null);
			closed = true;
			if (menu != null)
				menu.cancel();
			if (closeListener != null)
				closeListener.onContextMenuClosed(this);
			if (parentItem != null) {
				parentItem.subMenu = null; //prevent recursion
				parentItem = null;
			}
			if (parentMenu != null) {
				parentMenu.closingByItemClick = true;
				parentMenu.close();
				parentMenu = null;
			}
			//only call the click handler after closing
			//the parent and all its sub menus!
			if (list != null)
				list.onMenuItemClick(clkIt);
		}
		//help the gc
		context = null;
		if (items != null) {
			final Item[] items = this.items.getRawArray();
			for (int i = this.items.size() - 1; i >= 0; i--) {
				final Item it = items[i];
				it.context = null;
				it.menuItemClickListener = null;
				it.icon = null;
				it.title = null;
				it.condensedTitle = null;
				it.actionView = null;
				if (it.subMenu != null) {
					it.subMenu.parentItem = null; //prevent recursion and force the cleanup
					it.subMenu.parentMenu = null;
					it.subMenu.closeListener = null; //prevent unwanted callbacks
					it.subMenu.close();
					it.subMenu = null;
				}
			}
			this.items.clear();
			this.items = null;
		}
		listener = null;
		closeListener = null;
		view = null;
		backgroundDrawable = null;
		itemBackgroundDrawable = null;
		itemClassConstructor = null;
		itemTextColors = null;
		menu = null;
		clickedItem = null;
		parentItem = null;
		parentMenu = null;
	}
	
	@Override
	public MenuItem findItem(int id) {
		final Item[] items = this.items.getRawArray();
		for (int i = this.items.size() - 1; i >= 0; i--) {
			final Item it = items[i];
			if (it.itemId == id) return it;
		}
		return null;
	}
	
	@Override
	public MenuItem getItem(int index) {
		return items.get(index);
	}
	
	@Override
	public boolean hasVisibleItems() {
		final Item[] items = this.items.getRawArray();
		for (int i = this.items.size() - 1; i >= 0; i--) {
			if (items[i].visible) return true;
		}
		return false;
	}
	
	@Override
	public boolean isShortcutKey(int keyCode, KeyEvent event) {
		return false;
	}
	
	@Override
	public boolean performIdentifierAction(int id, int flags) {
		return false;
	}
	
	@Override
	public boolean performShortcut(int keyCode, KeyEvent event, int flags) {
		return false;
	}
	
	@Override
	public void removeGroup(int groupId) {
		for (int i = 0; i < items.size(); i++) {
			if (items.get(i).groupId == groupId) {
				items.remove(i);
				i--;
			}
		}
	}
	
	@Override
	public void removeItem(int id) {
		final Item[] items = this.items.getRawArray();
		for (int i = this.items.size() - 1; i >= 0; i--) {
			if (items[i].itemId == id) {
				this.items.remove(i);
				return;
			}
		}
	}
	
	@Override
	public void setGroupCheckable(int group, boolean checkable, boolean exclusive) {
		final Item[] items = this.items.getRawArray();
		for (int i = this.items.size() - 1; i >= 0; i--) {
			final Item it = items[i];
			if (it.groupId == group) it.checkable = checkable;
		}
	}
	
	@Override
	public void setGroupEnabled(int group, boolean enabled) {
		final Item[] items = this.items.getRawArray();
		for (int i = this.items.size() - 1; i >= 0; i--) {
			final Item it = items[i];
			if (it.groupId == group) it.enabled = enabled;
		}
	}
	
	@Override
	public void setGroupVisible(int group, boolean visible) {
		final Item[] items = this.items.getRawArray();
		for (int i = this.items.size() - 1; i >= 0; i--) {
			final Item it = items[i];
			if (it.groupId == group) it.visible = visible;
		}
	}
	
	@Override
	public void setQwertyMode(boolean isQwerty) {
	}
	
	@Override
	public int size() {
		return items.size();
	}
	
	@Override
	public void clearHeader() {
	}
	
	//WARNING! EXTREME WORKAROUND BELOW THIS POINT (JUST TO REUSE EVERYTHING FOR SubMenu)! ;)
	@NonNull
	@Override
	public CustomContextMenu setHeaderIcon(int iconRes) {
		return this;
	}

	@NonNull
	@Override
	public CustomContextMenu setHeaderIcon(Drawable icon) {
		return this;
	}

	@NonNull
	@Override
	public CustomContextMenu setHeaderTitle(int titleRes) {
		return this;
	}

	@NonNull
	@Override
	public CustomContextMenu setHeaderTitle(CharSequence title) {
		return this;
	}

	@NonNull
	@Override
	public CustomContextMenu setHeaderView(View view) {
		return this;
	}
	
	@Override
	public MenuItem getItem() {
		return parentItem;
	}

	@NonNull
	@Override
	public SubMenu setIcon(int iconRes) {
		if (parentItem != null) parentItem.setIcon(iconRes);
		return this;
	}

	@NonNull
	@Override
	public SubMenu setIcon(Drawable icon) {
		if (parentItem != null) parentItem.setIcon(icon);
		return this;
	}
}
