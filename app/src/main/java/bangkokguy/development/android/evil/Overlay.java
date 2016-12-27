package bangkokguy.development.android.evil;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import static android.content.Intent.ACTION_BATTERY_CHANGED;
import static android.content.Intent.ACTION_SCREEN_OFF;
import static android.content.Intent.ACTION_SCREEN_ON;
import static android.os.BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER;
import static android.os.BatteryManager.BATTERY_STATUS_CHARGING;
import static android.os.BatteryManager.BATTERY_STATUS_UNKNOWN;
import static android.support.v4.app.NotificationCompat.DEFAULT_LIGHTS;

/* DONE:    Battery receivert ki lehet kapcsolni, ha screen off és not charging
 *            --> nem lehet, mert akkor ha a chargert bedugják
 *            (és nem kapcsolja be a képernyőt), nem fogja érzékelni
 * DONE:    Animációt megcsinálni (bar view)
 * DONE:    Tesztelni, hogy install után be van-e kapcsolva a battery bar
 * DONE:    Verziót kezelni, és a message summa sorába kiírni
 * NextRel.:Esetleg többképernyős setup?...
 * DONE:    Tesztelni, hogy miért nem kapcsol ki a led ha a telefon charge-ból discharge-ba megy
 * DONE:    Kiirni a messageba, hogy mi volt az utolso leallas oka
 * DONE:    mHandler a battery eventbe...
 * NextRel.:Battery bar process animation color should depend on bar color
 */

/**---------------------------------------------------------------------------
 * Main Service to draw the battery line and set the led color
 */
public class Overlay extends Service {

    final static String TAG="Overlay";
    final static boolean DEBUG=true;

    final static int ONMS=255;
    final static int OFFMS=0;
    final static int OPAQUE=0xff;
    final static int MAX_STROKE_WIDTH = 0x0f;

    NotificationManagerCompat nm;
    BatteryManager bm;
    WindowManager wm;

    public DrawView barView;
    ReceiveBroadcast receiveBroadcast;

    int screenWidth;
    int screenHeight;

    String  versionName = "";
    String  stopCode = "";
    boolean showOverlay;
    boolean stopService = false;
    boolean isBatteryCharging = false;
    boolean isFastCharging = false;

    int 	eHealth = -1;       //battery health
    int 	eIconSmall = -1;    //resource ID of the small battery icon
    int 	eLevel = -1;        //battery percentage
    int 	ePlugged = -1;      //0=battery
    boolean ePresent = true;    //true if battery present
    int     eScale = -1;        //the maximum battery level
    int 	eStatus = -1;       //the current status constant
    String 	eTechnology = "";   //the technology of the current battery
    int     eTemperature = -1;  //the current battery temperature
    int 	eVoltage = -1;      //the current battery voltage level
    boolean	eLEDon = false;     //whether use led or not

    SharedPreferences preferences;
    SharedPreferences sharedPref;

    Handler mHandler;
    MyRunnable myRunnable;

    public Overlay() {
    }

