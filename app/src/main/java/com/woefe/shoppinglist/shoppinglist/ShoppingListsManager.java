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

import android.os.FileObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * @author Wolfgang Popp.
 */
class ShoppingListsManager {
    private static final String TAG = ShoppingListsManager.class.getSimpleName();
    private static final String FILE_ENDING = ".lst";

    private final MetadataContainer shoppingListsMetadata = new MetadataContainer();
    private final List<ListsChangeListener> listeners = new LinkedList<>();
    private FileObserver directoryObserver;
    private String directory;

    ShoppingListsManager() {
    }

    void setListChangeListener(ListsChangeListener listener) {
        this.listeners.add(listener);
    }

    void removeListChangeListenerListener(ListsChangeListener listener) {
        this.listeners.remove(listener);
    }

    void onStart(final String directory) {
        this.directory = directory;
        directoryObserver = new FileObserver(directory) {
            @Override
            public void onEvent(int event, @Nullable String path) {
                if (path == null) {
                    return;
                }
                if (!path.endsWith(FILE_ENDING)) {
                    return;
                }
                File file = new File(directory, path);
                switch (event) {
                    case FileObserver.DELETE:
                        shoppingListsMetadata.removeByFile(file.getPath());
                        break;
                    case FileObserver.CREATE:
                        SystemClock.sleep(100);
                        loadFromFile(file);
                        break;
                    case FileObserver.CLOSE_WRITE:
                    case FileObserver.MODIFY:
                        ShoppingListMetadata metadata = shoppingListsMetadata.getByFile(file.getPath());
                        if (metadata != null && (metadata.isDragging || metadata.isSyncing)) {
                            return;
                        }
                        loadFromFile(file);
                        break;
                }
            }
        };

        Log.d(getClass().getSimpleName(), "Initializing from dir " + directory);
        maybeAddInitialList();
        loadFromDirectory(directory);
        directoryObserver.startWatching();
    }

    void onStop() {
        listeners.clear();
        directoryObserver.stopWatching();
        directoryObserver = null;

        for (ShoppingListMetadata metadata : shoppingListsMetadata.values()) {
            metadata.observer.stopWatching();
        }

        shoppingListsMetadata.clear();
    }

    private void maybeAddInitialList() {
        boolean foundFile = false;

        File dir = new File(directory);
        File[] files = dir.listFiles((dir1, name) -> name.endsWith(FILE_ENDING));
        if (files != null) {
            for (File file : files) {
                foundFile = foundFile || file.isFile();
            }
        }

        if (!foundFile) {
            try {
                addList("Shopping List");
            } catch (ShoppingListException e) {
                Log.e(getClass().getSimpleName(), "Failed to add initial list", e);
            }
        }
    }

    private long lastReloadTime = 0;
    private String lastReloadedPath = null;

    private void loadFromFile(File file) {
        String filePath = file.getPath();
        long now = SystemClock.elapsedRealtime();
        if (lastReloadedPath != null && lastReloadedPath.equals(filePath) && (now - lastReloadTime) < 200) {
            Log.d(TAG, "loadFromFile: SKIPPED (throttled) " + filePath);
            return;
        }
        Log.d(TAG, "loadFromFile: LOADING " + filePath);
        lastReloadTime = now;
        lastReloadedPath = filePath;

        try {
            final ShoppingList list = ShoppingListUnmarshaller.unmarshal(filePath);
            ShoppingListMetadata existing = shoppingListsMetadata.getByFile(filePath);
            if (existing != null) {
                if (existing.isSyncing) {
                    Log.d(TAG, "loadFromFile: skipping due to isSyncing");
                    return;
                }
                if (existing.isDragging) {
                    Log.d(TAG, "loadFromFile: skipping due to isDragging");
                    return;
                }
                boolean hadListener = existing.updateListener != null;
                if (hadListener) {
                    existing.shoppingList.removeListener(existing.updateListener);
                }
                existing.isSyncing = true;
                try {
                    existing.shoppingList.setSuppressNotifications(true);
                    existing.shoppingList.clear();
                    existing.shoppingList.addAllWithoutNewId(list);
                    existing.isDirty = false;
                    rename(existing.shoppingList.getName(), list.getName());
                    existing.shoppingList.setSuppressNotifications(false);
                    existing.shoppingList.notifyListChanged(ShoppingList.Event.newOther());
                    shoppingListsMetadata.notifyListeners();
                } catch (Exception e) {
                    Log.e(TAG, "Error during loadFromFile update", e);
                } finally {
                    existing.isSyncing = false;
                    if (hadListener) {
                        existing.shoppingList.addListener(existing.updateListener);
                    }
                }
            } else {
                addShoppingList(list, file.getPath());
            }
        } catch (IOException | UnmarshallException e) {
            Log.v(getClass().getSimpleName(), "Ignoring file " + file);
        }
    }

