package com.example.android.cloudsigner;

import android.content.Context;
import android.content.Intent;
import android.icu.text.SimpleDateFormat;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutionException;

import javax.net.ssl.HttpsURLConnection;

import SecureBlackbox.Base.SBUtils;
import SecureBlackbox.PDF.TElPDFDocument;
import SecureBlackbox.PDF.TElPDFSignature;
import SecureBlackbox.PKIPDF.TElPDFAdvancedPublicKeySecurityHandler;

public class MainActivity extends AppCompatActivity {
    public static final String FILE_PATH = "Path";
    private static final String AUTH_CODE = "AuthCode";
    private static final String BASE_URL = "https://msign-test.transsped.ro/csc/v0/";
    private String authCode = "";
    private String authToken = "";
    private final String clientSecret = "paulssecret";
    private final String clientID = "urian";
    private final String redirectUri = "http://cloud-signer/";
    private Button loadInfoButton;
    private Button viewButton;
    private Button authorizeButton;
    private Button certificatesButton;
    private Button certInfoButton;
    private Spinner certSpinner;
    private Button sendOtpButton;
    private Button getSadButton;
    private Button signButton;
    private EditText tanCode;
    private EditText signPasswd;
    private boolean isPdf = false;
    private ArrayList<String> credentialIds;
    private ArrayList<String> certInfo;
    private int certNumber = 0;
    private int selectedCertificateIndex = 0;
    private TElPDFAdvancedPublicKeySecurityHandler tElPDFAdvancedPublicKeySecurityHandler = null;
    private TElPDFDocument m_CurrDoc = null;

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
        loadInfoButton = (Button) findViewById(R.id.button_loadInfo);
        viewButton = (Button) findViewById(R.id.button_view);
        authorizeButton = (Button) findViewById(R.id.button_authorize);
        certificatesButton = (Button) findViewById(R.id.certificatesBtn);
        certInfoButton = (Button) findViewById(R.id.selectCertBtn);
        certSpinner = (Spinner) findViewById(R.id.certList);
        sendOtpButton = (Button) findViewById(R.id.sendOtpBtn);
        getSadButton = (Button) findViewById(R.id.getSadBtn);
        tanCode = (EditText) findViewById(R.id.enterOtp);
        signPasswd = (EditText) findViewById(R.id.signPasswd);
        signButton = (Button) findViewById(R.id.signButton);
        //1. Obtain auth_code.
        Login(authorizeButton);
    }

    //1. Save pdf path to shared preferences.
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

    //2. If auth_code, obtain auth_token.
    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = getIntent();
        String scheme = intent.getScheme();
        try {
            if (scheme.equals("content") || isPdf) {
                //viewButton.setVisibility(View.VISIBLE);
            }
            if (scheme.equals("http")) {
                loadInfoButton.setVisibility(View.VISIBLE);
                viewButton.setVisibility(View.VISIBLE);
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

                        if (authToken.isEmpty()) {
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
            loadInfoButton.setVisibility(View.VISIBLE);
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

    //3. If auth_token, obtain Credentials IDS.
    public void getCredentialIds(View view) {
        Context context = this;
        HelperClass helperClass = new HelperClass(this);
        Intent intent = new Intent(this, ViewPDFActivity.class);
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
        }
    }

    public void getInfo(View view) {
        certInfo = new ArrayList<String>();
        if (credentialIds.size() > 0) {
            for (int i = 0; i < credentialIds.size(); i++) {
                Log.d("InFunction", credentialIds.get(i));
                GetCertificates getCertificates = new GetCertificates();
                String url = BASE_URL + "credentials/info";
                certInfoButton.setVisibility(View.VISIBLE);
                getCertificates.execute(url, credentialIds.get(i));
            }
        }
    }

    public void setCertInfo(View view) {
        certSpinner.setVisibility(View.VISIBLE);
        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, certInfo);
        Log.d("Adapter", certInfo.get(0));
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        certSpinner.setAdapter(arrayAdapter);
        sendOtpButton.setVisibility(View.VISIBLE);
    }


    public void SetDateAndTime() {
        Date currentTime = Calendar.getInstance().getTime();
        TextView date = (TextView) findViewById(R.id.text_datetime);
        SimpleDateFormat newDate = new SimpleDateFormat("dd/MM/yyyy, EEEE");
        String myDate = newDate.format(currentTime);
        date.setText(myDate);
    }

    public void viewPdf(View view) {
        Intent intent = new Intent(MainActivity.this, ViewPDFActivity.class);
        startActivity(intent);
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

    public void sendOTP(View view) {
        String selectedCertificate = certSpinner.getSelectedItem().toString();
        int index = (int) certSpinner.getSelectedItemId();
        selectedCertificateIndex = index;
        Log.d("Selected", selectedCertificate);
        Log.d("SelectedID", Long.toString(index));
        getSadButton.setVisibility(View.VISIBLE);
        String url = BASE_URL + "credentials/sendOTP";
        tanCode.setVisibility(View.VISIBLE);
        signPasswd.setVisibility(View.VISIBLE);
        SendOtp sendOtp = new SendOtp();
        Log.d("OTP-credentialID", credentialIds.get(selectedCertificateIndex));
        sendOtp.execute(url, credentialIds.get(selectedCertificateIndex));
    }

    public String GetSadResponse(String hash, String otpCode, String signPassword) {
        String sadValue = "";
        String response = "";
        String url = BASE_URL + "credentials/authorize";
        SadClass sadClass = new SadClass();
        try {
            response = sadClass.execute(url, credentialIds.get(selectedCertificateIndex), hash, signPassword, otpCode).get();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (!response.isEmpty()) {
            StringTokenizer stringTokenizer = new StringTokenizer(response, "\":,");
            while (stringTokenizer.hasMoreTokens()) {
                String token = stringTokenizer.nextToken();
                if (token.equals("SAD")) {
                    sadValue = stringTokenizer.nextToken();
                }
            }
        }
        return sadValue;
    }

    public String GetSignature(String hash, String sadResponse) {
        String response = "";
        String url = BASE_URL + "signatures/signHash";
        SignClass signClass = new SignClass();
        try {
            response = signClass.execute(url, credentialIds.get(selectedCertificateIndex), hash, sadResponse).get();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        String signatureString = "";
        if (!response.isEmpty()) {
            Log.d("Response from server", response);
            StringTokenizer stringTokenizer = new StringTokenizer(response, ":\"{}[]");
            String line = "";
            while (stringTokenizer.hasMoreTokens()) {
                line = stringTokenizer.nextToken();
                if (line.equals("signatures")) {
                    signatureString = stringTokenizer.nextToken();
                }
            }
        }
        if (signatureString.isEmpty()) {
            Log.d("Signature", signatureString);
        }
        return signatureString;
    }

    public void GetSad(View view) {
        /*String otpCode = tanCode.getText().toString();
        String signPassword = signPasswd.getText().toString();
        Log.d("Tan Code", otpCode);
        Log.d("Sign Password", signPassword);
        SadClass sadClass = new SadClass();
        String response = "";
        String hash = "";
        String url = BASE_URL + "credentials/authorize";
        try {
            byte[] bytes = ("text to hash").getBytes("UTF-8");
            MessageDigest mHash = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = mHash.digest(bytes);
            hash = Base64.encodeToString(encodedHash, Base64.NO_WRAP);
            Log.d("hash", hash);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        String hashTest = "E3z8VQoHVdzWBfdlxmLEnPVIgYE9ZZkM+XWz6QNK+Ls=";
        Log.d("SAD-credentialID", credentialIds.get(selectedCertificateIndex));
        String sadValue = "";
        try {
            sadValue = GetSadResponse(hashTest, otpCode, signPassword);
        } catch (Exception e) {
            Log.d("Error", e.getMessage());
        }

        Log.d("SAD-value", sadValue);*/
        signButton.setVisibility(View.VISIBLE);
/*        tanCode.setText("");
        signPasswd.setText("");*/
    }


    void onRemoteSignHandler(Object sender, byte[] Hash, byte[] SignedHash) {
        String otpCode = "";
        String signPassword = "";
        String sadValue = "";
        //byte[] to base64
        String docHash = "";
        //base64 to byte[]
        String respHash = "";

        if (!tanCode.getText().toString().isEmpty()) {
            otpCode = tanCode.getText().toString();
        }
        if (!signPasswd.getText().toString().isEmpty()) {
            signPassword = signPasswd.getText().toString();
        }
        if (!signPassword.isEmpty() && !otpCode.isEmpty()) {
            //get sad for hash
            sadValue = GetSadResponse(docHash, otpCode, signPassword);
        }


        //sign hash
    }

    public void SignDocument(View view) {
        TElPDFSignature tElPDFSignature;
        SBUtils.setLicenseKey("914800F8C906204E26B3879514D6A459D6C317817E6D68EFB2B70B6A38221B534442125967" +
                "4E353F6B3BA7F405A895CE1B9F1B6A27F119474E37F2CAA0F325DD9C1C2E9B7D064AA997C23B7A092CA12CB14" +
                "EC8E82D221A87566A13A50E4C51BDFDE66AD289A1F910E456E969FBA03674EE44E1822379C01FF2861A652FF58" +
                "7940634F6365C818A123775BAA414C3BBFF6940655E7D3F5C30551F850AACCF88AACCB481A51A792A10BCED386F" +
                "F7CF422F50DDED61B1285139B9DC34719BF4F5F81ACF2DE0649923898CE2DAAE313C385A2A7B6388EE2A73CEC17" +
                "30C5021FEB2C65EF65D3BB10FE1B92FE4912E333647324E5DC68344AA26BDCF4A65EB365F461E");
        String filePath = "";
        HelperClass helperClass = new HelperClass(this);
        filePath = helperClass.getValue(this, FILE_PATH);
        Log.d("Sign_FilePath", filePath);

        //test
        String otpCode = "";
        String signPassword = "";
        String sadValue = "";
        //byte[] to base64
        String docHash = "E3z8VQoHVdzWBfdlxmLEnPVIgYE9ZZkM+XWz6QNK+Ls=";
        //base64 to byte[]
        String respHash = "";

        if (!tanCode.getText().toString().isEmpty()) {
            otpCode = tanCode.getText().toString();
        }
        if (!signPasswd.getText().toString().isEmpty()) {
            signPassword = signPasswd.getText().toString();
        }
        if (!signPassword.isEmpty() && !otpCode.isEmpty()) {
            //get sad for hash
            sadValue = GetSadResponse(docHash, otpCode, signPassword);
        }
        if (!sadValue.isEmpty()) {
            try {
                respHash = GetSignature(docHash, sadValue);
            } catch (Exception e) {
                Log.d("Error", e.getMessage());
            }
        }

    }


    private class SignClass extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls) {
            String response = "";
            JSONObject jsonObject = new JSONObject();
            JSONArray hashesArray = new JSONArray();
            try {
                hashesArray.put(urls[2]);
            } catch (Exception e) {
                Log.d("Error", e.getMessage());
            }
            try {
                jsonObject.accumulate("credentialID", urls[1]);
                jsonObject.accumulate("signAlgo", "1.2.840.113549.1.1.11");
                jsonObject.accumulate("hashAlgo", "2.16.840.1.101.3.4.2.1");
                jsonObject.accumulate("signAlgoParams", "");
                jsonObject.accumulate("SAD", urls[3]);
                jsonObject.accumulate("hash", hashesArray);
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

                Log.i("Conn_status_Sign", String.valueOf(conn.getResponseCode()));
                Log.i("Conn_message_Sign", conn.getResponseMessage());

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
                Log.d("SAD", sb.toString());
                response = sb.toString();
            } catch (Exception e) {
                Log.d("Error", e.getMessage());
            }
            return response;
        }

        @Override
        protected void onPostExecute(String result) {

        }
    }

    private class SadClass extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls) {

            String response = "";
            JSONObject jsonObject = new JSONObject();
            JSONArray jsonArray = new JSONArray();
            try {
                jsonArray.put(urls[2]);
            } catch (Exception e) {
                Log.d("Error", e.getMessage());
            }
            try {
                jsonObject.accumulate("credentialID", urls[1]);
                jsonObject.accumulate("numSignatures", "1");
                jsonObject.put("hash", jsonArray);
                jsonObject.accumulate("PIN", urls[3]);
                jsonObject.accumulate("OTP", urls[4]);
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

                Log.i("Conn_status_SAD", String.valueOf(conn.getResponseCode()));
                Log.i("Conn_message_SAD", conn.getResponseMessage());

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
                Log.d("SAD", sb.toString());
                response = sb.toString();
            } catch (Exception e) {
                Log.d("Error", e.getMessage());
            }
            return response;
        }

        @Override
        protected void onPostExecute(String result) {
            Log.d("Result-PostExe", result);

        }
    }

    //SendOTP class
    private class SendOtp extends AsyncTask<String, Void, Boolean> {
        @Override
        protected Boolean doInBackground(String... urls) {
            Boolean response = false;
            JSONObject jsonObject = new JSONObject();
            try {
                jsonObject.accumulate("credentialID", urls[1]);
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
                    response = true;
                    BufferedReader br = new BufferedReader(new InputStreamReader(
                            conn.getInputStream(), "utf-8"));
                    String line = null;
                    while ((line = br.readLine()) != null) {
                        sb.append(line + "\n");
                    }
                    br.close();
                }

                Log.d("OTP", sb.toString());
            } catch (Exception e) {
                Log.d("Error", e.getMessage());
            }
            return response;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            Log.d("Sent OTP", result.toString());
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
                Log.d("certInfo[certNumber]", certInfo.get(certInfo.size() - 1));
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
            try {
                StringTokenizer stringTokenizer = new StringTokenizer(result, ":{}[],\"");
                String token = "";
                while (stringTokenizer.hasMoreTokens()) {
                    token = stringTokenizer.nextToken();
                    if (token.equals("credentialIDs")) {
                        while (stringTokenizer.hasMoreTokens()) {
                            String certToken = stringTokenizer.nextToken();
                            if (!certToken.equals("\n")) {
                                credentialIds.add(certToken);
                            }
                        }
                    } else {
                        stringTokenizer.nextToken();
                    }
                }

            } catch (Exception e) {
                Log.d("Error", e.getMessage());
            }
        }
    }
}
