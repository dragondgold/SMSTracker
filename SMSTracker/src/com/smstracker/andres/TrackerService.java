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
	
	/** ID que identifica a la notificación que creo */
	private static final int mNotificationID = 0;
	private static ContentResolver contentResolver;
	private static long sent, received;
	/** ID del último mensaje enviado o recibido */
	private static String lastID = "";
	
	private static PendingIntent contentIntent;
	private static NotificationManager mNotificationManager;
	private static NotificationCompat.Builder notificationBuilder;
	private static Context mContext;
	
	private static final int icon = R.drawable.ic_launcher;      		// Icono de la notificación
	private static final CharSequence tickerText = "SMS Counter"; 	// Mensaje que aparece al "saltar" la notificación
	private static final CharSequence contentTitle = "SMS Counter";  	// Titulo de la notificación
	
	private static SharedPreferences mPrefs;
	/** Textos usados en la notificación para indicar los SMS */
	private static String sentSMS, receivedSMS;
	private static Calendar mCalendar;
	private static PendingIntent alarmPendingIntent;
	private static AlarmManager mAlarmManager;
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.i("SMSTrackerService","Service Started");
		
		isRunning = true;
		lastID = "";
		
		// Inicializaciones
		mCalendar = Calendar.getInstance();
		mContext = getApplicationContext();
		
		mPrefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		sent = Long.decode( mPrefs.getString("sentSMS", "0") );
		received = Long.decode( mPrefs.getString("receivedSMS", "0") );
		
		sentSMS = getString(R.string.SentSMSNotification);
		receivedSMS = getString(R.string.ReceivedSMSNotification);	
		
		// ContentResolver para obtener los SMS salientes
		contentResolver = this.getContentResolver();
		contentResolver.registerContentObserver(Uri.parse("content://sms"), true, new mObserver(new Handler()));
		
		// Intent al que se llama al hacer click en la notificación
		contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0);
		
		// Creo la notifiación con el FLAG_ONGOING_EVENT que hace que la notifiación sea persistente
		// y no pueda ser eliminada cuando se limpian las notificaciones
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
		mNotificationManager.notify(mNotificationID, notificationBuilder.getNotification());
		
		// En caso de que nunca se halla creado la preferencia del mes donde se debe reiniciar
		// el contador (primera instalación de la app) verifico:
		if(!mPrefs.contains("resetMonth")){
			// Si aún no llego el dia del reinicio permanezco en el mismo mes
			if(mCalendar.get(Calendar.DAY_OF_MONTH) < mPrefs.getInt("NumberPickerDay", 1)){
				mPrefs.edit().putInt("resetMonth", mCalendar.get(Calendar.MONTH)).apply();
				mPrefs.edit().putInt("resetYear", mCalendar.get(Calendar.YEAR)).apply();
			}
			// Sino ya pasé el dia del reinicio voy al mes siguiente
			else{
				mCalendar.add(Calendar.MONTH, 1);
				mPrefs.edit().putInt("resetMonth", mCalendar.get(Calendar.MONTH)).apply();
				mPrefs.edit().putInt("resetYear", mCalendar.get(Calendar.YEAR)).apply();
			}
		}
		
		// Creo un calendario, seteo el dia de reinicio a las 00:00:00 PM  
		// para que cuando sea el dia del reinicio salte el Receiver de la alarma para reiniciar
		mCalendar.set(Calendar.YEAR, mPrefs.getInt("resetYear", 0));								// Año
		mCalendar.set(Calendar.MONTH, mPrefs.getInt("resetMonth", mCalendar.get(Calendar.MONTH)));	// Mes
		mCalendar.set(Calendar.DAY_OF_MONTH, mPrefs.getInt("NumberPickerDay", 1));					// Dia
		mCalendar.set(Calendar.HOUR_OF_DAY, 0);			// Hora
		mCalendar.set(Calendar.MINUTE, 0);				// Minutos
		mCalendar.set(Calendar.SECOND, 0);				// Segundos
		
		Log.i("SMSTrackerCalendar", "Mes: " + mCalendar.get(Calendar.MONTH));
		Log.i("SMSTrackerCalendar", "Dia: " + mCalendar.get(Calendar.DAY_OF_MONTH));
		Log.i("SMSTrackerCalendar", "Año: " + mCalendar.get(Calendar.YEAR));
		Log.i("SMSTrackerCalendar", "Hora: " + mCalendar.get(Calendar.HOUR));
		Log.i("SMSTrackerCalendar", "Minuto: " + mCalendar.get(Calendar.MINUTE));
		Log.i("SMSTrackerCalendar", "Segundo: " + mCalendar.get(Calendar.SECOND));
		
		// Creo un Intent con la clase que se va a llamar al dispararse la alarma
		Intent alarmIntent = new Intent(this, AlarmReceiver.class);
		// Creo un PendingIntent con un ID y con el FLAG_CANCEL_CURRENT por si hay otra alarma con el mismo ID que se cancele
		// y se ponga esta que llama a un BroadcastReceiver
		alarmPendingIntent = PendingIntent.getBroadcast(this, mNotificationID, alarmIntent, PendingIntent.FLAG_CANCEL_CURRENT);
		// Creo una alarma y la seteo en donde dice el calendario que configuré arriba
		mAlarmManager =  (AlarmManager) getSystemService(Activity.ALARM_SERVICE);
		mAlarmManager.set(AlarmManager.RTC_WAKEUP, mCalendar.getTimeInMillis(), alarmPendingIntent);
		
		return super.onStartCommand(intent, flags, startId);
	}
	
	/**
	 * Actualiza los parametros cuando se cambia el dia de reinicio
	 * @param newDay nuevo día para el reinicio de los contadores
	 */
	public static void dayChanged (int newDay){
		
		Log.i("SMSTrackerPreferences", "Day Changed from: " + mPrefs.getInt("NumberPickerDay", 1) +
				" to " + newDay);
		mPrefs.edit().putInt("NumberPickerDay", newDay).apply();
		
		Calendar now = Calendar.getInstance();

		// Si aún no llego el dia del reinicio permanezco en el mismo mes
		if(mCalendar.before(now)){
			mCalendar.set(Calendar.DAY_OF_MONTH, newDay);
			mAlarmManager.set(AlarmManager.RTC_WAKEUP, mCalendar.getTimeInMillis(), alarmPendingIntent);
		}
		// Pero si me pase del dia de reinicio que habia antes voy al mes siguiente
		else{
			mCalendar.set(Calendar.DAY_OF_MONTH, newDay);
			// Al mes actual le sumo 1 (siguiente mes)
			mCalendar.set(Calendar.MONTH, now.get(Calendar.MONTH));
			mCalendar.add(Calendar.MONTH, 1);
			
			// Guardo las nueva preferencias
			mPrefs.edit().putInt("resetMonth", mCalendar.get(Calendar.MONTH)).apply();
			mPrefs.edit().putInt("resetYear", mCalendar.get(Calendar.YEAR)).apply();
			
			// Seteo el receiver de la alarma nuevamente
			mAlarmManager.set(AlarmManager.RTC_WAKEUP, mCalendar.getTimeInMillis(), alarmPendingIntent);
			Log.i("SMSTrackerPreferences", "Siguiente mes");
		}
		
		Log.i("SMSTrackerCalendar", "Mes: " + mCalendar.get(Calendar.MONTH));
		Log.i("SMSTrackerCalendar", "Dia: " + mCalendar.get(Calendar.DAY_OF_MONTH));
		Log.i("SMSTrackerCalendar", "Año: " + mCalendar.get(Calendar.YEAR));
		Log.i("SMSTrackerCalendar", "Hora: " + mCalendar.get(Calendar.HOUR));
		Log.i("SMSTrackerCalendar", "Minuto: " + mCalendar.get(Calendar.MINUTE));
		Log.i("SMSTrackerCalendar", "Segundo: " + mCalendar.get(Calendar.SECOND));
	}
	
	/**
	 * Actualiza el valor del limite de SMS
	 * @param value es el nuevo valor de la preferencia (numero máximo de mensajes) que se cambió
	 */
	public static void updateSMSLimit (String value){
		Log.i("SMSTrackerPreferences", "UpdateSMSLimit to " + value);
		
		notificationBuilder.setContentText(sentSMS + " " + sent +
					   "/" + value + " -- " + receivedSMS + " " + received);
		mNotificationManager.notify(mNotificationID, notificationBuilder.getNotification());
	}
	
	/**
	 * Actualiza la cantidad de mensajes enviados
	 * @param value es el nuevo valor de la preferencia (numero de mensajes enviados) que se cambió
	 */
	public static void updateSentSMS (String value){
		Log.i("SMSTrackerPreferences", "UpdateSentSMS to " + value);
		
		sent = Long.decode(value);

		notificationBuilder.setContentText(sentSMS + " " + sent +
				   "/" + mPrefs.getString("SMSSentLimit", "0") + " -- " + receivedSMS + " " + received);
		mNotificationManager.notify(mNotificationID, notificationBuilder.getNotification());
	}
	
	/**
	 * Actualiza la cantidad de mensajes recibidos
	 * @param value es el nuevo valor de la preferencia (numero de mensajes recibidos) que se cambió
	 */
	public static void updateReceivedSMS (String value){
		Log.i("SMSTrackerPreferences", "UpdateReceivedSMS to " + value);
		
		received = Long.decode(value);
		
		notificationBuilder.setContentText(sentSMS + " " + sent +
				   "/" + mPrefs.getString("SMSSentLimit", "0") + " -- " + receivedSMS + " " + received);
		mNotificationManager.notify(mNotificationID, notificationBuilder.getNotification());
	}
	
	/**
	 * Reinicia los contadores por el cambio de mes
	 * @param manual true si se hace un reinicio manual, false si se reinicia porque se llego a la fecha de reinicio
	 */
	public static void resetCounter (boolean manual){
		Log.i("SMSTrackerPreferences", "Counter Reset -- Month: " + mCalendar.get(Calendar.MONTH));
		
		// Reinicio los contadores
		mPrefs.edit().putString("sentSMS", "0").apply();
		mPrefs.edit().putString("receivedSMS", "0").apply();
		sent = received = 0;
		
		// Si el reinicio fue porque se llegó al mes de reinicio
		if(!manual){
			// Seteo la alarma para el siguiente mes
			mCalendar.add(Calendar.MONTH, 1);
			mAlarmManager.set(AlarmManager.RTC_WAKEUP, mCalendar.getTimeInMillis(), alarmPendingIntent);
			// Guardo el mes y año donde será el proximo reinicio
			mPrefs.edit().putInt("resetMonth", mCalendar.get(Calendar.MONTH)).apply();
			mPrefs.edit().putInt("resetYear", mCalendar.get(Calendar.YEAR)).apply();
		}
		
		// Actualizo la notificación
		notificationBuilder.setContentText(sentSMS + " " + sent +
				   "/" + mPrefs.getString("SMSSentLimit", "0") + " -- " + receivedSMS + " " + received);
		mNotificationManager.notify(mNotificationID, notificationBuilder.getNotification());
		
		Log.i("SMSTrackerCalendar", "Mes: " + mCalendar.get(Calendar.MONTH));
		Log.i("SMSTrackerCalendar", "Dia: " + mCalendar.get(Calendar.DAY_OF_MONTH));
		Log.i("SMSTrackerCalendar", "Año: " + mCalendar.get(Calendar.YEAR));
		Log.i("SMSTrackerCalendar", "Hora: " + mCalendar.get(Calendar.HOUR));
		Log.i("SMSTrackerCalendar", "Minuto: " + mCalendar.get(Calendar.MINUTE));
		Log.i("SMSTrackerCalendar", "Segundo: " + mCalendar.get(Calendar.SECOND));
	}
	
	// Observer que obtiene los SMS salientes y entrantes
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
	        Log.i("SMSTracker","onChange()");
	        
	        // Obtengo los mensajes que se recibierion o enviaron
	        Uri uriSMS = Uri.parse("content://sms");
	        Cursor cur = contentResolver.query(uriSMS, null, null, null, null);
	        cur.moveToNext();
	        
	        final String[] names = cur.getColumnNames();
	        for(int n = 0; n < cur.getColumnNames().length; ++n){
		        Log.i("SMSTracker", "Column[" + n + "] = " + names[n] + " --> " + cur.getString(cur.getColumnIndex(names[n])));
	        }      
	        Log.i("SMSTracker", "SMS length: " + cur.getString(cur.getColumnIndex("body")).length() );
	               
	        final int type = cur.getInt(cur.getColumnIndex("type"));
	        
	        // type == 2 significa un mensaje enviado SATISFACTORIAMENTE y type==1 un mensaje recibido
	        // Verifico tmb que el id del mensaje no sea el mismo que el anterior (para evitar contar varias veces un
	        // mismo mensaje) ya que cada mensaje tiene un id único
	        if( (type == 2 || type == 1) && (!lastID.contentEquals(cur.getString(cur.getColumnIndex("_id")))) ){
		        String protocol = cur.getString(cur.getColumnIndex("protocol"));
		        String smsString = cur.getString(cur.getColumnIndex("body"));
		        lastID = cur.getString(cur.getColumnIndex("_id"));
		        
		        // Mensaje enviado
		        if(protocol == null){
		        	// Sumo el numero de paginas
		        	sent += numberOfSMSPages(smsString);
		        	mPrefs.edit().putString("sentSMS", ""+sent ).apply();
		        	
		        	notificationBuilder.setContentText(sentSMS + " " + sent +
							   "/" + mPrefs.getString("SMSSentLimit", "0") + " -- " + receivedSMS + " " + received);
		        	// Alerto si me pase del número máximo de msjs
		        	if(sent > Long.decode(mPrefs.getString("SMSSentLimit", "0"))){
		        		Toast.makeText(mContext, getString(R.string.SMSSentLimitExceed), Toast.LENGTH_LONG).show();
		        	}
		        	
		            Log.i("SMSTracker", "SMS Sent - count: " + sent);
		        }
		        // Mensaje recibido
		        else{
		        	// Sumo el numero de paginas
		        	received += numberOfSMSPages(smsString);
		            mPrefs.edit().putString("receivedSMS", ""+received).apply();
		            
		        	notificationBuilder.setContentText(sentSMS + " " + sent +
							   "/" + mPrefs.getString("SMSSentLimit", "0") + " -- " + receivedSMS + " " + received);
		            
		        	Log.i("SMSTracker", "SMS received - count: " + received);
		        }
	        	mNotificationManager.notify(mNotificationID, notificationBuilder.getNotification());
	        }
	    }
	}
	
	private static int numberOfSMSPages (final String mString){
		final int stringLenght = mString.length();
		if(hasForbiddenChars(mString, 0, mString.length())){
			return (int)Math.ceil(stringLenght/2);
		}
		else{
			return (int)Math.ceil(stringLenght/160);
		}
	}
	
	private static boolean hasForbiddenChars (final String mString, int start, int end){
		final char[] acceptedCharacters = {'@','Δ',' ','0','¡','P','¿','p',
				'£','_','!','1','A','Q','a','q',
				'$','Φ','"','2','B','R','b','r',
				',','¥','Γ','#','3','C','S','c',
				's','è','Λ','¤','4','D','T','d',
				't','é','Ω','%','5','E','U','e',
				'u','ù','Π','&','6','F','V','f',
				'v','ì','Ψ','\'','7','G','W','g',
				'w','ò','Σ','(','8','H','X','h',
				'x','Ç','Θ',')','9','I','Y','i',
				'y','\n','Ξ','*',':','J','Z','j',
				'z','Ø','\u010B','+',';','K','Ä',
				'k','ä','ø','Æ',',','<','L','Ö','l',
				'ö','\r','æ','-','=','M','Ñ','m',
				'ñ','Å','ß','.','>','N','Ü','n','ü',
				'å','É','/','?','O','§','o','à',
				'|','Á','á','^','Ú','€','ú','{','}',
				'ç','Í','í','[',']','~','\\','Ó','ó'};
		final String mChars = new String(acceptedCharacters);
		
		for(int n = start; n < end; ++n){
			if(!mChars.contains(""+mString.charAt(n))) return true;
		}
		return false;
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		isRunning = false;
		Log.i("SMSTracker","Service Destroy");
	}

	@Override
	public IBinder onBind(Intent intent) {
		Log.i("SMSTracker","Service Bind");
		return null;
	}
	
}
