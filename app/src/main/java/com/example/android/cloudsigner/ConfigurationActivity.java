package com.example.android.cloudsigner;

import android.content.Context;
import android.content.Intent;
import android.icu.text.SimpleDateFormat;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Array;
import java.net.URL;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.StringTokenizer;

import javax.net.ssl.HttpsURLConnection;

import SecureBlackbox.Base.SBPKICommon;

public class ConfigurationActivity extends AppCompatActivity {

    private Spinner certSpinner;
    private CheckBox checkBox;
    private static final String SAVED_CRT = "Saved_crt";
    private static final String SAVED_ALIAS = "Saved_alias";
    private static final String SAVED_PATH = "Crt_path";
    private static final int LOCAL_CERTIFICATE = 0;
    private static final int GLOBAL_CERTIFICATE = 1;
    private static final String BASE_URL = "https://msign-test.transsped.ro/csc/v0/";
    private int selectedAliasIndex = 0;
    private String selectedAliasCrt = "";
    private String selectedCertificatePath = "";
    private String selectedCertificateID = "";
    private String authToken = "";
    private ArrayList<String> credentialIdsList;
    private ArrayList<CredentialsInfo> credentialsInfoArrayList;
    private ArrayList<Cert> certificates = new ArrayList<>();
    private ArrayList<String> intentDetails;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_configuration);
        certSpinner = findViewById(R.id.certList11);
        checkBox = findViewById(R.id.checkBox1);
        SetDateAndTime();
        try {
            Intent intent = getIntent();
            authToken = intent.getStringExtra("AuthToken");
            credentialIdsList = new ArrayList<>();
            GetCredentialIds getCredentialIds = new GetCredentialIds();
            String url = BASE_URL + "credentials/list";
            credentialsInfoArrayList = new ArrayList<>();
            if (!authToken.isEmpty()) {
                try {
                    credentialIdsList = getCredentialIds.execute(url, authToken).get();
                    if (credentialIdsList.size() > 0) {
                        try {
                            String infoUrl = BASE_URL + "credentials/info";
                            for (int i = 0; i < credentialIdsList.size(); i++) {
                                try {
                                    GetCertInfo getCertInfo = new GetCertInfo();
                                    CredentialsInfo crdInfo = new CredentialsInfo();
                                    crdInfo = getCertInfo.execute(infoUrl, credentialIdsList.get(i), authToken).get();
                                    if (crdInfo != null) {
                                        credentialsInfoArrayList.add(crdInfo);
                                    }
                                } catch (Exception e) {
                                    Log.d("Error", e.getMessage());
                                }
                            }

                        } catch (Exception e) {

                        }
                    }
                } catch (Exception e) {
                    Log.d("Error", e.getMessage());
                }
            }
            Log.d("AuthToken", authToken);

        } catch (Exception e) {
            Log.d("Error", e.getMessage());
        }
        ArrayList<String> aliasList = new ArrayList<>();
        if (credentialsInfoArrayList.size() > 0) {
            for (int i = 0; i < credentialsInfoArrayList.size(); i++) {
                String description = credentialsInfoArrayList.get(i).description;
                String alias = "";
                StringTokenizer stringTokenizer = new StringTokenizer(description, ":");
                while (stringTokenizer.hasMoreTokens()) {
                    String token = stringTokenizer.nextToken();
                    if (token.equals("Card alias")) {
                        alias = stringTokenizer.nextToken();
                    }
                    aliasList.add(alias);
                }
                certificates.add(credentialsInfoArrayList.get(i).cert);
            }
        }
        setCertInfo(aliasList, credentialIdsList);
    }

    public void setAsDefault(View view) {
        Log.d("Def", "Default");
        if (checkBox.isChecked()) {
            HelperClass helperClass = new HelperClass(this);
            try {
                helperClass.saveValue(this, SAVED_CRT, selectedCertificateID);
                helperClass.saveValue(this, SAVED_ALIAS, selectedAliasCrt);
                helperClass.saveValue(this, SAVED_PATH, selectedCertificatePath);
                Log.d("Selected Certificate", selectedCertificateID + " " + selectedAliasCrt + " " + selectedCertificatePath);
            } catch (Exception e) {
                Log.d("Error", e.getMessage());
            }
            Intent intent = new Intent();
            ArrayList<String> newArray = new ArrayList<>();
            newArray.add(selectedCertificateID);
            newArray.add(selectedCertificatePath);
            intent.putExtra("selectedDetails", newArray);
            setResult(GLOBAL_CERTIFICATE, intent);
            finish();
        } else {
            Intent intent = new Intent();
            intentDetails = new ArrayList<>();
            intentDetails.add(selectedCertificateID);
            intentDetails.add(selectedCertificatePath);
            intent.putExtra("selectedDetails", intentDetails);
            setResult(LOCAL_CERTIFICATE, intent);
            finish();
        }
    }

    public void SetDateAndTime() {
        Date currentTime = Calendar.getInstance().getTime();
        TextView date = (TextView) findViewById(R.id.time);
        SimpleDateFormat newDate = new SimpleDateFormat("dd/MM/yyyy, EEEE");
        String myDate = newDate.format(currentTime);
        date.setText(myDate);
    }

    public void cancelDefCertificate(View view) {
        HelperClass helperClass = new HelperClass(this);
        String certificateID="";
        try{
            certificateID = helperClass.getValue(this, SAVED_CRT);
        }catch (Exception e){
            Log.d("Error",e.getMessage());
        }
        final String url = BASE_URL + "credentials/sendOTP";
        //resend otp if the user doesn't change the default certificate.
        if (!certificateID.equals("Not found!") && !certificateID.equals("")) {
            try {
                SendOtp sendOtp = new SendOtp();
                sendOtp.execute(url, certificateID, authToken);
                finish();
            } catch (Exception e) {
                Log.d("Error", e.getMessage());
            }
        }else {
            finish();
        }
    }

    private static long hexToLong(String hex) {
        return Long.parseLong(hex, 16);
    }

    public String loadCertificate(Cert crt, String alias) {
        String location = "";
        try {
            String serialNumber = crt.serialNumber;
            location = checkIfCertExists(serialNumber);
            if (location.isEmpty()) {
                location = writeCrtToFile(crt.certificates.get(0), alias);
            }
        } catch (Exception e) {
            Log.d("Error", e.getMessage());
        }
        return location;
    }

    public String writeCrtToFile(String crt, String crtFileName) {
        String path = Environment.getExternalStorageDirectory().toString() + "/Download/Private/";
        String location = "";
        try {
            location = path + crtFileName.replace(" ", "") + ".cer";
            FileOutputStream file = new FileOutputStream(new File(location));
            OutputStreamWriter writer = new OutputStreamWriter(file);
            writer.write(crt);
            writer.close();
            file.close();
            return location;
        } catch (Exception e) {
            Log.d("Error", e.getMessage());
        }
        return location;
    }

    private String checkIfCertExists(String serialNumber) {
        String path = Environment.getExternalStorageDirectory().toString() + "/Download/Private";
        Log.d("Files", "Path: " + path);
        File directory = new File(path);
        File[] files = directory.listFiles();
        Log.d("Files", "Size: " + files.length);
        for (int i = 0; i < files.length; i++) {
            Log.d("Files", "FileName:" + files[i].getName());
            String location = path + "/" + files[i].getName();
            try {
                File file = new File(location);
                InputStream is = new FileInputStream(file);
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                X509Certificate certificate = (X509Certificate) cf.generateCertificate(is);
                Log.d("Certificate", certificate.getSigAlgName());
                Log.d("SerialNumber", certificate.getSerialNumber().toString());

                if (Long.toString(hexToLong(serialNumber)).equals(certificate.getSerialNumber().toString())) {
                    return location;
                }

            } catch (Exception e) {
                Log.d("Error", e.getMessage());
            }
        }
        return "";
    }

    public void setCertInfo(final ArrayList<String> aliasList, final ArrayList<String> credIds) {
        final HelperClass myClass = new HelperClass(this);
        String certID = myClass.getValue(this, SAVED_CRT);
        String certificateAlias = myClass.getValue(this, SAVED_ALIAS);
        if ((!certificateAlias.equals("Not found!")) && (!certID.equals("Not found!"))) {
            aliasList.remove(certificateAlias);
            credIds.remove(certID);

            aliasList.add(0, certificateAlias);
            credIds.add(0, certID);
        }
        certSpinner.setVisibility(View.VISIBLE);
        final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(this, R.layout.spinner_item, aliasList);
        Log.d("Adapter", aliasList.get(0));
        arrayAdapter.setDropDownViewResource(R.layout.spinner_drop_down_items);
        certSpinner.setAdapter(arrayAdapter);
        certSpinner.setPrompt("Select your certificate!");
        selectedAliasIndex = 0;
        selectedAliasCrt = aliasList.get(selectedAliasIndex);
        selectedCertificateID = credIds.get(selectedAliasIndex);
        final Context context = this;
        certSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                int index = (int) certSpinner.getSelectedItemId();
                selectedAliasIndex = index;
                selectedAliasCrt = aliasList.get(selectedAliasIndex);
                selectedCertificateID = credIds.get(selectedAliasIndex);
                for (int j = 0; j < credentialsInfoArrayList.size(); j++) {
                    Cert crt = new Cert();
                    String descr = credentialsInfoArrayList.get(j).description;
                    String alias = "";
                    StringTokenizer stringTokenizer = new StringTokenizer(descr, ":");
                    while (stringTokenizer.hasMoreTokens()) {
                        String token = stringTokenizer.nextToken();
                        if (token.equals("Card alias")) {
                            alias = stringTokenizer.nextToken();
                        }
                    }
                    if (selectedAliasCrt.equals(alias)) {
                        String crtLoc = "";
                        crtLoc = loadCertificate(credentialsInfoArrayList.get(j).cert, selectedAliasCrt);
                        if (!crtLoc.isEmpty()) {
                            selectedCertificatePath = crtLoc;
                        }
                        Log.d("Loc", crtLoc);
                    }
                }
            }

            public void onNothingSelected(AdapterView<?> adapterView) {
                return;
            }
        });
    }

    //JSON Classes
    public class ErrorCode {
        String error;
        String errorDescription;
    }

    public class CredentialsIds {
        public ArrayList<String> credentialIDs;
        public String nextPageToken;
    }

    public class CredentialsInfo {
        public String description;
        public Key key;
        public Cert cert;
        public String authMode;
        public String SCAL;
    }

    public class Key {
        public String status;
        public ArrayList<String> algo;
        public int len;
        public String curve;
    }

    public class Cert {
        public String status;
        public ArrayList<String> certificates;
        public String issuerDN;
        public String serialNumber;
        public String subjectDN;
        public String validFrom;
        public String validTo;
    }

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
                conn.addRequestProperty("AUTHORIZATION", "Bearer " + urls[2]);
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

    private class GetCertInfo extends AsyncTask<String, Void, CredentialsInfo> {
        @Override
        protected CredentialsInfo doInBackground(String... urls) {
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
                conn.addRequestProperty("AUTHORIZATION", "Bearer " + urls[2]);
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
                    CredentialsInfo credInfo = new CredentialsInfo();
                    Gson gson = new Gson();
                    credInfo = gson.fromJson(sb.toString(), CredentialsInfo.class);
                    return credInfo;

                }
                //error case
                else {
                    BufferedReader br = new BufferedReader(new InputStreamReader(
                            conn.getErrorStream(), "utf-8"));
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
            return null;
        }

        @Override
        protected void onPostExecute(CredentialsInfo result) {

        }
    }

    //CredentialList class
    private class GetCredentialIds extends AsyncTask<String, Void, ArrayList<String>> {
        @Override
        protected ArrayList<String> doInBackground(String... urls) {
            JSONObject jsonObject = new JSONObject();
            ArrayList<String> credIds = new ArrayList<>();
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
                conn.addRequestProperty("AUTHORIZATION", "Bearer " + urls[1]);
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

                    Gson gson = new Gson();
                    CredentialsIds credentialsIds = gson.fromJson(sb.toString(), CredentialsIds.class);
                    return credentialsIds.credentialIDs;
                } else {
                    BufferedReader br = new BufferedReader(new InputStreamReader(
                            conn.getErrorStream(), "utf-8"));
                    String line = null;
                    while ((line = br.readLine()) != null) {
                        sb.append(line + "\n");
                    }
                    br.close();

                    Gson gson = new Gson();
                    ErrorCode errorCode = new ErrorCode();
                    errorCode = gson.fromJson(sb.toString(), ErrorCode.class);
                }
            } catch (Exception e) {
                Log.d("Error", e.getMessage());
            }
            return null;
        }

        @Override
        protected void onPostExecute(ArrayList<String> result) {
        }
    }

}
