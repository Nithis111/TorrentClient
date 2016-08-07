package com.github.axet.torrentclient.widgets;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.preference.DialogPreference;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.github.axet.androidlibrary.widgets.OpenFileDialog;
import com.github.axet.torrentclient.R;

import java.util.ArrayList;
import java.util.Arrays;

public class MultiEditListPreference extends DialogPreference {
    public String mText;
    public OpenFileDialog f;

    ArrayList<String> trackers = new ArrayList<>();

    Files files = new Files();

    class Files extends BaseAdapter {
        @Override
        public int getCount() {
            return trackers.size();
        }

        @Override
        public String getItem(int i) {
            return trackers.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(final int i, View view, ViewGroup viewGroup) {
            LayoutInflater inflater = LayoutInflater.from(getContext());

            if (view == null) {
                view = inflater.inflate(R.layout.multiedit_content_item, viewGroup, false);
            }

            final String f = getItem(i);

            TextView url = (TextView) view.findViewById(R.id.torrent_trackers_url);

            View trash = view.findViewById(R.id.torrent_trackers_trash);
            trash.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getContext());
                    builder.setTitle(R.string.delete_tracker);
                    builder.setMessage(f + "\n\n" + getContext().getString(R.string.are_you_sure));
                    builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                            trackers.remove(i);
                            notifyDataSetChanged();
                        }
                    });
                    builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });
                    builder.show();
                }
            });

            url.setText(f);

            return view;
        }
    }

    public MultiEditListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MultiEditListPreference(Context context) {
        this(context, null);
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
    }

    public String getDefault() {
        return Environment.getExternalStorageDirectory().getPath();
    }

    @Override
    protected void showDialog(Bundle state) {
        LayoutInflater inflater = LayoutInflater.from(getContext());

        View view = inflater.inflate(R.layout.multiedit_content, null);

        trackers.clear();
        trackers.addAll(Arrays.asList(mText.trim().split("\n")));

        ListView list = (ListView) view.findViewById(R.id.list);

        list.setAdapter(files);

        final AlertDialog.Builder builder = new AlertDialog.Builder(getContext());

        builder.setView(view);
        builder.setTitle(getTitle());
        builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                String v = TextUtils.join("\n", trackers.toArray()).trim();
                if (callChangeListener(v)) {
                    setText(v);
                }
            }
        });
        builder.setPositiveButton(R.string.close, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.setNeutralButton(getContext().getString(R.string.add), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();

        Button b = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final OpenFileDialog.EditTextDialog d = new OpenFileDialog.EditTextDialog(getContext());
                d.setTitle(R.string.add_tracker);
                d.setPositiveButton(new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        trackers.add(d.getText());
                    }
                });
                d.show();
            }
        });
    }

    void setText(String s) {
        mText = s;

        persistString(s);
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getString(index);
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        setText(restoreValue ? getPersistedString(mText) : (String) defaultValue);
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        super.onRestoreInstanceState(state);
    }
}
