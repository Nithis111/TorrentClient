package com.github.axet.torrentclient.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;

import com.github.axet.torrentclient.R;
import com.github.axet.torrentclient.app.MainApplication;

public class RatesDialogFragment extends DialogFragment {
    Handler handler = new Handler();
    ViewGroup v;
    EditText upload;
    EditText download;
    CheckBox speedlimit;

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        final Activity activity = getActivity();
    }

    @Override
    public void onStart() {
        super.onStart();

    }


    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        final AlertDialog d = new AlertDialog.Builder(getActivity())
                .setNegativeButton(getContext().getString(android.R.string.cancel),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                dialog.dismiss();
                            }
                        }
                )
                .setPositiveButton(getContext().getString(android.R.string.ok),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                dialog.dismiss();
                                final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(getContext());
                                SharedPreferences.Editor edit = shared.edit();
                                edit.putBoolean(MainApplication.PREFERENCE_SPEEDLIMIT, speedlimit.isChecked());
                                String u = upload.getText().toString();
                                Integer i = new Integer(-1);
                                if (u != null && !u.isEmpty())
                                    i = Integer.parseInt(u);
                                edit.putInt(MainApplication.PREFERENCE_UPLOAD, i);
                                u = download.getText().toString();
                                i = new Integer(-1);
                                if (u != null && !u.isEmpty())
                                    i = Integer.parseInt(u);
                                edit.putInt(MainApplication.PREFERENCE_DOWNLOAD, i);
                                edit.commit();
                            }
                        }
                )
                .setView(createView(LayoutInflater.from(getContext()), null, savedInstanceState))
                .create();

        return d;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return v;
    }

    View createView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        v = new FrameLayout(inflater.getContext());

        View vv = inflater.inflate(R.layout.rates, v);

        upload = (EditText) vv.findViewById(R.id.upload_rate);
        download = (EditText) vv.findViewById(R.id.download_rate);
        speedlimit = (CheckBox) vv.findViewById(R.id.speedlimit);

        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(getContext());
        int u = shared.getInt(MainApplication.PREFERENCE_UPLOAD, 50);
        if (u > 0) {
            upload.setText(u + "");
        } else {
            upload.setText("");
            upload.setHint(R.string.unlimited);
        }

        u = shared.getInt(MainApplication.PREFERENCE_DOWNLOAD, 100);
        if (u > 0) {
            download.setText(u + "");
        } else {
            download.setText("");
            download.setHint(R.string.unlimited);
        }

        upload.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                update();
            }
        });

        download.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                update();
            }
        });

        speedlimit.setChecked(shared.getBoolean(MainApplication.PREFERENCE_SPEEDLIMIT, false));

        update();

        return v;
    }

    void update() {
        if (!upload.getText().toString().isEmpty() || !download.getText().toString().isEmpty()) {
            speedlimit.setEnabled(true);
        } else {
            speedlimit.setEnabled(false);
            speedlimit.setChecked(false);
        }
    }
}
