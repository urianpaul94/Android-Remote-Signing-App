package com.example.android.cloudsigner;

import android.Manifest;
import android.app.Activity;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.icu.text.SimpleDateFormat;
import android.icu.util.TimeZone;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatCheckBox;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Base64;
import android.util.Log;
import android.util.Xml;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;

import org.freepascal.rtl.TObject;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.lang.Thread;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.sql.Time;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutionException;

import javax.net.ssl.HttpsURLConnection;

import SecureBlackbox.Base.*;
import SecureBlackbox.HTTPClient.*;
import SecureBlackbox.PDF.*;
import SecureBlackbox.PKI.TElCertificateRevocationListEx;
import SecureBlackbox.PKIPDF.*;

public class MainActivity extends AppCompatActivity {
    public static final String FILE_PATH = "Path";
    private static final String AUTH_CODE = "AuthCode";
    private static final int LOCAL_CERTIFICATE = 0;
    private static final int GLOBAL_CERTIFICATE = 1;
    private static final String BASE_URL = "https://msign-test.transsped.ro/csc/v0/";
    private String authCode = "";
    private String authToken = "";
    private final String clientSecret = "paulssecret";
    private final String clientID = "urian";
    private final String redirectUri = "http://cloud-signer/";
    private Button loadInfoButton;
    private Button viewButton;
    private Button authorizeButton;
    private Button viewSignature;
    private Button testBtn;
    private Spinner certSpinner;
    private Button signButton;
    private EditText tanCode;
    private EditText signPasswd;
    private ProgressBar progressBar;
    private AppCompatCheckBox checkBox;
    private boolean isPdf = false;
    private ArrayList<String> credentialIds;
    private ArrayList<String> certInfo;
    private ArrayList<String> serialNumbers;
    private int selectedCertificateIndex = 0;
    private String selectedCertificatePath = "";
    private boolean wrongCredentials = false;
    private TElX509Certificate signingCertificate;
    private int hashAlgorithm = 0;

    //if user selects 1 certificate to sign once
    private String oneTimeCertificate = "";
    private String oneTimeCertificatePath = "";
    //if user selects 1 certificate as default - read from shared preferences.
    private String savedCertificate = "";
    private String savedCertificatePath = "";
    private static final String SAVED_CRT = "Saved_crt";
    private static final String SAVED_ALIAS = "Saved_alias";
    private static final String SAVED_PATH = "Crt_path";
    private CertificateForSign certificateForSign = new CertificateForSign();


    //signature_
    private TElPDFDocument m_CurrDoc = null;
    private String m_CurrOrigFile = "";
    private String m_CurrTempFile = "";
    private TElFileStream m_CurrStream = null;

    TElPDFPublicKeyRevocationInfo m_DocRevInfo = new TElPDFPublicKeyRevocationInfo();
    TElPDFPublicKeyRevocationInfo m_LocalRevInfo = new TElPDFPublicKeyRevocationInfo();
    TElMemoryCertStorage m_TrustedCerts = new TElMemoryCertStorage();
    TElMemoryCRLStorage m_KnownCRLs = new TElMemoryCRLStorage();
    TElPDFAdvancedPublicKeySecurityHandler m_Handler = null;
    TElMemoryCertStorage m_CertStorage = new TElMemoryCertStorage();
    TElHTTPTSPClient m_TspClient = new TElHTTPTSPClient();
    TElHTTPSClient m_HttpClient = new TElHTTPSClient();
    TElStringList m_CertValidationLog = new TElStringList();
    TElX509Certificate m_cert = new TElX509Certificate();

    TElX509Certificate root_cert = new TElX509Certificate();
    TElX509Certificate intermediate_cert = new TElX509Certificate();
    TElCertificateRevocationListEx cert_crl = new TElCertificateRevocationListEx();

    String rootPath = "";
    String intermediatePath = "";
    String crlPath = "";
    public static final String pdu_type = "pdus";

    String stateMessage = "";

