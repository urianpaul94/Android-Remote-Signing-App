package com.example.android.cloudsigner;

import android.content.Context;
import android.content.Intent;
import android.icu.text.SimpleDateFormat;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.graphics.PathUtils;
import android.support.v4.provider.DocumentFile;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;

public class MainActivity extends AppCompatActivity {
    public static final String FILE_PATH = "Path";
    private static final String AUTH_CODE = "AuthCode";
    private String authCode = "";
    private Button signButton;
    private Button viewButton;
    private boolean isPdf = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        SetDateAndTime();
        Intent intent = getIntent();
        if (intent == null) {
            finish();
        }
        HelperClass helperClass = new HelperClass(this);
        String pdfPath = "";
        try {
            pdfPath = helperClass.getValue(this, FILE_PATH);
            if (pdfPath != null) {
                Log.d("Pdf Path: ", pdfPath);
                isPdf = true;
            }

        } catch (Exception e) {
            pdfPath = "";
            isPdf = false;
            Log.d("Error", e.getMessage());
        }
        Button authorize = (Button) findViewById(R.id.button_authorize);
        signButton = (Button) findViewById(R.id.button_sign);
        viewButton = (Button) findViewById(R.id.button_view);
        Login(authorize);
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            HelperClass helperClass = new HelperClass(this);
            Intent intent = getIntent();
            Uri uri = intent.getData();
            String scheme = intent.getScheme();
            if (scheme.equals("content")) {
                String path = helperClass.getUriRealPathAboveKitKat(this, uri);
                if (helperClass.saveValue(this, MainActivity.FILE_PATH, path)) {
                    Log.d("Saved file path to Shared Preferences: ", path);
                } else {
                    Log.d("Error: ", "Saving path to Shared Preferences failed!.");
                }
            }
        } catch (Exception e) {
            Log.d("Error", e.getMessage());
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = getIntent();
        String scheme = intent.getScheme();
        try {
            if (scheme.equals("content") || isPdf) {
                viewButton.setVisibility(View.VISIBLE);
            }
            if (scheme.equals("http")) {
                signButton.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            Log.d("Error:", e.getMessage());
        }

        try {
            if (scheme.equals("http")) {
                Uri uri = intent.getData();
                String host = uri.getHost();
                Log.d("Host", host);
                Log.d("Uri", uri.toString());
                if (host.equals("cloud-signer")) {
                    String response = uri.toString();
                    StringTokenizer stringTokenizer = new StringTokenizer(response, "=&");
                    String link = stringTokenizer.nextToken();
                    String code = stringTokenizer.nextToken();
                    String state=stringTokenizer.nextToken();
                    if (code != null) {
                        authCode = code;
                        Log.d("Code", code);
                        Log.d("Redirect",link);
                        Log.d("State",state);
                    }
                }

            }
        } catch (Exception e) {
            Log.d("Error: ", e.getMessage());
        }


        if (!authCode.equals("")) {
            signButton.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        HelperClass helperClass = new HelperClass(this);
        if (!helperClass.getValue(this, FILE_PATH).isEmpty()) {
            helperClass.removeValue(this, FILE_PATH);
        }
    }


    public void SetDateAndTime() {
        Date currentTime = Calendar.getInstance().getTime();
        TextView date = (TextView) findViewById(R.id.text_datetime);
        SimpleDateFormat newDate = new SimpleDateFormat("dd/MM/yyyy, EEEE");
        String myDate = newDate.format(currentTime);
        date.setText(myDate);
    }

    public void Login(Button btn) {
        final String clientSecret = "paulssecret";
        final String clientID = "urian";
        final String redirectUri = "http://cloud-signer/";
        final String webServiceUrl = "https://msign-test.transsped.ro/csc/v0/";
        String url = webServiceUrl + "oauth2/authorize?response_type=token&client_id=" + clientID + "&client_secret=" + clientSecret + "&redirect_uri=" + redirectUri;

        final Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        final HelperClass myClass = new HelperClass(this);
        final Context context = this;

        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (myClass.InternetConnection() == false) {
                    myClass.AlertDialogBuilder("You must enable Internet Connection to be able to sign documents!",
                            context, "Internet Error!");
                } else {
                    startActivity(intent);
                }
            }
        });
    }
}
