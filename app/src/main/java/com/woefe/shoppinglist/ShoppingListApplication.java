/*
 * ShoppingList - A simple shopping list for Android
 *
 * Copyright (C) 2019.  Wolfgang Popp
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.woefe.shoppinglist;

import android.app.Application;
import android.os.Build;

import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.color.DynamicColors;

public class ShoppingListApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DynamicColors.applyToActivitiesIfAvailable(this);
        }
        SettingsRepository settingsRepository = new SettingsRepository(this);
        AppCompatDelegate.setDefaultNightMode(settingsRepository.getTheme());
    }
}
