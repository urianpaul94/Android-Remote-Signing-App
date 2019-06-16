package com.example.android.cloudsigner;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.ParseException;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.text.HtmlCompat;
import android.telephony.CellInfoGsm;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.text.Html;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.view.Gravity;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;

import static android.content.Context.CONNECTIVITY_SERVICE;
import static android.support.v4.content.ContextCompat.getSystemService;

public class HelperClass {

    Context helperContext;

    // Storage Permissions
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

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

    public boolean isAirplaneModeOn(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
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

    public boolean removeValue(Context context, String type) {
        try {
            SharedPreferences mySPrefs = PreferenceManager.getDefaultSharedPreferences(context);
            SharedPreferences.Editor editor = mySPrefs.edit();
            editor.remove(type);
            editor.apply();
            Log.d("Delete: ", "Deleted successfully!");
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
                cursor.close();
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



    public Date getUTCdatetimeAsDate() {
        String str=getUTCdatetimeAsString();
        if(str==null){
            return null;
        }
        return stringDateToDate(getUTCdatetimeAsString());
    }

    private static String getUTCdatetimeAsString() {
        final SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        final String utcTime = sdf.format(new Date());
        return utcTime;
    }

    private static Date stringDateToDate(String StrDate) {
        Date dateToReturn = null;
        SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);

        try {
            dateToReturn = (Date)dateFormat.parse(StrDate);
        }
        catch (ParseException e) {
            e.printStackTrace();
        } catch (java.text.ParseException e) {
            e.printStackTrace();
        }

        return dateToReturn;
    }

    public static void verifyStoragePermissions(Activity activity) {
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int readPermission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE);
        int readSmsPermission=ActivityCompat.checkSelfPermission(activity,Manifest.permission.READ_SMS);
        int receiveSmsPermission=ActivityCompat.checkSelfPermission(activity,Manifest.permission.RECEIVE_SMS);
        String [] permissionRequests=new String[4];
        int permissionNumber=0;
        if (permission != PackageManager.PERMISSION_GRANTED) {
            permissionRequests[permissionNumber]=Manifest.permission.WRITE_EXTERNAL_STORAGE;
            permissionNumber++;
        }
        if (readPermission != PackageManager.PERMISSION_GRANTED) {
            permissionRequests[permissionNumber]=Manifest.permission.READ_EXTERNAL_STORAGE;
            permissionNumber++;
        }
        if (readSmsPermission != PackageManager.PERMISSION_GRANTED) {
            permissionRequests[permissionNumber]=Manifest.permission.READ_SMS;
            permissionNumber++;
        }
        if (receiveSmsPermission != PackageManager.PERMISSION_GRANTED) {
            permissionRequests[permissionNumber]=Manifest.permission.RECEIVE_SMS;
            permissionNumber++;
        }
        if(permissionNumber>0){
            try {
                ActivityCompat.requestPermissions(
                        activity,
                        permissionRequests,
                        REQUEST_EXTERNAL_STORAGE
                );
            }catch (Exception e){
                Log.d("Error",e.getMessage());
            }
        }
    }

    public void AlertDialogBuilder(String message, final Context context, String title, boolean... success) {
        final SpannableString str = new SpannableString(message);
        Linkify.addLinks(str, Linkify.WEB_URLS);

        TextView textView = new TextView(context);
        textView.setText(title);
        textView.setGravity(Gravity.CENTER);
        if (success.length <= 0) {
            textView.setTextColor(Color.parseColor("#FF0000"));
        } else {
            if (success[0] == true) {
                textView.setTextColor(Color.parseColor("#00cc00"));
            } else {
                textView.setTextColor(Color.parseColor("#FF0000"));
            }
        }
        textView.setTextSize(22);

        AlertDialog.Builder newAlert = new AlertDialog.Builder(context);
        newAlert.setMessage(str);
        newAlert.setCustomTitle(textView);
        newAlert.setCancelable(true);
        newAlert.setNegativeButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
        });
        AlertDialog alert = newAlert.create();
        alert.show();
        ((TextView) alert.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
    }
}
