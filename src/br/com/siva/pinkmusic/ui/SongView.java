
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

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.ViewDebug.ExportedProperty;

import br.com.siva.pinkmusic.list.Song;
import br.com.siva.pinkmusic.ui.drawable.TextIconDrawable;

public final class SongView extends View implements View.OnClickListener, View.OnLongClickListener {
	private Song song;
	private String ellipsizedTitle, ellipsizedExtraInfo;
	private int state, width, lengthX, lengthWidth, position;

	private static int height, textX, titleY, extraY, currentX, currentY, leftMargin, topMargin, rightMargin;

	public static int getViewHeight() {
		final int bottomMargin;
		if (UI.is3D) {
			switch (UI.songListScrollBarType) {
			case BgListView.SCROLLBAR_LARGE:
				if (UI.scrollBarToTheLeft) {
					leftMargin = 0;
					rightMargin = UI.controlSmallMargin;
				} else {
					leftMargin = UI.controlSmallMargin;
					rightMargin = UI.strokeSize;
				}
				break;
			default:
				leftMargin = UI.controlSmallMargin;
				rightMargin = UI.controlSmallMargin;
				break;
			}
			topMargin = UI.controlSmallMargin;
			bottomMargin = UI.strokeSize;
		} else {
			leftMargin = 0;
			topMargin = 0;
			rightMargin = 0;
			bottomMargin = 0;
		}
	//	height = (UI._1dp << 1) + (UI.verticalMargin << 1) + UI._22spBox + UI._14spBox + topMargin + bottomMargin;
	//	textX = leftMargin + UI.controlMargin;
	//	titleY = UI.verticalMargin + UI._22spYinBox + topMargin;
	//	extraY = UI.verticalMargin + UI._1dp + UI._22spBox + UI._14spYinBox + topMargin;
	//	currentY = height - UI.defaultControlContentsSize - UI.controlXtraSmallMargin - bottomMargin;
	//	return height;
    	height = (UI._1dp << 1) + (UI.verticalMargin << 1) + UI._14spBox + topMargin + bottomMargin;
		height =  UI._14spBox + UI._14spBox + topMargin + bottomMargin;
		textX = leftMargin + UI.controlMargin;
		titleY = UI.verticalMargin + UI._14spYinBox + topMargin;
		titleY = 30 + UI._14spYinBox + topMargin;
		extraY = UI.verticalMargin + UI._1dp + UI._22spBox + UI._14spYinBox + topMargin;
		currentY = height - UI.defaultControlContentsSize - UI.controlXtraSmallMargin - bottomMargin;
		return height;	
	}

	public SongView(Context context) {
		super(context);
		setOnClickListener(this);
		setOnLongClickListener(this);
		getViewHeight();
		super.setDrawingCacheEnabled(false);
	}

	private void processEllipsis() {
		final int w = lengthX - textX - UI.controlMargin;
		ellipsizedTitle = UI.ellipsizeText(song.title, UI._22sp, w, false);
		ellipsizedExtraInfo = UI.ellipsizeText(song.extraInfo, UI._14sp, w, false);
	}

	public void updateIfCurrent() {
		if ((state & UI.STATE_CURRENT) != 0) {
			processEllipsis();
			invalidate();
		}
	}

	@Override
	public CharSequence getContentDescription() {
		if (song != null)
			return song.title;
		return super.getContentDescription();
	}

