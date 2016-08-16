package com.github.axet.torrentclient.app;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.axet.androidlibrary.widgets.OpenFileDialog;
import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.androidlibrary.widgets.UnreadCountDrawable;
import com.github.axet.torrentclient.R;
import com.github.axet.torrentclient.activities.MainActivity;
import com.github.axet.torrentclient.navigators.Search;
import com.github.axet.torrentclient.navigators.Torrents;
import com.github.axet.torrentclient.widgets.AddDrawerItem;
import com.github.axet.torrentclient.widgets.ProxyDrawerItem;
import com.github.axet.torrentclient.widgets.SearchDrawerItem;
import com.github.axet.torrentclient.widgets.SectionPlusDrawerItem;
import com.mikepenz.fastadapter.adapters.ItemAdapter;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.SectionDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import go.libtorrent.Libtorrent;

// Reduce MainActivity size, move code related to Drawer here
public class Drawer implements com.mikepenz.materialdrawer.Drawer.OnDrawerItemClickListener, UnreadCountDrawable.UnreadCount {
    public static final String TAG = Drawer.class.getSimpleName();

    static final long INFO_MANUAL_REFRESH = 5 * 1000;
    static final long INFO_AUTO_REFRESH = 5 * 60 * 1000;
    static final long ENGINES_AUTO_REFRESH = 24 * 60 * 60 * 1000;

    Context context;
    MainActivity main;
    Handler handler = new Handler();

    View navigationHeader;
    com.mikepenz.materialdrawer.Drawer drawer;
    UnreadCountDrawable unread;

    Thread update;
    Thread updateOne;
    int updateOneIndex;

    Thread infoThread;
    List<String> infoOld;
    boolean infoPort;
    long infoTime; // last time checked

