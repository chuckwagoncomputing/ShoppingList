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

package com.woefe.shoppinglist.dialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.woefe.shoppinglist.R;

public class TextInputDialog extends DialogFragment {
    private static final String TAG = DialogFragment.class.getSimpleName();
    private static final String KEY_MESSAGE = "MESSAGE";
    private static final String KEY_INPUT = "INPUT";
    private static final String KEY_HINT = "INPUT";
    private TextInputDialogListener listener;
    private String message;
    private String hint;
    private int action;
    private TextInputEditText inputField;


    public interface TextInputDialogListener {
        void onInputComplete(String input, int action);
    }

    @Override
    public void onAttach(Context ctx) {
        super.onAttach(ctx);
        Fragment owner = getParentFragment();
        if (ctx instanceof TextInputDialogListener) {
            listener = (TextInputDialogListener) ctx;
        } else if (owner instanceof TextInputDialogListener) {
            listener = (TextInputDialogListener) owner;
        } else {
            Log.e(TAG, "Dialog not attached");
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());

        View dialogRoot = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_text_input, null);
        TextView label = dialogRoot.findViewById(R.id.dialog_label);
        TextInputLayout textInputLayout = dialogRoot.findViewById(R.id.text_input_layout);
        label.setText(message);
        textInputLayout.setHint(hint);

        inputField = dialogRoot.findViewById(R.id.dialog_text_field);
        inputField.requestFocus();

        builder.setView(dialogRoot)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    String input = inputField.getText().toString();
                    if (onValidateInput(input)) {
                        listener.onInputComplete(input, action);
                    }
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> dismiss());

        return builder.create();
    }

    public boolean onValidateInput(String input) {
        return true;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_MESSAGE, message);
        outState.putString(KEY_HINT, hint);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        inputField = null;
    }

    public static class Builder {
        private final FragmentActivity activity;
        private TextInputDialog dialog;
        private FragmentManager fragmentManager;

        public Builder(FragmentActivity activity, Class<? extends TextInputDialog> clazz) {
            this.activity = activity;
            try {
                this.dialog = clazz.newInstance();
            } catch (java.lang.InstantiationException | IllegalAccessException e) {
                throw new IllegalStateException("Cannot start dialog" + clazz.getSimpleName());
            }
        }

        public Builder setMessage(String message) {
            dialog.message = message;
            return this;
        }

        public Builder setMessage(@StringRes int messageID) {
            return setMessage(activity.getString(messageID));
        }

        public Builder setHint(String hint) {
            dialog.hint = hint;
            return this;
        }

        public Builder setHint(@StringRes int hintID) {
            return setHint(activity.getString(hintID));
        }

        public Builder setAction(int action) {
            dialog.action = action;
            return this;
        }

        public Builder setFragmentManager(FragmentManager manager) {
            fragmentManager = manager;
            return this;
        }

        public Builder setTargetFragment(Fragment fragment, int requestCode) {
            dialog.setTargetFragment(fragment, requestCode);
            return this;
        }

        public void show() {
            if (fragmentManager == null) {
                fragmentManager = activity.getSupportFragmentManager();
            }
            dialog.show(fragmentManager, TAG);
        }
    }
}
