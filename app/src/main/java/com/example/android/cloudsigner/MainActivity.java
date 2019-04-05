package com.example.android.cloudsigner;

import android.content.Context;
import android.content.Intent;
import android.icu.text.SimpleDateFormat;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.v4.graphics.PathUtils;
import android.support.v4.provider.DocumentFile;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;

import javax.net.ssl.HttpsURLConnection;

public class MainActivity extends AppCompatActivity {
    public static final String FILE_PATH = "Path";
    private static final String AUTH_CODE = "AuthCode";
    private static final String BASE_URL = "https://msign-test.transsped.ro/csc/v0/";
    private String authCode = "";
    private String authToken = "";
    private final String clientSecret = "paulssecret";
    private final String clientID = "urian";
    private final String redirectUri = "http://cloud-signer/";
    private Button signButton;
    private Button viewButton;
    private Button authorizeButton;
    private Button certificatesButton;
    private Button certInfoButton;
    private Spinner certSpinner;
    private Button sendOtpButton;
    private Button getSadButton;
    private boolean isPdf = false;
    private ArrayList<String> credentialIds;
    private ArrayList<String> certInfo;
    private int certNumber = 0;


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
        signButton = (Button) findViewById(R.id.button_sign);
        viewButton = (Button) findViewById(R.id.button_view);
        authorizeButton = (Button) findViewById(R.id.button_authorize);
        certificatesButton = (Button) findViewById(R.id.certificatesBtn);
        certInfoButton = (Button) findViewById(R.id.selectCertBtn);
        certSpinner=(Spinner)findViewById(R.id.certList);
        sendOtpButton=(Button)findViewById(R.id.sendOtpBtn);
        getSadButton=(Button)findViewById(R.id.getSadBtn);
        Login(authorizeButton);
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
                    String state = stringTokenizer.nextToken();
                    if (code != null) {
                        authCode = code;
                        Log.d("Code", code);
                        Log.d("Redirect", link);
                        Log.d("State", state);
                        authorizeButton.setEnabled(false);

                        String tokenUri = BASE_URL + "oauth2/token";
                        Log.d("Location", "main");

                        if(authToken.isEmpty()) {
                            GetAuthToken getAuthToken = new GetAuthToken();
                            getAuthToken.execute(tokenUri);
                        }
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

    public void getCredentialIds(View view) {
        Context context = this;
        HelperClass helperClass = new HelperClass(this);
        Intent intent = new Intent(this, OTPpopUp.class);
        if (helperClass.InternetConnection() == false) {
            helperClass.AlertDialogBuilder("You must enable Internet Connection to be able to sign documents!",
                    context, "Internet Error!");
        } else {
            if (!authCode.isEmpty()) {
                if (!authToken.isEmpty()) {
                    Log.d("Access", "message");
                    Log.d("we_are", "here");
                    Log.d("Auth Token", authToken);
                    String url = BASE_URL + "credentials/list";
                    certificatesButton.setVisibility(View.VISIBLE);
                    credentialIds = new ArrayList<String>();
                    GetCredentialIds getCredentialIds = new GetCredentialIds();
                    getCredentialIds.execute(url);
                }
            }
            //startActivity(intent);
        }
    }

    public void getInfo(View view) {
        //TODO: aici o sa trebuiasca sa apelez functia de getInfo pentru fiecare ID din lista de credentialIds.
        if (credentialIds.size() > 0) {
            Log.d("InFunction", credentialIds.get(0));
            GetCertificates getCertificates = new GetCertificates();
            String url = BASE_URL + "credentials/info";
            certInfo = new ArrayList<String>();
            certInfoButton.setVisibility(View.VISIBLE);
            getCertificates.execute(url, credentialIds.get(0));
        }
    }

    public void setCertInfo(View view) {
        certSpinner.setVisibility(View.VISIBLE);
        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, certInfo);
        Log.d("Adapter",certInfo.get(0));
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        certSpinner.setAdapter(arrayAdapter);
        sendOtpButton.setVisibility(View.VISIBLE);
    }

