package com.example.android.cloudsigner;

import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import com.github.barteksc.pdfviewer.PDFView;

import java.io.File;


public class ViewPDFActivity extends AppCompatActivity {
    PDFView pdfView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.viewpdf);
        int width, height;
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        width = dm.widthPixels;
        height = dm.heightPixels;
        getWindow().setLayout((int) (width * .7), (int) (height * .6));

        pdfView = (PDFView) findViewById(R.id.pdfView);
        HelperClass helperClass = new HelperClass(this);
        String path = helperClass.getValue(this, "Path");
        String test = Environment.getExternalStorageDirectory().getAbsolutePath();
        Log.d("Test", test);
        Log.d("Pdf Path", path);
        File file = new File(path);
        try {
            if (file.exists()) {
                pdfView.fromFile(file).load();
            }
            else {
                helperClass.AlertDialogBuilder("No pdf file selected! " +
                        "You must select a pdf file first, to be able to view it!",this,"Error!",false);
            }
        } catch (Exception e) {
            Log.d("Error", e.getMessage());
        }
    }
}
