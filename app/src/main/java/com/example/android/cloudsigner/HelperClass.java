package com.example.android.cloudsigner;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
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

    public boolean saveValue(Context context, String type, String value) {
        try {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(type, value);
            editor.apply();
            return true;
        } catch (Exception e) {
            Log.d("Error: ", e.getMessage());
            return false;
        }
    }

    public String getValue(Context context, String type) {
        try {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            String value = sharedPreferences.getString(type, null);

            if (value != null) {
                return value;
            } else {
                return "Not found!";
            }
        } catch (Exception e) {
            Log.d("Error: ", e.getMessage());
            return null;
        }
    }

    public String getUriRealPathAboveKitKat(Context context, Uri uri) {
        String path = "";

        if (context != null && uri != null) {
            if (isContentUri(uri)) {
                ContentResolver contentResolver = context.getContentResolver();
                path = getRealPath(contentResolver, uri, null);
            } else if (isFileUri(uri)) {
                path = uri.getPath();
            } else {
                if (isDocumentUri(context, uri)) {
                    String docId = DocumentsContract.getDocumentId(uri);
                    String uriAuthority = uri.getAuthority();

                    if (isMediaDoc(uriAuthority)) {
                        Log.d("Doc: ", "Media");
                    } else if (isDownloadDoc(uriAuthority)) {
                        Log.d("Doc: ", "Download");
                    } else if (isExternalStoreDoc(uriAuthority)) {
                        Log.d("Doc: ", "External");
                    }
                }

            }

        }

        return path;
    }

    private String getRealPath(ContentResolver contentResolver, Uri uri, String whereClause) {
        String ret = "";
        Cursor cursor = contentResolver.query(uri, null, whereClause, null, null);

        if (cursor != null) {
            boolean moveToFirst = cursor.moveToFirst();
            if (moveToFirst) {
                String columnName = MediaStore.Images.Media.DATA;
                String col = MediaStore.Files.FileColumns.DATA;

                if (uri == MediaStore.Images.Media.EXTERNAL_CONTENT_URI) {
                    columnName = MediaStore.Images.Media.DATA;
                } else if (uri == MediaStore.Audio.Media.EXTERNAL_CONTENT_URI) {
                    columnName = MediaStore.Audio.Media.DATA;
                } else if (uri == MediaStore.Video.Media.EXTERNAL_CONTENT_URI) {
                    columnName = MediaStore.Video.Media.DATA;
                }
                int imageColumnIndex = cursor.getColumnIndex(columnName);
                ret = cursor.getString(imageColumnIndex);
            }
        }

        return ret;
    }


    private boolean isAboveKitKat() {
        boolean ret = false;
        ret = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
        return ret;
    }

    private boolean isFileUri(Uri uri) {
        boolean ret = false;
        if (uri != null) {
            String uriSchema = uri.getScheme();
            if ("file".equalsIgnoreCase(uriSchema)) {
                ret = true;
            }
        }
        return ret;
    }

    private boolean isDocumentUri(Context ctx, Uri uri) {
        boolean ret = false;
        if (ctx != null && uri != null) {
            ret = DocumentsContract.isDocumentUri(ctx, uri);
        }
        return ret;
    }

    private boolean isContentUri(Uri uri) {
        boolean ret = false;
        if (uri != null) {
            String uriSchema = uri.getScheme();
            if ("content".equalsIgnoreCase(uriSchema)) {
                ret = true;
            }
        }
        return ret;
    }

    private boolean isExternalStoreDoc(String uriAuthority) {
        boolean ret = false;

        if ("com.android.externalstorage.documents".equals(uriAuthority)) {
            ret = true;
        }

        return ret;
    }

    private boolean isDownloadDoc(String uriAuthority) {
        boolean ret = false;

        if ("com.android.providers.downloads.documents".equals(uriAuthority)) {
            ret = true;
        }

        return ret;
    }

    private boolean isMediaDoc(String uriAuthority) {
        boolean ret = false;

        if ("com.android.providers.media.documents".equals(uriAuthority)) {
            ret = true;
        }

        return ret;
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
