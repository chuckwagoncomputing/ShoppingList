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

package com.woefe.shoppinglist.shoppinglist;

import java.util.UUID;

/**
 * @author Wolfgang Popp.
 */
public class ListItem {
    private boolean isChecked;
    private String description;
    private String quantity;
    private UUID uuid;

    public ListItem(boolean isChecked, String description, String quantity, UUID uuid) {
        android.util.Log.d("ListItem", "ListItem: isChecked=" + isChecked + " desc=" + description + " qty=" + quantity + " uuid=" + uuid);
        this.isChecked = isChecked;
        this.description = description;
        this.quantity = quantity;
        this.uuid = uuid;
    }

    public ListItem(boolean isChecked, String description, String quantity) {
        this(isChecked, description, quantity, UUID.randomUUID());
    }

    public UUID getUuid() {
        android.util.Log.d("ListItem", "getUuid: returning " + uuid);
        return uuid;
    }

    public void setUuid(UUID uuid) {
        android.util.Log.d("ListItem", "setUuid: setting to " + uuid);
        this.uuid = uuid;
    }

    public boolean isChecked() {
        return isChecked;
    }

    public void setChecked(boolean isChecked) {
        this.isChecked = isChecked;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getQuantity() {
        return quantity;
    }

    public void setQuantity(String quantity) {
        this.quantity = quantity;
    }

    static class ListItemWithUuid extends ListItem {
        private final UUID uuid;

        public ListItemWithUuid(UUID uuid, ListItem item) {
            super(item.isChecked, item.description, item.quantity, item.getUuid());
            this.uuid = uuid;
        }

        public UUID getUuid() {
            return uuid;
        }

    }
}