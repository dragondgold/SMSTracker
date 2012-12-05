package com.smstracker.andres;

import java.util.Calendar;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

public class TrackerService extends Service{

	// Nombre del service
	public static final String mAction = "SMSTracker";
	public static boolean isRunning = false;
	
	private static final int Notification_ID = 0;
	private static ContentResolver contentResolver;
	private static long sent, received;
	private static String lastID = "";
	
	private static Intent notificationIntent;
	private static PendingIntent contentIntent;
	private static NotificationManager mNotificationManager;
	private static NotificationCompat.Builder notificationBuilder;
	private static Context mContext;
	
	private static final int icon = R.drawable.ic_launcher;      		// Icono de la notificación
	private static final CharSequence tickerText = "SMS Counter"; 		// Mensaje que aparece al "saltar" la notificación
	private static final CharSequence contentTitle = "SMS Counter";  		// Titulo de la notificación
	
	private static SharedPreferences mPrefs;
	private static String sentSMS, receivedSMS;
	private static Calendar calendar;
	private static Calendar curTime;
	private static PendingIntent alarmPendingIntent;
	private static AlarmManager mAlarmManager;
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.i("Status","Service Start");
		
		// Inicializaciones
		calendar = Calendar.getInstance();
		curTime = Calendar.getInstance();
		isRunning = true;
		mPrefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		sent = Long.decode( mPrefs.getString("sentSMS" + curTime.get(Calendar.YEAR) + curTime.get(Calendar.MONTH), "0") );
		received = Long.decode( mPrefs.getString("receivedSMS" + curTime.get(Calendar.YEAR) + curTime.get(Calendar.MONTH), "0") );
		lastID = "";
		sentSMS = getString(R.string.SentSMSNotification);
		receivedSMS = getString(R.string.ReceivedSMSNotification);
		
		// Si se viene de una versión anterior que no toma en cuenta la fecha, esto edita automáticamente la base de datos de SharedPreferences
		if(mPrefs.contains("sentSMS")){
			String temp = mPrefs.getString("sentSMS", "0");
			mPrefs.edit().remove("sentSMS").apply();
			mPrefs.edit().putString("sentSMS" + curTime.get(Calendar.YEAR) + curTime.get(Calendar.MONTH), temp);
		}
		if(mPrefs.contains("receivedSMS")){
			String temp = mPrefs.getString("receivedSMS", "0");
			mPrefs.edit().remove("receivedSMS").apply();
			mPrefs.edit().putString("receivedSMS" + curTime.get(Calendar.YEAR) + curTime.get(Calendar.MONTH), temp);
		}		
		
		// ContentResolver para obtener los SMS salientes
		contentResolver = this.getContentResolver();
		contentResolver.registerContentObserver(Uri.parse("content://sms"), true, new mObserver(new Handler()));
		
		// Context
		mContext = getApplicationContext();
		
		// Intent al que se llama al hacer click en la notificación
		notificationIntent = new Intent(this, MainActivity.class);
		contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
		
		// Creo la notifiación con el FLAG_ONGOING_EVENT que hace que la notifiación sea persistente
		mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		notificationBuilder = new NotificationCompat.Builder(mContext);
		notificationBuilder.setContentTitle(contentTitle)
						   .setContentText(sentSMS + " " + sent +
								   "/" + mPrefs.getString("SMSSentLimit", "0") + " -- " + receivedSMS + " " + received)
						   .setSmallIcon(icon)
						   .setContentIntent(contentIntent)
						   .setOngoing(true)
						   .setTicker(tickerText);
		
		// Muestra la notificación
		mNotificationManager.notify(Notification_ID, notificationBuilder.getNotification());
		
		// En caso de que nunca se halla creado la preferencia del mes donde se debe reincir (primera instalación de la app) verifico:
		// Si aún no llego el dia del reinicio permanezco en el mismo mes
		// Sino ya pasé el dia del reinicio voy al mes siguiente
		if(!mPrefs.contains("resetMonth")){
			if(calendar.get(Calendar.DAY_OF_MONTH) < mPrefs.getInt("NumberPickerDay", 1)){
				mPrefs.edit().putInt("resetMonth", calendar.get(Calendar.MONTH)).apply();
			}
			else{
				calendar.add(Calendar.MONTH, 1);
				mPrefs.edit().putInt("resetMonth", calendar.get(Calendar.MONTH)).apply();
			}
		}
		
