package com.smstracker.andres;

import java.util.Calendar;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

import android.os.Bundle;
import android.preference.PreferenceManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends SherlockActivity {

	private static Intent serviceIntent;
	private static TextView sentSMS, receivedSMS;
	private static Button resetButton;
	private static SharedPreferences mPrefs;
	private static Calendar calendar = Calendar.getInstance(); 
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        sentSMS = (TextView) findViewById(R.id.tvSent);
        receivedSMS = (TextView) findViewById(R.id.tvReceived);

        resetButton = (Button) findViewById(R.id.bReset);
        
        resetButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				TrackerService.resetCounter(true);
				sentSMS.setText(getString(R.string.SentSMS) + mPrefs.getString("sentSMS", "0"));
		        receivedSMS.setText(getString(R.string.ReceivedSMS) + mPrefs.getString("receivedSMS", "0"));
			}
        });
        
        // Preferencias
        mPrefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

    }

	// Creo el ActionBar con los iconos
    @Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = this.getSupportMenuInflater();
		inflater.inflate(R.menu.actionbar_main, menu);
		return true;
	}
    
    // Al presionar los iconos del ActionBar
    @Override
 	public boolean onOptionsItemSelected(MenuItem item) {
    	switch(item.getItemId()){
    		case R.id.settingsMain:
    			startActivityForResult(new Intent(this, MainPrefs.class), 0);
    			break;
    	}
 		return true;
 	}
    
    @Override
	protected void onPause() {
		Log.i("Status","Pause");
		super.onPause();
	}
    
    @Override
	protected void onResume() {
		Log.i("Status","Resume");
		// Inicio el Servicio
		if(!TrackerService.isRunning){
			serviceIntent = new Intent(MainActivity.this, TrackerService.class);
			startService(serviceIntent);
		}
		
        // Muestro los contadores de mensajes
        sentSMS.setText(getString(R.string.SentSMS) + mPrefs.getString("sentSMS", "0"));
        receivedSMS.setText(getString(R.string.ReceivedSMS) + mPrefs.getString("receivedSMS", "0"));
		
		super.onResume();
	}
}
