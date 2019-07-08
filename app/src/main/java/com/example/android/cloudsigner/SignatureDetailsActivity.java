package com.example.android.cloudsigner;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.Button;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import org.freepascal.rtl.TObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import SecureBlackbox.Base.SBPKICommon;
import SecureBlackbox.Base.SBUtils;
import SecureBlackbox.Base.TElAbstractCRL;
import SecureBlackbox.Base.TElFileStream;
import SecureBlackbox.Base.TElMemoryCRLStorage;
import SecureBlackbox.Base.TElMemoryCertStorage;
import SecureBlackbox.Base.TElOCSPResponse;
import SecureBlackbox.Base.TElStringList;
import SecureBlackbox.Base.TElX509Certificate;
import SecureBlackbox.Base.TElX509CertificateValidator;
import SecureBlackbox.Base.TSBCMSAdvancedSignatureValidity;
import SecureBlackbox.Base.TSBCertificateValidity;
import SecureBlackbox.HTTPClient.TElHTTPSClient;
import SecureBlackbox.HTTPClient.TElHTTPTSPClient;
import SecureBlackbox.PDF.TElPDFDocument;
import SecureBlackbox.PDF.TElPDFPublicKeyRevocationInfo;
import SecureBlackbox.PDF.TElPDFSignature;
import SecureBlackbox.PKI.TElCertificateRevocationListEx;
import SecureBlackbox.PKIPDF.TElPDFAdvancedPublicKeySecurityHandler;
import SecureBlackbox.PKIPDF.TSBPAdESSignatureType;
import SecureBlackbox.PKIPDF.TSBPDFCertValidatorFinishedEvent;
import SecureBlackbox.PKIPDF.TSBPDFCertValidatorPreparedEvent;

public class SignatureDetailsActivity extends AppCompatActivity {

    public static final String FILE_PATH = "Path";
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
    TElCertificateRevocationListEx cert_crl=new TElCertificateRevocationListEx();
    //TElAbstractCRL cert_crl=new TElAbstractCRL();
    String certificatePath="";
    String rootPath="";
    String intermediatePath="";
    String crlPath="";

