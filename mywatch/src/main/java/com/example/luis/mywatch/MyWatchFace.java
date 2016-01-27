/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.luis.mywatch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.ContextCompat;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.TypedValue;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(60);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;
    String[] data = {"20", "10"};
    Bitmap image = null;
    String maxTemp = degreeSymbol(data[0]);
    String minTemp = degreeSymbol(data[1]);

    GoogleApiClient googleApiClient;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    @Override
    public void onConnected(Bundle bundle) {
        Wearable.DataApi.addListener(googleApiClient, new DataApi.DataListener() {
            @Override
            public void onDataChanged(DataEventBuffer dataEventBuffer) {


                for (DataEvent event: dataEventBuffer) {


                    String eventUri = event.getDataItem().getUri().toString();

                    if (eventUri.contains ("/temps")) {

                        DataMapItem dataItem = DataMapItem.fromDataItem(event.getDataItem());
                        data = dataItem.getDataMap().getStringArray("contents");
                        byte[] byteArray = dataItem.getDataMap().getByteArray("image");
                        image = BitmapFactory.decodeByteArray(byteArray, 100, byteArray.length);
                        image = getResizedBitmap(BitmapFactory.decodeResource(MyWatchFace.this.getResources(), R.drawable.art_clear),
                                Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 40, getResources().getDisplayMetrics())),
                                Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 40, getResources().getDisplayMetrics())));
                        maxTemp = degreeSymbol(data[0]);
                        minTemp = degreeSymbol(data[1]);
                    }
                }

            }
        });

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mHourTextPaint;
        Paint mDateTextPaint;
        Paint mMinuteTextPaint;
        Paint mMaxTempTextPaint;
        Paint mMinTempTextPaint;
        boolean mAmbient;
        Calendar mTime;
        String dateText = "";
        boolean burnInProtection;
        int mainTextColor = ContextCompat.getColor(getApplicationContext(), R.color.digital_text);
        int secondaryTextColor = ContextCompat.getColor(getApplicationContext(), R.color.digital_date_text);
        Date mDate = new Date();
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.getInstance(TimeZone.getTimeZone(intent.getStringExtra("time-zone")));
//                mTime.setToNow();
                invalidate();
            }
        };
        int mTapCount;

        float mXOffset;
        float mYOffset;
        float mXOffsetDate;
        float mXOffsetImage;
        float mXOffsetMax;
        float mXOffsetMin;
        float mXRectBegin;
        float mXRectEnd;
        float yImage;
        float yMax;
        float yMin;
        float yDate;
        float rectTop;
        float rectBottom;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = MyWatchFace.this.getResources();
            image = getResizedBitmap(BitmapFactory.decodeResource(MyWatchFace.this.getResources(), R.drawable.art_clear),
                    Math.round(resources.getDimension(R.dimen.image_height)),
                    Math.round(resources.getDimension(R.dimen.image_width)));
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(ContextCompat.getColor(getApplicationContext(),R.color.background));

            mHourTextPaint = new Paint();
            mHourTextPaint = createTextPaint(mainTextColor);
            mDateTextPaint = createTextPaint(secondaryTextColor);
            mMinuteTextPaint = createTextPaint(mainTextColor);
            mMaxTempTextPaint = createTextPaint(mainTextColor);
            mMinTempTextPaint = createTextPaint(secondaryTextColor);

            mTime = Calendar.getInstance();
            updateDate();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.getInstance(TimeZone.getTimeZone(TimeZone.getDefault().getID()));
