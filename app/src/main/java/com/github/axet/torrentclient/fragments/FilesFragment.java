package com.github.axet.torrentclient.fragments;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.github.axet.torrentclient.R;
import com.github.axet.torrentclient.activities.MainActivity;
import com.github.axet.torrentclient.app.MainApplication;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

import libtorrent.Libtorrent;

public class FilesFragment extends Fragment implements MainActivity.TorrentFragmentInterface {
    View v;
    ListView list;
    View toolbar;
    View download;

    Files adapter;
    TextView size;
    long t;

    public static class TorName {
        public String path; // sort by value
        public String name;
        public long size;

        public String toString() {
            return path;
        }
    }

    public static class TorFolder extends TorName {
        public boolean expand;
        public ArrayList<TorName> files = new ArrayList<>();

        public boolean getCheck() {
            boolean b = true;
            for (TorName m : files) {
                TorFile k = (TorFile) m;
                if (!k.file.getCheck())
                    b = false;
            }
            return b;
        }
    }

    public static class TorFile extends TorName {
        public TorFolder folder;
        public long index;
        public libtorrent.File file;
        public long t;

        public TorFile(long t, long i) {
            this.t = t;
            index = i;
            update();
            size = file.getLength();
        }

        public void update() {
            file = Libtorrent.torrentFiles(t, index);
        }

        public void setCheck(boolean b) {
            Libtorrent.torrentFilesCheck(t, index, b);
            file.setCheck(b);
        }
    }

    public static class SortFiles implements Comparator<TorName> {
        @Override
        public int compare(TorName file, TorName file2) {
            return file.path.compareTo(file2.path);
        }
    }

    public static class Files extends BaseAdapter {
        Context context;
        long t;
        ArrayList<TorName> files = new ArrayList<>();
        HashMap<String, TorFolder> folders = new HashMap<>();

        public Files(Context context, long t) {
            this.context = context;
            this.t = t;
        }

        @Override
        public int getCount() {
            return files.size();
        }

        @Override
        public TorName getItem(int i) {
            return files.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(final int i, View view, ViewGroup viewGroup) {
            LayoutInflater inflater = LayoutInflater.from(context);

            if (view == null) {
                view = inflater.inflate(R.layout.torrent_files_item, viewGroup, false);
            }

            TextView percent = (TextView) view.findViewById(R.id.torrent_files_percent);
            final View folder = view.findViewById(R.id.torrent_files_folder);
            View fc = view.findViewById(R.id.torrent_files_file);
            TextView folderName = (TextView) view.findViewById(R.id.torrent_files_folder_name);
            TextView file = (TextView) view.findViewById(R.id.torrent_files_name);

            TorName item = getItem(i);

            if (item instanceof TorFolder) {
                folder.setVisibility(View.VISIBLE);
                fc.setVisibility(View.GONE);
                final TorFolder f = (TorFolder) item;
                folderName.setText(f.name);

                final CheckBox check = (CheckBox) view.findViewById(R.id.torrent_files_folder_check);
                check.setChecked(f.getCheck());
                check.jumpDrawablesToCurrentState();
                check.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        for (TorName m : f.files) {
                            TorFile k = (TorFile) m;
                            k.setCheck(check.isChecked());
                        }
                        updateTotal();
                        notifyDataSetChanged();
                    }
                });

                TextView size = (TextView) view.findViewById(R.id.torrent_files_folder_size);
                size.setText(MainApplication.formatSize(context, item.size));

