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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShoppingListUnmarshaller {
    private static final Pattern EMPTY_LINE = Pattern.compile("^\\s*$");

    public static ShoppingList unmarshal(String filename) throws IOException, UnmarshallException {
        String name = filename.substring(filename.lastIndexOf(File.separator)+1).replace("+", " ").replace(".lst", "");
        return unmarshal(new FileInputStream(filename), name);
    }

    public static ShoppingList unmarshal(InputStream inputStream, String name) throws IOException, UnmarshallException {
        ShoppingList shoppingList;
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

        shoppingList = new ShoppingList(name);

        String line;
        while ((line = reader.readLine()) != null) {
            if (!EMPTY_LINE.matcher(line).matches()) {
                shoppingList.add(createListItem(line));
            }
        }

        return shoppingList;
    }

    private static ListItem createListItem(String item) {
        boolean isChecked = item.startsWith("//");
        int index;
        String quantity;
        String name;

        if (isChecked) {
            item = item.substring(2);
        }

        index = item.lastIndexOf("#");

        if (index != -1) {
            quantity = item.substring(index + 1).trim();
            name = item.substring(0, index).trim();
        } else {
            quantity = "";
            name = item.trim();
        }

        return new ListItem(isChecked, name.trim(), quantity);
    }
}