//                mTime.setToNow();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = MyWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            mXOffsetDate = resources.getDimension(isRound ? R.dimen.digital_x_offset_round_date : R.dimen.digital_x_offset_date);
            mXOffsetImage = resources.getDimension(isRound ? R.dimen.digital_x_offset_round_image : R.dimen.digital_x_offset_image);
            mXOffsetMax = resources.getDimension(isRound ? R.dimen.digital_x_offset_round_max : R.dimen.digital_x_offset_max);
            mXOffsetMin = resources.getDimension(isRound ? R.dimen.digital_x_offset_round_min : R.dimen.digital_x_offset_min);
            mXRectBegin = resources.getDimension(isRound ? R.dimen.rect_x_offset_begin_round : R.dimen.rect_x_offset_begin);
            mXRectEnd  = resources.getDimension(isRound ? R.dimen.rect_x_offset_end_round : R.dimen.rect_x_offset_end);
            yImage = resources.getDimension(isRound ? R.dimen.y_offset_image_round : R.dimen.y_offset_image);
            yMax = resources.getDimension(isRound ? R.dimen.y_offset_max_round : R.dimen.y_offset_max);
            yMin = resources.getDimension(isRound ? R.dimen.y_offset_min_round : R.dimen.y_offset_min);
            yDate = resources.getDimension(isRound ? R.dimen.y_offset_date_round : R.dimen.y_offset_date);
            rectTop = resources.getDimension(isRound ? R.dimen.y_rect_top_round : R.dimen.y_rect_top);
            rectBottom = resources.getDimension(isRound ? R.dimen.y_rect_bottom_round : R.dimen.y_rect_bottom);

            float hourTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);
            float dateTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_date_text_size_round : R.dimen.digital_date_text_size);
            float maxTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round_max : R.dimen.digital_text_size_max);

            mHourTextPaint.setTextSize(hourTextSize);
            mDateTextPaint.setTextSize(dateTextSize);
            mMinuteTextPaint.setTextSize(hourTextSize);
            mMaxTempTextPaint.setTextSize(maxTextSize);
            mMinTempTextPaint.setTextSize(maxTextSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            burnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            updateDate();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (inAmbientMode) {
                    if (burnInProtection) {
                        mHourTextPaint.setStyle(Paint.Style.STROKE);
                        mMinuteTextPaint.setStyle(Paint.Style.STROKE);
                        mDateTextPaint.setStyle(Paint.Style.STROKE);
                    }
                    mDateTextPaint.setColor(mainTextColor);
                } else {
                    mHourTextPaint.setStyle(Paint.Style.FILL);
                    mMinuteTextPaint.setStyle(Paint.Style.FILL);
                    mDateTextPaint.setStyle(Paint.Style.FILL);
                    mDateTextPaint.setColor(secondaryTextColor);
                }
                if (mLowBitAmbient) {
                    mHourTextPaint.setAntiAlias(!inAmbientMode);
                    mDateTextPaint.setAntiAlias(!inAmbientMode);
                    mMinuteTextPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            Resources resources = MyWatchFace.this.getResources();
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    mTapCount++;
                    mBackgroundPaint.setColor(resources.getColor(mTapCount % 2 == 0 ?
                            R.color.background : R.color.background2));
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
                canvas.drawText(maxTemp, mXOffsetMax,
                        yMax, mMaxTempTextPaint);
                canvas.drawText(minTemp, mXOffsetMin,
                        yMin, mMinTempTextPaint);
                canvas.drawBitmap(image,
                        mXOffsetImage,
                        yImage,
                        mHourTextPaint);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
//            mTime.setToNow();
            String text = String.format("%d:", mTime.get(Calendar.HOUR_OF_DAY));
            String minuteText = String.format("%02d", mTime.get(Calendar.MINUTE));
            canvas.drawText(text, mXOffset, mYOffset, mHourTextPaint);
            float[] size = new float[3];
            mHourTextPaint.getTextWidths(text, size);
            float min = size[0] + size[1] + size[2];
            canvas.drawText(minuteText, mXOffset + min + TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                    1,
                    getResources().getDisplayMetrics()),
                    mYOffset, mMinuteTextPaint);
            canvas.drawText(dateText,
                    mXOffsetDate,
                    yDate,
                    mDateTextPaint);
            canvas.drawRect(mXRectBegin,
                    rectTop,
                    mXRectEnd,
                    rectBottom,
                    mDateTextPaint);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);

            }
        }

        public void updateDate() {
            mDate.setTime(mTime.getTimeInMillis());
            DateFormat dateFormat = android.text.format.DateFormat.getMediumDateFormat(getApplicationContext());
            Calendar c = Calendar.getInstance();
            c.setTime(mDate);
            dateText = getDayWeek(mTime.get(Calendar.DAY_OF_WEEK)) + ", " + dateFormat.format(mDate).replace(",","").toUpperCase();
        }


    }

    public String getDayWeek(int weekDay) {
        switch (weekDay){
            case 1:
                return "SUN";
            case 2:
                return "MON";
            case 3:
                return "TUE";
            case 4:
                return "WED";
            case 5:
                return "THU";
            case 6:
                return "FRI";
            default:
                return "SAT";
        }
    }

    public Bitmap getResizedBitmap(Bitmap bm, int newHeight, int newWidth)
    {
        int width = bm.getWidth();
        int height = bm.getHeight();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        // create a matrix for the manipulation
        Matrix matrix = new Matrix();
        // resize the bit map
        matrix.postScale(scaleWidth, scaleHeight);
        // recreate the new Bitmap
        Bitmap resizedBitmap = Bitmap.createBitmap(bm, 0, 0, width, height, matrix, false);
        return resizedBitmap;
    }

    public String degreeSymbol (String number) {
        return number + (char)0x00B0;
    }
}
