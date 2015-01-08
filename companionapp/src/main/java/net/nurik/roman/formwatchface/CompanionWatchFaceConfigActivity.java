/*
 * Copyright 2014 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.nurik.roman.formwatchface;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.widget.ImageButton;

import net.nurik.roman.formwatchface.common.ChangeConfigIntentService;
import net.nurik.roman.formwatchface.common.FormClockView;
import net.nurik.roman.formwatchface.common.MathUtil;
import net.nurik.roman.formwatchface.common.Themes;

import java.util.ArrayList;

import static net.nurik.roman.formwatchface.common.Themes.Theme;

public class CompanionWatchFaceConfigActivity extends Activity {
    private static final String TAG = "CompanionWatchFaceConfigActivity";

    private ViewGroup mThemeItemContainer;
    private ArrayList<ThemeUiHolder> mThemeUiHolders = new ArrayList<>();

    private View mMainClockContainerView;
    private FormClockView mMainClockView;
    private View mAnimateClockContainerView;
    private FormClockView mAnimateClockView;
    private Animator mCurrentRevealAnimator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        setContentView(R.layout.activity_config);

        mMainClockContainerView = ((ViewGroup) findViewById(R.id.clock_container)).getChildAt(0);
        mMainClockView = (FormClockView) mMainClockContainerView.findViewById(R.id.clock);

        mAnimateClockContainerView = ((ViewGroup) findViewById(R.id.clock_container)).getChildAt(1);
        mAnimateClockView = (FormClockView) mAnimateClockContainerView.findViewById(R.id.clock);

        mAnimateClockContainerView.setVisibility(View.INVISIBLE);

        setupThemeList();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(
                CompanionWatchFaceConfigActivity.this);
        String themeId = sp.getString("theme", Themes.DEFAULT_THEME.id);
        updateSelectedTheme(themeId, false);

        registerSharedPrefsListener();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterSharedPrefsListener();
    }

    private void registerSharedPrefsListener() {
        PreferenceManager.getDefaultSharedPreferences(this)
                .registerOnSharedPreferenceChangeListener(mOnSharedPreferenceChangeListener);
    }

    private void unregisterSharedPrefsListener() {
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(mOnSharedPreferenceChangeListener);
    }

    private SharedPreferences.OnSharedPreferenceChangeListener mOnSharedPreferenceChangeListener
            = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if ("theme".equals(key)) {
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(
                        CompanionWatchFaceConfigActivity.this);
                String themeId = sp.getString("theme", Themes.DEFAULT_THEME.id);
                updateSelectedTheme(themeId, true);
            }
        }
    };

    private void setupThemeList() {
        mThemeUiHolders.clear();
        mThemeItemContainer = (ViewGroup) findViewById(R.id.theme_list);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (final Theme theme : Themes.THEMES) {
            ThemeUiHolder holder = new ThemeUiHolder();

            holder.theme = theme;
            holder.container = inflater.inflate(R.layout.theme_item, mThemeItemContainer, false);
            holder.button = (ImageButton) holder.container.findViewById(R.id.button);

            LayerDrawable bgDrawable = (LayerDrawable)
                    getResources().getDrawable(R.drawable.theme_item_bg).mutate();

            GradientDrawable gd = (GradientDrawable) bgDrawable.findDrawableByLayerId(R.id.color);
            gd.setColor(getResources().getColor(theme.midRes));
            holder.button.setBackground(bgDrawable);

            holder.button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    updateSelectedTheme(theme.id, true);
                    startService(new Intent(CompanionWatchFaceConfigActivity.this,
                            ChangeConfigIntentService.class)
                            .putExtra(ChangeConfigIntentService.EXTRA_THEME, theme.id));
                }
            });

            mThemeUiHolders.add(holder);
            mThemeItemContainer.addView(holder.container);
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void updateSelectedTheme(String themeId, boolean animate) {
        for (final ThemeUiHolder holder : mThemeUiHolders) {
            boolean selected = holder.theme.id.equals(themeId);

            holder.button.setSelected(selected);

            if (holder.selected != selected && selected) {
                if (mCurrentRevealAnimator != null) {
                    mCurrentRevealAnimator.cancel();
                }

                if (animate && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setClockViewColors(holder.theme, mAnimateClockContainerView, mAnimateClockView);

                    Rect buttonRect = new Rect();
                    Rect clockContainerRect = new Rect();
                    holder.button.getGlobalVisibleRect(buttonRect);
                    mMainClockContainerView.getGlobalVisibleRect(clockContainerRect);

                    int cx = buttonRect.centerX() - clockContainerRect.left;
                    int cy = buttonRect.centerY() - clockContainerRect.top;
                    clockContainerRect.offsetTo(0, 0);

                    mCurrentRevealAnimator = ViewAnimationUtils.createCircularReveal(
                            mAnimateClockContainerView, cx, cy, 0,
                            MathUtil.maxDistanceToCorner(clockContainerRect, cx, cy));
                    mAnimateClockContainerView.setVisibility(View.VISIBLE);
                    mCurrentRevealAnimator.setDuration(300);
                    mCurrentRevealAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            mAnimateClockContainerView.setVisibility(View.INVISIBLE);
                            setClockViewColors(holder.theme, mMainClockContainerView, mMainClockView);
                        }
                    });
                    mCurrentRevealAnimator.start();
                } else {
                    setClockViewColors(holder.theme, mMainClockContainerView, mMainClockView);
                }
            }

            holder.selected = selected;
        }
    }

    private void setClockViewColors(Theme theme, View clockContainerView, FormClockView clockView) {
        final Resources res = getResources();
        clockView.setColors(
                res.getColor(theme.lightRes),
                res.getColor(theme.midRes),
                Color.WHITE);
        clockContainerView.setBackgroundColor(
                res.getColor(theme.darkRes));
    }

    private static class ThemeUiHolder {
        Theme theme;
        View container;
        ImageButton button;
        boolean selected;
    }
}
