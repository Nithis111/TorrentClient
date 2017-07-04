package com.github.axet.torrentclient.navigators;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.PopupMenu;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.github.axet.androidlibrary.animations.RemoveItemAnimation;
import com.github.axet.androidlibrary.widgets.HeaderGridView;
import com.github.axet.androidlibrary.widgets.PopupShareActionProvider;
import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.androidlibrary.widgets.UnreadCountDrawable;
import com.github.axet.torrentclient.R;
import com.github.axet.torrentclient.activities.MainActivity;
import com.github.axet.torrentclient.animations.RecordingAnimation;
import com.github.axet.torrentclient.app.MainApplication;
import com.github.axet.torrentclient.app.Storage;
import com.github.axet.torrentclient.dialogs.TorrentDialogFragment;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import libtorrent.Libtorrent;

public class Torrents extends BaseAdapter implements DialogInterface.OnDismissListener,
        MainActivity.TorrentFragmentInterface, UnreadCountDrawable.UnreadCount,
        MainActivity.NavigatorInterface {
    public static final String TAG = Torrents.class.getSimpleName();

    public static final String PLAY = Torrents.class.getCanonicalName() + "_PLAY";

    static final int TYPE_COLLAPSED = 0;
    static final int TYPE_EXPANDED = 1;
    static final int TYPE_DELETED = 2;

    int selected = -1;

    Context context;
    MainActivity main;
    PopupShareActionProvider shareProvider;
    TorrentDialogFragment dialog;
    HeaderGridView list;
    Map<Storage.Torrent, Boolean> unread = new HashMap<>();
    BroadcastReceiver receiver;
    boolean openFolder;
    Storage storage;

    public static class Tag {
        public int tag;
        public int position;

        public Tag(int t, int p) {
            this.tag = t;
            this.position = p;
        }

        public static boolean animate(View v, int s, int p) {
            if (v.getTag() == null)
                return true;
            if (animate(v, s))
                return true;
            return ((Tag) v.getTag()).position != p;
        }

        public static boolean animate(View v, int s) {
            if (v.getTag() == null)
                return false;
            return ((Tag) v.getTag()).tag == s;
        }

        public static void setTag(View v, int t, int p) {
            v.setTag(new Tag(t, p));
        }
    }

    public static void play(Context context, long t) {
        Intent i = new Intent(PLAY);
        i.putExtra("torrent", t);
        context.sendBroadcast(i);
    }

    public Torrents(MainActivity main, HeaderGridView list) {
        super();

        this.main = main;
        this.context = main;
        this.list = list;
        this.storage = getApp().getStorage();

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String a = intent.getAction();
                if (a.equals(PLAY)) {
                    Storage.Torrent t = storage.find(intent.getLongExtra("torrent", -1));
                    play(t);
                }
            }
        };
        IntentFilter fi = new IntentFilter();
        fi.addAction(PLAY);
        getContext().registerReceiver(receiver, fi);

        Intent intent = MainActivity.openFolderIntent(storage.getStoragePath());
        openFolder = intent.resolveActivityInfo(context.getPackageManager(), 0) != null;
    }

    public Context getContext() {
        return context;
    }

    public MainApplication getApp() {
        return (MainApplication) main.getApplication();
    }

    @Override
    public void update() {
        if (dialog != null)
            dialog.update();
    }

    public void updateStorage() {
        for (int i = 0; i < getCount(); i++) {
            Storage.Torrent t = getItem(i);
            if (Libtorrent.torrentActive(t.t)) {
                t.update();
            }
        }
        notifyDataSetChanged();
    }

    public void close() {
        if (receiver != null) {
            getContext().unregisterReceiver(receiver);
            receiver = null;
        }
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        if (this.dialog != null)
            this.dialog.close();
        this.dialog = null;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return storage.count();
    }

    @Override
    public Storage.Torrent getItem(int i) {
        return storage.torrent(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(final int position, View convertView, final ViewGroup parent) {
        LayoutInflater inflater = LayoutInflater.from(getContext());

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.torrent, parent, false);
            convertView.setTag(null);
        }

        if (!openFolder) {
            convertView.findViewById(R.id.recording_player_open).setVisibility(View.GONE);
            convertView.findViewById(R.id.recording_player_open_space).setVisibility(View.GONE);
        }

        final View view = convertView;
        final View base = convertView.findViewById(R.id.recording_base); // GridView unable to handle GONE views, hide its base.

        if (Tag.animate(convertView, TYPE_DELETED)) {
            RemoveItemAnimation.restore(base);
            convertView.setTag(null);
        }

        final Storage.Torrent t = getItem(position);

        Boolean u = unread.get(t);
        if (u != null && u)
            convertView.setBackgroundColor(ThemeUtils.getThemeColor(getContext(), R.attr.unreadColor));
        else
            convertView.setBackgroundColor(0);

        TextView title = (TextView) convertView.findViewById(R.id.torrent_title);
        title.setText(t.name());

        TextView time = (TextView) convertView.findViewById(R.id.torrent_status);
        time.setText(t.status());

        final View playerBase = convertView.findViewById(R.id.recording_player);
        // cover area, prevent click over to convertView
        playerBase.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            }
        });

        // we need runnable because we have View references
        final Runnable delete = new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle(R.string.delete_torrent);

                String name = Libtorrent.metaTorrent(t.t) ? ".../" + t.name() : t.name();

                builder.setMessage(name + "\n\n" + context.getString(R.string.are_you_sure));
                if (Libtorrent.metaTorrent(t.t)) {
                    builder.setNeutralButton(R.string.delete_with_data, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                            RemoveItemAnimation.apply(list, base, new Runnable() {
                                @Override
                                public void run() {
                                    if (Torrents.this.dialog != null) { // prevent showing deleted torrent
                                        Torrents.this.dialog.dismissAllowingStateLoss();
                                        Torrents.this.dialog.close();
                                        Torrents.this.dialog = null;
                                    }
                                    t.stop();
                                    Uri f = storage.child(t.path, t.name());
                                    storage.delete(f);
                                    storage.remove(t);
                                    Tag.setTag(view, TYPE_DELETED, -1);
                                    select(-1);
                                    main.updateUnread();
                                }
                            });
                        }
                    });
                }
                builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                        RemoveItemAnimation.apply(list, base, new Runnable() {
                            @Override
                            public void run() {
                                if (Torrents.this.dialog != null) { // prevent showing deleted torrent
                                    Torrents.this.dialog.dismissAllowingStateLoss();
                                    Torrents.this.dialog.close();
                                    Torrents.this.dialog = null;
                                }
                                t.stop();
                                storage.remove(t);
                                Tag.setTag(view, TYPE_DELETED, -1);
                                select(-1);
                                main.updateUnread();
                            }
                        });
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
        };

        View play = convertView.findViewById(R.id.torrent_play);
        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                play(t);
            }
        });

        {
            // should be done using states, so animation will apply
            ProgressBar bar = (ProgressBar) convertView.findViewById(R.id.torrent_process);
            ImageView stateImage = (ImageView) convertView.findViewById(R.id.torrent_state_image);

            TextView tt = (TextView) convertView.findViewById(R.id.torrent_process_text);

            long p = t.getProgress();

            int color = 0;
            String text = "";

            Drawable d = null;
            switch (Libtorrent.torrentStatus(t.t)) {
                case Libtorrent.StatusChecking:
                    d = ContextCompat.getDrawable(getContext(), R.drawable.ic_pause_24dp);
                    color = Color.YELLOW;
                    text = p + "%";
                    break;
                case Libtorrent.StatusPaused:
                    d = ContextCompat.getDrawable(getContext(), R.drawable.ic_pause_24dp);
                    color = ThemeUtils.getThemeColor(getContext(), R.attr.secondBackground);
                    text = p + "%";
                    break;
                case Libtorrent.StatusQueued:
                    d = ContextCompat.getDrawable(getContext(), R.drawable.ic_pause_24dp);
                    color = Color.GREEN;
                    text = "Qued";
                    break;
                case Libtorrent.StatusDownloading:
                    d = ContextCompat.getDrawable(getContext(), R.drawable.play);
                    color = ThemeUtils.getThemeColor(getContext(), R.attr.colorAccent);
                    text = p + "%";
                    break;
                case Libtorrent.StatusSeeding:
                    d = ContextCompat.getDrawable(getContext(), R.drawable.play);
                    color = ThemeUtils.getThemeColor(getContext(), R.attr.colorAccent);
                    text = "Seed";
                    break;
            }
            PorterDuffColorFilter filter = new PorterDuffColorFilter(0xb0000000 | (0xFFFFFF & color), PorterDuff.Mode.MULTIPLY);
            stateImage.setColorFilter(filter);

            if (t.fail()) {
                d = ContextCompat.getDrawable(getContext(), R.drawable.ic_exclamation);
                int color2 = ThemeUtils.getThemeColor(getContext(), R.attr.colorAccent);
                PorterDuffColorFilter filter2 = new PorterDuffColorFilter(color2, PorterDuff.Mode.SRC_IN);
                stateImage.setColorFilter(filter2);
            }

            stateImage.setImageDrawable(d);

            bar.getBackground().setColorFilter(filter);
            bar.getProgressDrawable().setColorFilter(color, PorterDuff.Mode.SRC_IN);
            bar.setProgress((int) p);

            tt.setText(text);
        }

        ImageView expand = (ImageView) convertView.findViewById(R.id.torrent_expand);

        if (selected == position) {
            if (Tag.animate(convertView, TYPE_COLLAPSED, position))
                RecordingAnimation.apply(list, convertView, true, main.scrollState == MainActivity.SCROLL_STATE_IDLE && Tag.animate(convertView, TYPE_COLLAPSED));
            Tag.setTag(convertView, TYPE_EXPANDED, position);

            final ImageView rename = (ImageView) convertView.findViewById(R.id.recording_player_rename);
            rename.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    main.renameDialog(t.t);
                }
            });
            if (!Libtorrent.metaTorrent(t.t)) {
                rename.setColorFilter(Color.GRAY);
                rename.setOnClickListener(null);
            } else {
                rename.setColorFilter(ThemeUtils.getThemeColor(getContext(), R.attr.colorAccent));
            }

            final ImageView open = (ImageView) convertView.findViewById(R.id.recording_player_open);

            open.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    main.openFolder(t);
                }
            });

            KeyguardManager myKM = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
            if (myKM.inKeyguardRestrictedInputMode()) {
                open.setColorFilter(Color.GRAY);
                open.setOnClickListener(null);
            } else {
                open.setColorFilter(ThemeUtils.getThemeColor(getContext(), R.attr.colorAccent));
            }

            final View share = convertView.findViewById(R.id.recording_player_share);
            share.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    shareProvider = new PopupShareActionProvider(getContext(), share);

                    Intent emailIntent = new Intent(Intent.ACTION_SEND);
                    emailIntent.setType("text/plain");
                    emailIntent.putExtra(Intent.EXTRA_EMAIL, "");
                    emailIntent.putExtra(Intent.EXTRA_SUBJECT, Libtorrent.torrentName(t.t));
                    emailIntent.putExtra(Intent.EXTRA_TEXT, Libtorrent.torrentMagnet(t.t));

                    shareProvider.setShareIntent(emailIntent);

                    shareProvider.show();
                }
            });

            View trash = convertView.findViewById(R.id.recording_player_trash);
            trash.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    delete.run();
                }
            });

            expand.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ic_expand_less_black_24dp));
            expand.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    select(-1);
                }
            });
        } else {
            if (Tag.animate(convertView, TYPE_EXPANDED, position))
                RecordingAnimation.apply(list, convertView, false, main.scrollState == MainActivity.SCROLL_STATE_IDLE && Tag.animate(convertView, TYPE_EXPANDED));
            Tag.setTag(convertView, TYPE_COLLAPSED, position);

            expand.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ic_expand_more_black_24dp));
            expand.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    select(position);
                }
            });
        }

        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (dialog == null) { // prevent double dialogs
                    if (t.t == -1) {
                        Log.d(TAG, "show deleted torrent");
                        return;
                    }
                    showDetails(t.t);
                }
            }
        });

        convertView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                PopupMenu popup = new PopupMenu(getContext(), v);
                MenuInflater inflater = popup.getMenuInflater();
                inflater.inflate(R.menu.menu_context, popup.getMenu());
                popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        if (item.getItemId() == R.id.action_delete) {
                            delete.run();
                            return true;
                        }
                        if (item.getItemId() == R.id.action_rename) {
                            main.renameDialog(t.t);
                            return true;
                        }
                        if (item.getItemId() == R.id.action_check) {
                            main.checkTorrent(t.t);
                            notifyDataSetChanged();
                            return true;
                        }
                        return false;
                    }
                });
                popup.show();
                return true;
            }
        });

        return convertView;
    }

    public void select(int pos) {
        selected = pos;
        notifyDataSetChanged();
    }

    public int getSelected() {
        return selected;
    }

    public void install(HeaderGridView list) {
        unread.clear();

        list.setAdapter(this);

        for (int i = 0; i < storage.count(); i++) {
            Storage.Torrent t = storage.torrent(i);
            unread.put(t, t.message);
        }

        storage.clearUnreadCount();
    }

    public void remove(HeaderGridView list) {
    }

    public void showDetails(Long f) {
        TorrentDialogFragment d = TorrentDialogFragment.create(f);
        if (dialog != null)
            dialog.close();
        dialog = d;
        d.show(main.getSupportFragmentManager(), "");
    }

    @Override
    public int getUnreadCount() {
        return storage.getUnreadCount();
    }

    public void play(Storage.Torrent t) {
        int s = Libtorrent.torrentStatus(t.t);

        if (s == Libtorrent.StatusChecking) {
            Libtorrent.stopTorrent(t.t);
            notifyDataSetChanged();
            return;
        }

        if (s == Libtorrent.StatusQueued) {
            // are we on wifi pause mode?
            if (Libtorrent.paused()) // drop torrent from queue
                storage.stop(t);
            else { // nope, we are on library pause, start torrent
                start(t);
            }
            notifyDataSetChanged();
            return;
        }

        if (s == Libtorrent.StatusPaused)
            start(t);
        else
            storage.stop(t);

        notifyDataSetChanged();
    }

    void start(Storage.Torrent t) {
        String s = t.path.getScheme();
        if (s.startsWith(ContentResolver.SCHEME_FILE)) {
            File p = new File(t.path.getPath());
            if(!p.exists() && !p.mkdirs()) {
                Toast.makeText(main, R.string.torrent_notfound, Toast.LENGTH_LONG).show();
                t.ejected = true;
                return;
            }
        }

        if (t.ejected(storage)) {
            Toast.makeText(main, R.string.torrent_notfound, Toast.LENGTH_LONG).show();
            t.ejected = true;
            return;
        }
        t.ejected = false;

        if (t.readonly) {
            if (t.readonly()) {
                main.Error(main.getString(R.string.filedialog_readonly) + " " + storage.getTargetName(t.path));
                return;
            }
            t.readonly = false;
        }

        if (t.check || t.altered(storage)) {
            Toast.makeText(main, R.string.torrent_altered, Toast.LENGTH_LONG).show();
            t.check = false;
            Libtorrent.checkTorrent(t.t);
            return;
        }

        storage.start(t);
    }
}
