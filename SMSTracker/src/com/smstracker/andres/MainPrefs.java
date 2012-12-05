package com.smstracker.andres;

import com.michaelnovakjr.numberpicker.NumberPickerDialog;
import com.michaelnovakjr.numberpicker.NumberPickerDialog.OnNumberSetListener;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.util.Log;

public class MainPrefs extends PreferenceActivity{

	private static SharedPreferences mPrefs;
	
	@SuppressWarnings("deprecation")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		this.addPreferencesFromResource(R.xml.main_preferences);
		mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		
		this.findPreference("SMSSentLimit").setOnPreferenceChangeListener(new OnPreferenceChangeListener(){
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				TrackerService.updateSMSLimit(newValue.toString());
				return true;
			}
		});
		
		this.findPreference("sentSMS").setOnPreferenceChangeListener(new OnPreferenceChangeListener(){
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				TrackerService.updateSentSMS(newValue.toString());
				return true;
			}
		});
		
		this.findPreference("receivedSMS").setOnPreferenceChangeListener(new OnPreferenceChangeListener(){
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				TrackerService.updateReceivedSMS(newValue.toString());
				return true;
			}
		});
		
		this.findPreference("NumberPickerDay").setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				
				NumberPickerDialog pickerDialog = new NumberPickerDialog(MainPrefs.this, -1, mPrefs.getInt("NumberPickerDay", 1));
	            pickerDialog.setTitle(getString(R.string.NumberPickerTitle));
	            
	            pickerDialog.setOnNumberSetListener(new OnNumberSetListener(){
					@Override
					public void onNumberSet(int selectedNumber) {
						Log.i("Preference", "NumberPicker Changed");
						TrackerService.dayChanged(selectedNumber);
					}
	            });
	            pickerDialog.show();
				
				Log.i("Preference", "NumberPicker Clicked");
				return true;
			}
		});
		
	}
	
}
