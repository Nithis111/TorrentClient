package com.github.axet.torrentclient.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.TabLayout;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.github.axet.torrentclient.R;
import com.github.axet.torrentclient.activities.MainActivity;
import com.github.axet.torrentclient.fragments.DetailsFragment;
import com.github.axet.torrentclient.fragments.FilesFragment;
import com.github.axet.torrentclient.fragments.PeersFragment;
import com.github.axet.torrentclient.fragments.TrackersFragment;

import java.util.HashMap;
import java.util.Map;

import go.libtorrent.Libtorrent;

public class TorrentDialogFragment extends DialogFragment implements MainActivity.TorrentFragmentInterface {
    ViewPager pager;
    View v;

    public static class TorrentPagerAdapter extends FragmentPagerAdapter {
        long t;

        Map<Integer, Fragment> map = new HashMap<>();
        Context context;

        public TorrentPagerAdapter(Context context, FragmentManager fm, long t) {
            super(fm);

            this.context = context;
            this.t = t;
        }

        @Override
        public Fragment getItem(int i) {
            Fragment f;

            switch (i) {
                case 0:
                    f = new DetailsFragment();
                    break;
                case 1:
                    f = new FilesFragment();
                    break;
                case 2:
                    f = new PeersFragment();
                    break;
                case 3:
                    f = new TrackersFragment();
                    break;
                default:
                    return null;
            }

            Bundle args = new Bundle();
            args.putLong("torrent", t);
            f.setArguments(args);

            return f;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            Object o = super.instantiateItem(container, position);
            map.put(position, (Fragment) o);
            return o;
        }

        public MainActivity.TorrentFragmentInterface getFragment(int pos) {
            return (MainActivity.TorrentFragmentInterface) map.get(pos);
        }

        @Override
        public int getCount() {
            return 4;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return context.getString(R.string.tab_details);
                case 1:
                    return context.getString(R.string.tab_files);
                case 2:
                    return context.getString(R.string.tab_peers);
                case 3:
                    return context.getString(R.string.tab_trackers);
                default:
                    return "EMPTY";
            }
        }
    }

    public static TorrentDialogFragment create(Long t) {
        TorrentDialogFragment f = new TorrentDialogFragment();
        Bundle args = new Bundle();
        args.putLong("torrent", t);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        final Activity activity = getActivity();
        if (activity instanceof DialogInterface.OnDismissListener) {
            ((DialogInterface.OnDismissListener) activity).onDismiss(dialog);
        }
    }

    @Override
    public void dismiss() {
        super.dismiss();
        pager = null;
    }

    @Override
    public void update() {
        // dialog maybe created but onCreateView not yet called
        if (pager == null)
            return;

        int i = pager.getCurrentItem();
        TorrentPagerAdapter a = (TorrentPagerAdapter) pager.getAdapter();
        MainActivity.TorrentFragmentInterface f = a.getFragment(i);
        if (f == null)
            return;
        f.update();
    }

    @Override
    public void close() {

    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final AlertDialog d = new AlertDialog.Builder(getActivity())
                .setNeutralButton(buttonText(), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                })
                .setPositiveButton(getContext().getString(R.string.close),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                dialog.dismiss();
                            }
                        }
                )
                .setView(createView(LayoutInflater.from(getContext()), null, savedInstanceState))
                .create();
        d.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                final Button b = d.getButton(DialogInterface.BUTTON_NEUTRAL);
                b.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        long t = getArguments().getLong("torrent");
                        int s = Libtorrent.torrentStatus(t);
                        switch (s) {
                            case Libtorrent.StatusChecking:
                            case Libtorrent.StatusDownloading:
                            case Libtorrent.StatusQueued:
                            case Libtorrent.StatusSeeding:
                                Libtorrent.stopTorrent(t);
                                break;
                            default:
                                Libtorrent.startTorrent(t);
                                break;
                        }
                        b.setText(buttonText());
                    }
                });
            }
        });
        return d;
    }

    int buttonText() {
        long t = getArguments().getLong("torrent");
        int s = Libtorrent.torrentStatus(t);
        switch (s) {
            case Libtorrent.StatusDownloading:
            case Libtorrent.StatusQueued:
            case Libtorrent.StatusSeeding:
                return R.string.stop;
            default:
                return R.string.start;
        }
    }

    @Nullable
    @Override
    public View getView() {
        return null;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return v;
    }

    public View createView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        v = inflater.inflate(R.layout.torrent_details, container);

        long t = getArguments().getLong("torrent");

        pager = (ViewPager) v.findViewById(R.id.pager);
        TorrentPagerAdapter adapter = new TorrentPagerAdapter(getContext(), getChildFragmentManager(), t);
        pager.setAdapter(adapter);

        TabLayout tabLayout = (TabLayout) v.findViewById(R.id.tab_layout);
        tabLayout.setupWithViewPager(pager);

        pager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabLayout));
        return v;
    }

}
