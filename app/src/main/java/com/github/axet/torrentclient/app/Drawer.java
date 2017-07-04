package com.github.axet.torrentclient.app;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
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

import com.github.axet.androidlibrary.app.AlarmManager;
import com.github.axet.androidlibrary.net.HttpClient;
import com.github.axet.androidlibrary.widgets.OpenFileDialog;
import com.github.axet.androidlibrary.widgets.StoragePathPreferenceCompat;
import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.androidlibrary.widgets.UnreadCountDrawable;
import com.github.axet.torrentclient.R;
import com.github.axet.torrentclient.activities.MainActivity;
import com.github.axet.torrentclient.navigators.Search;
import com.github.axet.torrentclient.navigators.Torrents;
import com.github.axet.torrentclient.net.GoogleProxy;
import com.github.axet.torrentclient.widgets.AddDrawerItem;
import com.github.axet.torrentclient.widgets.ProgressDrawerItem;
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

import libtorrent.Libtorrent;

// Reduce MainActivity size, move code related to Drawer here
public class Drawer implements com.mikepenz.materialdrawer.Drawer.OnDrawerItemClickListener, UnreadCountDrawable.UnreadCount {
    public static final String TAG = Drawer.class.getSimpleName();

    public static final String[] PERMISSIONS = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};

    public static String VERSION_CHECK = "https://gitlab.com/axet/android-torrent-client/tags";
    public static int[] DEVELOPERS = new int[]{0xc38af5bf, 0x3feda1d1}; // 0xc38af5bf release, 0x3feda1d1 debug

    static final long INFO_MANUAL_REFRESH = 5 * AlarmManager.SEC1; // prevent refresh if button hit often then 5 seconds
    static final long INFO_AUTO_REFRESH = 5 * AlarmManager.MIN1; // ping external port on drawer open not often then 5 minutes
    static final long ENGINES_AUTO_REFRESH = 12 * AlarmManager.HOUR1; // auto refresh engines every 12 hours

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

    List<ProxyDrawerItem.ViewHolder> viewList = new ArrayList<>();

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
        if (torrents == null) {
            ProgressDrawerItem progress = new ProgressDrawerItem();
            progress.withIdentifier(R.id.progress);
            progress.withSelectable(true);
            progress.withSetSelected(false);
            list.add(progress);
            update(list);
            return;
        }

        PrimaryDrawerItem tt = new PrimaryDrawerItem();
        tt.withIdentifier(R.id.nav_torrents);
        tt.withName(R.string.torrents);
        tt.withIcon(new UnreadCountDrawable(context, R.drawable.ic_storage_black_24dp, torrents));
        tt.withIconTintingEnabled(true);
        tt.withSelectable(true);
        tt.withSetSelected(main.active(torrents));
        list.add(tt);

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

                                    main.openTorrents();
                                    engines.remove(search);

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
            item.withIcon(new UnreadCountDrawable(context, R.drawable.ic_share_black_24dp, search));
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

        update(list);
    }

    void update(List<IDrawerItem> list) {
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
        if (auto && main.getTorrents() == null)
            return;
        if (update != null) {
            if (auto)
                return;
        }
        final EnginesManager engines = main.getEngines();
        final Thread old = update;
        update = new Thread(new Runnable() {
            @Override
            public void run() {
                if (old != null) {
                    old.interrupt();
                    try {
                        old.join();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                boolean a = auto;

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        updateManager();
                    }
                });
                try {
                    versionCheck();
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

    void versionCheck() {
        PackageManager pm = context.getPackageManager();
        String installer = pm.getInstallerPackageName(context.getPackageName());
        boolean apk = installer == null || installer.startsWith("com.android.packageinstaller"); // apk installed
        boolean store = installer != null && installer.startsWith("com.android.vending"); // google play or amazon store

        if (store) // no version check for play store
            return;

        try {
            boolean developer = false;
            Signature[] ss = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES).signatures;
            for (Signature s : ss) {
                int hash = s.hashCode();
                for (int d : DEVELOPERS) {
                    if (d == hash)
                        developer = true;
                }
            }
            if (apk && !developer) // no version check for releases signed by other side
                return;
        } catch (PackageManager.NameNotFoundException e) {
        }

        final View b = navigationHeader.findViewById(R.id.search_engine_new);
        String url = VERSION_CHECK;
        if (url != null && url.isEmpty()) {
            HttpClient client = new HttpClient();
            HttpClient.DownloadResponse w = client.getResponse(null, url);
            w.download();
            if (w.getError() == null) { // throw new RuntimeException(w.getError() + ": " + url);
                String html = w.getHtml();
                try {
                    PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                    String v = pInfo.versionName;
                    final String version = Search.matcher(html, ".ref-name:regex(.*-(.*))", "");
                    if (!v.equals(version)) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                b.setVisibility(View.VISIBLE);
                                b.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        Toast.makeText(context, context.getString(R.string.new_version) + " v" + version, Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        });
                        return;
                    }
                } catch (PackageManager.NameNotFoundException ignore) {
                }
            }
        }
        b.setVisibility(View.GONE);
    }

    void updateProxies(List<IDrawerItem> list) {
        LayoutInflater inflater = LayoutInflater.from(context);

        View up = inflater.inflate(R.layout.drawer_search_plus, null);
        list.add(new SectionPlusDrawerItem(up)
                .withIdentifier(R.string.web_proxy_s)
                .withName(R.string.web_proxy_s));

        ProxyDrawerItem google = createProxy(GoogleProxy.NAME, R.string.google_proxy);
        list.add(google);

        //ProxyDrawerItem tor = createProxy(TorProxy.NAME, R.string.tor_proxy);
        //list.add(tor);
    }

    ProxyDrawerItem createProxy(final String tag, final int res) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        final String proxy = shared.getString(MainApplication.PREFERENCE_PROXY, "");
        ProxyDrawerItem google = new ProxyDrawerItem() {
            @Override
            public void bindView(final ViewHolder viewHolder) {
                super.bindView(viewHolder);
                viewList.add(viewHolder);
                viewHolder.w.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        SharedPreferences.Editor edit = shared.edit();
                        if (viewHolder.w.isChecked()) {
                            edit.putString(MainApplication.PREFERENCE_PROXY, tag);
                        } else {
                            edit.putString(MainApplication.PREFERENCE_PROXY, "");
                        }
                        edit.commit();
                        for (ViewHolder h : viewList) {
                            if (h == viewHolder)
                                continue;
                            h.w.setChecked(false);
                        }
                    }
                });
                viewHolder.w.setChecked(proxy.equals(tag));
            }
        };
        google.withIdentifier(res);
        google.withName(res);
        google.withIcon(R.drawable.ic_vpn_key_black_24dp);
        google.withIconTintingEnabled(true);
        google.withSelectable(false);
        return google;
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
        long c = Libtorrent.portCount();
        for (long i = 0; i < c; i++) {
            info.add(Libtorrent.port(i));
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
                        final boolean b = Libtorrent.portCheck();
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                infoPort = b;
                                infoThread = null;
                            }
                        });
                    }
                }, "Port Check");
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
            main.openTorrents();
            closeDrawer();
            return true;
        }

        if (drawerItem.getTag() != null) {
            Search search = (Search) drawerItem.getTag();
            main.show(search);
            engies.save();
            closeDrawer();
            return true;
        }

        if (id == R.id.nav_add) {
            Uri u = getApp().getStorage().getStoragePath();
            if (Build.VERSION.SDK_INT >= 21 && StoragePathPreferenceCompat.showStorageAccessFramework(main, u.toString(), MainActivity.PERMISSIONS)) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                intent.setType("*/*");
                main.startActivityForResult(intent, MainActivity.RESULT_ADD_ENGINE_URL);
            } else if (Storage.permitted(main, PERMISSIONS, MainActivity.RESULT_ADD_ENGINE)) {
                openNavFiles();
            }
            return true; // prevent close drawer
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

    @TargetApi(21)
    public void onActivityResult(int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK)
            return;
        Uri uri = data.getData();
        final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        ContentResolver resolver = context.getContentResolver();
        resolver.takePersistableUriPermission(uri, takeFlags);
        save(uri);
    }

    public void openNavFiles() {
        final OpenFileDialog f = new OpenFileDialog(context, OpenFileDialog.DIALOG_TYPE.FILE_DIALOG);
        f.setReadonly(true);

        String path = "";

        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);

        if (path == null || path.isEmpty()) {
            path = MainApplication.getPreferenceLastPath(context);
        }

        f.setCurrentPath(new File(path));
        f.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                File p = f.getCurrentPath();
                shared.edit().putString(MainApplication.PREFERENCE_LAST_PATH, p.getParent()).commit();
                Uri u = Uri.fromFile(p);
                save(u);
            }
        });
        f.show();
    }

    void save(Uri p) {
        final EnginesManager engies = main.getEngines();

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

    public MainApplication getApp() {
        return (MainApplication) context.getApplicationContext();
    }

}
