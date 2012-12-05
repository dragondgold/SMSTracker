package com.smstracker.andres;

import java.util.Calendar;

import com.actionbarsherlock.app.ActionBar;
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
	private static TextView sentSMS, receivedSMS, history;
	private static Button resetButton;
	private static SharedPreferences mPrefs;
	private static Calendar calendar = Calendar.getInstance(); 
	
	/** ActionBar */
	private static ActionBar actionBar;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        actionBar = getSupportActionBar();
        
        sentSMS = (TextView) findViewById(R.id.tvSent);
        receivedSMS = (TextView) findViewById(R.id.tvReceived);
        
        resetButton = (Button) findViewById(R.id.bReset);
        history = (TextView) findViewById(R.id.tvHistory);
        
        resetButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				TrackerService.resetCounter(true);
				sentSMS.setText(getString(R.string.SentSMS) + mPrefs.getString("sentSMS" + calendar.get(Calendar.YEAR) + calendar.get(Calendar.MONTH), "0"));
		        receivedSMS.setText(getString(R.string.ReceivedSMS) + mPrefs.getString("receivedSMS" + calendar.get(Calendar.YEAR) + calendar.get(Calendar.MONTH), "0"));
			}
        });
        
        // Preferencias
        mPrefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        
        // Calculo cuantos años diferentes tengo (si son más de 12 meses)
        final int years = (int) Math.ceil( (double)Integer.decode(mPrefs.getString("HistoryLength", "0")) / 12d );
        Calendar calendar = Calendar.getInstance();
        
        // Voy pasando por los años en caso de que halla más de 12 meses
    	for(int y = 0; y < years; ++y){
    		// Voy pasando por los meses empezando por el actual-1 hasta los que diga el usuario
	        for(int months = Integer.decode(mPrefs.getString("HistoryLength", "0")), n = 1; n <= months ; ++n){
	    		if( mPrefs.contains( "sentSMS" + (calendar.get(Calendar.YEAR)-y) + (calendar.get(Calendar.MONTH)-n) ) ){
	    			
	    			// Escribo en el TextView la cantidad de mensajes correspondiente al mes y año
	    			// El mes en el calendario es +1 porque por ejemplo en Calendar enero = 0
	    			history.append("" + (calendar.get(Calendar.MONTH)+1) + "/" + (calendar.get(Calendar.YEAR)-y)
	    					+ "   " + getString(R.string.SentSMS) + ": " + mPrefs.getString( "sentSMS" + (calendar.get(Calendar.YEAR)-y) + (calendar.get(Calendar.MONTH)-n), "0")
	    					+ "   " + getString(R.string.ReceivedSMS) + ": " + mPrefs.getString( "receivedSMS" + (calendar.get(Calendar.YEAR)-y) + (calendar.get(Calendar.MONTH)-n), "0")
	    					+ "\n");
	    		}
	        }
    	}

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
        sentSMS.setText(getString(R.string.SentSMS) + mPrefs.getString("sentSMS" + calendar.get(Calendar.YEAR) + calendar.get(Calendar.MONTH), "0"));
        receivedSMS.setText(getString(R.string.ReceivedSMS) + mPrefs.getString("receivedSMS" + calendar.get(Calendar.YEAR) + calendar.get(Calendar.MONTH), "0"));
		
		super.onResume();
	}
}
