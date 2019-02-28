package com.example.android.cloudsigner;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class OTPpopUp extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.otppopup);
        int width,height;
        DisplayMetrics dm=new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        width=dm.widthPixels;
        height=dm.heightPixels;

        getWindow().setLayout((int)(width*.7),(int)(height*.6));

        SendOTP();
    }

    public void SendOTP()
    {
        Button btn=(Button) findViewById(R.id.btnOtpSend);
        EditText otpCode=(EditText)findViewById(R.id.editOtpText);

        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(OTPpopUp.this,SecondActivity.class));
                finish();
            }
        });
    }

}