    HashMap<String, List<String>> expandableListDetail;
    List<String> expandableListTitle;
    ExpandableListView expandableListView;
    ExpandableListAdapter expandableListAdapter;
    TextView signaturesNumber;
    ImageButton closeBtn;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signature_details);
        expandableListDetail = new HashMap<>();
        signaturesNumber = findViewById(R.id.signNo);
        closeBtn=findViewById(R.id.closeButton);
        closeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        try {
            viewSignatures();
        } catch (Exception e) {
            Log.d("Error", e.getMessage());
        }
        expandableListView = findViewById(R.id.expandableListView);
        expandableListTitle = new ArrayList<>(expandableListDetail.keySet());

        expandableListAdapter = new CustomExpandableListAdapter(this, expandableListTitle, expandableListDetail);
        expandableListView.setAdapter(expandableListAdapter);
        expandableListView.setOnGroupExpandListener(new ExpandableListView.OnGroupExpandListener() {
            @Override
            public void onGroupExpand(int groupPosition) {
                Toast.makeText(getApplicationContext(),
                        expandableListTitle.get(groupPosition) + " List Expanded.",
                        Toast.LENGTH_SHORT).show();
            }
        });
        expandableListView.setOnGroupCollapseListener(new ExpandableListView.OnGroupCollapseListener() {

            @Override
            public void onGroupCollapse(int groupPosition) {
                Toast.makeText(getApplicationContext(),
                        expandableListTitle.get(groupPosition) + " List Collapsed.",
                        Toast.LENGTH_SHORT).show();

            }
        });

    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK)) {
            finish();
        }
        return super.onKeyDown(keyCode, event);
    }

    private void viewSignatures() {
        HelperClass helperClass = new HelperClass(this);
        String pdfPath = helperClass.getValue(this, FILE_PATH);
        try {
            openDocument(pdfPath);
            int sigNumber = m_CurrDoc.getSignatureCount();
            signaturesNumber.setText("This PDF has " + sigNumber + " signatures!");
            for (int i = 0; i < sigNumber; i++) {
                TElPDFSignature sig = m_CurrDoc.getSignatureEntry(i);
                RefreshSignatureInfo(sig, false);
            }
        } catch (Exception e) {
            Log.d("Error", e.getMessage());
        }
    }
    private String loadCertificate(String serialNumber) {

        String externalPath=Environment.getExternalStorageDirectory().toString();
        String path = externalPath + "/Download/Private";
        rootPath=externalPath+"/Download/Private/Root/root.crt";
        intermediatePath=externalPath+"/Download/Private/Root/intermediate.crt";
        crlPath=externalPath+"/Download/Private/Crl/mobile.crl";

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
    private static long hexToLong(String hex) {
        return Long.parseLong(hex, 16);
    }

    private void RefreshSignatureInfo(TElPDFSignature sig, boolean revalidate) {
        TElPDFAdvancedPublicKeySecurityHandler handler = null;
        List<String> detailsList = new ArrayList<>();
        if (sig.getHandler() instanceof TElPDFAdvancedPublicKeySecurityHandler) {
            handler = (TElPDFAdvancedPublicKeySecurityHandler) sig.getHandler();
            int test=handler.getHashAlgorithm();
            String sigLoc=sig.getLocation();
            Log.d("sigLoc",sigLoc);
            if (handler.getPAdESSignatureType().equals(TSBPAdESSignatureType.pastBasic)) {
                detailsList.add("Signature type: Basic");
            }
            detailsList.add("Signature name: " + sig.getSignatureName());
            String issuer = "Issuer: " + handler.getCMS().getSignature(0).getSigner().getIssuer().saveToDNString();
            detailsList.add(issuer);
            String sN=byteArrayToHex((handler.getCMS().getSignature(0).getSigner().getSerialNumber()));
            String serialNumber = "serialNumber: " + byteArrayToHex((handler.getCMS().getSignature(0).getSigner().getSerialNumber()));
            detailsList.add(serialNumber);

            try{
                long serial = hexToLong(sN);
                certificatePath=loadCertificate(Long.toString(serial));
            }
            catch (Exception e){
                Log.d("Error",e.getMessage());
            }


            String signTime = "Signing time: " + sig.getSigningTime().toString();
            detailsList.add(signTime);

            String timeStamp = "";
            if (handler.getTimestampCount() > 0) {
                timeStamp = "Timestamp: " + "Yes; " + handler.getTimestamp(0).getTime().toString();
            } else {
                timeStamp = "Timestamp: " + "No";
            }
            detailsList.add(timeStamp);
            String signatureValidity = "";
            TSBCMSAdvancedSignatureValidity validity = handler.getValidationDetails();
            String tryValidate="";
            if(validity.equals(TSBCMSAdvancedSignatureValidity.casvUnknown)){
                try{
                    PrepareValidation(handler);
                }
                catch (Exception e){
                    Log.d("Error",e.getMessage());
                }
                try{
                    //handler.setValidationMoment(new Date());
                    handler.setValidationMoment(SBUtils.utcNow());
                    sig.validate();
                    if(!sig.isDocumentSigned()){
                        tryValidate = "Signature does not cover the entire document (signed revision)";
                    }
                }
                catch (Exception e){
                    Log.d("Error",e.getMessage());
                }
            }
            if (validity.equals(TSBCMSAdvancedSignatureValidity.casvValid)) {
                signatureValidity = "Valid";
            }
            else if(validity.equals(TSBCMSAdvancedSignatureValidity.casvSignatureCorrupted)){
                signatureValidity = "Signature corrupted";
            }
            else if(validity.equals(TSBCMSAdvancedSignatureValidity.casvSignerNotFound)){
                signatureValidity = "Signer not found";
            }
            else if(validity.equals(TSBCMSAdvancedSignatureValidity.casvIncompleteChain)){
                signatureValidity = "Incomplete chain";
            }
            else if(validity.equals(TSBCMSAdvancedSignatureValidity.casvBadCountersignature)){
                signatureValidity = "Bad counters signature";
            }
            else if(validity.equals(TSBCMSAdvancedSignatureValidity.casvBadTimestamp)){
                signatureValidity = "Bad timestamp";
            }
            else if(validity.equals(TSBCMSAdvancedSignatureValidity.casvCertificateExpired)){
                signatureValidity = "Certificate expired";
            }
            else if(validity.equals(TSBCMSAdvancedSignatureValidity.casvCertificateRevoked)){
                signatureValidity = "Certificate revoked";
            }
            else if(validity.equals(TSBCMSAdvancedSignatureValidity.casvCertificateCorrupted)){
                signatureValidity = "Certificate corrupted";
            }
            else if(validity.equals(TSBCMSAdvancedSignatureValidity.casvUntrustedCA)){
                signatureValidity = "Untrusted CA";
            }
            else if(validity.equals(TSBCMSAdvancedSignatureValidity.casvRevInfoNotFound)){
                signatureValidity = "Revocation info not fount";
            }
            else if(validity.equals(TSBCMSAdvancedSignatureValidity.casvTimestampInfoNotFound)){
                signatureValidity = "Timestamp info not found";
            }
            else if(validity.equals(TSBCMSAdvancedSignatureValidity.casvFailure)){
                signatureValidity = "Failure";
            }
            else if(validity.equals(TSBCMSAdvancedSignatureValidity.casvCertificateMalformed)){
                signatureValidity = "Certificate malformed";
            }
            else if(validity.equals(TSBCMSAdvancedSignatureValidity.casvUnknown)){
                signatureValidity = "Unknown";
            }
            else if(validity.equals(TSBCMSAdvancedSignatureValidity.casvChainValidationFailed)){
                signatureValidity = "Chain validation failed";
            }

            detailsList.add("Signature validity: " + signatureValidity+" -- "+tryValidate);
            expandableListDetail.put(sig.getSignatureName(), detailsList);
        }

    }

    private void PrepareValidation(TElPDFAdvancedPublicKeySecurityHandler handler) {
        m_TrustedCerts.clear();
        m_KnownCRLs.clear();

        try {
            root_cert.loadFromFileAuto(rootPath,"");
            //intermediate_cert.loadFromFileAuto(intermediatePath,"");
            TElX509Certificate myCrt=new TElX509Certificate();
            if(!certificatePath.equals("")){
                myCrt.loadFromFileAuto(certificatePath,"");
            }
            Log.d("CrtPath",certificatePath);
            m_TrustedCerts.add(myCrt,false);
            //m_TrustedCerts.add(root_cert,false);
           // m_TrustedCerts.add(intermediate_cert,false);
        }
        catch (Exception e)
        {
            Log.d("Error",e.getMessage());
        }
   /*     try{
            //FileInputStream f = new FileInputStream(crlPath);
            TElFileStream fileStream=new TElFileStream(crlPath,0);
            try
            {
                int r = cert_crl.loadFromStream(fileStream,(int)fileStream.getLength());
                Log.d("R",Integer.toString(r));
            }
            finally
            {
                fileStream.close();
            }

            //m_KnownCRLs.add(cert_crl);
        }catch (Exception e){
            Log.d("Error",e.getMessage());
        }*/
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
            //CertValidator.addKnownCRLs(m_KnownCRLs);
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

    public static String byteArrayToHex(byte[] a) {
        StringBuilder sb = new StringBuilder(a.length * 2);
        for (byte b : a)
            sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private void openDocument(String path) {
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
}

class CustomExpandableListAdapter extends BaseExpandableListAdapter {

    private Context context;
    private List<String> expandableListTitle;
    private HashMap<String, List<String>> expandableListDetail;

    public CustomExpandableListAdapter(Context context, List<String> expandableListTitle,
                                       HashMap<String, List<String>> expandableListDetail) {
        this.context = context;
        this.expandableListTitle = expandableListTitle;
        this.expandableListDetail = expandableListDetail;
    }

    @Override
    public Object getChild(int listPosition, int expandedListPosition) {
        return this.expandableListDetail.get(this.expandableListTitle.get(listPosition))
                .get(expandedListPosition);
    }

    @Override
    public long getChildId(int listPosition, int expandedListPosition) {
        return expandedListPosition;
    }

    @Override
    public View getChildView(int listPosition, final int expandedListPosition,
                             boolean isLastChild, View convertView, ViewGroup parent) {
        final String expandedListText = (String) getChild(listPosition, expandedListPosition);
        if (convertView == null) {
            LayoutInflater layoutInflater = (LayoutInflater) this.context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = layoutInflater.inflate(R.layout.list_item, null);
        }
        TextView expandedListTextView = (TextView) convertView
                .findViewById(R.id.expandedListItem);
        expandedListTextView.setText(expandedListText);
        return convertView;
    }

    @Override
    public int getChildrenCount(int listPosition) {
        return this.expandableListDetail.get(this.expandableListTitle.get(listPosition))
                .size();
    }

    @Override
    public Object getGroup(int listPosition) {
        return this.expandableListTitle.get(listPosition);
    }

    @Override
    public int getGroupCount() {
        return this.expandableListTitle.size();
    }

    @Override
    public long getGroupId(int listPosition) {
        return listPosition;
    }

    @Override
    public View getGroupView(int listPosition, boolean isExpanded,
                             View convertView, ViewGroup parent) {
        String listTitle = (String) getGroup(listPosition);
        if (convertView == null) {
            LayoutInflater layoutInflater = (LayoutInflater) this.context.
                    getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = layoutInflater.inflate(R.layout.list_group, null);
        }
        TextView listTitleTextView = (TextView) convertView
                .findViewById(R.id.listTitle);
        listTitleTextView.setTypeface(null, Typeface.BOLD);
        listTitleTextView.setText(listTitle);
        return convertView;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public boolean isChildSelectable(int listPosition, int expandedListPosition) {
        return true;
    }
}
