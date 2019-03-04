package com.example.android.cloudsigner;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.widget.Toast;

import static android.content.Context.CONNECTIVITY_SERVICE;
import static android.support.v4.content.ContextCompat.getSystemService;

public class HelperClass {

    Context helperContext;

    public HelperClass(Context context) {
        this.helperContext = context;
    }

    public boolean InternetConnection() {
        ConnectivityManager connectivity = (ConnectivityManager) helperContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity.getActiveNetworkInfo() != null) {
            if (connectivity.getActiveNetworkInfo().isConnected())
                return true;
        }
        return false;
    }

    public void AlertDialogBuilder(String message, final Context context, String title) {
        AlertDialog.Builder newAlert = new AlertDialog.Builder(context);
        newAlert.setMessage(message);
        newAlert.setTitle(title);
        newAlert.setCancelable(true);

        /*
        newAlert.setPositiveButton(
                "Yes",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        Toast.makeText(context,
                                "Go to Internet Settings and set it to Enable!", Toast.LENGTH_SHORT)
                                .show();
                    }
                });
        newAlert.setNegativeButton(
                "No",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        Toast.makeText(context,
                                "You're not able to sign your file without internet!", Toast.LENGTH_SHORT)
                                .show();
                        dialog.cancel();
                    }
                });
        */
        AlertDialog alert = newAlert.create();
        alert.show();
    }
}
