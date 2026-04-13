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

import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;

import com.woefe.shoppinglist.BuildConfig;
import com.woefe.shoppinglist.R;

/**
 * @author Wolfgang Popp
 */

public class AboutActivity extends AppCompatActivity {

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

		setContentView(R.layout.activity_about);

		getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
		getWindow().getDecorView().setSystemUiVisibility(
			isLightMode() ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR : 0);

		final Toolbar toolbar = findViewById(R.id.toolbar_about);
		toolbar.setTitle(R.string.about);
		setSupportActionBar(toolbar);
		
		int currentHeight = toolbar.getLayoutParams().height;
        if (currentHeight > 0) {
            toolbar.getLayoutParams().height = currentHeight + 48;
        }
        toolbar.setPadding(toolbar.getPaddingLeft(), toolbar.getPaddingTop() + 48,
            toolbar.getPaddingRight(), toolbar.getPaddingBottom());

		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
		}

		TextView textView = findViewById(R.id.about_text);
		textView.setMovementMethod(LinkMovementMethod.getInstance());
		textView.setText(Html.fromHtml(getString(R.string.about_version, BuildConfig.VERSION_NAME)));
		textView.append("\n");
		textView.append("\n");
		textView.append("\n");
		textView.append(Html.fromHtml(getString(R.string.about_github)));
		textView.append("\n");
		textView.append("\n");
		textView.append("\n");
		textView.append(Html.fromHtml(getString(R.string.about_license)));
		textView.append("\n");
		textView.append("\n");
		textView.append("\n");
		textView.append(Html.fromHtml(getString(R.string.about_author)));
		textView.append("\n");
		textView.append("\n");
		textView.append("\n");
		textView.append(Html.fromHtml(getString(R.string.about_contributors)));
	}
	
	private boolean isLightMode() {
		int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
		return currentNightMode == Configuration.UI_MODE_NIGHT_NO;
	}
}