    private void loadFromDirectory(String directory) {
        File d = new File(directory);
        File[] files = d.listFiles((dir, name) -> name.endsWith(FILE_ENDING));
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    loadFromFile(file);
                }
            }
        }
    }

    private ShoppingListMetadata addShoppingList(ShoppingList list, String filename) {
        final ShoppingListMetadata metadata = new ShoppingListMetadata(list, filename);
        final ShoppingList.ShoppingListListener updateListener = new ShoppingList.ShoppingListListener() {
            @Override
            public void onShoppingListUpdate(ShoppingList list, ShoppingList.Event e) {
                if (metadata.isSyncing) {
                    return;
                }
                int eventState = e.getState();
                if (eventState == ShoppingList.Event.ITEM_REMOVED) {
                    UUID removedUuid = e.getRemovedUuid();
                    boolean removedIsChecked = e.getRemovedIsChecked();
                    metadata.locallyDeletedUuids.add(removedUuid);
                    metadata.locallyModifiedChecked.put(removedUuid, removedIsChecked);
                    metadata.isDirty = true;
                    try {
                        relistAndWrite(metadata);
                    } catch (IOException | UnmarshallException ex) {
                        Log.e(TAG, "Failed to relistAndWrite after delete", ex);
                    }
                    notifyListChanged(metadata, ShoppingList.Event.newOther());
                } else if (eventState == ShoppingList.Event.ITEM_CHANGED) {
                    int index = e.getIndex();
                    android.util.Log.d("ShoppingListsManager", "onShoppingListUpdate: ITEM_CHANGED at index=" + index);
                    if (index >= 0 && index < list.size()) {
                        UUID uuid = list.getUuid(index);
                        android.util.Log.d("ShoppingListsManager", "onShoppingListUpdate: ITEM_CHANGED uuid=" + uuid + " isChecked=" + list.get(index).isChecked());
                        metadata.locallyModifiedChecked.put(uuid, list.get(index).isChecked());
                        metadata.locallyModifiedDescriptions.put(uuid, list.get(index).getDescription());
                        metadata.locallyModifiedQuantities.put(uuid, list.get(index).getQuantity());
                        metadata.locallyDeletedUuids.remove(uuid);
                    }
                } else if (eventState == ShoppingList.Event.ITEM_MOVED) {
                    metadata.isDirty = true;
                    int newIndex = e.getNewIndex();
                    UUID uuid = list.getUuid(newIndex);
                    metadata.locallyModifiedOrders.put(uuid, newIndex);
                } else if (eventState == ShoppingList.Event.ITEM_INSERTED) {
                    metadata.isDirty = true;
                    int newIndex = e.getIndex();
                    if (newIndex >= 0 && newIndex < list.size()) {
                        UUID newUuid = list.getUuid(newIndex);
                        metadata.locallyAddedUuids.add(newUuid);
                    }
                } else if (eventState == ShoppingList.Event.OTHER) {
                    return;
                }
                try {
                    relistAndWrite(metadata);
                } catch (IOException | UnmarshallException ex) {
                    Log.e(TAG, "Failed to relistAndWrite on update", ex);
                }
            }
        };
        metadata.updateListener = updateListener;
        list.addListener(updateListener);
        setupObserver(metadata);
        shoppingListsMetadata.add(metadata);
        return metadata;
    }

    private void relistAndWrite(ShoppingListMetadata metadata) throws IOException, UnmarshallException {
        android.util.Log.d("ShoppingListsManager", "relistAndWrite: START");
        metadata.isSyncing = true;
        android.util.Log.d("ShoppingListsManager", "relistAndWrite: current list size=" + metadata.shoppingList.size());
        boolean hadListener = metadata.updateListener != null;
        if (hadListener) {
            metadata.shoppingList.removeListener(metadata.updateListener);
        }

        metadata.shoppingList.setSuppressNotifications(true);

        Map<UUID, Boolean> localCheckedChanges = new HashMap<>(metadata.locallyModifiedChecked);
        Map<UUID, String> localDescChanges = new HashMap<>(metadata.locallyModifiedDescriptions);
        Map<UUID, String> localQtyChanges = new HashMap<>(metadata.locallyModifiedQuantities);
        Map<UUID, Integer> localOrderChanges = new HashMap<>(metadata.locallyModifiedOrders);
        Set<UUID> localDeletions = new HashSet<>(metadata.locallyDeletedUuids);
        Set<UUID> localAddedUuids = new HashSet<>(metadata.locallyAddedUuids);
        Map<UUID, ListItem> localItemByUuid = new HashMap<>();
        for (ListItem item : metadata.shoppingList) {
            UUID uuid = item.getUuid();
            if (uuid != null) {
                localItemByUuid.put(uuid, item);
            }
        }

        try {
            File file = new File(metadata.filename);
            if (file.exists()) {
                ShoppingList latestList = ShoppingListUnmarshaller.unmarshal(metadata.filename);
                Set<UUID> fileUuids = new HashSet<>();
                for (int i = 0; i < latestList.size(); i++) {
                    UUID uuid = latestList.getUuid(i);
                    if (uuid != null) {
                        fileUuids.add(uuid);
                    }
                }

                android.util.Log.d("ShoppingListsManager", "relistAndWrite: clearing list");
                metadata.shoppingList.clear();
                android.util.Log.d("ShoppingListsManager", "relistAndWrite: after clear, size=" + metadata.shoppingList.size());

                for (int i = 0; i < latestList.size(); i++) {
                    ListItem item = latestList.get(i);
                    UUID uuid = item.getUuid();
                    android.util.Log.d("ShoppingListsManager", "relistAndWrite: processing i=" + i + " uuid=" + uuid);

                    if (uuid == null || localDeletions.contains(uuid)) {
                        android.util.Log.d("ShoppingListsManager", "relistAndWrite: skipping " + uuid);
                        continue;
                    }

                    if (localCheckedChanges.containsKey(uuid)) {
                        item.setChecked(localCheckedChanges.get(uuid));
                    }
                    if (localDescChanges.containsKey(uuid)) {
                        item.setDescription(localDescChanges.get(uuid));
                    }
                    if (localQtyChanges.containsKey(uuid)) {
                        item.setQuantity(localQtyChanges.get(uuid));
                    }
                    android.util.Log.d("ShoppingListsManager", "relistAndWrite: adding item " + item.getDescription());
                    if (!localOrderChanges.containsKey(uuid)) {
                        metadata.shoppingList.addItemPreservingUuid(item);
                    }
                }

                for (UUID uuid : localAddedUuids) {
                    if (!fileUuids.contains(uuid)) {
                        ListItem item = localItemByUuid.get(uuid);
                        if (item != null) {
                            metadata.shoppingList.addItemPreservingUuid(item);
                        }
                    }
                }

                for (UUID uuid : localOrderChanges.keySet()) {
                    ListItem item = localItemByUuid.get(uuid);
                    if (item != null) {
                        metadata.shoppingList.add(localOrderChanges.get(uuid), item);
                    }
                }

                for (int i = 0; i < metadata.shoppingList.size(); i++) {
                    ListItem it = metadata.shoppingList.get(i);
                    android.util.Log.d("ShoppingListsManager", "relistAndWrite: item " + i + " uuid=" + it.getUuid() + " desc=" + it.getDescription());
                }

                metadata.isDirty = true;
                writeToFile(metadata);
                metadata.locallyAddedUuids.clear();
                metadata.locallyModifiedDescriptions.clear();
                metadata.locallyModifiedQuantities.clear();
                metadata.locallyModifiedChecked.clear();
                metadata.locallyModifiedOrders.clear();
            } else {
                metadata.isDirty = true;
                writeToFile(metadata);
                metadata.locallyAddedUuids.clear();
                metadata.locallyModifiedDescriptions.clear();
                metadata.locallyModifiedQuantities.clear();
                metadata.locallyModifiedChecked.clear();
                metadata.locallyModifiedOrders.clear();
            }
        } finally {
            metadata.shoppingList.setSuppressNotifications(false);
            if (hadListener) {
                metadata.shoppingList.addListener(metadata.updateListener);
            }
            metadata.isSyncing = false;
        }
    }

    private void writeToFile(ShoppingListMetadata metadata) throws IOException, UnmarshallException {
        ShoppingList copy = new ShoppingList(metadata.shoppingList.getName());
        copy.addAllWithoutNewId(metadata.shoppingList);
        int size = copy.size();
        try (OutputStream os = new FileOutputStream(metadata.filename)) {
            ShoppingListMarshaller.marshall(os, copy);
            metadata.isDirty = false;
        }
    }

    private void notifyListChanged(ShoppingListMetadata metadata, ShoppingList.Event event) {
        for (ListsChangeListener listener : listeners) {
            listener.onListsChanged();
        }
    }

    private void setupObserver(final ShoppingListMetadata metadata) {
        final Handler handler = new Handler(Looper.getMainLooper());
        FileObserver fileObserver = new FileObserver(metadata.filename) {
            @Override
            public void onEvent(int event, String path) {
                switch (event) {
                    case FileObserver.CLOSE_WRITE:
                        handler.post(() -> {
                            if (!metadata.isSyncing && !metadata.isDragging) {
                                metadata.isSyncing = true;
                                boolean hadListener = metadata.updateListener != null;
                                if (hadListener) {
                                    metadata.shoppingList.removeListener(metadata.updateListener);
                                }
                                metadata.shoppingList.setSuppressNotifications(true);
                                try {
                                    ShoppingList list = ShoppingListUnmarshaller.unmarshal(metadata.filename);
                                    metadata.shoppingList.clear();
                                    metadata.shoppingList.addAllWithoutNewId(list);
                                    metadata.isDirty = false;

                                    String oldName = metadata.shoppingList.getName();
                                    rename(oldName, list.getName());
                                } catch (IOException | UnmarshallException e) {
                                    Log.e(TAG, "FileObserver could not read file.", e);
                                } finally {
                                    metadata.shoppingList.setSuppressNotifications(false);
                                    if (hadListener) {
                                        metadata.shoppingList.addListener(metadata.updateListener);
                                    }
                                    metadata.isSyncing = false;
                                }
                            }
                        });
                        break;
                }
            }
        };
        fileObserver.startWatching();
        metadata.observer = fileObserver;
    }

    void setDragging(String listName, boolean dragging) {
        ShoppingListMetadata metadata = shoppingListsMetadata.getByName(listName);
        if (metadata != null) {
            metadata.isDragging = dragging;
        }
    }

    void addList(String name) throws ShoppingListException {
        if (hasList(name)) {
            throw new ShoppingListException("List already exists");
        }

        String filename = new File(this.directory, URLEncoder.encode(name) + FILE_ENDING).getPath();
        ShoppingListMetadata metadata = addShoppingList(new ShoppingList(name), filename);
        metadata.isDirty = true;
        try {
            writeToFile(metadata);
        } catch (IOException | UnmarshallException e) {
            Log.e(TAG, "Failed to write new list", e);
        }
    }

    boolean removeList(String name) {
        if (hasList(name)) {
            ShoppingListMetadata toRemove = shoppingListsMetadata.removeByName(name);
            new File(toRemove.filename).delete();
            Log.d(TAG, "removeList: deleted file " + toRemove.filename);
            return true;
        }
        return false;
    }

    @Nullable
    ShoppingList getList(String name) {
        ShoppingListMetadata metadata = shoppingListsMetadata.getByName(name);
        if (metadata != null) {
            return metadata.shoppingList;
        }
        return null;
    }

    Set<String> getListNames() {
        return shoppingListsMetadata.getListNames();
    }

    int size() {
        return shoppingListsMetadata.size();
    }

    boolean hasList(String name) {
        return shoppingListsMetadata.hasName(name);
    }

    String getListFilename(String name) {
        ShoppingListMetadata metadata = shoppingListsMetadata.getByName(name);
        if (metadata != null) {
            return metadata.filename;
        }
        return null;
    }

    void setListSortComparator(String name, Comparator<ListItem> comparator) {
        ShoppingListMetadata metadata = shoppingListsMetadata.getByName(name);
        if (metadata != null) {
            metadata.setSortComparator(comparator);
        }
    }

    void rename(String oldName, String newName) {
        if (!oldName.equals(newName)) {
            ShoppingListMetadata metadata = shoppingListsMetadata.removeByName(oldName);
            metadata.shoppingList.setName(newName);
            shoppingListsMetadata.add(metadata);
        }
    }

    private class ShoppingListMetadata {
        private final ShoppingList shoppingList;
        private final String filename;
        private boolean isDirty;
        private boolean isSyncing;
        private boolean isDragging;
        private boolean pendingWrite;
        private FileObserver observer;
        private ShoppingList.ShoppingListListener updateListener;
        private Map<UUID, Boolean> locallyModifiedChecked = new HashMap<>();
        private Map<UUID, String> locallyModifiedDescriptions = new HashMap<>();
        private Map<UUID, String> locallyModifiedQuantities = new HashMap<>();
        private Map<UUID, Integer> locallyModifiedOrders = new HashMap<>();
        private Set<UUID> locallyDeletedUuids = new HashSet<>();
        private Set<UUID> locallyAddedUuids = new HashSet<>();
        private Comparator<ListItem> sortComparator;

        private ShoppingListMetadata(ShoppingList shoppingList, String filename) {
            this.shoppingList = shoppingList;
            this.filename = filename;
            this.isDirty = false;
            this.isSyncing = false;
            this.isDragging = false;
            this.pendingWrite = false;
        }

        private void setSortComparator(Comparator<ListItem> comparator) {
            this.sortComparator = comparator;
        }
    }

    private class MetadataContainer {
        private Map<String, ShoppingListMetadata> byName = new HashMap<>();
        private Map<String, String> filenameResolver = new HashMap<>();

        private void add(ShoppingListMetadata metadata) {
            String name = metadata.shoppingList.getName();
            byName.put(name, metadata);
            filenameResolver.put(metadata.filename, name);
            notifyListeners();
        }

        private void clear() {
            filenameResolver.clear();
            byName.clear();
            notifyListeners();
        }

        private ShoppingListMetadata removeByName(String name) {
            ShoppingListMetadata toRemove = byName.remove(name);
            filenameResolver.remove(toRemove.filename);
            notifyListeners();
            return toRemove;
        }

        private ShoppingListMetadata removeByFile(String filename) {
            ShoppingListMetadata toRemove = byName.remove(filenameResolver.remove(filename));
            notifyListeners();
            return toRemove;
        }

        @Nullable
        private ShoppingListMetadata getByName(String name) {
            return byName.get(name);
        }

        private ShoppingListMetadata getByFile(String filename) {
            return getByName(filenameResolver.get(filename));
        }

        private boolean hasName(String name) {
            return byName.containsKey(name);
        }

        private Collection<ShoppingListMetadata> values() {
            return byName.values();
        }

        private Set<String> getListNames() {
            return byName.keySet();
        }

        private int size() {
            return byName.size();
        }

        private void notifyListeners() {
            for (ListsChangeListener listener : listeners) {
                listener.onListsChanged();
            }
        }
    }
}
