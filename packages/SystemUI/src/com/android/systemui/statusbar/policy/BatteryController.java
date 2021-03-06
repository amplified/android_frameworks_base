/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.systemui.statusbar.policy;

import java.util.ArrayList;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Color;
import android.os.BatteryManager;
import android.os.Handler;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.systemui.R;

public class BatteryController extends BroadcastReceiver {
    private static final String TAG = "StatusBar.BatteryController";

    private Context mContext;
    private ArrayList<ImageView> mIconViews = new ArrayList<ImageView>();
    private ArrayList<TextView> mLabelViews = new ArrayList<TextView>();

    private static final int BATTERY_STYLE_NORMAL         = 0;
    private static final int BATTERY_STYLE_PERCENT        = 1;
    private static final int BATTERY_STYLE_ICON_PERCENT   = 2;
    /***
     * BATTERY_STYLE_CIRCLE* cannot be handled in this controller, since we cannot get views from
     * statusbar here. Yet it is listed for completion and not to confuse at future updates
     * See CircleBattery.java for more info
     *
     * set to public to be reused by CircleBattery
     */
    public  static final int BATTERY_STYLE_CIRCLE                      = 3;
    public  static final int BATTERY_STYLE_CIRCLE_PERCENT           = 4;
    public  static final int BATTERY_STYLE_DOTTED_CIRCLE            = 5;
    public  static final int BATTERY_STYLE_DOTTED_CIRCLE_PERCENT = 6;
    private static final int BATTERY_STYLE_GONE                        = 7;

    private static final int BATTERY_TEXT_STYLE_NORMAL  = R.string.status_bar_settings_battery_meter_format;
    private static final int BATTERY_TEXT_STYLE_MIN     = R.string.status_bar_settings_battery_meter_min_format;

    private int mLevel = 0;
    private int mTextColor = -2;
    private int mTextChargingColor = -2;
    private int mBatteryStyle;
    private boolean mBatteryPlugged = false;
    private int mBatteryStatus = BatteryManager.BATTERY_STATUS_UNKNOWN;

