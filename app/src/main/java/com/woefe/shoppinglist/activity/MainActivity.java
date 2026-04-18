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

import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.content.SharedPreferences;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.ShareActionProvider;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuItemCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.snackbar.Snackbar;

import com.woefe.shoppinglist.R;
import com.woefe.shoppinglist.dialog.ConfirmationDialog;
import com.woefe.shoppinglist.dialog.TextInputDialog;
import com.woefe.shoppinglist.shoppinglist.ListItem;
import com.woefe.shoppinglist.shoppinglist.ListsChangeListener;
import com.woefe.shoppinglist.shoppinglist.ShoppingList;
import com.woefe.shoppinglist.shoppinglist.ShoppingListException;
import com.woefe.shoppinglist.shoppinglist.ShoppingListMarshaller;
import com.woefe.shoppinglist.shoppinglist.ShoppingListService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Comparator;

public class MainActivity extends BinderActivity implements
        ConfirmationDialog.ConfirmationDialogListener, TextInputDialog.TextInputDialogListener, ListsChangeListener {

    private static final String KEY_FRAGMENT = "FRAGMENT";
    private static final String KEY_LIST_NAME = "LIST_NAME";
    private static final String KEY_SORT_ORDER_PREFIX = "SORT_ORDER_";

    private enum SortType {
        NONE,
        MANUAL,
        A_TO_Z,
        Z_TO_A,
        CHECKED_ASC,
        CHECKED_DESC
    }
    private DrawerLayout drawerLayout;
    private ListView drawerList;
    private LinearLayout drawerContainer;
    private ArrayAdapter<String> drawerAdapter;
    private ActionBarDrawerToggle drawerToggle;
    private Fragment currentFragment;
    private String currentListName;
    private ShareActionProvider actionProvider;
    private int lastTheme;
    private SharedPreferences prefs;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Enable edge-to-edge so we draw behind system bars
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        
        setContentView(R.layout.activity_main);

        // Make status bar and navigation bar transparent
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        
        // Set light/dark status bar icons based on theme
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        final Toolbar toolbar = findViewById(R.id.toolbar_main);
        
        int currentHeight = toolbar.getLayoutParams().height;
        if (currentHeight > 0) {
            toolbar.getLayoutParams().height = currentHeight + 48;
        }
        toolbar.setPadding(toolbar.getPaddingLeft(), toolbar.getPaddingTop() + 48,
            toolbar.getPaddingRight(), toolbar.getPaddingBottom());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (currentFragment instanceof ShoppingListFragment &&
                        !((ShoppingListFragment) currentFragment).onBackPressed()) {
                    finish();
                }
            }
        });

        drawerLayout = findViewById(R.id.drawer_layout);
        drawerContainer = findViewById(R.id.nav_drawer_container);
        drawerList = findViewById(R.id.nav_drawer_content);
        drawerAdapter = new ArrayAdapter<>(this, R.layout.drawer_list_item);
        drawerList.setAdapter(drawerAdapter);
        drawerList.setOnItemClickListener(new ListView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                selectList(position);
            }
        });
        lastTheme = AppCompatDelegate.getDefaultNightMode();
        prefs = getSharedPreferences("shopping_list_prefs", MODE_PRIVATE);
        
        toolbar.setNavigationIcon(R.drawable.ic_menu_24dp);
        toolbar.setNavigationContentDescription(getString(R.string.app_name));
        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeButtonEnabled(true);
        }

        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.drawer_open, R.string.drawer_close);
        drawerToggle.setDrawerIndicatorEnabled(true);
        drawerLayout.addDrawerListener(drawerToggle);

        if (savedInstanceState != null) {
            Fragment fragment = getSupportFragmentManager().getFragment(savedInstanceState, KEY_FRAGMENT);
            String name = savedInstanceState.getString(KEY_LIST_NAME);
            setFragment(fragment, name);
        }
    }

    @Override
    public void onPostCreate(@Nullable Bundle savedInstanceState, @Nullable PersistableBundle persistentState) {
        super.onPostCreate(savedInstanceState, persistentState);
        drawerToggle.syncState();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (AppCompatDelegate.getDefaultNightMode() != lastTheme) {
            recreate();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (currentFragment != null) {
            getSupportFragmentManager().putFragment(outState, KEY_FRAGMENT, currentFragment);
        }
        outState.putString(KEY_LIST_NAME, currentListName);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onServiceConnected(ShoppingListService.ShoppingListBinder binder) {
        updateDrawer();

        if (currentFragment == null || !binder.hasList(currentListName)) {
            selectList(0);
        }
        if (currentFragment != null && currentFragment instanceof ShoppingListFragment) {
            ((ShoppingListFragment) currentFragment).setShoppingList(binder.getList(currentListName));
        }
        binder.addListChangeListener(this);

        if (binder.usesFallbackDir()) {
            Snackbar snackbar = Snackbar.make(findViewById(R.id.content_frame),
                    R.string.warn_ignore_directory, Snackbar.LENGTH_LONG);

            snackbar.setAction(R.string.action_settings, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openSettings();
                }
            });
            snackbar.show();
        }
    }

    @Override
    protected void onServiceDisconnected(ShoppingListService.ShoppingListBinder binder) {
        binder.removeListChangeListener(this);
        drawerAdapter.clear();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    private void doShare() {
        String text;
        ShoppingList list = getBinder().getList(currentListName);

        if (list == null) {
            Toast.makeText(this, R.string.err_share_list, Toast.LENGTH_LONG).show();
            return;
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ShoppingListMarshaller.marshall(outputStream, list);
            text = outputStream.toString();
        } catch (IOException ignored) {
            return;
        }

        Intent intent = new Intent()
                .setAction(Intent.ACTION_SEND)
                .putExtra(Intent.EXTRA_TEXT, text)
                .setType("text/plain");
        if (actionProvider != null) {
            actionProvider.setShareIntent(intent);
        } else {
            startActivity(Intent.createChooser(intent, getString(R.string.share)));
        }
    }

    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_settings) {
            openSettings();
            return true;
        } else if (itemId == R.id.action_delete_checked) {
            String message = getString(R.string.remove_checked_items);
            ConfirmationDialog.show(this, message, R.id.action_delete_checked);
            return true;
        } else if (itemId == R.id.action_delete_list) {
            String message = getString(R.string.confirm_delete_list, getTitle());
            if (getBinder().hasList(currentListName)) {
                ConfirmationDialog.show(this, message, R.id.action_delete_list);
            } else {
                Toast.makeText(this, R.string.err_cannot_delete_list, Toast.LENGTH_LONG).show();
            }
            return true;
        } else if (itemId == R.id.action_new_list) {
            NewListDialog.Builder builder = new TextInputDialog.Builder(this, NewListDialog.class);
            builder.setAction(R.id.action_new_list)
                    .setMessage(R.string.add_new_list)
                    .setHint(R.string.add_list_hint)
                    .show();
            return true;
        } else if (itemId == R.id.action_view_about) {
            Intent intent = new Intent(this, AboutActivity.class);
            startActivity(intent);
            return true;
        } else if (itemId == R.id.action_share) {
            doShare();
            return true;
        } else if (itemId == R.id.action_sort_manual) {
            if (currentListName != null) {
                getBinder().setListSortComparator(currentListName, null);
            }
            if (currentFragment instanceof ShoppingListFragment) {
                RecyclerListAdapter adapter = ((ShoppingListFragment) currentFragment).getRecyclerListAdapter();
                if (adapter != null) {
                    adapter.setSortComparator(null);
                }
            }
            saveSortOrder(SortType.MANUAL);
            updateDragHandlerState();
            return true;
        } else if (itemId == R.id.action_sort_a_to_z) {
						applySortOrder(getBinder().getList(currentListName), SortType.A_TO_Z);
            return true;
        } else if (itemId == R.id.action_sort_z_to_a) {
						applySortOrder(getBinder().getList(currentListName), SortType.Z_TO_A);
            return true;
        } else if (itemId == R.id.action_sort_by_checked_asc) {
						applySortOrder(getBinder().getList(currentListName), SortType.CHECKED_ASC);
            return true;
        } else if (itemId == R.id.action_sort_by_checked_desc) {
						applySortOrder(getBinder().getList(currentListName), SortType.CHECKED_DESC);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void saveSortOrder(SortType sortType) {
        if (currentListName != null) {
            prefs.edit().putString(KEY_SORT_ORDER_PREFIX + currentListName, sortType.name()).apply();
        }
    }

    private SortType getSavedSortOrder(String listName) {
        String name = prefs.getString(KEY_SORT_ORDER_PREFIX + listName, SortType.NONE.name());
        try {
            return SortType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return SortType.NONE;
        }
    }

    public void applySavedSortOrder() {
        if (currentListName != null && getBinder().hasList(currentListName)) {
            applySortOrder(getBinder().getList(currentListName), getSavedSortOrder(currentListName));
        }
    }

    private void applySortOrder(ShoppingList list, SortType sortType) {
        if (list == null) {
            return;
        }
        saveSortOrder(sortType);
        Comparator<ListItem> comparator = null;
        switch (sortType) {
            case A_TO_Z:
                comparator = new Comparator<ListItem>() {
                    @Override
                    public int compare(ListItem o1, ListItem o2) {
                        return o1.getDescription().compareToIgnoreCase(o2.getDescription());
                    }
                };
                break;
            case Z_TO_A:
                comparator = new Comparator<ListItem>() {
                    @Override
                    public int compare(ListItem o1, ListItem o2) {
                        return o2.getDescription().compareToIgnoreCase(o1.getDescription());
                    }
                };
                break;
            case CHECKED_ASC:
                comparator = new Comparator<ListItem>() {
                    @Override
                    public int compare(ListItem o1, ListItem o2) {
                        if (o1.isChecked() && !o2.isChecked()) {
                            return -1;
                        }
                        return 1;
                    }
                };
                break;
            case CHECKED_DESC:
                comparator = new Comparator<ListItem>() {
                    @Override
                    public int compare(ListItem o1, ListItem o2) {
                        if (!o1.isChecked() && o2.isChecked()) {
                            return -1;
                        }
                        return 1;
                    }
                };
                break;
        }
        if (currentFragment instanceof ShoppingListFragment) {
            RecyclerListAdapter adapter = ((ShoppingListFragment) currentFragment).getRecyclerListAdapter();
            if (adapter != null) {
                adapter.setSortComparator(comparator);
            }
        }
        if (comparator != null && currentListName != null) {
            getBinder().setListSortComparator(currentListName, comparator);
        }
        updateDragHandlerState();
    }

    private void updateDragHandlerState() {
        if (currentFragment instanceof ShoppingListFragment) {
            ShoppingListFragment fragment = (ShoppingListFragment) currentFragment;
            if (fragment.getRecyclerListAdapter() == null) {
                return;
            }
            SortType sortType = getCurrentSortOrder();
            boolean enabled = (sortType == SortType.MANUAL || sortType == SortType.NONE);
            fragment.setDragHandlerEnabled(enabled);
            android.util.Log.d("MainActivity", "Drag handlers " + (enabled ? "enabled" : "disabled") + " for sort type " + sortType);
        }
    }

    @Override
    public void onPositiveButtonClicked(int action) {
        if (action == R.id.action_delete_checked) {
            if (currentFragment != null && currentFragment instanceof ShoppingListFragment) {
                ((ShoppingListFragment) currentFragment).removeAllCheckedItems();
            }
        } else if (action == R.id.action_delete_list) {
            boolean success = getBinder().removeList(getTitle().toString());
            if (!success) {
                Toast.makeText(this, R.string.err_cannot_delete_list, Toast.LENGTH_LONG).show();
            } else {
                updateDrawer();
                selectList(0);
            }
        }
    }

    @Override
    public void onNegativeButtonClicked(int action) {
    }

    public void onInputComplete(String input, int action) {
        if (isServiceConnected() && action == R.id.action_new_list) {
            try {
                getBinder().addList(input);
            } catch (ShoppingListException e) {
                Log.e(getClass().getSimpleName(), "List already exists", e);
            }
            selectList(getBinder().indexOf(input));
        }
    }

    @Override
    public void onListsChanged() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateDrawer();
                if (!getBinder().hasList(currentListName)) {
                    selectList(0);
                }
                if (currentFragment != null && currentFragment instanceof ShoppingListFragment) {
                    ((ShoppingListFragment) currentFragment).setShoppingList(getBinder().getList(currentListName));
                }
            }
        });
    }

    private void setFragment(Fragment fragment, String name) {
        this.currentListName = name;
        this.currentFragment = fragment;
        setTitle(name);
        updateDrawer();
        FragmentManager manager = getSupportFragmentManager();
        manager.beginTransaction().replace(R.id.content_frame, fragment).commit();
    }

    private void selectList(int position) {
        if (position >= getBinder().size()) {
            setFragment(new InvalidFragment(), getString(R.string.app_name));
            return;
        }

        String name = drawerAdapter.getItem(position);
        Fragment fragment = ShoppingListFragment.newInstance(getBinder().getList(name));
        setFragment(fragment, name);

        drawerLayout.closeDrawer(drawerContainer);
    }

    private void updateDrawer() {
        drawerAdapter.clear();

        if (!isServiceConnected()) {
            return;
        }

        drawerAdapter.addAll(getBinder().getListNames());

        int fragmentPos = getBinder().indexOf(currentListName);
        if (fragmentPos >= 0) {
            drawerList.setItemChecked(fragmentPos, true);
        }
    }

    public static class NewListDialog extends TextInputDialog {
        @Override
        public boolean onValidateInput(String input) {
            MainActivity activity = (MainActivity) getActivity();

            if (input == null || input.equals("")) {
                Toast.makeText(activity, R.string.error_list_name_empty, Toast.LENGTH_SHORT).show();
                return false;
            }

            assert activity != null;
            if (!activity.isServiceConnected() || activity.getBinder().hasList(input)) {
                Toast.makeText(activity, R.string.error_list_exists, Toast.LENGTH_SHORT).show();
                return false;
            }

            return true;
        }
    }

    public Snackbar makeUndoSnackbar() {
        View snackbarView;
        if (currentFragment instanceof ShoppingListFragment && ((ShoppingListFragment) currentFragment).getEditBar().isVisible()) {
            snackbarView = findViewById(R.id.shoppingListView);
        } else {
            snackbarView = findViewById(R.id.fab_add_parent);
        }
        return Snackbar.make(snackbarView, R.string.item_deleted, Snackbar.LENGTH_LONG);
    }

    private boolean isLightMode() {
        int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return currentNightMode == Configuration.UI_MODE_NIGHT_NO;
    }

    public SortType getCurrentSortOrder() {
        if (currentListName != null) {
            return getSavedSortOrder(currentListName);
        }
        return SortType.NONE;
    }
}