    /**---------------------------------------------------------------------------
     * Register the broadcast receiver for
     * - battery events
     * - screen on
     * - screen off
     * and then show the initial notification.
     * Possible improvement for API level 23 and above:
     *      this.registerReceiver(
     *           receiveBroadcast,
     *           new IntentFilter(ACTION_CHARGING));
     *
     *      this.registerReceiver(
     *           receiveBroadcast,
     *           new IntentFilter(ACTION_DISCHARGING));
     */
    @Override
    public void onCreate() {
        if(DEBUG)Log.d(TAG, "OnCreate()");

        mHandler = new Handler();
        myRunnable = new MyRunnable();
        myRunnable.setContext(this);

        try {
            versionName = getPackageManager().getPackageInfo(getPackageName(), 0)
                    .versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        sharedPref = this.getSharedPreferences(
                getString(R.string.preference_file_key), Context.MODE_PRIVATE);

        nm = NotificationManagerCompat.from(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            bm = (BatteryManager) this.getSystemService(Context.BATTERY_SERVICE);
        }

        barView = initBarView(this);
        myRunnable.setBarView(barView);
        receiveBroadcast = new ReceiveBroadcast();

        this.registerReceiver(
                receiveBroadcast,
                new IntentFilter(ACTION_BATTERY_CHANGED));

        this.registerReceiver(
                receiveBroadcast,
                new IntentFilter(ACTION_SCREEN_OFF));

        this.registerReceiver(
                receiveBroadcast,
                new IntentFilter(ACTION_SCREEN_ON));

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.registerOnSharedPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        MyRunnable.OnFinishListener onFinishListener = new MyRunnable.OnFinishListener() {
            @Override
            public void onFinish() {
                if(myRunnable.isCanceled()) {
                    barView.setColor(argbLedColor(getBatteryPercent()));
                    barView.setLength(screenWidth*getBatteryPercent()/100);
                    barView.invalidate();
                }
            }
        };
        myRunnable.setOnFinishListener(onFinishListener);


    }

    /**---------------------------------------------------------------------------
     * Depending on the Battery percentage delivers the calculated LED color.
     * @param percent the battery percent (int)
     * @return the argb LED color (int)
     */
    public int argbLedColor (int percent) {
        int j = (percent/(100/6));
        int r, g, b;
        int grade=((percent%(100/6))*255/(100/6));

        switch (j) { // @formatter:off
            case 0: r = 255;        g = 0;              b = 255-grade;  break;//0-16 pink_to_red         255,0:255,0
            case 1: r = 255;        g = (grade/2);      b = 0;          break;//17-33 red_to_orange      255:0,255,0
            case 2: r = 255;        g = 128+(grade/2);  b = 0;          break;//34-50 orange_to_yellow   0,255,0:255
            case 3: r = 255-grade;  g = 255;            b = 0;          break;//51-66 yellow_to_green    0,255:0,255
            case 4: r = 0;          g = 255;            b = grade;      break;//67-83 green_to_cyan      0:255,0,255
            case 5: r = 0;          g = 255-grade;      b = 255;        break;//84-100 cyan_to_blue
            default:r = 200;        g = 200;            b = 200;        break;//gray if full
        } //@formatter:on

        return Color.argb(OPAQUE, r, g, b);
    }

    /**---------------------------------------------------------------------------
     * Retrieves the battery % from the os.
     * @return battery percentage (int)
     */
    int getBatteryPercent () {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        } else
            return eLevel;
    }

