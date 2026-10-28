package com.transiva.app;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
/** External links enter the existing startup/security and login flow. */
public class CustomerReferralLinkActivity extends CustomerAdaptiveActivity {
    @Override protected void onCreate(Bundle state){super.onCreate(state);route(getIntent());}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);route(intent);}
    private void route(Intent intent){
        Uri uri=intent==null?null:intent.getData();String code="";
        if(uri!=null){
            boolean web="https".equalsIgnoreCase(uri.getScheme())&&"transiva.my.id".equalsIgnoreCase(uri.getHost())&&"/referral.php".equals(uri.getPath());
            boolean app="transiva".equalsIgnoreCase(uri.getScheme())&&"referral".equalsIgnoreCase(uri.getHost());
            if(web||app)try{code=ReferralCodeRules.normalize(uri.getQueryParameter("code"));}catch(Exception ignored){}
        }
        if(!ReferralCodeRules.valid(code)){Toast.makeText(this,"Tautan referral tidak valid.",Toast.LENGTH_LONG).show();finish();return;}
        CustomerReferralLinkStore.remember(this,code);
        startActivity(new Intent(this,SplashActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));finish();
    }
}
