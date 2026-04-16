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

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.woefe.shoppinglist.R;
import com.woefe.shoppinglist.shoppinglist.ListItem;
import com.woefe.shoppinglist.shoppinglist.ShoppingList;

import androidx.recyclerview.widget.RecyclerView;

public class ShoppingListFragment extends Fragment implements EditBar.EditBarListener, RecyclerListAdapter.DragListener {

    private EditBar editBar;
    private RecyclerView recyclerView;
    private RecyclerView.LayoutManager layoutManager;
    private RecyclerListAdapter adapter;
    private View rootView;
    private ShoppingList shoppingList;

    public static ShoppingListFragment newInstance(ShoppingList shoppingList) {
        ShoppingListFragment fragment = new ShoppingListFragment();
        fragment.setShoppingList(shoppingList);
        return fragment;
    }

    public void setShoppingList(ShoppingList shoppingList) {
        this.shoppingList = shoppingList;
        connectList();
    }

    public ShoppingList getShoppingList() {
        return shoppingList;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_shoppinglist, container, false);

        recyclerView = rootView.findViewById(R.id.shoppingListView);
        registerForContextMenu(recyclerView);

        View editBarLayout = rootView.findViewById(R.id.layout_add_item);
        ViewCompat.setOnApplyWindowInsetsListener(editBarLayout, (v, windowInsets) -> {
            int imeBottom = windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            int navBottom = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            int bottomMargin = Math.max(imeBottom, navBottom);

            android.widget.RelativeLayout.LayoutParams params =
                    (android.widget.RelativeLayout.LayoutParams) v.getLayoutParams();
            if (params.bottomMargin != bottomMargin) {
                params.bottomMargin = bottomMargin;
                v.setLayoutParams(params);
            }
            return windowInsets;
        });

        recyclerView.setHasFixedSize(true);

        layoutManager = new LinearLayoutManager(getActivity());
        recyclerView.setLayoutManager(layoutManager);

        editBar = new EditBar(rootView, getActivity());
        editBar.addEditBarListener(this);
        

        if (savedInstanceState != null) {
            editBar.restoreState(savedInstanceState);
        }

        return rootView;
    }

    @Override
    public void onDestroyView() {
        editBar.removeEditBarListener(this);
        editBar.hide();
        super.onDestroyView();
    }

    private void connectList() {
        if (shoppingList != null && adapter != null) {
            adapter.connectShoppingList(shoppingList);
        }
        if (shoppingList != null && editBar != null) {
            editBar.connectShoppingList(shoppingList);
        }
    }

    @Override
    public void onStop() {
        adapter.disconnectShoppingList();
        editBar.disconnectShoppingList();
        super.onStop();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        adapter = new RecyclerListAdapter(getActivity());
        connectList();
        adapter.registerRecyclerView(recyclerView);
        recyclerView.setItemAnimator(null);
        adapter.setDragListener(this);
        adapter.setOnItemLongClickListener(new RecyclerListAdapter.ItemLongClickListener() {
            @Override
            public boolean onLongClick(int position) {
                ListItem listItem = shoppingList.get(position);
                editBar.showEdit(position, listItem.getDescription(), listItem.getQuantity());
                return true;
            }
        });
        recyclerView.setAdapter(adapter);
    }

    @Override
    public void onDragStart() {
        MainActivity activity = (MainActivity) getActivity();
        if (activity != null && activity.isServiceConnected()) {
            activity.getBinder().setDragging(shoppingList.getName(), true);
        }
    }

    @Override
    public void onDragEnd() {
        MainActivity activity = (MainActivity) getActivity();
        if (activity != null && activity.isServiceConnected()) {
            activity.getBinder().setDragging(shoppingList.getName(), false);
        }
    }

    public boolean onBackPressed() {
        if (editBar.isVisible()) {
            editBar.hide();
            return true;
        }
        return false;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        editBar.saveState(outState);
    }

    @Override
    public void onEditSave(int position, String description, String quantity) {
        shoppingList.editItem(position, description, quantity);
        editBar.hide();
        recyclerView.smoothScrollToPosition(position);
    }

    @Override
    public void onNewItem(String description, String quantity) {
        shoppingList.add(description, quantity);
        recyclerView.smoothScrollToPosition(recyclerView.getAdapter().getItemCount() - 1);
    }

    public void removeAllCheckedItems() {
        shoppingList.removeAllCheckedItems();
    }

	public EditBar getEditBar() {
		return editBar;
	}

	public void setDragHandlerEnabled(boolean enabled) {
		adapter.setDragHandlerEnabled(enabled);
	}

	public RecyclerListAdapter getRecyclerListAdapter() {
		return adapter;
	}
}