    public Drawer(final MainActivity main, final Toolbar toolbar) {
        this.context = main;
        this.main = main;

        LayoutInflater inflater = LayoutInflater.from(context);

        navigationHeader = inflater.inflate(R.layout.nav_header_main, null, false);

        drawer = new DrawerBuilder()
                .withActivity(main)
                .withToolbar(toolbar)
                .withHeader(navigationHeader)
                .withActionBarDrawerToggle(true)
                .withActionBarDrawerToggleAnimated(true)
                .withOnDrawerListener(new com.mikepenz.materialdrawer.Drawer.OnDrawerListener() {
                    @Override
                    public void onDrawerSlide(View drawerView, float slideOffset) {
                        View view = main.getCurrentFocus();
                        if (view != null) {
                            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                        }
                    }

                    @Override
                    public void onDrawerOpened(View drawerView) {
                        long time = System.currentTimeMillis();
                        EnginesManager engies = main.getEngines();
                        long t = engies.getTime();
                        if (t + ENGINES_AUTO_REFRESH < time) {
                            refreshEngines(true);
                        }
                    }

                    @Override
                    public void onDrawerClosed(View drawerView) {
                    }
                })
                .withOnDrawerItemClickListener(this)
                .build();

        Drawable navigationIcon = toolbar.getNavigationIcon();
        unread = new UnreadCountDrawable(context, navigationIcon, Drawer.this);
        unread.setPadding(ThemeUtils.dp2px(main, 15));
        toolbar.setNavigationIcon(unread);

        TextView ver = (TextView) navigationHeader.findViewById(R.id.nav_version);
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            String version = "v" + pInfo.versionName;
            ver.setText(version);
        } catch (PackageManager.NameNotFoundException e) {
            ver.setVisibility(View.GONE);
        }
    }

    public void setCheckedItem(long id) {
        drawer.setSelection(id, false);
    }

    public boolean isDrawerOpen() {
        return drawer.isDrawerOpen();
    }

    public void openDrawer() {
        drawer.openDrawer();
    }

    public void closeDrawer() {
        drawer.closeDrawer();
    }

    public void updateUnread() {
        unread.update();
    }

    long counter = 1;

    long getEngineId(Search s) {
        ArrayList<IDrawerItem> list = new ArrayList<IDrawerItem>(drawer.getDrawerItems());
        for (int i = 0; i < list.size(); i++) {
            IDrawerItem item = list.get(i);
            Search search = (Search) item.getTag();
            if (search == s) {
                return item.getIdentifier();
            }
        }
        return -1;
    }

    long getEngineId(List<IDrawerItem> ll, Search s) {
        ArrayList<IDrawerItem> list = new ArrayList<>(drawer.getDrawerItems());
        long id = getEngineId(s);
        if (id != -1)
            return id;
        list.addAll(ll);
        for (int i = 0; i < list.size(); i++) {
            IDrawerItem item = list.get(i);
            if (item.getIdentifier() == counter) {
                counter++;
                // save to set < 0x00ffffff. check View.generateViewId()
                if (counter >= 0x00ffffff)
                    counter = 1;
                i = -1; // restart search
            }
        }
        return counter;
    }

    public void updateManager() {
        List<IDrawerItem> list = new ArrayList<>();

        final Torrents torrents = main.getTorrents();
        if (torrents != null) {
            PrimaryDrawerItem item = new PrimaryDrawerItem();
            item.withIdentifier(R.id.nav_torrents);
            item.withName(R.string.torrents);
            item.withIcon(new UnreadCountDrawable(context, R.drawable.ic_storage_black_24dp, torrents));
            item.withIconTintingEnabled(true);
            item.withSelectable(true);
            item.withSetSelected(main.active(torrents));
            list.add(item);
        }

        KeyguardManager myKM = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        final boolean locked = myKM.inKeyguardRestrictedInputMode();

        final EnginesManager engines = main.getEngines();

        for (int i = 0; i < engines.getCount(); i++) {
            final Search search = engines.get(i);
            final SearchEngine engine = search.getEngine();

            final int fi = i;
            SearchDrawerItem item = new SearchDrawerItem() {
                @Override
                public void bindView(ViewHolder viewHolder) {
                    super.bindView(viewHolder);

                    viewHolder.release.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (updateOne != null) {
                                updateOne.interrupt();
                            }
                            updateOneIndex = fi;
                            updateOne = new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    engines.update(fi);
                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            updateOne = null;
                                            updateManager();
                                            Search search = engines.get(fi);
                                            SearchEngine engine = search.getEngine();
                                            Toast.makeText(context, engine.getName() + context.getString(R.string.engine_updated) + engine.getVersion(), Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                }
                            }, "Update One");
                            updateOne.start();
                        }
                    });

                    viewHolder.panel.setVisibility(View.GONE);
                    if (updateOne != null && updateOneIndex == fi) {
                        viewHolder.progress.setVisibility(View.VISIBLE);
                        viewHolder.panel.setVisibility(View.VISIBLE);
                    } else {
                        viewHolder.progress.setVisibility(View.INVISIBLE);
                    }

                    if (engines.getUpdate(fi)) {
                        viewHolder.release.setVisibility(View.VISIBLE);
                        viewHolder.panel.setVisibility(View.VISIBLE);
                    } else {
                        viewHolder.release.setVisibility(View.INVISIBLE);
                    }

                    viewHolder.trash.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(context);
                            builder.setTitle(context.getString(R.string.delete_search));

                            String name = engine.getName();

                            builder.setMessage(name + "\n\n" + context.getString(R.string.are_you_sure));
                            builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.cancel();

                                    engines.remove(search);
                                    search.close();

                                    engines.save();
                                    updateManager();
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
                    if (locked) {
                        viewHolder.trash.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                            }
                        });
                        viewHolder.trash.setColorFilter(Color.GRAY);
                    } else {
                        viewHolder.trash.setColorFilter(ThemeUtils.getThemeColor(context, R.attr.colorAccent));
                    }
                }
            };
            item.withIdentifier(getEngineId(list, search));
            item.withTag(search);
            item.withName(engine.getName());
            item.withIconTintingEnabled(true);
            item.withIcon(new UnreadCountDrawable(context, R.drawable.share, search));
            item.withSelectable(true);
            item.withSetSelected(main.active(search));
            list.add(item);
        }

        updateProxies(list);

        list.add(new SectionDrawerItem()
                .withIdentifier(R.string.action_settings)
                .withName(R.string.action_settings));

        AddDrawerItem item = new AddDrawerItem() {
            @Override
            public void bindView(ViewHolder viewHolder) {
                super.bindView(viewHolder);

                viewHolder.update.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (Drawer.this.update != null) {
                            Drawer.this.update.interrupt();
                            Drawer.this.update = null;
                            updateManager();
                        } else {
                            refreshEngines(false);
                        }
                    }
                });
                if (Drawer.this.update != null) {
                    viewHolder.progress.setVisibility(View.VISIBLE);
                    viewHolder.refresh.setVisibility(View.INVISIBLE);
                } else {
                    viewHolder.progress.setVisibility(View.INVISIBLE);
                    viewHolder.refresh.setVisibility(View.VISIBLE);
                }
                if (locked) {
                    viewHolder.update.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                        }
                    });
                    viewHolder.refresh.setColorFilter(Color.GRAY);
                    viewHolder.update.setEnabled(false);
                } else {
                    viewHolder.refresh.setColorFilter(ThemeUtils.getThemeColor(context, R.attr.colorAccent));
                    viewHolder.update.setEnabled(true);
                }
            }
        };
        item.withIdentifier(R.id.nav_add)
                .withName(R.string.add_search_engine)
                .withIcon(R.drawable.ic_add_black_24dp)
                .withIconTintingEnabled(true)
                .withSelectable(false);
        list.add(item);

        ItemAdapter<IDrawerItem> ad = drawer.getItemAdapter();
        for (int i = ad.getAdapterItemCount() - 1; i >= 0; i--) {
            boolean delete = true;
            for (int k = 0; k < list.size(); k++) {
                if (list.get(k).getIdentifier() == ad.getAdapterItem(i).getIdentifier()) {
                    delete = false;
                    ad.set(ad.getGlobalPosition(i), list.get(k));
                }
            }
            if (delete) {
                ad.remove(ad.getGlobalPosition(i));
            }
        }
        for (int k = 0; k < list.size(); k++) {
            boolean add = true;
            for (int i = 0; i < ad.getAdapterItemCount(); i++) {
                if (list.get(k).getIdentifier() == ad.getAdapterItem(i).getIdentifier()) {
                    add = false;
                }
            }
            if (add) {
                ad.add(ad.getGlobalPosition(k), list.get(k));
            }
        }
    }

    public void refreshEngines(final boolean auto) {
        if (update != null) {
            if (auto)
                return;
            else
                update.interrupt();
        }
        final EnginesManager engines = main.getEngines();
        update = new Thread(new Runnable() {
            @Override
            public void run() {
                boolean a = auto;

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        updateManager();
                    }
                });
                try {
                    engines.refresh();
                } catch (final RuntimeException e) {
                    Log.e(TAG, "Update Engine", e);
                    // only report errors for current active update thread
                    if (update == Thread.currentThread()) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                Throwable t = e;
                                while (t.getCause() != null)
                                    t = t.getCause();
                                String msg = "'" + e.getMessage() + "' ";
                                if (t instanceof FileNotFoundException)
                                    msg += "not found ";
                                msg += t.getMessage();
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                    a = true; // hide update toast on error
                }
                final boolean b = a;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        update = null;
                        if (!b) {
                            if (!engines.updates())
                                Toast.makeText(context, R.string.no_updates, Toast.LENGTH_SHORT).show();
                        }
                        updateManager();
                    }
                });
            }
        }, "Engines Update");
        update.start();
    }

    void updateProxies(List<IDrawerItem> list) {
        LayoutInflater inflater = LayoutInflater.from(context);

        View up = inflater.inflate(R.layout.search_plus, null);
        list.add(new SectionPlusDrawerItem(up)
                .withIdentifier(R.string.web_proxy_s)
                .withName(R.string.web_proxy_s));

        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        final String proxy = shared.getString(MainApplication.PREFERENCE_PROXY, "");

        ProxyDrawerItem item = new ProxyDrawerItem() {
            @Override
            public void bindView(final ViewHolder viewHolder) {
                super.bindView(viewHolder);

                viewHolder.w.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        SharedPreferences.Editor edit = shared.edit();
                        if (viewHolder.w.isChecked()) {
                            edit.putString(MainApplication.PREFERENCE_PROXY, GoogleProxy.NAME);
                        } else {
                            edit.putString(MainApplication.PREFERENCE_PROXY, "");
                        }
                        edit.commit();
                    }
                });
                viewHolder.w.setChecked(proxy.equals(GoogleProxy.NAME));
            }
        };
        item.withIdentifier(R.drawable.ic_vpn_key_black_24dp);
        item.withName("Google Data Saver");
        item.withIcon(R.drawable.ic_vpn_key_black_24dp);
        item.withIconTintingEnabled(true);
        item.withSelectable(false);
        list.add(item);
    }

    public void openDrawer(Search search) {
        openDrawer();
        EnginesManager engies = main.getEngines();
        for (int i = 0; i < engies.getCount(); i++) {
            if (engies.get(i) == search) {
                setCheckedItem(getEngineId(search));
                main.show(search);
                return;
            }
        }
    }

    public void updateHeader() {
        ArrayList<String> info = new ArrayList<>();
        long c = Libtorrent.PortCount();
        for (long i = 0; i < c; i++) {
            info.add(Libtorrent.Port(i));
        }

        String str = "";
        for (String ip : info) {
            str += ip + "\n";
        }
        str = str.trim();


        TextView textView = (TextView) navigationHeader.findViewById(R.id.torrent_ip);
        textView.setText(str);

        View portButton = navigationHeader.findViewById(R.id.torrent_port_button);
        ImageView portIcon = (ImageView) navigationHeader.findViewById(R.id.torrent_port_icon);
        TextView port = (TextView) navigationHeader.findViewById(R.id.torrent_port_text);

        portButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                long time = System.currentTimeMillis();
                if (infoTime + INFO_MANUAL_REFRESH < time) {
                    infoTime = time;
                    infoOld = null;
                }
            }
        });

        long time = System.currentTimeMillis();
        if (infoTime + INFO_AUTO_REFRESH < time) {
            infoTime = time;
            infoOld = null;
        }

        if (infoOld == null || !Arrays.equals(info.toArray(), infoOld.toArray())) {
            if (drawer.isDrawerOpen()) { // only probe port when drawer is open
                if (infoThread != null) {
                    return;
                }
                infoOld = info;
                infoThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final boolean b = Libtorrent.PortCheck();
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                infoPort = b;
                                infoThread = null;
                            }
                        });
                    }
                });
                infoThread.start();
                infoPort = false;
                portIcon.setImageResource(R.drawable.port_no);
                port.setText(R.string.port_checking);
            } else {
                portIcon.setImageResource(R.drawable.port_no);
                port.setText(R.string.port_closed);
            }
        } else {
            if (infoPort) {
                portIcon.setImageResource(R.drawable.port_ok);
                port.setText(R.string.port_open);
            } else {
                portIcon.setImageResource(R.drawable.port_no);
                port.setText(R.string.port_closed);
            }
        }
    }


    @Override
    public boolean onItemClick(View view, int position, IDrawerItem drawerItem) {
        long id = drawerItem.getIdentifier();

        final EnginesManager engies = main.getEngines();

        // here only two types of adapters, so setup empty view manually here.

        if (id == R.id.nav_torrents) {
            main.show(main.getTorrents());
            closeDrawer();
            return true;
        }

        if (drawerItem.getTag() != null) {
            Search search = (Search) drawerItem.getTag();
            main.show(search);
            closeDrawer();
            return true;
        }

        if (id == R.id.nav_add) {
            final OpenFileDialog f = new OpenFileDialog(context);

            String path = "";

            final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);

            if (path == null || path.isEmpty()) {
                path = shared.getString(MainApplication.PREFERENCE_LAST_PATH, Environment.getExternalStorageDirectory().getPath());
            }

            f.setCurrentPath(new File(path));
            f.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    File p = f.getCurrentPath();
                    shared.edit().putString(MainApplication.PREFERENCE_LAST_PATH, p.getParent()).commit();
                    Search search = null;
                    try {
                        search = engies.add(p);
                    } catch (RuntimeException e) {
                        main.Error(e);
                        return;
                    }
                    engies.save();
                    updateManager();
                    openDrawer(search);
                }
            });
            f.show();
            // prevent close drawer
            return true;
        }
        return true;
    }

    @Override
    public int getUnreadCount() {
        int count = 0;
        Torrents torrents = main.getTorrents();
        if (torrents != null)
            count += torrents.getUnreadCount();
        EnginesManager engies = main.getEngines();
        if (engies != null) {
            for (int i = 0; i < engies.getCount(); i++) {
                count += engies.get(i).getUnreadCount();
            }
        }
        return count;
    }
}
