// Pink Music Android is distributed under the FreeBSD License
//
// Copyright (c) 2013-2016, Siva Prasad												
// All rights reserved.																
//*******************************************************************************************
//*******************************************************************************************
//	Redistribution and use in source and binary forms, with or without		   
//	modification, are permitted provided that the following conditions are met:     	
//																						**
//	 1. Redistributions of source code must retain the above copyright notice, this		
//       list of conditions and the following disclaimer.									
//	 2. Redistributions in binary form must reproduce the above copyright notice		
//       this list of conditions and the following disclaimer in the documentation			
//       and/or other materials provided with the distribution.							    
//																						**
//	THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND		
//   	ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED		
//	WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE				
//      DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR		
//      ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES		
//      (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;	
//      LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND		
//      ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT		
//      (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS		
//      SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.						
//																						**
//      The views and conclusions contained in the software and documentation are those		
//      of the authors and should not be interpreted as representing official policies,		
//      either expressed or implied, of the FreeBSD Project.								
//********************************************************************************************
//********************************************************************************************
package br.com.siva.pinkmusic;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.widget.RemoteViews;

import br.com.siva.pinkmusic.playback.Player;
import br.com.siva.pinkmusic.ui.UI;

public final class WidgetMain extends AppWidgetProvider {
	private static AppWidgetManager appWidgetManager;
	private static ComponentName widgetComponent;
	
	private static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
		appWidgetManager.updateAppWidget(appWidgetId, Player.prepareRemoteViews(context, new RemoteViews(context.getPackageName(), UI.widgetTransparentBg ? R.layout.main_widget_transparent : R.layout.main_widget), true, false, false));
	}
	
	public static void updateWidgets(Context context) {
		if (appWidgetManager == null)
			appWidgetManager = AppWidgetManager.getInstance(context);
		if (widgetComponent == null)
			widgetComponent = new ComponentName(context, WidgetMain.class);
		final int[] appWidgetIds = appWidgetManager.getAppWidgetIds(widgetComponent);
		if (appWidgetIds == null)
			return;
		for (int i = appWidgetIds.length - 1; i >= 0; i--)
			updateAppWidget(context, appWidgetManager, appWidgetIds[i]);
	}
	
	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
		UI.loadWidgetRelatedSettings(context);
		for (int i = appWidgetIds.length - 1; i >= 0; i--)
			updateAppWidget(context, appWidgetManager, appWidgetIds[i]);
	}
}
