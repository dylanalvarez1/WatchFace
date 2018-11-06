package com.example.dylan.digitaltemperaturewatch;

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
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.ContextCompat;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static android.graphics.Color.colorSpace;
import static android.graphics.Color.parseColor;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 * <p>
 * Important Note: Because watch face apps do not have a default Activity in
 * their project, you will need to set your Configurations to
 * "Do not launch Activity" for both the Wear and/or Application modules. If you
 * are unsure how to do this, please review the "Run Starter project" section
 * in the Google Watch Face Code Lab:
 * https://codelabs.developers.google.com/codelabs/watchface/index.html#0
 */
public class MyWatchFace extends CanvasWatchFaceService{
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);





    /**
     * Update rate in milliseconds for interactive mode. Defaults to one second
     * because the watch face needs to update seconds in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
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

    private class Engine extends CanvasWatchFaceService.Engine implements SensorEventListener {

        private final Handler mUpdateTimeHandler = new EngineHandler(this);
        private Calendar mCalendar;

        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        private boolean mRegisteredTimeZoneReceiver = false;
        private float mXOffset;
        private float mYOffset;
        private Paint mBackgroundPaint;
        private Paint mTextPaint;
        private Paint mTextPaintTemp;
        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        private boolean mLowBitAmbient;
        private boolean mBurnInProtection;
        private boolean mAmbient;

        private float ambient_temperature = 0;
        private float ambient_temperature_faren = 0;

        private SensorManager mSensorManager;
        private Sensor mPressure;
        private Sensor mTemperature;
        private TextView temperaturelabel;
        private float currentTemperature;
        boolean isCelsius = true;
        private Bitmap mBackgroundBitmap;
        private Bitmap timeIcon;
        private Paint mBackgroundImagePaint;
        boolean changed = false;



        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setAcceptsTapEvents(true)
                    .build());

            mCalendar = Calendar.getInstance();

            Resources resources = MyWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);



            // Initializes background.
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(
                    ContextCompat.getColor(getApplicationContext(), R.color.background));


            // Initializes Watch Face.
            mTextPaint = new Paint();
            mTextPaint.setTypeface(NORMAL_TYPEFACE);
            mTextPaint.setAntiAlias(true);
            mTextPaint.setColor(
                    ContextCompat.getColor(getApplicationContext(), R.color.digital_text));

            // Initializes Watch Face.
            mTextPaintTemp = new Paint();
            //CUSTOM_TYPEFACE = ResourcesCompat.getFont(this, R.font.FiraSans-Black);

            mTextPaintTemp.setTypeface(NORMAL_TYPEFACE);
            mTextPaintTemp.setAntiAlias(true);
            mTextPaintTemp.setColor(
                    ContextCompat.getColor(getApplicationContext(), R.color.digital_text));
            mTextPaintTemp.setTextSize(28f);

            mBackgroundImagePaint = new Paint();
            mBackgroundImagePaint.setColor(Color.BLACK);


        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                registerSensor();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
                unregisterSensor();
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

        private void registerSensor() {
            //mTemperature= mSensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE); // requires API level 14
            mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            mSensorManager.registerListener(this,
                    mSensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE),
                    SensorManager.SENSOR_DELAY_NORMAL);
        }

        private void unregisterSensor() {
            mSensorManager.unregisterListener(this);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = MyWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mTextPaint.setTextSize(textSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);

            mAmbient = inAmbientMode;
            if (mLowBitAmbient) {
                mTextPaint.setAntiAlias(!inAmbientMode);
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
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
                    //Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT)
                           // .show();
                    //Toast.makeText(getApplicationContext(), Float.toString(ambient_temperature), Toast.LENGTH_SHORT).show();
                    isCelsius = !isCelsius;
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if(changed) {

                if (ambient_temperature_faren > 100) {
                    mBackgroundBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.desert);
                    mTextPaintTemp.setColor(
                            ContextCompat.getColor(getApplicationContext(), R.color.white));

                }
                else if (ambient_temperature_faren > 80) {
                    mBackgroundBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.beachtwo);
                    mTextPaintTemp.setColor(
                            ContextCompat.getColor(getApplicationContext(), R.color.white));
                }
                else if (ambient_temperature_faren > 70) {
                    mBackgroundBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.spring);
                    mTextPaintTemp.setColor(
                            ContextCompat.getColor(getApplicationContext(), R.color.white));
                }
                else if (ambient_temperature_faren > 60) {
                    mBackgroundBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.squirrel);
                    mTextPaintTemp.setColor(
                            ContextCompat.getColor(getApplicationContext(), R.color.white));
                }
                else if (ambient_temperature_faren > 32) {
                    mBackgroundBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.autumn);
                    mTextPaintTemp.setColor(
                            ContextCompat.getColor(getApplicationContext(), R.color.white));
                }
                else if (ambient_temperature_faren > 0) {
                    mBackgroundBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.freeze);
                    mTextPaintTemp.setColor(
                            ContextCompat.getColor(getApplicationContext(), R.color.white));
                }
                else {
                    mBackgroundBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ice);
                    mTextPaintTemp.setColor(
                            ContextCompat.getColor(getApplicationContext(), R.color.white));
                }

                if (isInAmbientMode()) {
                    canvas.drawColor(Color.BLACK);
                } else {
                    canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
                }

                DisplayMetrics metrics = new DisplayMetrics();
                WindowManager window = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
                Display display = window.getDefaultDisplay();
                display.getMetrics(metrics);


                //System.out.println("metrics.density: " + metrics.density + " metrics.scaledDensity: " + metrics.scaledDensity + " metrics.widthPixels" + metrics.widthPixels);
                drawBackground(canvas,  Bitmap.createScaledBitmap(mBackgroundBitmap, (int) metrics.widthPixels, (int) metrics.heightPixels, false), bounds);
            }
            changed = false;

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            String text = mAmbient
                    ? String.format("%d:%02d", mCalendar.get(Calendar.HOUR),
                    mCalendar.get(Calendar.MINUTE))
                    : String.format("%d:%02d:%02d", mCalendar.get(Calendar.HOUR),
                    mCalendar.get(Calendar.MINUTE), mCalendar.get(Calendar.SECOND));
            canvas.drawText(text, (bounds.centerX() - mTextPaint.measureText(text)/2), mYOffset, mTextPaint);

            //Its between 6 am and 6pm
            if( mCalendar.get(Calendar.HOUR_OF_DAY) > 6 &&  mCalendar.get(Calendar.HOUR_OF_DAY) < 18) {
                timeIcon = BitmapFactory.decodeResource(getResources(), R.drawable.sun);
                timeIcon = Bitmap.createScaledBitmap(timeIcon, (int) 50, (int) 50, false);
            }
            else {
                timeIcon = BitmapFactory.decodeResource(getResources(), R.drawable.moon);
            }



            if(isCelsius) { //Then show the temp in celsius
                canvas.drawText(Float.toString(ambient_temperature) + " \u2103", (bounds.centerX() - mTextPaintTemp.measureText(Float.toString(ambient_temperature) + " \u2103")/2), bounds.centerY() + 20, mTextPaintTemp);
            }
            else {
                canvas.drawText(Float.toString(ambient_temperature_faren) + " \u2109", (bounds.centerX() - mTextPaintTemp.measureText(Float.toString(ambient_temperature_faren) + " \u2109")/2), bounds.centerY() + 20, mTextPaintTemp);
            }

            canvas.drawBitmap(timeIcon, (bounds.centerX()) - 25, bounds.centerY() + 40, mBackgroundImagePaint);



        }

        private void drawBackground(Canvas canvas, Bitmap background, Rect bounds) {
                canvas.drawBitmap(background, 0, 0, mBackgroundImagePaint);

        }

        private Bitmap getScaledBitMapBaseOnScreenSize(Bitmap bitmapOriginal){

            Bitmap scaledBitmap=null;
            try {
                DisplayMetrics metrics = new DisplayMetrics();
                WindowManager window = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
                Display display = window.getDefaultDisplay();
                display.getMetrics(metrics);

                int width = bitmapOriginal.getWidth();
                int height = bitmapOriginal.getHeight();


                float scaleWidth = metrics.scaledDensity;
                float scaleHeight = metrics.scaledDensity;

                // create a matrix for the manipulation
                Matrix matrix = new Matrix();
                // resize the bit map
                matrix.postScale(scaleWidth, scaleHeight);

                // recreate the new Bitmap
                scaledBitmap = Bitmap.createBitmap(bitmapOriginal, 0, 0, width, height, matrix, true);
            }
            catch (Exception e){
                e.printStackTrace();
            }
            return scaledBitmap;
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

        @Override
        public final void onAccuracyChanged(Sensor sensor, int accuracy) {
            // Do something here if sensor accuracy changes.
        }

        @Override
        public final void onSensorChanged(SensorEvent event) {
            ambient_temperature = event.values[0];
            ambient_temperature_faren = convertToFaren(ambient_temperature);
            // Do something with this sensor data.
            changed = true;
        }

        public float convertToFaren(float temp) {
            return (float) Math.floor(((temp * (9/5.0) + 32.0) * 10)) / 10;
        }

    }



}
