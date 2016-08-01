package com.github.axet.torrentclient.dialogs;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.github.axet.torrentclient.R;
import com.github.axet.torrentclient.activities.MainActivity;

public class LoginDialogFragment extends BrowserDialogFragment {
    ViewPager pager;
    FrameLayout v;
    TextView login;
    TextView pass;

    Result result = new Result();

    public static class Result implements DialogInterface {
        public boolean ok;
        public boolean browser;
        public String login;
        public String pass;

        @Override
        public void cancel() {
        }

        @Override
        public void dismiss() {
        }
    }

    public static LoginDialogFragment create(String url) {
        LoginDialogFragment f = new LoginDialogFragment();
        Bundle args = new Bundle();
        args.putString("url", url);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(result);
    }

    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        final AlertDialog d = new AlertDialog.Builder(getActivity())
                .setNeutralButton("Browser", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                })
                .setNegativeButton(getContext().getString(R.string.cancel),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                dialog.dismiss();
                            }
                        }
                )
                .setPositiveButton(getContext().getString(R.string.ok),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                result.ok = true;
                                result.login = getLogin();
                                result.pass = getPass();
                                dialog.dismiss();
                            }
                        }
                )
                .setView(createViewLogin(LayoutInflater.from(getContext()), null, savedInstanceState))
                .create();

        d.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                Button b = d.getButton(AlertDialog.BUTTON_NEUTRAL);
                b.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        browserMode(savedInstanceState);
                    }
                });
            }
        });

        return d;
    }

    public void browserMode(final Bundle savedInstanceState) {
        result.browser = true;

        AlertDialog d = (AlertDialog) getDialog();

        Button b = d.getButton(AlertDialog.BUTTON_NEUTRAL);
        b.setVisibility(View.GONE);

        Button b1 = d.getButton(AlertDialog.BUTTON_NEGATIVE);
        b1.setVisibility(View.GONE);

        Button b2 = d.getButton(AlertDialog.BUTTON_POSITIVE);
        b2.setText(R.string.close);

        v.removeAllViews();
        View vv = createView(LayoutInflater.from(getContext()), v, savedInstanceState);
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

    MainActivity getMainActivity() {
        return (MainActivity) getActivity();
    }

    public View createViewLogin(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        v = new FrameLayout(getContext());

        View vv = inflater.inflate(R.layout.search_login, v);

        login = (TextView) vv.findViewById(R.id.search_login_login);
        pass = (TextView) vv.findViewById(R.id.search_login_pass);

        return v;
    }

    public String getLogin() {
        return login.getText().toString();
    }

    public String getPass() {
        return pass.getText().toString();
    }
}
