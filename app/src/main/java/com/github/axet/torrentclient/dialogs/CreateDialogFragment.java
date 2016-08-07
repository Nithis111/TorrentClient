package com.github.axet.torrentclient.dialogs;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;

import com.github.axet.torrentclient.R;

public class CreateDialogFragment extends AddDialogFragment {

    @Override
    public View createView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = super.createView(inflater, container, savedInstanceState);

        browse.setVisibility(View.GONE);

//        ImageButton check = (ImageButton) v.findViewById(R.id.torrent_add_check);
//        check.setVisibility(View.GONE);

        return v;
    }

    void builder(AlertDialog.Builder b) {
        b.setTitle(getContext().getString(R.string.create_torrent));
    }

    @Override
    public void updateView(View view) {
        final CheckBox check = (CheckBox) view.findViewById(R.id.torrent_files_check);
        check.setEnabled(false);
    }

    @Override
    public void update() {
        super.update();

        if (v == null)
            return;

        renameButton.setVisibility(View.GONE);
        toolbar.setVisibility(View.GONE);
    }
}