    PhoneStateListener phoneStateListener = new PhoneStateListener() {
        public void onServiceStateChanged(ServiceState serviceState) {
            Log.d("State", Integer.toString(serviceState.getState()));
            int state = serviceState.getState();
            switch (state) {
                case ServiceState.STATE_EMERGENCY_ONLY:
                    stateMessage = "Emergency call only!";
                case ServiceState.STATE_OUT_OF_SERVICE:
                    stateMessage = "Out of services!";
                case ServiceState.STATE_POWER_OFF:
                    stateMessage = "Airplane mode is on!";
                case ServiceState.STATE_IN_SERVICE:
                default: {
                    break;
                }
            }
        }

        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            Log.d("Signal", signalStrength.toString());
        }
    };

    //create broadcast receiver to auto complete tan code from sms.
    private BroadcastReceiver otpReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Get the SMS message.
            Bundle bundle = intent.getExtras();
            SmsMessage[] msgs;
            String format = bundle.getString("format");
            // Retrieve the SMS message received.
            Object[] pdus = (Object[]) bundle.get(pdu_type);
            if (pdus != null) {
                msgs = new SmsMessage[pdus.length];
                for (int i = 0; i < msgs.length; i++) {
                    msgs[i] = SmsMessage.createFromPdu((byte[]) pdus[i], format);
                    if (msgs[i].getDisplayOriginatingAddress().equals("1867")) {
                        int len = msgs[i].getMessageBody().length();
                        if (len == 58) {
                            String otp = "";
                            StringTokenizer str = new StringTokenizer(msgs[i].getDisplayMessageBody(), " :");
                            while (str.hasMoreTokens()) {
                                String line = str.nextToken();
                                if (line.equals("Tancode")) {
                                    otp = str.nextToken();
                                }
                            }
                            tanCode = findViewById(R.id.enterOtp);
                            tanCode.setText(otp);
                        }
                    }
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        SetDateAndTime();
        Intent intent = getIntent();
        if (intent == null) {
            finish();
        }
        //Secure BlackBox license key - 30 days.
        SBUtils.setLicenseKey("86F232FD1D12D863DC84C0DD0ECFD7D27B23911823DA6841C76C1DEDFCF49614AC2AC2ED6D13" +
                "899C643782FA30D770CE8371C3946972FA8ECEF602B701AB3C797BFD78C5F4B23E442350876779D0156A51A6535" +
                "5957226F421BC2B52C949604A5926B25A5B7E24BF5E517D518C43EEAB1F9898C0E03E42AE2B88A753B4C7841CB1" +
                "E85F9D761EAFADA99E62749915C2E8C823A71FA4B3E0B1F72BA164624540C4B17002A5C25F7D1F8E9B41701A04C" +
                "EC66FCFDB77843B309793D3A2EEB7AB2B9041F9D22791057E842FF565C1C95DA47A0ED5FFCC76556C54DDB7E34C" +
                "98B9C86EFCF5C554B759F139EBAF5D3C58B124A7A06DB8C279CAD835748B82A1A41FDFDA");
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
        tanCode = (EditText) findViewById(R.id.enterOtp);
        signPasswd = (EditText) findViewById(R.id.signPasswd);
        signButton = (Button) findViewById(R.id.signButton);
        progressBar = (ProgressBar) findViewById(R.id.progress_bar);
        viewSignature = (Button) findViewById(R.id.viewSignaturesBtn);
        checkBox = (AppCompatCheckBox) findViewById(R.id.checkBox);
        serialNumbers = new ArrayList<>();
        m_Handler = new TElPDFAdvancedPublicKeySecurityHandler();
        helperClass.verifyStoragePermissions(MainActivity.this);
        //1. Obtain auth_code.
        Login(authorizeButton);
    }

    //1. Save pdf path to shared preferences.
    @Override
    protected void onStop() {
        super.onStop();
        //unregister receiver for otp-sms
        try {
            tanCode.setText("");
            signPasswd.setText("");
        } catch (Exception e) {
            Log.d("Error", e.getMessage());
        }
        try {
            unregisterReceiver(otpReceiver);
            Log.d("Unregistered receiver", "true");
        } catch (Exception e) {
            Log.d("Error", e.getMessage());
        }
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
        //create intent filter for otp sms broadcast
        try {
            IntentFilter smsFilter = new IntentFilter();
            smsFilter.addAction("android.provider.Telephony.SMS_RECEIVED");
            registerReceiver(otpReceiver, smsFilter);
            Log.d("Registered receiver", "true");
        } catch (Exception e) {
            Log.d("Error", e.getMessage());
        }

        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    signPasswd.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                } else {
                    signPasswd.setTransformationMethod(PasswordTransformationMethod.getInstance());
                }
            }
        });
        String scheme = intent.getScheme();
        try {
            if (scheme.equals("content") || isPdf) {
                //viewButton.setVisibility(View.VISIBLE);
            }
            if (scheme.equals("http")) {
                viewButton.setVisibility(View.VISIBLE);
                authorizeButton.setVisibility(View.INVISIBLE);
                loadInfoButton.setVisibility(View.VISIBLE);
                //verify - if the document has been signed previously.
                try {
                    openDocument();
                    int idx = m_CurrDoc.addSignature();
                    if (idx > 0) {
                        viewSignature.setVisibility(View.VISIBLE);
                    }
                    CloseCurrentDocument(false);
                } catch (Exception e) {
                    Log.d("Error", e.getMessage());
                }
            }
        } catch (Exception e) {
            Log.d("Error:", e.getMessage());
        }

        //get auth Token.
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
                        if (authToken.isEmpty()) {
                            try {
                                GetAuthToken getAuthToken = new GetAuthToken();
                                authToken = getAuthToken.execute(tokenUri, authCode).get();
                                if (!authToken.isEmpty()) {
                                    HelperClass helperClass = new HelperClass(this);
                                    try {
                                        //read certificate/certificate's path from shared preferences for default certificate.
                                        savedCertificate = helperClass.getValue(this, SAVED_CRT);
                                        savedCertificatePath = helperClass.getValue(this, SAVED_PATH);
                                        boolean fileExists=false;
                                        if (!savedCertificate.isEmpty() && !savedCertificatePath.equals("Not found!") && !savedCertificate.equals("Not found!")) {
                                            try{
                                                File checkFile=new File(savedCertificatePath);
                                                if(checkFile.exists()){
                                                    fileExists=true;
                                                }
                                            }catch (Exception e){
                                                Log.d("Error",e.getMessage());
                                            }
                                            if(fileExists) {
                                                try {
                                                    //verify if certID belongs to the logged user.
                                                    GetCredentialIds getCredentialIds = new GetCredentialIds();
                                                    String urlCredIds = BASE_URL + "credentials/list";
                                                    boolean isSameUser = false;
                                                    try {
                                                        credentialIds = new ArrayList<>();
                                                        credentialIds = getCredentialIds.execute(urlCredIds, authToken).get();
                                                    } catch (Exception e) {
                                                        Log.d("Error", e.getMessage());
                                                    }
                                                    if (credentialIds.size() > 0) {
                                                        for (String credId : credentialIds) {
                                                            if (credId.equals(savedCertificate)) {
                                                                isSameUser = true;
                                                            }
                                                        }
                                                    }
                                                    // if is CredId belongs to the logged user, sendOtp!
                                                    if (isSameUser) {
                                                        //save default certificate to certificate for sign class.
                                                        certificateForSign.isOneTime = false;
                                                        certificateForSign.certificateID = savedCertificate;
                                                        certificateForSign.certificatePath = savedCertificatePath;

                                                        final String url = BASE_URL + "credentials/sendOTP";
                                                        SendOtp sendOtp = new SendOtp();
                                                        sendOtp.execute(url, savedCertificate, authToken);
                                                        tanCode.setVisibility(View.VISIBLE);
                                                        signPasswd.setVisibility(View.VISIBLE);
                                                        checkBox.setVisibility(View.VISIBLE);
                                                        signButton.setVisibility(View.VISIBLE);
                                                    }
                                                } catch (Exception e) {
                                                    Log.d("Error", e.getMessage());
                                                }
                                            }

                                        }
                                    } catch (Exception e) {
                                        Log.d("Error", e.getMessage());
                                    }
                                }
                            } catch (Exception e) {
                                Log.d("Error", e.getMessage());
                            }
                        }
                    }
                }

            }
        } catch (Exception e) {
            Log.d("Error: ", e.getMessage());
        }
        if (!authCode.equals("")) {
            loadInfoButton.setVisibility(View.VISIBLE);
            //testBtn.setVisibility(View.VISIBLE);
        }
    }

    //communicates with Configuration Activity. Gets certificateID, certificatePath when user selects
    //certificate but does not use it as default.
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        final String url = BASE_URL + "credentials/sendOTP";
        if (resultCode == LOCAL_CERTIFICATE) {
            try {
                ArrayList<String> details = new ArrayList<>();
                details = data.getStringArrayListExtra("selectedDetails");
                oneTimeCertificate = details.get(0);
                oneTimeCertificatePath = details.get(1);

                if (!oneTimeCertificate.isEmpty()) {
                    //save one time certificate to certificate for sign class.
                    certificateForSign.isOneTime = true;
                    certificateForSign.certificateID = oneTimeCertificate;
                    certificateForSign.certificatePath = oneTimeCertificatePath;

                    SendOtp sendOtp = new SendOtp();
                    sendOtp.execute(url, oneTimeCertificate, authToken);
                    signButton.setVisibility(View.VISIBLE);
                    tanCode.setVisibility(View.VISIBLE);
                    signPasswd.setVisibility(View.VISIBLE);
                    checkBox.setVisibility(View.VISIBLE);
                }
            } catch (Exception e) {
                Log.d("Error", e.getMessage());
            }
        } else if (resultCode == GLOBAL_CERTIFICATE) {
            ArrayList<String> details = new ArrayList<>();
            details = data.getStringArrayListExtra("selectedDetails");
            certificateForSign.isOneTime = false;
            certificateForSign.certificateID = details.get(0);
            certificateForSign.certificatePath = details.get(1);

            SendOtp sendOtp = new SendOtp();
            sendOtp.execute(url, certificateForSign.certificateID, authToken);
            signButton.setVisibility(View.VISIBLE);
            tanCode.setVisibility(View.VISIBLE);
            signPasswd.setVisibility(View.VISIBLE);
            checkBox.setVisibility(View.VISIBLE);
        }
    }

    public void startConfiguration(View view) {
        Intent intent = new Intent(MainActivity.this, ConfigurationActivity.class);
        if (!authToken.isEmpty()) {
            intent.putExtra("AuthToken", authToken);
        }
        startActivityForResult(intent, LOCAL_CERTIFICATE);
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
        //Log.d("Method","OnDestroy()");
     /*   try{
            deleteAccessToken();
        }catch (Exception e){
            Log.d("Error",e.getMessage());
        }*/
    }

    public void deleteAccessToken(){
        String url=BASE_URL+"oauth2/revoke";
        Boolean resp=false;
        if(!authToken.equals("")) {
            try {
                RevokeAccessToken revokeAccessToken = new RevokeAccessToken();
                resp=revokeAccessToken.execute(url, authToken).get();
                Log.d("ResponseToken",resp.toString());
                if(resp){
                    Log.d("AccessToken","Access token successfully deleted!");
                }
            } catch (Exception e) {
                Log.d("Error", e.getMessage());
            }
        }
    }

    //Hex-to-Long
    private static long hexToLong(String hex) {
        return Long.parseLong(hex, 16);
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

    //loads pdf file and searches for signatures
    public void viewSignatures(View view) {
        Intent intent = new Intent(MainActivity.this, SignatureDetailsActivity.class);
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
                //verify services state.
                if (myClass.isAirplaneModeOn(context)) {
                    myClass.AlertDialogBuilder("Airplane mode is on!", context, "Services state");

                } else if (!myClass.InternetConnection()) {
                    myClass.AlertDialogBuilder("You must enable Internet Connection to be able to sign documents!",
                            context, "Internet Error!");

                } else if (!myClass.verifyGrantedPermissions(MainActivity.this)) {
                    final Toast toast = Toast.makeText(MainActivity.this, "Not enough permissions!", Toast.LENGTH_SHORT);
                    TextView toastMessage = toast.getView().findViewById(android.R.id.message);
                    toastMessage.setTextColor(Color.RED);
                    toast.show();
                    myClass.verifyStoragePermissions(MainActivity.this);
                } else {
                    startActivity(intent);
                }

            }
        });
    }


    //gets sad - signature activation data
    public String GetSadResponse(String hash, String otpCode, String signPassword) {
        String sadValue = "";
        String response = "";
        String url = BASE_URL + "credentials/authorize";
        SadClass sadClass = new SadClass();
        try {
            if (certificateForSign.isOneTime) {
                if (!certificateForSign.certificateID.isEmpty() && !certificateForSign.certificatePath.equals("Not found!")) {
                    response = sadClass.execute(url, certificateForSign.certificateID, hash, signPassword, otpCode).get();
                }
            } else {
                if (!certificateForSign.certificateID.isEmpty() && !certificateForSign.certificatePath.equals("Not found!")) {
                    response = sadClass.execute(url, certificateForSign.certificateID, hash, signPassword, otpCode).get();
                }
            }
        /*    if (!oneTimeCertificate.isEmpty()) {
                response = sadClass.execute(url, oneTimeCertificate, hash, signPassword, otpCode).get();
            } else {
                response = sadClass.execute(url, savedCertificate, hash, signPassword, otpCode).get();
            }*/
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (!response.isEmpty()) {
            HelperClass helperClass = new HelperClass(this);
            if (response.equals("invalid_otp")) {
                wrongCredentials = true;
                helperClass.AlertDialogBuilder("The OTP code provided by you does not match. " +
                        "Please review it. The signature will not be valid!", this, "Wrong OTP!");
            } else if (response.equals("invalid_pin")) {
                wrongCredentials = true;
                helperClass.AlertDialogBuilder("The password provided by you does not match. " +
                        "Please review it. The signature will not be valid!", this, "Wrong Password!");
            } else {
                sadValue = response;
                wrongCredentials = false;
            }
        }
        return sadValue;
    }

    //signs hash
    public String GetSignature(String hash, String sadResponse) {
        String response = "";
        ArrayList<String> signList = new ArrayList<>();
        String url = BASE_URL + "signatures/signHash";
        SignClass signClass = new SignClass();
        String toastmsg = "";
        toastmsg = "" + certificateForSign.isOneTime + " " + certificateForSign.certificateID + " " + certificateForSign.certificatePath;
        Toast.makeText(MainActivity.this,
                toastmsg, Toast.LENGTH_LONG).show();

        try {
            if (certificateForSign.isOneTime) {
                if (!certificateForSign.certificateID.isEmpty() && !certificateForSign.certificatePath.equals("Not found!")) {
                    signList = signClass.execute(url, certificateForSign.certificateID, hash, sadResponse).get();
                }
            } else {
                if (!certificateForSign.certificateID.isEmpty() && !certificateForSign.certificatePath.equals("Not found!")) {
                    signList = signClass.execute(url, certificateForSign.certificateID, hash, sadResponse).get();
                }
            }

        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        String signatureString = "";
        if (signList.size() > 0) {
            signatureString = signList.get(0);
        }
        if (!signatureString.isEmpty()) {
            byte[] bytes = signatureString.getBytes(StandardCharsets.UTF_8);
            String signData = signatureString.replace("\\r", "").replace("\\n", "");
            signatureString = signData;
            Log.d("Signature", signatureString);
            Log.d("Bytes", Integer.toString(bytes.length));
        }
        return signatureString;
    }


    //prepare signature
    private void openDocument() {
        HelperClass helperClass = new HelperClass(this);
        String path = helperClass.getValue(this, FILE_PATH);
        PrepareTemporaryFile(path);
        try {
            m_CurrDoc = new TElPDFDocument();
            try {
                m_CurrDoc.setOwnActivatedSecurityHandlers(true);
                m_CurrDoc.open(m_CurrStream);
                ExtractRevInfo();
            } catch (Exception exc) {
                m_CurrDoc = null;
                throw exc;
            }
        } catch (Exception ex) {
            DeleteTemporaryFile(false);
            throw ex;
        }
    }

    private void PrepareTemporaryFile(String srcFile) {
        String TempPath = getCacheDir().toString() + "/wantedFile.pdf";
        try {
            copyFile(srcFile, TempPath, true);
        } catch (IOException e) {
            e.printStackTrace();
            Log.d("Error", e.getMessage());
        }
        m_CurrStream = new TElFileStream(TempPath, "rw", true);
        m_CurrOrigFile = srcFile;
        m_CurrTempFile = TempPath;
    }

    private void copyFile(String src, String dest, boolean overwrite) throws IOException {
        RandomAccessFile inputFile = new RandomAccessFile(src, "r");
        RandomAccessFile outputFile = new RandomAccessFile(dest, "rw");
        byte[] buf = new byte[1024];
        int r;

        if (overwrite)
            outputFile.setLength(0);

        do {
            r = inputFile.read(buf);
            if (r > 0)
                outputFile.write(buf, 0, r);
        } while (r > 0);

        inputFile.close();
        outputFile.close();
    }

    private void DeleteTemporaryFile(boolean saveChanges) {
        if (m_CurrStream != null) {
            m_CurrStream.Destroy();
            m_CurrStream = null;
        }
        if (saveChanges) {
            try {
                copyFile(m_CurrTempFile, m_CurrOrigFile, true);
            } catch (IOException e) {
                Log.d("Error", e.getMessage());
                e.printStackTrace();
            }
        }
        new File(m_CurrTempFile).delete();
        m_CurrTempFile = "";
        m_CurrOrigFile = "";
    }

    private void ExtractRevInfo() {
        if (m_CurrDoc == null) {
            return;
        }
        m_DocRevInfo.clear();
        boolean DSSAdded = false;
        for (int i = 0; i < m_CurrDoc.getSignatureCount(); i++) {
            if (m_CurrDoc.getSignatureEntry(i).getHandler() instanceof TElPDFAdvancedPublicKeySecurityHandler) {
                TElPDFAdvancedPublicKeySecurityHandler handler = (TElPDFAdvancedPublicKeySecurityHandler) m_CurrDoc.getSignatureEntry(i).getHandler();
                if (!DSSAdded) {
                    m_DocRevInfo.assign(handler.getDSSRevocationInfo(), false);
                    DSSAdded = true;
                }
                m_DocRevInfo.assign(handler.getRevocationInfo(), false);
            }
        }
    }

    private boolean CloseCurrentDocument(boolean saveChanges) {
        boolean res = true;
        try {
            if (m_CurrDoc != null) {
                try {
                    m_CurrDoc.close(saveChanges);
                } finally {
                    m_CurrDoc = null;
                }
            }
        } catch (Exception ex) {
            res = false;
            Log.d("Error", ex.getMessage());
        }
        if (m_CurrStream != null) {
            DeleteTemporaryFile(saveChanges);
        }
        return res;
    }

    //load certificate from file to app.
    private int certLoaded() {
        String signPassword = "";
        int response = 1;
        if (!signPasswd.getText().toString().isEmpty()) {
            signPassword = signPasswd.getText().toString();
        }
        try {
            if (certificateForSign.isOneTime && !certificateForSign.certificateID.isEmpty() &&
                    !certificateForSign.certificatePath.equals("Not found!") && !certificateForSign.certificatePath.isEmpty()) {
                response = m_cert.loadFromFileAuto(certificateForSign.certificatePath, signPassword);
            } else if (!certificateForSign.isOneTime && !certificateForSign.certificateID.isEmpty() &&
                    !certificateForSign.certificatePath.equals("Not found!") && !certificateForSign.certificatePath.isEmpty()) {
                response = m_cert.loadFromFileAuto(certificateForSign.certificatePath, signPassword);
            }

            /*   if (!savedCertificate.isEmpty() && !savedCertificatePath.equals("Not found!")) {
                response = m_cert.loadFromFileAuto(savedCertificatePath, signPassword);
            } else if (!oneTimeCertificate.isEmpty() && !oneTimeCertificatePath.isEmpty()) {
                //response = m_cert.loadFromFileAuto(oneTimeCertificatePath, signPassword);
            }*/
        } catch (Exception e) {
            Log.d("Error", e.getMessage());
        }
        return response;
    }

    private void PrepareValidation(TElPDFAdvancedPublicKeySecurityHandler handler) {
        m_TrustedCerts.clear();
        String signPassword = "";
        int load = 1;
        if (!signPasswd.getText().toString().isEmpty()) {
            signPassword = signPasswd.getText().toString();
        }
        try {
            signingCertificate=new TElX509Certificate();
            load = signingCertificate.loadFromFileAuto(certificateForSign.certificatePath, signPassword);
        } catch (Exception e) {
            Log.d("Error", e.getMessage());
        }
        if (load == 0) {
            m_TrustedCerts.add(signingCertificate, true);
        }
        handler.setOnCertValidatorPrepared(new TSBPDFCertValidatorPreparedEvent(onCertValidatorPrepared));
        handler.setOnCertValidatorFinished(new TSBPDFCertValidatorFinishedEvent(onCertValidatorFinished));
        int k = m_CertValidationLog.indexOfObject(handler);
        if (k >= 0)
            m_CertValidationLog.removeAt(k);
    }


    TSBPDFCertValidatorFinishedEvent.Callback onCertValidatorFinished = new TSBPDFCertValidatorFinishedEvent.Callback() {

        @Override
        public void tsbpdfCertValidatorFinishedEventCallback(TObject arg0,
                                                             TElX509CertificateValidator arg1, TElX509Certificate arg2,
                                                             TSBCertificateValidity arg3, int arg4) {
            int k = m_CertValidationLog.indexOfObject(arg0);
            if (k >= 0 && k < m_CertValidationLog.getCount())
                m_CertValidationLog.setString(k, m_CertValidationLog.getString(k) + "\n" + arg1.getInternalLogger().getLog().getText());
            else
                m_CertValidationLog.addObject(arg1.getInternalLogger().getLog().getText(), arg0);
        }
    };

    TSBPDFCertValidatorPreparedEvent.Callback onCertValidatorPrepared = new TSBPDFCertValidatorPreparedEvent.Callback() {

        public TElX509CertificateValidator tsbpdfCertValidatorPreparedEventCallback(
                TObject sender, TElX509CertificateValidator CertValidator,
                TElX509Certificate Cert) {
            CertValidator.addTrustedCertificates(m_TrustedCerts);
            CertValidator.addKnownCertificates(m_TrustedCerts);
            CertValidator.addKnownCRLs(m_KnownCRLs);
            for (int i = 0; i < m_LocalRevInfo.getOCSPResponseCount(); i++) {
                TElOCSPResponse resp = new TElOCSPResponse();
                try {
                    byte[] encodedResp = m_LocalRevInfo.getOCSPResponse(i);
                    resp.load(encodedResp, 0, encodedResp.length);
                    CertValidator.addKnownOCSPResponses(resp);
                } catch (Exception ex) {
                }
            }
            CertValidator.setForceCompleteChainValidationForTrusted(false);

            return CertValidator;
        }
    };

    TSBPDFRemoteSignEvent.Callback OnRemoteSign = new TSBPDFRemoteSignEvent.Callback() {
        @Override
        public byte[] tsbpdfRemoteSignEventCallback(TObject Sender, byte[] Hash) {
            String otpCode = "";
            String signPassword = "";
            String sadValue = "";
            byte[] SignedHash;
            //byte[] to base64
            String docHashBase64 = "";
            try {
                Log.d("DocHash", Hash.toString());
                docHashBase64 = Base64.encodeToString(Hash, Base64.NO_WRAP);
                Log.d("ConvertedHash", docHashBase64);
            } catch (Exception e) {
                Log.d("Error", e.getMessage());
            }
            //base64 to byte[]
            String respHashBase64 = "";
            try {
                if (!tanCode.getText().toString().isEmpty()) {
                    otpCode = tanCode.getText().toString();
                }
                if (!signPasswd.getText().toString().isEmpty()) {
                    signPassword = signPasswd.getText().toString();
                }
                if (!signPassword.isEmpty() && !otpCode.isEmpty()) {
                    //get sad for hash
                    sadValue = GetSadResponse(docHashBase64, otpCode, signPassword);
                }
                if (!sadValue.isEmpty()) {
                    try {
                        respHashBase64 = GetSignature(docHashBase64, sadValue);
                    } catch (Exception e) {
                        Log.d("Error", e.getMessage());
                    }
                }
                if (!respHashBase64.isEmpty()) {
                    try {
                        SignedHash = Base64.decode(respHashBase64, Base64.NO_WRAP);
                        return SignedHash;
                    } catch (Exception e) {
                        Log.d("Error", e.getMessage());
                    }
                }
            } catch (Exception e) {
                Log.d("Error", e.getMessage());
            }
            return new byte[0];
        }
    };

    public void SignDocument(View view) {
        String filePath = "";
        HelperClass helperClass = new HelperClass(this);
        if (tanCode.getText().toString().isEmpty()) {
            helperClass.AlertDialogBuilder("Please fill in the OTP code received in text messages box!", this, "OTP code error");
        } else if (signPasswd.getText().toString().isEmpty()) {
            helperClass.AlertDialogBuilder("Please fill in the signing password!", this, "Password error");
        } else if (!helperClass.InternetConnection()) {
            helperClass.AlertDialogBuilder("You must enable Internet Connection to be able to sign documents!",
                    this, "Internet Error!");
        } else {
            filePath = helperClass.getValue(this, FILE_PATH);
            Log.d("Sign_FilePath", filePath);
            try {
                openDocument();
            } catch (Exception e) {
                Log.d("Error", "No document selected!");
                helperClass.AlertDialogBuilder("No document selected!", this, "Error");
            }
            if (certLoaded() == 0) {
                Log.d("Certificate", "Loaded successfully!");
            }

            try {
                int idx = m_CurrDoc.addSignature();
                TElPDFSignature sig = m_CurrDoc.getSignatureEntry(idx);

                if (idx > 0) {
                    String sigLoc = sig.getLocation();
                    Log.d("SigLocation", sigLoc);
                }
                TElPDFSignatureWidgetProps tElPDFSignatureWidgetProps=new TElPDFSignatureWidgetProps();
                tElPDFSignatureWidgetProps.setAutoSize(true);
                int xDim=tElPDFSignatureWidgetProps.getOffsetX();
                int yDim=tElPDFSignatureWidgetProps.getOffsetY();
                tElPDFSignatureWidgetProps.setOffsetX(xDim);
                tElPDFSignatureWidgetProps.setOffsetY(yDim-20);

                sig.setOptions(1);
                sig.setAuthorName("Cloud Signer - Mobile");
                sig.setHandler(m_Handler);
                sig.setInvisible(false);
                int hashAlg = m_Handler.getHashAlgorithm();
                //28929 - SHA1
                //28932 - SHA256
                if (hashAlg != 28932) {
                    m_Handler.setHashAlgorithm(28932);
                    hashAlgorithm = 28932;
                }
                m_CertStorage.clear();
                m_CertStorage.add(m_cert, true);
                m_Handler.setPAdESSignatureType(TSBPAdESSignatureType.pastBasic);
                m_Handler.setCustomName("Adobe.PPKMS");
                m_Handler.setCertStorage(m_CertStorage);
                PrepareValidation(m_Handler);

                m_Handler.setRemoteSigningMode(true);
                m_Handler.setOnRemoteSign(new TSBPDFRemoteSignEvent(OnRemoteSign));
                m_Handler.setIgnoreChainValidationErrors(true);

                sig.setSigningTime(SBUtils.utcNow());

                if (CloseCurrentDocument(true)) {
                    if (!wrongCredentials) {
                        Log.d("Message", "\"Signed document!");
                        helperClass.AlertDialogBuilder("Congratulations! You've successfully signed your pdf!" +
                                "Go to View Signatures button to see the signature!", this, "Signature successful!", true);
                        tanCode.setText("");
                        signPasswd.setText("");
                    }
                    viewSignature.setVisibility(View.VISIBLE);
                } else {
                    Log.d("Error", "\"Signature failed!");
                }
            } catch (Exception e) {
                Log.d("Error", e.getMessage());
            }

        }
    }


    //AsyncTask Classes - Token, Otp, SAD, Sign.
    //Sign class - call signing service -- signs hash.
    private class SignClass extends AsyncTask<String, Void, ArrayList<String>> {
        @Override
        protected ArrayList<String> doInBackground(String... urls) {
            String response = "";
            ArrayList<String> signaturesList = new ArrayList<>();
            JSONObject jsonObject = new JSONObject();
            JSONArray hashesArray = new JSONArray();
            String hashAlgo_SHA1 = "1.3.14.3.2.26";
            String signAlgo_RSA_SHA1 = "1.3.14.3.2.29";

            String hashAlgo_SHA256 = "1.2.840.113549.1.1.11";
            String signAlgo_RSA_SHA256 = "2.16.840.1.101.3.4.2.1";
            try {
                hashesArray.put(urls[2]);
            } catch (Exception e) {
                Log.d("Error", e.getMessage());
            }
            try {
                jsonObject.accumulate("credentialID", urls[1]);
                //sha256
                if (hashAlgorithm == 28932) {
                    jsonObject.accumulate("signAlgo", "1.2.840.113549.1.1.11");
                    jsonObject.accumulate("hashAlgo", "2.16.840.1.101.3.4.2.1");
                }
                //sha1
                else {
                    jsonObject.accumulate("signAlgo", signAlgo_RSA_SHA1);
                    //
                    jsonObject.accumulate("hashAlgo", hashAlgo_SHA1);
                }
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
                            conn.getInputStream(), StandardCharsets.UTF_8));
                    String line = null;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                    br.close();
                    try {
                        Signature signature = new Signature();
                        Gson gson = new Gson();
                        signature = gson.fromJson(sb.toString(), Signature.class);
                        signaturesList = signature.signatures;
                        return signaturesList;
                    } catch (Exception e) {
                        Log.d("Error", e.getMessage());
                    }
                }
                Log.d("Signature", sb.toString());
                response = sb.toString();
            } catch (Exception e) {
                Log.d("Error", e.getMessage());
            }
            return null;
        }

        @Override
        protected void onPostExecute(ArrayList<String> result) {

        }
    }


    //GetSad value class
    private class SadClass extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls) {

            String response = "";
            String errorCode = "";
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

                try {
                    DataOutputStream os = new DataOutputStream(conn.getOutputStream());
                    os.writeBytes(jsonObject.toString());
                    os.flush();
                    os.close();
                } catch (Exception e) {
                    Log.d("Error", e.getMessage());
                }

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
                    try {
                        Gson gson = new Gson();
                        SignatureActivationData signatureActivationData = new SignatureActivationData();
                        signatureActivationData = gson.fromJson(sb.toString(), SignatureActivationData.class);
                        response = signatureActivationData.SAD;
                        return signatureActivationData.SAD;
                    } catch (Exception e) {
                        Log.d("Error", e.getMessage());
                    }
                } else if (httpsResult == HttpsURLConnection.HTTP_BAD_REQUEST) {
                    try {
                        BufferedReader br = new BufferedReader(new InputStreamReader(
                                conn.getErrorStream(), "utf-8"));
                        String line = null;
                        while ((line = br.readLine()) != null) {
                            sb.append(line + "\n");
                        }
                        br.close();
                        Gson gson = new Gson();
                        ErrorCode errorCode1 = gson.fromJson(sb.toString(), ErrorCode.class);
                        errorCode = errorCode1.error;
                        Log.d("ErCode", errorCode1.error);
                        return errorCode1.error;
                    } catch (Exception e) {
                        Log.d("Error", e.getMessage());
                    }
                }
                Log.d("SAD", sb.toString());
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

    //revoke access token
    private class RevokeAccessToken extends AsyncTask<String, Void, Boolean> {
        @Override
        protected Boolean doInBackground(String... urls) {
            Boolean response = false;
            JSONObject jsonObject = new JSONObject();
            try {
                jsonObject.accumulate("token", urls[1]);
                jsonObject.accumulate("token_hint_uri", "access_token");
                jsonObject.accumulate("client_id", clientID);
                jsonObject.accumulate("client_secret", clientSecret);
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

                Log.i("STATUS_Certificates", String.valueOf(conn.getResponseCode()));
                Log.i("MSG_Certificates", conn.getResponseMessage());

                StringBuilder sb = new StringBuilder();
                int httpsResult = conn.getResponseCode();
                Log.d("HttpResult",Integer.toString(httpsResult));
                if (httpsResult == 204) {
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


    //AuthToken class - OAuth2_Token
    private class GetAuthToken extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls) {
            JSONObject jsonObject = new JSONObject();
            try {
                jsonObject.accumulate("grant_type", "authorization_code");
                jsonObject.accumulate("code", urls[1]);
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
                Gson gson = new Gson();
                AccessToken accessToken = new AccessToken();
                accessToken = gson.fromJson(sb.toString(), AccessToken.class);

                conn.disconnect();
                return accessToken.access_token;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return "Post failed";
        }

        @Override
        protected void onPostExecute(String result) {
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

    //get credential info, if user's certificate is not found on phone.
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

    //Class for certificates
    public class CertificateForSign {
        public String certificatePath;
        public String certificateID;
        public boolean isOneTime;

        CertificateForSign() {
            this.certificateID = "";
            this.certificatePath = "";
            this.isOneTime = false;
        }
    }

    //JSON Classes
    public class CredentialsIds {
        public ArrayList<String> credentialIDs;
        public String nextPageToken;
    }

    public class ErrorCode {
        String error;
        String errorDescription;
    }

    public class AccessToken {
        public String access_token;
        public String refresh_token;
        public String token_type;
        public int expires_in;
    }

    public class SignatureActivationData {
        public String SAD;
        public int expiresIn;
    }

    public class Signature {
        public ArrayList<String> signatures;
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

}
