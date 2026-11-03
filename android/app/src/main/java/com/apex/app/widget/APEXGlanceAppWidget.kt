package com.apex.app.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import com.apex.app.R

/** Shared error handling for every APEX home-screen widget. */
abstract class APEXGlanceAppWidget : GlanceAppWidget(R.layout.widget_initial_fallback) {
    override fun onCompositionError(
        context: Context,
        glanceId: GlanceId,
        appWidgetId: Int,
        throwable: Throwable
    ) {
        Log.e(TAG, "Widget composition failed for appWidgetId=$appWidgetId", throwable)
        super.onCompositionError(context, glanceId, appWidgetId, throwable)
    }

    private companion object {
        const val TAG = "APEXWidget"
    }
}
