package com.github.axet.torrentclient.widgets;

import android.support.annotation.LayoutRes;
import android.view.View;

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
        return R.id.drawer_search_plus;
    }

    @Override
    @LayoutRes
    public int getLayoutRes() {
        return R.layout.drawer_search_plus;
    }
}