    public void sendOTP(View view){
        String selectedCertificate=certSpinner.getSelectedItem().toString();
        Log.d("Selected",selectedCertificate);

        getSadButton.setVisibility(View.VISIBLE);
    }

    public void SetDateAndTime() {
        Date currentTime = Calendar.getInstance().getTime();
        TextView date = (TextView) findViewById(R.id.text_datetime);
        SimpleDateFormat newDate = new SimpleDateFormat("dd/MM/yyyy, EEEE");
        String myDate = newDate.format(currentTime);
        date.setText(myDate);
    }

    public void Login(Button btn) {
        final String url = BASE_URL + "oauth2/authorize?response_type=token&client_id=" + clientID + "&client_secret=" + clientSecret + "&redirect_uri=" + redirectUri;
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

    //SendOTP class
    private class SendOtp extends AsyncTask<String,Void,Boolean>
    {
        @Override
        protected Boolean doInBackground(String... urls) {
            Boolean response=false;
            JSONObject jsonObject=new JSONObject();
            try{
                jsonObject.accumulate("","");
            } catch (JSONException e) {
                e.printStackTrace();
            }

            return response;
        }
        @Override
        protected void onPostExecute(Boolean result){

        }

    }

    //GetInfo class -- CredentialInfo.
    private class GetCertificates extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls) {
            String response = "";
            JSONObject jsonObject = new JSONObject();
            try {
                jsonObject.accumulate("credentialID", urls[1]);
                jsonObject.accumulate("certInfo", "true");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            try {
                URL url = new URL(urls[0]);
                HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.addRequestProperty("AUTHORIZATION", "Bearer " + authToken);
                conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);
                conn.setDoInput(true);

                DataOutputStream os = new DataOutputStream(conn.getOutputStream());
                os.writeBytes(jsonObject.toString());
                os.flush();
                os.close();

                Log.i("STATUS_Certificates", String.valueOf(conn.getResponseCode()));
                Log.i("MSG_Certificates", conn.getResponseMessage());

                StringBuilder sb = new StringBuilder();
                int httpsResult = conn.getResponseCode();
                if (httpsResult == HttpsURLConnection.HTTP_OK) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(
                            conn.getInputStream(), "utf-8"));
                    String line = null;
                    while ((line = br.readLine()) != null) {
                        sb.append(line + "\n");
                    }
                    br.close();
                }
                Log.d("GetCertificates", sb.toString());
                response = sb.toString();
            } catch (Exception e) {
                Log.d("Error", e.getMessage());
            }
            return response;
        }

        @Override
        protected void onPostExecute(String result) {
            Log.d("Location", "GetCertificates");
            Log.d("Result", result);
            StringTokenizer stringTokenizer = new StringTokenizer(result, ",:{}\"");
            int tokens = stringTokenizer.countTokens();
            Log.d("Count", Integer.toString(tokens));

            String token = "";
            String alias = "";
            String keyStatus = "";
            String certStatus = "";
            while (stringTokenizer.hasMoreTokens()) {
                token = stringTokenizer.nextToken();
                if (token.equals("Card alias")) {
                    alias = stringTokenizer.nextToken();
                } else if (token.equals("key")) {
                    stringTokenizer.nextToken();
                    keyStatus = stringTokenizer.nextToken();
                } else if (token.equals("cert")) {
                    stringTokenizer.nextToken();
                    certStatus = stringTokenizer.nextToken();
                }
            }
            Log.d("Alias", alias + " " + keyStatus + " " + certStatus);
            if (keyStatus.equals("enabled") && certStatus.equals("valid")) {
                certInfo.add(alias);
                Log.d("certInfo[certNumber]", certInfo.get(0));
                Log.d("cert number", Integer.toString(certInfo.size()));
            }
        }
    }

    //AuthToken class - OAuth2_Token
    private class GetAuthToken extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls) {
            JSONObject jsonObject = new JSONObject();
            try {
                jsonObject.accumulate("grant_type", "authorization_code");
                jsonObject.accumulate("code", authCode);
                jsonObject.accumulate("client_id", clientID);
                jsonObject.accumulate("client_secret", clientSecret);
                jsonObject.accumulate("redirect_uri", redirectUri);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            try {
                URL url = new URL(urls[0]);
                HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);
                conn.setDoInput(true);

                Log.i("JSON", jsonObject.toString());

                DataOutputStream os = new DataOutputStream(conn.getOutputStream());
                os.writeBytes(jsonObject.toString());
                os.flush();
                os.close();

                Log.i("STATUS", String.valueOf(conn.getResponseCode()));
                Log.i("MSG", conn.getResponseMessage());

                StringBuilder sb = new StringBuilder();
                int httpsResult = conn.getResponseCode();
                if (httpsResult == HttpsURLConnection.HTTP_OK) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(
                            conn.getInputStream(), "utf-8"));
                    String line = null;
                    while ((line = br.readLine()) != null) {
                        sb.append(line + "\n");
                    }
                    br.close();
                }
                Log.d("Response", sb.toString());
                StringTokenizer stringTokenizer = new StringTokenizer(sb.toString(), ":,\"");
                String first = stringTokenizer.nextToken();
                String second = stringTokenizer.nextToken();
                String myToken = stringTokenizer.nextToken();
                Log.d("First", first);
                Log.d("Second", second);
                Log.d("Token", myToken);

                String response = new String(myToken);
                conn.disconnect();
                return response;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return "Post failed";
        }

        @Override
        protected void onPostExecute(String result) {
            Log.d("Location", "GetAuthCode");
            Log.d("Result", result);
            authToken = result;
        }
    }

    //CredentialList class
    private class GetCredentialIds extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls) {
            JSONObject jsonObject = new JSONObject();
            String response = "";
            try {
                jsonObject.accumulate("", "");
            } catch (JSONException e) {
                e.printStackTrace();
            }

            try {
                URL url = new URL(urls[0]);
                HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.addRequestProperty("AUTHORIZATION", "Bearer " + authToken);
                conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);
                conn.setDoInput(true);

                DataOutputStream os = new DataOutputStream(conn.getOutputStream());
                os.writeBytes(jsonObject.toString());
                os.flush();
                os.close();

                Log.i("STATUS_GetCredentials", String.valueOf(conn.getResponseCode()));
                Log.i("MSG_GetCredentials", conn.getResponseMessage());

                StringBuilder sb = new StringBuilder();
                int httpsResult = conn.getResponseCode();
                if (httpsResult == HttpsURLConnection.HTTP_OK) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(
                            conn.getInputStream(), "utf-8"));
                    String line = null;
                    while ((line = br.readLine()) != null) {
                        sb.append(line + "\n");
                    }
                    br.close();
                }
                Log.d("GetCredentials", sb.toString());
                response = sb.toString();
            } catch (Exception e) {
                Log.d("Error", e.getMessage());
            }
            return response;
        }

        @Override
        protected void onPostExecute(String result) {
            Log.d("Location", "GetCredentialIds");
            Log.d("Result_auth", result);
            //TODO: get result as String[] - pentru mai multe certificate...si salvare credentiale intr-un dictionar - nr_crt + value;
            try {
                StringTokenizer stringTokenizer = new StringTokenizer(result, ":{}[],\"");
                String first = stringTokenizer.nextToken();
                String second = stringTokenizer.nextToken();
                String third = stringTokenizer.nextToken();
                String fourth = stringTokenizer.nextToken();
                Log.d("Result_first", first);
                Log.d("Result_second", second);
                Log.d("Result_third", third);
                Log.d("Result_fourth", fourth);
                //credentialIds = new String(fourth);
                credentialIds.add(fourth);
            } catch (Exception e) {
                Log.d("Error", e.getMessage());
            }
        }
    }
}