	public void setItemState(Song song, int position, int state) {
		this.state = (this.state & ~(UI.STATE_CURRENT | UI.STATE_SELECTED | UI.STATE_MULTISELECTED)) | state;
		this.position = position;
		//watch out, DO NOT use equals() in favor of speed!
		if (this.song == song)
			return;
		this.song = song;
		lengthWidth = (song.isHttp ? UI._14spBox : UI.measureText(song.length, UI._14sp));
		lengthX = width - lengthWidth - UI.controlMargin - rightMargin;
		processEllipsis();
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	@Override
	public void setBackground(Drawable background) {
		super.setBackground(null);
	}

	@Override
	@Deprecated
	public void setBackgroundDrawable(Drawable background) {
		super.setBackgroundDrawable(null);
	}

	@Override
	public void setBackgroundResource(int resid) {
		super.setBackgroundResource(0);
	}

	@Override
	public void setBackgroundColor(int color) {
		super.setBackgroundResource(0);
	}

	@Override
	public Drawable getBackground() {
		return null;
	}

	@Override
	@ExportedProperty(category = "drawing")
	public boolean isOpaque() {
		return false;
	}

	@Override
	protected void drawableStateChanged() {
		super.drawableStateChanged();
		state = UI.handleStateChanges(state, isPressed(), isFocused(), this);
	}

	@Override
	protected int getSuggestedMinimumHeight() {
		return height;
	}

	@Override
	public int getMinimumHeight() {
		return height;
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		setMeasuredDimension(resolveSize(0, widthMeasureSpec), height);
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		if (width != w) {
			width = w;
			currentX = w - UI.defaultControlContentsSize - rightMargin;
			lengthX = width - lengthWidth - UI.controlMargin - rightMargin;
			processEllipsis();
		}
	}
//List Text size
	//@Override
	//protected void onDraw(Canvas canvas) {
	//	if (ellipsizedTitle == null)
	//		return;
	//	final int txtColor = (((state & ~UI.STATE_CURRENT) == 0) ? UI.color_text_listitem : UI.color_text_selected);
	//	getDrawingRect(UI.rect);
	//	UI.drawBgListItem(canvas, state | ((state & UI.STATE_SELECTED & BgListView.extraState) >>> 2), true, leftMargin, rightMargin);
	//	if ((state & UI.STATE_CURRENT) != 0)
	//		TextIconDrawable.drawIcon(canvas, UI.ICON_PINKMUSIC, currentX, currentY, UI.defaultControlContentsSize, ((state & ~UI.STATE_CURRENT) == 0) ? UI.color_text_listitem_secondary : UI.color_text_selected);
	//	UI.drawText(canvas, ellipsizedTitle, txtColor, UI._22sp, textX, titleY);
	//	if (song.isHttp)
	//		TextIconDrawable.drawIcon(canvas, UI.ICON_RADIO, lengthX, UI.verticalMargin + topMargin, UI._14spBox, txtColor);
	//	else
	//		UI.drawText(canvas, song.length, txtColor, UI._14sp, lengthX, UI.verticalMargin + UI._14spYinBox + topMargin);
	//	UI.drawText(canvas, ellipsizedExtraInfo, txtColor, UI._14sp, textX, extraY);
	//}
	@Override
	protected void onDraw(Canvas canvas) {
		if (ellipsizedTitle == null)
			return;
		final int txtColor = (((state & ~UI.STATE_CURRENT) == 0) ? UI.color_text_listitem : UI.color_text_selected);
		getDrawingRect(UI.rect);
		UI.drawBgListItem(canvas, state | ((state & UI.STATE_SELECTED & BgListView.extraState) >>> 2), true, leftMargin, rightMargin);
		if ((state & UI.STATE_CURRENT) != 0)
			TextIconDrawable.drawIcon(canvas, UI.ICON_PINKMUSIC, currentX - 150, currentY +10, UI.defaultControlContentsSize, ((state & ~UI.STATE_CURRENT) == 0) ? UI.color_text_listitem_secondary : UI.color_text_selected);
		UI.drawText(canvas, ellipsizedTitle, txtColor, UI._14sp, textX, titleY);
		if (song.isHttp)
			TextIconDrawable.drawIcon(canvas, UI.ICON_LINK, lengthX, UI.verticalMargin + topMargin, UI._14spBox, txtColor);
		else
			UI.drawText(canvas, song.length, txtColor, UI._14sp, lengthX, UI.verticalMargin + UI._14spYinBox + topMargin);
		    UI.drawText(canvas, ellipsizedExtraInfo, txtColor, UI._14sp, textX , extraY);
	}


	@Override
	protected void dispatchSetPressed(boolean pressed) {
	}

	@Override
	protected void onDetachedFromWindow() {
		song = null;
		ellipsizedTitle = null;
		ellipsizedExtraInfo = null;
		super.onDetachedFromWindow();
	}

	@Override
	public void onClick(View view) {
		if (UI.songActivity != null)
			UI.songActivity.processItemClick(position);
	}

	@Override
	public boolean onLongClick(View view) {
		if (UI.songActivity != null)
			UI.songActivity.processItemLongClick(position);
		return true;
	}
}
