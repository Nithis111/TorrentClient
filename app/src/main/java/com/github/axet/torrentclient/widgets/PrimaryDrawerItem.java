package com.github.axet.torrentclient.widgets;

import android.view.View;
import android.view.ViewGroup;

public class PrimaryDrawerItem extends com.mikepenz.materialdrawer.model.PrimaryDrawerItem {
    public View v;

    public PrimaryDrawerItem() {
    }

    public PrimaryDrawerItem(View view) {
        this.v = view;
    }

    @Override
    public void bindView(ViewHolder viewHolder) {
        super.bindView(viewHolder);
        if (v != null) {
            ViewGroup badgeContainer = (ViewGroup) viewHolder.itemView.findViewById(com.mikepenz.materialdrawer.R.id.material_drawer_badge_container);
            badgeContainer.removeAllViews();
            ViewGroup p = (ViewGroup) v.getParent();
            if (p != null)
                p.removeView(v);
            badgeContainer.addView(v);
            badgeContainer.setVisibility(View.VISIBLE);
        }
    }
}
