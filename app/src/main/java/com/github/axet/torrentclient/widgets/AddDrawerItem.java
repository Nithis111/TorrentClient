package com.github.axet.torrentclient.widgets;

import android.content.Context;
import android.support.annotation.LayoutRes;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.github.axet.torrentclient.R;
import com.mikepenz.fastadapter.utils.ViewHolderFactory;
import com.mikepenz.materialdrawer.model.BasePrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem;

public class AddDrawerItem extends BasePrimaryDrawerItem<AddDrawerItem, AddDrawerItem.ViewHolder> {
    public AddDrawerItem() {
    }

    @Override
    public int getType() {
        return R.id.search_update;
    }

    @Override
    @LayoutRes
    public int getLayoutRes() {
        return R.layout.search_update;
    }

    @Override
    public void bindView(ViewHolder viewHolder) {
        Context ctx = viewHolder.itemView.getContext();
        //bind the basic view parts
        bindViewHelper(viewHolder);

        //call the onPostBindView method to trigger post bind view actions (like the listener to modify the item if required)
        onPostBindView(this, viewHolder.itemView);
    }

    @Override
    public ViewHolderFactory<ViewHolder> getFactory() {
        return new ItemFactory();
    }

    public static class ItemFactory implements ViewHolderFactory<ViewHolder> {
        public ViewHolder create(View v) {
            return new ViewHolder(v);
        }
    }

    public static class ViewHolder extends PrimaryDrawerItem.ViewHolder {
        public ProgressBar progress;
        public ImageView refresh;
        public View update;

        public ViewHolder(View view) {
            super(view);

            update = view.findViewById(R.id.search_update_panel);
            progress = (ProgressBar) view.findViewById(R.id.search_update_progress);
            refresh = (ImageView) view.findViewById(R.id.search_update_refresh);
        }
    }
}
