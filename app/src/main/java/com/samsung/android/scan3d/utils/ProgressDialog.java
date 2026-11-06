package com.samsung.android.scan3d.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;

import com.samsung.android.scan3d.R;

public class ProgressDialog {

    private final AlertDialog dialog;
    private final ProgressBar progressBar;

    public ProgressDialog(Context context, int maxProgress) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.progress_dialog, null);
        progressBar = view.findViewById(R.id.progress_bar);
        progressBar.setMax(maxProgress);

        dialog = new AlertDialog.Builder(context)
                .setView(view)
                .setMessage("Saving data. Please wait...")
                .setCancelable(false)
                .create();
    }

    public void show() {
        dialog.show();
    }

    public void setProgress(int progress) {
        progressBar.setProgress(progress);
    }

    public void dismiss() {
        dialog.dismiss();
    }
}
