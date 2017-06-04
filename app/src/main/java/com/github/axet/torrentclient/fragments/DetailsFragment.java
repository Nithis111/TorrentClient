package com.github.axet.torrentclient.fragments;

import android.content.Context;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.github.axet.torrentclient.R;
import com.github.axet.torrentclient.activities.MainActivity;

import java.util.HashMap;
import java.util.Map;

public class DetailsFragment extends Fragment implements MainActivity.TorrentFragmentInterface {
    View v;
    ViewPager pager;

    public static class DetailsPagerAdapter extends FragmentPagerAdapter {
        long t;

        Map<Integer, Fragment> map = new HashMap<>();
        Context context;

        public DetailsPagerAdapter(Context context, FragmentManager fm, long t) {
            super(fm);
            this.context = context;
            this.t = t;
        }

        @Override
        public Fragment getItem(int i) {
            Fragment f;

            switch (i) {
                case 0:
                    f = new InfoFragment();
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
                    return context.getString(R.string.tab_info);
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

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        v = inflater.inflate(R.layout.torrent_details, container, false);

        final long t = getArguments().getLong("torrent");

        pager = (ViewPager) v.findViewById(R.id.pager);
        DetailsPagerAdapter adapter = new DetailsPagerAdapter(getContext(), getChildFragmentManager(), t);
        pager.setAdapter(adapter);

        TabLayout tabLayout = (TabLayout) v.findViewById(R.id.tab_layout);
        tabLayout.setupWithViewPager(pager);

        pager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabLayout));

        update();

        return v;
    }

    @Override
    public void update() {
        long t = getArguments().getLong("torrent");
    }

    @Override
    public void close() {
    }
}
