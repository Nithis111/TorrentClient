package com.github.axet.torrentclient.fragments;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
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
import java.util.List;

import libtorrent.Libtorrent;

public class FilesFragment extends Fragment implements MainActivity.TorrentFragmentInterface {
    View v;
    ListView list;
    View toolbar;
    View download;

    ArrayList<TorName> files = new ArrayList<>();
    Files adapter;
    TextView size;
    long t;

    String torrentName;

    static class TorName {
        public String path; // sort by value
        public String name;
        public long size;

        public String toString() {
            return path;
        }
    }

    static class TorFolder extends TorName {
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

    static class TorFile extends TorName {
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

    static class SortFiles implements Comparator<TorName> {
        @Override
        public int compare(TorName file, TorName file2) {
            return file.path.compareTo(file2.path);
        }
    }

    class Files extends BaseAdapter {

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
            LayoutInflater inflater = LayoutInflater.from(getContext());

            if (view == null) {
                view = inflater.inflate(R.layout.torrent_files_item, viewGroup, false);
            }

            TextView percent = (TextView) view.findViewById(R.id.torrent_files_percent);
            final View folder = view.findViewById(R.id.torrent_files_folder);
            View fc = view.findViewById(R.id.torrent_files_file);
            TextView folderName = (TextView) view.findViewById(R.id.torrent_files_folder_name);
            TextView file = (TextView) view.findViewById(R.id.torrent_files_name);

            final long t = getArguments().getLong("torrent");

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
                size.setText(MainApplication.formatSize(getContext(), item.size));

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
                size.setText(MainApplication.formatSize(getContext(), item.size));
            }

            return view;
        }
    }

    void updateTotal() {
        size.setText(MainApplication.formatSize(getContext(), Libtorrent.torrentPendingBytesLength(t)));
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

        adapter = new Files();

        list.setAdapter(adapter);

        size = (TextView) v.findViewById(R.id.torrent_files_size);

        View none = v.findViewById(R.id.torrent_files_none);
        none.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                for (TorName f : files) {
                    if (f instanceof TorFolder) {
                        TorFolder n = (TorFolder) f;
                        for (TorName k : n.files) {
                            TorFile m = (TorFile) k;
                            m.setCheck(false);
                        }
                    }
                    if (f instanceof TorFile) {
                        TorFile n = (TorFile) f;
                        n.setCheck(false);
                    }
                }
                updateTotal();
                adapter.notifyDataSetChanged();
            }
        });

        View all = v.findViewById(R.id.torrent_files_all);
        all.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                for (TorName f : files) {
                    if (f instanceof TorFolder) {
                        TorFolder n = (TorFolder) f;
                        for (TorName k : n.files) {
                            TorFile m = (TorFile) k;
                            m.setCheck(true);
                        }
                    }
                    if (f instanceof TorFile) {
                        TorFile n = (TorFile) f;
                        n.setCheck(true);
                    }
                }
                updateTotal();
                adapter.notifyDataSetChanged();
            }
        });

        View delete = v.findViewById(R.id.torrent_files_delete);
        delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle("Delete Unselected Files");
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

        torrentName = Libtorrent.torrentName(t);

        long l = Libtorrent.torrentFilesCount(t);

        if (files.size() == 0) {
            files.clear();
            TorFolder folder = null;
            if (l == 1) {
                TorFile f = new TorFile(t, 0);
                f.name = "./" + f.path;
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
                        if (folder == null || !folder.path.equals(parent)) {
                            if (folder != null) {
                                Collections.sort(folder.files, new SortFiles());
                            }
                            folder = new TorFolder();
                            folder.path = parent;
                            folder.name = folder.path;
                            folder.expand = false;
                            files.add(folder);
                        }
                        folder.size += f.size;
                        folder.files.add(f);
                        f.folder = folder;
                    }
                    if (f.folder == null)
                        files.add(f);
                }
                Collections.sort(files, new SortFiles());
            }

            updateTotal();
        }

        adapter.notifyDataSetChanged();
    }

    @Override
    public void close() {
    }
}
