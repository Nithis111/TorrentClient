package com.github.axet.torrentclient.widgets;

import android.support.annotation.LayoutRes;
import android.view.View;
import android.view.ViewGroup;

import com.github.axet.torrentclient.R;

public class SectionPlusDrawerItem extends com.mikepenz.materialdrawer.model.SectionDrawerItem {
    public View v;

    public SectionPlusDrawerItem() {
    }

    public SectionPlusDrawerItem(View view) {
        this.v = view;
    }

    @Override
    public int getType() {
        return R.id.search_plus;
    }

    @Override
    @LayoutRes
    public int getLayoutRes() {
        return R.layout.search_plus;
    }
}