    Handler mHandler;

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_BATTERY),
                    false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_BATTERY_TEXT_COLOR),
                    false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_BATTERY_TEXT_CHARGING_COLOR),
                    false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    private ArrayList<BatteryStateChangeCallback> mChangeCallbacks =
            new ArrayList<BatteryStateChangeCallback>();

    public interface BatteryStateChangeCallback {
        public void onBatteryLevelChanged(int level, boolean pluggedIn);
    }

    public BatteryController(Context context) {
        mContext = context;
        mHandler = new Handler();

        SettingsObserver settingsObserver = new SettingsObserver(mHandler);
        settingsObserver.observe();
        updateSettings();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        mContext.registerReceiver(this, filter);
    }

    public void addIconView(ImageView v) {
        mIconViews.add(v);
    }

    public void addLabelView(TextView v) {
        mLabelViews.add(v);
    }

    public void addStateChangedCallback(BatteryStateChangeCallback cb) {
        mChangeCallbacks.add(cb);
    }

    public int getIconStyleUnknown() {
        return R.drawable.stat_sys_battery;
    }

    public int getIconStyleNormal() {
        return R.drawable.stat_sys_battery;
    }

    public int getIconStyleCharge() {
        return R.drawable.stat_sys_battery_charge;
    }

    public int getIconStyleNormalMin() {
        return R.drawable.stat_sys_battery_min;
    }

    public int getIconStyleChargeMin() {
        return R.drawable.stat_sys_battery_charge_min;
    }

    public int getTextStyleNormal() {
        return R.string.status_bar_settings_battery_meter_format;
    }

    public int getTextStyleNormalMin() {
        return R.string.status_bar_settings_battery_meter_min_format;
    }

    protected int getBatteryStyle() {
        return mBatteryStyle;
    }

    protected int getBatteryStatus() {
        return mBatteryStatus;
    }

    protected boolean isBatteryPlugged() {
        return mBatteryPlugged;
    }

    protected boolean isBatteryPresent() {
        return true;
    }

    private boolean isBatteryStatusUnknown() {
        return getBatteryStatus() == BatteryManager.BATTERY_STATUS_UNKNOWN;
    }

    private boolean isBatteryStatusCharging() {
        return getBatteryStatus() == BatteryManager.BATTERY_STATUS_CHARGING;
    }

    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
            mLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            mBatteryPlugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
            mBatteryStatus = intent.getIntExtra(BatteryManager.EXTRA_STATUS,
                                                BatteryManager.BATTERY_STATUS_UNKNOWN);
            updateViews(mLevel);
            updateBattery();
        }
    }

    protected void updateViews(int level) {
        int N = mIconViews.size();
        for (int i=0; i<N; i++) {
            ImageView v = mIconViews.get(i);
            v.setImageLevel(level);
            v.setContentDescription(mContext.getString(
                    R.string.accessibility_battery_level, level));
        }
        N = mLabelViews.size();
        for (int i=0; i<N; i++) {
            TextView v = mLabelViews.get(i);
            if (mBatteryStyle == BATTERY_STYLE_PERCENT) {
                v.setText(mContext.getString(BATTERY_TEXT_STYLE_NORMAL, level));
            } else {
                v.setText(mContext.getString(BATTERY_TEXT_STYLE_MIN, level));
            }
        }

        for (BatteryStateChangeCallback cb : mChangeCallbacks) {
            cb.onBatteryLevelChanged(level, isBatteryStatusCharging());
        }
    }

    protected void updateBattery() {
        int mIcon = View.GONE;
        int mText = View.GONE;
        int mIconStyle = getIconStyleNormal();
        int mTextStyle = getTextStyleNormal();
        int pxTextPadding = 0;

        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getMetrics(metrics);
        float logicalDensity = metrics.density;

        if (isBatteryPresent()) {
            if (isBatteryStatusUnknown() && 
                (mBatteryStyle == BATTERY_STYLE_NORMAL || 
                mBatteryStyle == BATTERY_STYLE_PERCENT)) {
                // Unknown status doesn't relies on any style
                mIcon = (View.VISIBLE);
                mIconStyle = getIconStyleUnknown();
            } else if (mBatteryStyle == BATTERY_STYLE_NORMAL) {
                mIcon = (View.VISIBLE);
                mIconStyle = isBatteryPlugged() ?
                                getIconStyleCharge() : getIconStyleNormal();
            } else if (mBatteryStyle == BATTERY_STYLE_ICON_PERCENT) {
                pxTextPadding = 0;
                mIcon = (View.VISIBLE);
                mText = (View.VISIBLE);
                mIconStyle = isBatteryPlugged() ? 
                                getIconStyleChargeMin() : getIconStyleNormalMin();
                mTextStyle = getTextStyleNormalMin();
            } else if (mBatteryStyle == BATTERY_STYLE_PERCENT) {
                pxTextPadding = (int) (4 * logicalDensity + 0.5);
                mText = (View.VISIBLE);
                mTextStyle = getTextStyleNormal();
            }
        }

        int N = mIconViews.size();
        for (int i=0; i<N; i++) {
            ImageView v = mIconViews.get(i);
            v.setVisibility(mIcon);
            v.setImageResource(mIconStyle);
        }
        N = mLabelViews.size();
        for (int i=0; i<N; i++) {
            TextView v = mLabelViews.get(i);
            v.setVisibility(mText);
            v.setText(mContext.getString(mTextStyle, mLevel));
            v.setPadding(v.getPaddingLeft(),v.getPaddingTop(),
                    pxTextPadding,v.getPaddingBottom());

            // turn text red at 14% when not on charger - same level android battery warning appears
            // if no custom color is defined && over 14% use system color
            // if charging turn to green or to custom user color
            if (mLevel <= 14 && !mBatteryPlugged) {
                v.setTextColor(Color.RED);
            } else if (mTextColor == -2  && !mBatteryPlugged) {
                v.setTextColor(mContext.getResources().getColor(com.android.internal.R.color.holo_blue_light));
            } else if (mTextChargingColor != -2  && mBatteryPlugged) {
                v.setTextColor(mTextChargingColor);
            } else if (mBatteryPlugged) {
                v.setTextColor(Color.GREEN);
            } else {
                v.setTextColor(mTextColor);
            }
        }
    }

    private void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();

        mBatteryStyle = (Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_BATTERY, 0));

        mTextColor = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.STATUS_BAR_BATTERY_TEXT_COLOR, -2);

        mTextChargingColor = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.STATUS_BAR_BATTERY_TEXT_CHARGING_COLOR, -2);

        updateBattery();
    }
}

