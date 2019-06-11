package com.example.android.cloudsigner;

import android.Manifest;
import android.app.Activity;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.icu.text.SimpleDateFormat;
import android.icu.util.TimeZone;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
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
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
        SBUtils.setLicenseKey("4E6D44C2173B71B6C3EB107A72DB0C21F8A6508511DF58B527A4F002C69466DF5A4C6F741AB80E3135506DA5A882" +
                "ECCB75593C32CEF137607D92C36332C190ACD13D46733B9F832969451AAD26902F4D43E4831526E67AA150F1A62D1FBBDF3B2866D3" +
                "33293C3C03F1AADD0DE9110F442E1B7F570E71EF755465F94F294CB1595AED9FBDA6D0E4D5D6CAB9D06B730355EE501BD494ABACCE3" +
                "102474FC724D5F57D8C7AF7C77DF9547E9031084B6C4B492BE7BE8A3F26539D497005F802ADEB5F8D5953995A7594A3A0EE96410A9F" +
                "02BCD67D9220082ECC2C863956FF80579B52AE31D720BA6EA816CA0A82BFB54773D9CE520746BE11E3E3BEC30BB26C36733C");
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
                                        if (!savedCertificate.isEmpty() && !savedCertificatePath.equals("Not found!")) {
                                            try {
                                                final String url = BASE_URL + "credentials/sendOTP";
                                                //save default certificate to certificate for sign class.
                                                certificateForSign.isOneTime = false;
                                                certificateForSign.certificateID = savedCertificate;
                                                certificateForSign.certificatePath = savedCertificatePath;

                                                SendOtp sendOtp = new SendOtp();
                                                sendOtp.execute(url, savedCertificate, authToken);
                                                tanCode.setVisibility(View.VISIBLE);
                                                signPasswd.setVisibility(View.VISIBLE);
                                                checkBox.setVisibility(View.VISIBLE);
                                                signButton.setVisibility(View.VISIBLE);
                                            } catch (Exception e) {
                                                Log.d("Error", e.getMessage());
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

                } else if (myClass.InternetConnection() == false) {
                    myClass.AlertDialogBuilder("You must enable Internet Connection to be able to sign documents!",
                            context, "Internet Error!");

                } else {
                    //startActivity(new Intent(context, ConfigurationActivity.class));
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
          /*  if (!oneTimeCertificate.isEmpty()) {
                signList = signClass.execute(url, oneTimeCertificate, hash, sadResponse).get();
            } else if (!savedCertificate.isEmpty()) {

            }*/
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
            load = signingCertificate.loadFromFileAuto(selectedCertificatePath, signPassword);
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
            CertValidator.addKnownCertificates(m_LocalRevInfo.getCertificates());
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
                Date date = new Date();
                sig.setSigningTime(date);

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

       /* String otpCode = "";
        String signPassword = "";
        String sadValue = "";
        //byte[] to base64
        String docHash = "E3z8VQoHVdzWBfdlxmLEnPVIgYE9ZZkM+XWz6QNK+Ls=";
        String testHash="nkSljMwVccgkxlI5FqdLrNmVw7o=";
        //base64 to byte[]
        String respHash = "";

        if (!tanCode.getText().

                toString().

                isEmpty()) {
            otpCode = tanCode.getText().toString();
        }
        if (!signPasswd.getText().

                toString().

                isEmpty()) {
            signPassword = signPasswd.getText().toString();
        }
        if (!signPassword.isEmpty() && !otpCode.isEmpty()) {
            //get sad for hash
            sadValue = GetSadResponse(docHash, otpCode, signPassword);
        }
        if (!sadValue.isEmpty()) {
            try {
                respHash = GetSignature(docHash, sadValue);
                Log.d("Signature", respHash);
            } catch (Exception e) {
                Log.d("Error", e.getMessage());
            }
        }*/
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


    //functions used before code review.
    //deprecated functions -- functii care sunt folosite doar pentru testarea anumitor functionalitati.
    //deprecated.
    /* old - loadInfo button!
      loadInfoButton.setVisibility(View.VISIBLE);
                loadInfoButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        progressBar.setVisibility(View.VISIBLE);
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                GetCertificatesList();
                                progressBar.setVisibility(View.GONE);
                            }
                        }, 0);
                    }
                });
     */
    /*
    public void setCertInfo(View view) {
        certSpinner.setVisibility(View.VISIBLE);
        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, certInfo);
        Log.d("Adapter", certInfo.get(0));
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        certSpinner.setAdapter(arrayAdapter);
        //sendOtpButton.setVisibility(View.VISIBLE);
    }
    */
    //deprecated
   /* private ArrayList<String> GetCredentialIDS() {
        ArrayList<String> credIds = new ArrayList<String>();
        String response = "";
        String url = BASE_URL + "credentials/list";
        try {
            GetCredentialIds getCredentialIds = new GetCredentialIds();
            response = getCredentialIds.execute(url).get();
        } catch (Exception e) {
            Log.d("Error", e.getMessage());
        }
        try {
            StringTokenizer stringTokenizer = new StringTokenizer(response, ":{}[],\"");
            String token = "";
            while (stringTokenizer.hasMoreTokens()) {
                token = stringTokenizer.nextToken();
                if (token.equals("credentialIDs")) {
                    while (stringTokenizer.hasMoreTokens()) {
                        String certToken = stringTokenizer.nextToken();
                        if (!certToken.equals("\n")) {
                            credIds.add(certToken);
                        }
                    }
                } else {
                    stringTokenizer.nextToken();
                }
            }
        } catch (Exception e) {
            Log.d("Error", e.getMessage());
        }

        return credIds;
    }

    //deprecated
    private ArrayList<String> getCertificatesInformation(ArrayList<String> credentialIdList) {
        ArrayList<String> certArrayList = new ArrayList<>();
        String response = "";
        String serialNumber = "";
        String url = BASE_URL + "credentials/info";
        for (int i = 0; i < credentialIdList.size(); i++) {
            try {
                GetCertificates getCertificates = new GetCertificates();
                response = getCertificates.execute(url, credentialIdList.get(i)).get();
            } catch (Exception e) {
                Log.d("Error", e.getMessage());
            }
            StringTokenizer stringTokenizer = new StringTokenizer(response, ",:[]{}\"");
            int tokens = stringTokenizer.countTokens();
            String token = "";
            String alias = "";
            String keyStatus = "";
            String certStatus = "";
            boolean serialN = false;
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
                } else if (token.equals("serialNumber") && serialN == false) {
                    serialN = true;
                    serialNumber = stringTokenizer.nextToken();
                }
            }
            Log.d("SerialN", serialNumber);
            Log.d("Alias", alias + " " + keyStatus + " " + certStatus);
            if (keyStatus.equals("enabled") && certStatus.equals("valid")) {
                String crtAlias = "Cert-Alias:";
                String crtNo = certArrayList.size() + 1 + ". " + crtAlias;
                certArrayList.add(crtNo + alias);
                long serial = hexToLong(serialNumber);
                serialNumbers.add(Long.toString(serial));
                Log.d("certInfo[certNumber]", certArrayList.get(certArrayList.size() - 1));
                Log.d("cert number", Integer.toString(certArrayList.size()));
            }

        }
        return certArrayList;
    }

*//*    //deprecated
    public void GetSad(View view) {
        String otpCode = tanCode.getText().toString();
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

        Log.d("SAD-value", sadValue);
        //signButton.setVisibility(View.VISIBLE);
        tanCode.setText("");
        signPasswd.setText("");
    }

    //deprecated.
    public void sendOTP(View view) {
        String selectedCertificate = certSpinner.getSelectedItem().toString();
        int index = (int) certSpinner.getSelectedItemId();
        selectedCertificateIndex = index;
        Log.d("Selected", selectedCertificate);
        Log.d("SelectedID", Long.toString(index));
        //getSadButton.setVisibility(View.VISIBLE);
        String url = BASE_URL + "credentials/sendOTP";
        tanCode.setVisibility(View.VISIBLE);
        signPasswd.setVisibility(View.VISIBLE);
        SendOtp sendOtp = new SendOtp();
        Log.d("OTP-credentialID", credentialIds.get(selectedCertificateIndex));
        sendOtp.execute(url, credentialIds.get(selectedCertificateIndex));
    }*/

    //deprecated.
  /*  public void getInfo(View view) {
        certInfo = new ArrayList<String>();
        if (credentialIds.size() > 0) {
            for (int i = 0; i < credentialIds.size(); i++) {
                Log.d("InFunction", credentialIds.get(i));
                GetCertificates getCertificates = new GetCertificates();
                String url = BASE_URL + "credentials/info";
                //certInfoButton.setVisibility(View.VISIBLE);
                getCertificates.execute(url, credentialIds.get(i));
            }
        }
    }

    //deprecated.
    public void getCredentialIds(View view) {
        Context context = this;
        HelperClass helperClass = new HelperClass(this);
        //Intent intent = new Intent(this, ViewPDFActivity.class);
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
                    //certificatesButton.setVisibility(View.VISIBLE);
                    credentialIds = new ArrayList<String>();
                    GetCredentialIds getCredentialIds = new GetCredentialIds();
                    getCredentialIds.execute(url);
                }
            }
        }
    }
*/
    //deprecated
  /*  private String loadCertificate(String serialNumber) {

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

                if (serialNumber.equals(certificate.getSerialNumber().toString())) {
                    return location;
                }

            } catch (Exception e) {
                Log.d("Error", e.getMessage());
            }
        }
        return "";
    }

    //deprecated
    public void setCertInfo(final ArrayList<String> certificatesInfo, final ArrayList<String> credIds) {
        certSpinner.setVisibility(View.VISIBLE);
        final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(this, R.layout.spinner_item, certificatesInfo);
        Log.d("Adapter", certificatesInfo.get(0));
        arrayAdapter.setDropDownViewResource(R.layout.spinner_drop_down_items);
        certSpinner.setAdapter(arrayAdapter);
        final String url = BASE_URL + "credentials/sendOTP";
        //sendOtpButton.setVisibility(View.VISIBLE);
        certSpinner.setPrompt("Select your certificate!");
        final HelperClass myClass = new HelperClass(this);
        final Context context = this;
        certSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                Log.d("Spinner", "Selected");
                int index = (int) certSpinner.getSelectedItemId();
                selectedCertificateIndex = index;
                //this is the path to the certificate downloaded on the phone!
                selectedCertificatePath = loadCertificate(serialNumbers.get(selectedCertificateIndex));
                if (selectedCertificatePath.equals("")) {
                    myClass.AlertDialogBuilder("The certificate corresponding to the selected " +
                                    "alias is missing. Please go to https://msign-test.transsped.ro/serverbku/protected/index.jsf, " +
                                    "download the specified certificate and save it to /Download/Private !"
                            , context, "Missing certificate!");
                    signButton.setVisibility(View.GONE);
                } else {
                    Log.d("LoadedCertificate", selectedCertificatePath);
                    SendOtp sendOtp = new SendOtp();
                    sendOtp.execute(url, credIds.get(selectedCertificateIndex));
                    tanCode.setText("");
                    signPasswd.setText("");
                    signButton.setVisibility(View.VISIBLE);
                }

            }

            public void onNothingSelected(AdapterView<?> adapterView) {
                return;
            }
        });
    }
*/
    //deprecated
    //gets certificates list
   /* public void GetCertificatesList() {
        ArrayList<String> credIds = new ArrayList<>();
        ArrayList<String> certificatesInfo = new ArrayList<>();
        Context context = this;
        HelperClass helperClass = new HelperClass(this);
        if (helperClass.InternetConnection() == false) {
            helperClass.AlertDialogBuilder("You must enable Internet Connection to be able to sign documents!",
                    context, "Internet Error!");
        } else {
            try {
                credIds = GetCredentialIDS();
                credentialIds = credIds;
            } catch (Exception e) {
                Log.d("Error", e.getMessage());
            }
            if (credIds.size() > 0) {
                try {
                    certificatesInfo = getCertificatesInformation(credIds);
                } catch (Exception e) {
                    Log.d("Error", e.getMessage());
                }
                if (certificatesInfo.size() > 0) {
                    try {
                        setCertInfo(certificatesInfo, credIds);
                        tanCode.setVisibility(View.VISIBLE);
                        signPasswd.setVisibility(View.VISIBLE);
                        checkBox.setVisibility(View.VISIBLE);
                    } catch (Exception e) {
                        Log.d("Error", e.getMessage());
                    }
                }
            }
        }
    }
    //deprecated
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
    //deprecated
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
    }*/
}
