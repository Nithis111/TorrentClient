package com.github.axet.torrentclient.widgets;

import android.content.Context;
import android.support.annotation.LayoutRes;
import android.view.View;
import android.widget.ImageView;

import com.github.axet.torrentclient.R;
import com.mikepenz.fastadapter.utils.ViewHolderFactory;
import com.mikepenz.materialdrawer.model.BasePrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.BaseViewHolder;

public class SearchDrawerItem extends BasePrimaryDrawerItem<SearchDrawerItem, SearchDrawerItem.ViewHolder> {
    public View v;

    public SearchDrawerItem() {
    }

    public SearchDrawerItem(View view) {
        this.v = view;
    }

    @Override
    public int getType() {
        return R.id.search_engine;
    }

    @Override
    @LayoutRes
    public int getLayoutRes() {
        return R.layout.search_engine;
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

    public static class ViewHolder extends BaseViewHolder {
        public View panel;
        public View release;
        public View progress;
        public ImageView trash;

        public ViewHolder(View view) {
            super(view);

            panel = view.findViewById(R.id.search_engine_panel);
            release = view.findViewById(R.id.search_engine_new);
            progress = view.findViewById(R.id.search_engine_progress);
            trash = (ImageView) view.findViewById(R.id.search_engine_trash);
        }
    }
}