                final ImageView expand = (ImageView) view.findViewById(R.id.torrent_files_folder_expand);
                folder.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        f.expand = !f.expand;
                        if (f.expand) {
                            files.addAll(i + 1, f.files);
                        } else {
                            files.subList(i + 1, i + f.files.size() + 1).clear();
                        }
                        updateTotal();
                        notifyDataSetChanged();
                    }
                });

                if (!f.expand)
                    expand.setImageResource(R.drawable.ic_expand_more_black_24dp);
                else
                    expand.setImageResource(R.drawable.ic_expand_less_black_24dp);
            }

            if (item instanceof TorFile) {
                fc.setVisibility(View.VISIBLE);
                folder.setVisibility(View.GONE);

                final TorFile f = (TorFile) item;
                f.update();

                final CheckBox check = (CheckBox) view.findViewById(R.id.torrent_files_check);
                check.setChecked(f.file.getCheck());
                check.jumpDrawablesToCurrentState();
                check.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Libtorrent.torrentFilesCheck(t, f.index, check.isChecked());
                    }
                });

                percent.setEnabled(false);
                if (f.file.getLength() > 0)
                    MainApplication.setTextNA(percent, (f.file.getBytesCompleted() * 100 / f.file.getLength()) + "%");
                else
                    MainApplication.setTextNA(percent, "100%");

                fc.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Libtorrent.torrentFilesCheck(t, f.index, check.isChecked());
                        f.file.setCheck(check.isChecked()); // update runtime data
                        updateTotal();
                        notifyDataSetChanged();
                    }
                });

                file.setText(f.name);

                TextView size = (TextView) view.findViewById(R.id.torrent_files_size);
                size.setText(MainApplication.formatSize(context, item.size));
            }

            return view;
        }

        public void update() {
            long l = Libtorrent.torrentFilesCount(t);

            String torrentName = Libtorrent.torrentName(t);

            if (files.size() == 0 && l > 0) {
                files.clear();
                folders.clear();
                if (l == 1) {
                    TorFile f = new TorFile(t, 0);
                    f.name = "./" + f.file.getPath();
                    files.add(f);
                } else {
                    for (long i = 0; i < l; i++) {
                        TorFile f = new TorFile(t, i);

                        String p = f.file.getPath();
                        p = p.substring(torrentName.length() + 1);
                        f.path = p;
                        File file = new File(p);
                        String parent = file.getParent();
                        f.name = "./" + file.getName();

                        if (parent != null) {
                            TorFolder folder = folders.get(parent);
                            if (folder == null) {
                                folder = new TorFolder();
                                folder.path = parent;
                                folder.name = folder.path;
                                folder.expand = false;
                                files.add(folder);
                                folders.put(parent, folder);
                            }
                            folder.size += f.size;
                            folder.files.add(f);
                            f.folder = folder;
                        }
                        if (f.folder == null)
                            files.add(f);
                    }
                    for (TorName n : files) {
                        if (n instanceof TorFolder) {
                            TorFolder m = (TorFolder) n;
                            Collections.sort(m.files, new SortFiles());
                        }
                    }
                    Collections.sort(files, new SortFiles());
                }
            }

            notifyDataSetChanged();
        }

        public void checkAll() {
            Libtorrent.torrentFilesCheckAll(t, true);
            for (TorName f : files) {
                if (f instanceof TorFolder) {
                    TorFolder n = (TorFolder) f;
                    for (TorName k : n.files) {
                        TorFile m = (TorFile) k;
                        m.file.setCheck(true);
                    }
                }
                if (f instanceof TorFile) {
                    TorFile n = (TorFile) f;
                    n.file.setCheck(true);
                }
            }
            updateTotal();
            notifyDataSetChanged();
        }

        public void checkNone() {
            Libtorrent.torrentFilesCheckAll(t, false);
            for (TorName f : files) {
                if (f instanceof TorFolder) {
                    TorFolder n = (TorFolder) f;
                    for (TorName k : n.files) {
                        TorFile m = (TorFile) k;
                        m.file.setCheck(false);
                    }
                }
                if (f instanceof TorFile) {
                    TorFile n = (TorFile) f;
                    n.file.setCheck(false);
                }
            }
            updateTotal();
            notifyDataSetChanged();
        }

        public void updateTotal() {

        }
    }

    void updateTotal() {
        String p = "";
        if (Libtorrent.metaTorrent(t)) {
            p = MainApplication.formatSize(getContext(), Libtorrent.torrentPendingBytesLength(t));
        }
        MainApplication.setTextNA(size, p);
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        v = inflater.inflate(R.layout.torrent_files, container, false);

        t = getArguments().getLong("torrent");

        download = v.findViewById(R.id.torrent_files_metadata);
        download.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!Libtorrent.downloadMetadata(t)) {
                    ((MainActivity) getActivity().getApplicationContext()).Error(Libtorrent.error());
                    return;
                }
            }
        });

        list = (ListView) v.findViewById(R.id.list);

        toolbar = v.findViewById(R.id.torrent_files_toolbar);

        adapter = new Files(getContext(), t) {
            @Override
            public void updateTotal() {
                FilesFragment.this.updateTotal();
            }
        };

        list.setAdapter(adapter);

        size = (TextView) v.findViewById(R.id.torrent_files_size);

        View none = v.findViewById(R.id.torrent_files_none);
        none.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                adapter.checkNone();
            }
        });

        View all = v.findViewById(R.id.torrent_files_all);
        all.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                adapter.checkAll();
            }
        });

        View delete = v.findViewById(R.id.torrent_files_delete);
        delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle(R.string.delete_unselected);
                builder.setMessage(R.string.are_you_sure);
                builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ;
                    }
                });
                builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Libtorrent.torrentFileDeleteUnselected(t);
                    }
                });
                AlertDialog dialog = builder.create();
                dialog.show();
            }
        });

        update();

        return v;
    }

    @Override
    public void update() {
        long t = getArguments().getLong("torrent");

        download.setVisibility(Libtorrent.metaTorrent(t) ? View.GONE : View.VISIBLE);
        toolbar.setVisibility(Libtorrent.metaTorrent(t) ? View.VISIBLE : View.GONE);

        adapter.update();
        updateTotal();
    }

    @Override
    public void close() {
    }
}