    /**---------------------------------------------------------------------------
     * Shows a sticky notification. This one will be changed every time the os invokes
     * the battery changed event listener. The LED color will be
     * set according to the battery percentage.
     */
    void showNotification() {
        showNotification("");
    }
    void showNotification(String extraMessage) {

        myRunnable.cancel();
        barView.setColor(argbLedColor(getBatteryPercent()));
        barView.setLength(screenWidth*getBatteryPercent()/100);

        if(isBatteryCharging) {
            mHandler.removeCallbacks(myRunnable);
            myRunnable.start();
            mHandler.post(myRunnable);
        }

        barView.invalidate();

        String actionText = showOverlay ? "STOP" : "START";
        int icon =
                showOverlay ?
                        R.drawable.ic_stop_circle_outline_grey600_24dp :
                        R.drawable.ic_play_box_outline_grey600_24dp;

        NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();

        if(!extraMessage.isEmpty())style.addLine("Extra:"+extraMessage);

        if(preferences.getBoolean("battery_percent", false))
            style.addLine(getString(R.string.battery_percent_n)+" "+Integer.toString(getBatteryPercent()));
        if(preferences.getBoolean("battery_health", false))
            style.addLine(getString(R.string.battery_health_n)+" "+eHealth);
        if(preferences.getBoolean("battery_temperature", false))
            style.addLine(getString(R.string.battery_temperature_n)+" " + Integer.toString(eTemperature/10) + " C°");
        if(preferences.getBoolean("battery_status", false))
            style.addLine(getString(R.string.battery_status_n)+" "+Integer.toString(eStatus));
        if(preferences.getBoolean("battery_technology", false))
            style.addLine(getString(R.string.battery_technology_n)+" "+eTechnology);
        if(preferences.getBoolean("battery_power_source", false))
            style.addLine(getString(R.string.battery_power_source_n)+" " + ePlugged);
        if(preferences.getBoolean("battery_voltage", false))
            style.addLine(getString(R.string.battery_voltage_n)+" "+Integer.toString(eVoltage));

        eLEDon = preferences.getBoolean("led_on", false);

        int i;
        try {i = Integer.parseInt(preferences.getString("bar_thickness", "-1"));}
            catch(NumberFormatException nfe) {i=-1;}
        if(i>-1)barView.setStrokeWidth(i);

        style.setSummaryText(
                "Battery "+
                Integer.toString(getBatteryPercent())+
                ":"+
                extraMessage+
                ":"+
                stopCode +
                ":"+
                Boolean.toString(isBatteryCharging)+
                " ("+
                versionName+
                ")");

        NotificationCompat.Builder ncb =
                new NotificationCompat.Builder(this)
                        .setContentIntent(PendingIntent.getActivity(
                                this,
                                1,
                                new Intent(this, SettingsActivity.class),
                                0
                            )
                        )
                        .setStyle(style)
                        .setColor(argbLedColor(getBatteryPercent()))
                        .setSmallIcon(R.drawable.ic_car_battery_white_48dp)
                        .setContentTitle(getString(R.string.app_title))
                        .setOngoing(true)
                        .addAction(icon, actionText,
                                PendingIntent.getService(
                                        this,
                                        1,
                                        new Intent(this, Overlay.class)
                                                .putExtra("showOverlay", !showOverlay),
                                        PendingIntent.FLAG_CANCEL_CURRENT))

                        .addAction(R.drawable.ic_power_grey600_24dp, "EXIT",
                                PendingIntent.getService(
                                        this,
                                        2,
                                        new Intent(this, Overlay.class)
                                                .putExtra("STOP", true),
                                        PendingIntent.FLAG_CANCEL_CURRENT));

        if (eLEDon)
            if((isBatteryCharging) || (getBatteryPercent()<=15)){
                if(DEBUG)Log.d(TAG,"Battery Charging or low");
                ncb.setLights(argbLedColor(getBatteryPercent()), ONMS, OFFMS);
            }
            else {
                if(DEBUG)Log.d(TAG,"Battery NOT Charging and not low");
                //turn on "black" LED (this is the only possibility to turn off led in LG G5
                ncb.setLights(Color.argb(255,0,0,0), 255, 0);
                ncb.setDefaults(DEFAULT_LIGHTS);
            }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Notification noti = ncb.build();
            //nm.notify(1, noti);
            startForeground(1, noti);
        }
        else nm.notify(1, ncb.build());
    }

    /**---------------------------------------------------------------------------
     * A {@link View} that is extended with the overlay parameters and overlay procedures
     */
    public class DrawView extends View {
        Paint paint, p;
        int barLength;

        public DrawView(Context context, int argb, int length) {
            this(context, argb, length, MAX_STROKE_WIDTH);
        }

        public DrawView(Context context, int argb, int barLength, int strokeWidth) {
            super(context);
            paint = new Paint();
            paint.setStyle(Paint.Style.FILL);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(argb);
            paint.setStrokeWidth(strokeWidth);
            setLength(barLength);
            setBackgroundColor(Color.TRANSPARENT);

            p = new Paint();
            p.setStyle(Paint.Style.FILL);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(strokeWidth);
            p.setColor(Color.argb(255, 255, 0, 0)); //RED

        }

        public void setColor(int argb) {
            paint.setColor(argb);
        }

        public void setLength(int barLength) {
            this.barLength=barLength;
        }

        public void setStrokeWidth(int strokeWidth){
            paint.setStrokeWidth(strokeWidth);
            p.setStrokeWidth(strokeWidth);
        }

        static final int LEN = 64;
        static final int STEP = 32;
        int from = STEP * -1;
        int to;

        @Override
        public void onDraw(Canvas canvas) {
            canvas.drawLine(0, 0, barLength, 0, paint);
            if(isBatteryCharging) {
                from = from + STEP;
                to = from + LEN;
                if(to>barLength){
                    from = STEP * -1;
                } else {
                    canvas.drawLine(from, 0, to, 0, p);
                    //Log.d(TAG, "from, to:"+Integer.toString(from)+", "+Integer.toString(to));
                }
            }
        }
    }

    /**
     * ---------------------------------------------------------------------------
     * Broadcast receiver for all broadcasts
     */
    public class ReceiveBroadcast extends BroadcastReceiver {

        final static String TAG="ReceiveBroadcast";

        /**
         * above api level 23 - case ACTION_CHARGING: Log.d(TAG,"charger plugged"); isBatteryCharging=true; break;
         * above api level 23 - case ACTION_DISCHARGING: Log.d(TAG,"charger unplugged"); isBatteryCharging=false; break;
         */
        @Override
        public void onReceive(Context context, Intent intent) {
            if(DEBUG)Log.d(TAG,"intent: "+intent.toString()+"intent extraInteger:"+Integer.toString(intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)));

            switch (intent.getAction()) { // @formatter:off
                case ACTION_SCREEN_OFF: if(DEBUG)Log.d(TAG,"case screen off"); break;
                case ACTION_SCREEN_ON: if(DEBUG)Log.d(TAG,"case screen on"); break;
                case ACTION_BATTERY_CHANGED: if(DEBUG)Log.d(TAG,"case battery changed");
                    if(DEBUG)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            Log.d(TAG, "BATTERY_PROPERTY_CURRENT_NOW="+Integer.toString(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)));
                            Log.d(TAG, "BATTERY_PROPERTY_CURRENT_AVERAGE="+Integer.toString(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)));
                            Log.d(TAG, "BATTERY_PROPERTY_CHARGE_COUNTER="+Integer.toString(BATTERY_PROPERTY_CHARGE_COUNTER));
                    }
                //get extra info from intent
                    eHealth = intent.getIntExtra(BatteryManager.EXTRA_HEALTH,-1);
                    eIconSmall = intent.getIntExtra(BatteryManager.EXTRA_ICON_SMALL, -1);
                    eLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    ePlugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                    ePresent = intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true);
                    eScale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                    eStatus = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                    eTechnology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
                    eTemperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
                    eVoltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
                //prepare global variables
                    isBatteryCharging=(ePlugged==BATTERY_STATUS_CHARGING||ePlugged==BATTERY_STATUS_UNKNOWN);
                    isFastCharging=(ePlugged==BATTERY_STATUS_UNKNOWN);
                //at last show the notification with the actual data
                    showNotification();
                    break;
                default: if(DEBUG)Log.d(TAG,"case default"); break;
            } // @formatter:on
        }
    }

    /**---------------------------------------------------------------------------
     * Prepare the overlay view
     * @param context the application context (Context)
     * @return the overlay view (DrawView)
     */
    private DrawView initBarView(Context context) {
        wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        screenWidth = size.x;
        screenHeight = size.y;

        WindowManager.LayoutParams params = new
                WindowManager.LayoutParams (
                        screenWidth, MAX_STROKE_WIDTH,
                        WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY, //TYPE_SYSTEM_ALERT
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, //FLAG_WATCH_OUTSIDE_TOUCH,
                        PixelFormat.TRANSPARENT
                );

        params.gravity = Gravity.TOP; //CENTER

        DrawView barView = new DrawView(this, argbLedColor(getBatteryPercent()), screenWidth);
        wm.addView(barView, params);

        return barView;

    }

    /**---------------------------------------------------------------------------
     * Callback if the already started service is called from outside.
     * @param intent the invoking intent (Intent)
     * @param flags as defined in the android documentation (int)
     * @param startId as defined in the android documentation (int)
     * @return the Service type as described in the android documentation (int)
     */
    @SuppressLint("CommitPrefEdits")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(DEBUG)Log.d(TAG,"OnStartCommand");

        if (intent==null) {
            if(DEBUG)Log.d(TAG, "null intent in StartCommand");
            showOverlay = sharedPref.getBoolean("savedInstanceOverlay", false);
            if(DEBUG)Log.d(TAG, "restore savedInstanceOverlay:"+Boolean.toString(showOverlay));
        }
        else {
            showOverlay=intent.getBooleanExtra("showOverlay", false);
            stopService=intent.getBooleanExtra("STOP", false);
            if(DEBUG)Log.d(TAG, "save savedInstanceOverlay:"+Boolean.toString(showOverlay));
            sharedPref.edit()
                    .putBoolean("savedInstanceOverlay", showOverlay)
                    .apply();
        }

        stopCode = sharedPref.getString("stop_code", "");
        //to prevent misleading information, clear the stop code
        sharedPref.edit().putString("stop_code","")
                .commit();

        if(stopService)stopSelf();

        if (showOverlay)barView.setVisibility(View.VISIBLE);
        else barView.setVisibility(View.INVISIBLE);

        showNotification();

        return START_STICKY;
    }

    /**---------------------------------------------------------------------------
     * not used here
     * @param intent the invoking intent (Intent)
     * @return the binder object
     */
    @Override
    public IBinder onBind(Intent intent) {
        // DONE: Return the communication channel to the service. --- not used here
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(DEBUG)Log.d(TAG,"destroyed");
        unregisterReceiver(receiveBroadcast);
        nm.cancelAll();
        sharedPref.edit()
            .putString("stop_code", "destroyed")
            .apply();
        mHandler = null;
        myRunnable = null;

    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        sharedPref.edit()
                .putString("stop_code", "low memory")
                .apply();

    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        sharedPref.edit()
                .putString("stop_code", "task removed")
                .apply();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        sharedPref.edit()
                .putString("stop_code", "config changed")
                .apply();
    }

    private SharedPreferences.OnSharedPreferenceChangeListener sBindPreferenceSummaryToValueListener =
    new SharedPreferences.OnSharedPreferenceChangeListener() {
        final static String TAG = "OnSharedPrefChgListener";
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
            if(DEBUG)Log.d(TAG, "Preference:"+sharedPreferences.toString()+" Value:"+s);
            showNotification();
        }
    };
}