		// Creo un calendario, seteo el dia de reinicio a las 00:00:00 PM  para que cuando sea el dia del reinicio salte la alarma
		calendar.set(Calendar.MONTH, mPrefs.getInt("resetMonth", calendar.get(Calendar.MONTH)));		// Mes
		calendar.set(Calendar.DAY_OF_MONTH, mPrefs.getInt("NumberPickerDay", 1));						// Dia
		calendar.set(Calendar.HOUR_OF_DAY, 0);			// Hora
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		
		// Creo un Intent con la clase que se va a llamar al dispararse la alarma
		Intent alarmIntent = new Intent(this, AlarmReceiver.class);
		// Creo un PendingIntent con un ID y con el FLAG_CANCEL_CURRENT por si hay otra alarma con el mismo ID que se cancele y se ponga esta que
		// llama a un BroadcastReceiver (no una Activity porq no tengo que mostrar nada)
		alarmPendingIntent = PendingIntent.getBroadcast(this, Notification_ID, alarmIntent, PendingIntent.FLAG_CANCEL_CURRENT);
		// Creo una alarma y la seteo en donde dice el calendario que configuré arriba (1 mes)
		mAlarmManager =  (AlarmManager) getSystemService(Activity.ALARM_SERVICE);
		mAlarmManager.set(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), alarmPendingIntent);
		
		return super.onStartCommand(intent, flags, startId);
	}
	
	/**
	 * Actualiza los parametros cuando se cambia el dia de reinicio
	 * @param newDay nuevo día para el reinicio de los contadores
	 */
	public static void dayChanged (int newDay){
		
		mPrefs.edit().putInt("NumberPickerDay", newDay).apply();
		
		Log.i("SMSStatus", "Day Changed -- newDay: " + newDay);
		Calendar cal = Calendar.getInstance();
		
		// Si aún no llego el dia del reinicio permanezco en el mismo mes
		// Pero si me pase del dia de reinicio que habia antes voy al mes siguiente
		if(cal.get(Calendar.DAY_OF_MONTH) < newDay){
			calendar.set(Calendar.DAY_OF_MONTH, newDay);
			calendar.set(Calendar.MONTH, cal.get(Calendar.MONTH));
			mAlarmManager.set(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), alarmPendingIntent);
		}
		else{
			calendar.set(Calendar.DAY_OF_MONTH, newDay);
			calendar.set(Calendar.MONTH, cal.get(Calendar.MONTH));		// Al mes actual le sumo 1 (siguiente mes)
			calendar.add(Calendar.MONTH, 1);
			mPrefs.edit().putInt("resetMonth", calendar.get(Calendar.MONTH)).apply();
			mAlarmManager.set(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), alarmPendingIntent);
		}
		
		Log.i("SMSStatus", "Month: " + calendar.get(Calendar.MONTH));
		Log.i("SMSStatus", "Day: " + calendar.get(Calendar.DAY_OF_MONTH));
		Log.i("SMSStatus", "Hour: " + calendar.get(Calendar.HOUR));
		Log.i("SMSStatus", "Minute: " + calendar.get(Calendar.MINUTE));
	}
	
	/**
	 * Actualiza el valor del limite de SMS
	 * @param value es el valor de la preferencia (numero máximo de mensajes) que se cambió
	 */
	public static void updateSMSLimit (String value){
		Log.i("SMSStatus", "UpdateSMSLimit");
		notificationBuilder.setContentText(sentSMS + " " + sent +
					   "/" + value + " -- " + receivedSMS + " " + received);
		mNotificationManager.notify(Notification_ID, notificationBuilder.getNotification());
	}
	
	/**
	 * Actualiza la cantidad de mensajes enviados
	 * @param value
	 */
	public static void updateSentSMS (String value){
		Log.i("SMSStatus", "UpdateSentSMS");
		mPrefs.edit().putString("sentSMS" + curTime.get(Calendar.YEAR) + curTime.get(Calendar.MONTH), value).apply();
		sent = Long.decode(value);

		notificationBuilder.setContentText(sentSMS + " " + sent +
				   "/" + mPrefs.getString("SMSSentLimit", "0") + " -- " + receivedSMS + " " + received);
		mNotificationManager.notify(Notification_ID, notificationBuilder.getNotification());
	}
	
	/**
	 * Actualiza la cantidad de mensajes recibidos
	 * @param value
	 */
	public static void updateReceivedSMS (String value){
		Log.i("SMSStatus", "UpdateReceivedSMS");
		mPrefs.edit().putString("receivedSMS" + curTime.get(Calendar.YEAR) + curTime.get(Calendar.MONTH), value).apply();
		received = Long.decode(value);
		
		notificationBuilder.setContentText(sentSMS + " " + sent +
				   "/" + mPrefs.getString("SMSSentLimit", "0") + " -- " + receivedSMS + " " + received);
		mNotificationManager.notify(Notification_ID, notificationBuilder.getNotification());
	}
	
	/**
	 * Reinicia los contadores por el cambio de mes
	 * @param manual true si se hace un reinicio manual, false si se reinicia porque se llego a la fecha de reinicio
	 */
	public static void resetCounter (boolean manual){
		Log.i("SMSStatus", "Counter Reset -- Month: " + calendar.get(Calendar.MONTH));
		// Reinicio los contadores
		mPrefs.edit().putString("sentSMS" + curTime.get(Calendar.YEAR) + curTime.get(Calendar.MONTH), "0").apply();
		mPrefs.edit().putString("receivedSMS" + curTime.get(Calendar.YEAR) + curTime.get(Calendar.MONTH), "0").apply();
		sent = received = 0;
		
		if(!manual){
			// Seteo la alarma para el siguiente mes
			calendar.add(Calendar.MONTH, 1);
			mAlarmManager.set(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), alarmPendingIntent);
			// Guardo el mes donde será el proximo reinicio
			mPrefs.edit().putInt("resetMonth", calendar.get(Calendar.MONTH)).apply();
		}
		
		// Actualizo la notificación
		notificationBuilder.setContentText(sentSMS + " " + sent +
				   "/" + mPrefs.getString("SMSSentLimit", "0") + " -- " + receivedSMS + " " + received);
		mNotificationManager.notify(Notification_ID, notificationBuilder.getNotification());
		
		Log.i("SMSStatus", "Month: " + calendar.get(Calendar.MONTH));
		Log.i("SMSStatus", "Day: " + calendar.get(Calendar.DAY_OF_MONTH));
		Log.i("SMSStatus", "Hour: " + calendar.get(Calendar.HOUR));
		Log.i("SMSStatus", "Minute: " + calendar.get(Calendar.MINUTE));
	}
	
	/**
	 * Observer que obtiene los SMS salientes y entrantes
	 */
	class mObserver extends ContentObserver {
		
		// Constructor
	    public mObserver(Handler handler) {
	        super(handler);
	    }

	    @Override
	    public void onChange(boolean selfChange) {
	        super.onChange(selfChange);
	        /**
	         * ADVERTENCIA!!!!! El tag "SMS" hará que los logs no se muestren, supongo que se debe a que Android
	         * ya lo usa para otra cosa no usar el tag "SMS" o el log no aparecerá en el LogCat
	         */
	        Log.i("SMSStatus","onChange");
	        
	        // Obtengo los mensajes que se recibierion o enviaron
	        Uri uriSMS = Uri.parse("content://sms");
	        Cursor cur = contentResolver.query(uriSMS, null, null, null, null);
	        cur.moveToNext();
	        
	        final String[] names = cur.getColumnNames();
	        for(int n = 0; n < cur.getColumnNames().length; ++n){
		        Log.i("SMSStatus", "Column[" + n + "] = " + names[n] + " --> " + cur.getString(cur.getColumnIndex(names[n])));
	        }      
	        Log.i("SMSStatus", "SMS length: " + cur.getString(cur.getColumnIndex("body")).length() );
	               
	        final int type = cur.getInt(cur.getColumnIndex("type"));
	        
	        // type == 2 significa un mensaje enviado satisfactoriamente y type==1 un mensaje recibido
	        // Verifico tmb que el id del mensaje no sea el mismo que el anterior (para evitar contar varias veces un
	        // mismo mensaje) ya que cada mensaje tiene un id único
	        if( (type == 2 || type == 1) && (!lastID.contentEquals(cur.getString(cur.getColumnIndex("_id")))) ){
		        String protocol = cur.getString(cur.getColumnIndex("protocol"));
		        int length = cur.getString(cur.getColumnIndex("body")).length();
		        lastID = cur.getString(cur.getColumnIndex("_id"));
		        // Mensaje enviado
		        if(protocol == null){
		        	sent += Math.ceil((double)length / 160d);
		        	mPrefs.edit().putString("sentSMS" + curTime.get(Calendar.YEAR) + curTime.get(Calendar.MONTH), ""+sent ).apply();
		        	
		        	notificationBuilder.setContentText(sentSMS + " " + sent +
							   "/" + mPrefs.getString("SMSSentLimit", "0") + " -- " + receivedSMS + " " + received);
		        	// Alerto si me pase del número máximo de msjs
		        	if(sent > Long.decode(mPrefs.getString("SMSSentLimit", "0"))){
		        		Toast.makeText(mContext, getString(R.string.SMSSentLimitExceed), Toast.LENGTH_LONG).show();
		        	}
		        	
		            Log.i("SMSStatus", "SMS Sent, count: " + sent);
		        }
		        // Mensaje recibido
		        else{
		        	received += Math.ceil((double)length / 160d);
		            mPrefs.edit().putString("receivedSMS" + curTime.get(Calendar.YEAR) + curTime.get(Calendar.MONTH), ""+received).apply();
		            
		        	notificationBuilder.setContentText(sentSMS + " " + sent +
							   "/" + mPrefs.getString("SMSSentLimit", "0") + " -- " + receivedSMS + " " + received);
		            
		        	Log.i("SMSStatus", "SMS received, count: " + received);
		        }
	        	mNotificationManager.notify(Notification_ID, notificationBuilder.getNotification());
	        }
	    }
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		isRunning = false;
		Log.i("Status","Service Destroy");
	}

	@Override
	public IBinder onBind(Intent intent) {
		Log.i("Status","Service Bind");
		return null;
	}
	
}
