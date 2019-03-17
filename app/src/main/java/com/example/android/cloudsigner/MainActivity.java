package com.example.android.cloudsigner;

import android.content.Context;
import android.content.Intent;
import android.icu.text.SimpleDateFormat;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.graphics.PathUtils;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.util.Calendar;
import java.util.Date;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        SetDateAndTime();
        Intent intent = getIntent();

        try {
            String fileName = intent.getData().getPath();
            Uri uri = intent.getData();
            String str = intent.getDataString();

            InputStream inStream = getContentResolver().openInputStream(uri);

            Log.d("Uri", str);
            Log.d("Path", fileName);
        } catch (Exception e) {
            Log.d("Exception:", e.getMessage());
        }


        Login();
    }

    public void SetDateAndTime() {
        Date currentTime = Calendar.getInstance().getTime();
        TextView date = (TextView) findViewById(R.id.text_datetime);
        SimpleDateFormat newDate = new SimpleDateFormat("dd/MM/yyyy, EEEE");
        String myDate = newDate.format(currentTime);
        date.setText(myDate);
    }

    /* public void Login() {
         Button btnLogin = (Button) findViewById(R.id.button_login);
         final EditText emailText = (EditText) findViewById(R.id.edit_phone);
         final EditText passwordText = (EditText) findViewById(R.id.edit_password);
         final HelperClass myClass = new HelperClass(this);
         final Context context = this;
         btnLogin.setOnClickListener(new View.OnClickListener() {
             @Override
             public void onClick(View v) {
                 emailText.setText("");
                 passwordText.setText("");
                 if (myClass.InternetConnection() == false) {
                     myClass.AlertDialogBuilder("You must enable Internet Connection to be able to sign documents!",
                             context, "Internet Error!");
                 } else {
                     startActivity(new Intent(MainActivity.this, OTPpopUp.class));
                 }
             }
         });
     }*/
    public void Login() {
        Button btnLogin = (Button) findViewById(R.id.button_authorize);

        final String clientSecret = "paulssecret";
        final String clientID="urian";
        final String redirectUri = "http://cloud-signer/";
        final String webServiceUrl = "https://msign-test.transsped.ro/csc/v0/";
        String url = webServiceUrl + "oauth2/authorize?response_type=token&client_id="+clientID+"&client_secret=" + clientSecret + "&redirect_uri=" + redirectUri;

        final Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        final HelperClass myClass = new HelperClass(this);
        final Context context = this;

        btnLogin.setOnClickListener(new View.OnClickListener() {
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
