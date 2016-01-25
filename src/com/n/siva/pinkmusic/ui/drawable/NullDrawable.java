// Pink Music Android is distributed under the FreeBSD License
//
// Copyright (c) 2013-2016, Siva Prasad	
// All rights reserved.																
//*******************************************************************************************
//*******************************************************************************************
//	Redistribution and use in source and binary forms, with or without		   
//	modification, are permitted provided that the following conditions are met:     	
//																						
//	 1. Redistributions of source code must retain the above copyright notice, this		
//       list of conditions and the following disclaimer.									
//	 2. Redistributions in binary form must reproduce the above copyright notice		
//       this list of conditions and the following disclaimer in the documentation			
//       and/or other materials provided with the distribution.							    
//																						
//	    THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND		
//   	ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED		
//	    WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE				
//      DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR		
//      ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES		
//      (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;	
//      LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND		
//      ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT		
//      (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS		
//      SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.						
//																					
//      The views and conclusions contained in the software and documentation are those		
//      of the authors and should not be interpreted as representing official policies,		
//      either expressed or implied, of the FreeBSD Project.								
//********************************************************************************************
//********************************************************************************************
package com.n.siva.pinkmusic.ui.drawable;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

public final class NullDrawable extends Drawable {
	public NullDrawable() {
	}
	
	@Override
	public void draw(Canvas canvas) {
	}
	
	@Override
	public int getOpacity() {
		return PixelFormat.OPAQUE;
	}
	
	@Override
	public void setAlpha(int alpha) {
	}
	
	@Override
	public void setColorFilter(ColorFilter cf) {
	}
	
	@Override
	public boolean isStateful() {
		return false;
	}
	
	@Override
	public int getAlpha() {
		return 255;
	}
	
	@Override
	public boolean isAutoMirrored() {
		return true;
	}
	
	@Override
	public int getMinimumWidth() {
		return 0;
	}
	
	@Override
	public int getMinimumHeight() {
		return 0;
	}
}
