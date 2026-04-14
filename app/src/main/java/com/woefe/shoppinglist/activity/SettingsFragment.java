/*
 * ShoppingList - A simple shopping list for Android
 *
 * Copyright (C) 2018.  Wolfgang Popp
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

package com.woefe.shoppinglist.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.woefe.shoppinglist.R;
import com.woefe.shoppinglist.SettingsRepository;
import com.woefe.shoppinglist.dialog.DirectoryChooser;

/**
 * @author Wolfgang Popp.
 */
public class SettingsFragment extends PreferenceFragmentCompat implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String KEY_DIRECTORY_LOCATION = "FILE_LOCATION";
    public static final String KEY_THEME = "THEME";
    private static final int REQUEST_CODE_CHOOSE_DIR = 123;
    private static final int REQUEST_CODE_MANAGE_STORAGE = 124;

    private SettingsRepository settingsRepository;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        settingsRepository = new SettingsRepository(context);
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

        for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); i++) {
            initSummary(getPreferenceScreen().getPreference(i));
        }
        
        Preference permissionsPref = findPreference("PERMISSIONS");
        if (permissionsPref != null) {
            updatePermissionsSummary(permissionsPref);
        }
        
        View content = getActivity().findViewById(android.R.id.content);
        content.setBackgroundColor(getResources().getColor(R.color.colorBackground));
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        final Preference fileLocationPref = findPreference("FILE_LOCATION");

        fileLocationPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(getContext(), DirectoryChooser.class);
                startActivityForResult(intent, REQUEST_CODE_CHOOSE_DIR);
                return true;
            }
        });

        final Preference permissionsPref = findPreference("PERMISSIONS");
        if (permissionsPref != null) {
            updatePermissionsSummary(permissionsPref);
            permissionsPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                        intent.setData(Uri.parse("package:" + requireContext().getPackageName()));
                        startActivityForResult(intent, REQUEST_CODE_MANAGE_STORAGE);
                    } else {
                        ActivityCompat.requestPermissions(getActivity(),
                                new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                REQUEST_CODE_MANAGE_STORAGE);
                    }
                    return true;
                }
            });
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    private void initSummary(Preference p) {
        if (p instanceof PreferenceCategory) {
            PreferenceCategory cat = (PreferenceCategory) p;
            for (int i = 0; i < cat.getPreferenceCount(); i++) {
                initSummary(cat.getPreference(i));
            }
        } else {
            updatePreferences(p);
        }
    }

    private void updatePreferences(Preference p) {
        if (KEY_DIRECTORY_LOCATION.equals(p.getKey())) {
            String path = getSharedPreferences().getString(KEY_DIRECTORY_LOCATION, "");
            p.setSummary(path);
        }
        if ("PERMISSIONS".equals(p.getKey())) {
            updatePermissionsSummary(p);
        }
        if (p instanceof EditTextPreference) {
            EditTextPreference editTextPref = (EditTextPreference) p;
            p.setSummary(editTextPref.getText());
        }
    }

    private void updatePermissionsSummary(Preference p) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            boolean hasPermission = Environment.isExternalStorageManager();
            p.setSummary(hasPermission ? "Granted" : "Not granted - tap to request");
        } else {
            Context context = getContext();
            if (context != null) {
                boolean hasPermission = ContextCompat.checkSelfPermission(context,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
                p.setSummary(hasPermission ? "Granted" : "Not granted");
            }
        }
    }

    private SharedPreferences getSharedPreferences() {
        FragmentActivity activity = getActivity();
        assert activity != null;
        return PreferenceManager.getDefaultSharedPreferences(activity);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(KEY_DIRECTORY_LOCATION)) {
            Preference p = findPreference(key);
            updatePreferences(p);
        } else if (key.equals(KEY_THEME)) {
            int theme = settingsRepository.getTheme();
            AppCompatDelegate.setDefaultNightMode(theme);
            Activity activity = getActivity();
            if (activity != null) {
                activity.recreate();
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case (REQUEST_CODE_CHOOSE_DIR): {
                if (resultCode == Activity.RESULT_OK) {
                    String path = data.getStringExtra(DirectoryChooser.SELECTED_PATH);
                    SharedPreferences.Editor editor = getSharedPreferences().edit();
                    editor.putString(KEY_DIRECTORY_LOCATION, path).apply();
                }
                break;
            }
            case (REQUEST_CODE_MANAGE_STORAGE): {
                Preference p = findPreference("PERMISSIONS");
                if (p != null) {
                    updatePermissionsSummary(p);
                }
                break;
            }
        }
    }
}
