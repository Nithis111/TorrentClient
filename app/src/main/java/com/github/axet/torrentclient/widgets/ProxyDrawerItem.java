package com.github.axet.torrentclient.widgets;

import android.content.Context;
import android.support.annotation.LayoutRes;
import android.view.View;
import android.widget.Switch;

import com.github.axet.torrentclient.R;
import com.mikepenz.fastadapter.utils.ViewHolderFactory;
import com.mikepenz.materialdrawer.model.BasePrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.BaseViewHolder;

public class ProxyDrawerItem extends BasePrimaryDrawerItem<ProxyDrawerItem, ProxyDrawerItem.ViewHolder> {

    public ProxyDrawerItem() {
    }

    @Override
    public int getType() {
        return R.id.search_proxy;
    }

    @Override
    @LayoutRes
    public int getLayoutRes() {
        return R.layout.search_proxy;
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
        public Switch w;

        public ViewHolder(View view) {
            super(view);
            w = (Switch) view.findViewById(R.id.proxy_switch);
        }
    }
}
