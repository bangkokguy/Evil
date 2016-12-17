package bangkokguy.development.android.evil;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import static android.content.Intent.ACTION_BOOT_COMPLETED;

public class BootCompletedReceiver extends BroadcastReceiver {

    final String TAG = "BootCompletedReceiver";

    public BootCompletedReceiver() {
        super();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive");
        if (intent != null) {
            final String action = intent.getAction();
            if (action.equals(ACTION_BOOT_COMPLETED)) {
                SharedPreferences prefs = PreferenceManager
                        .getDefaultSharedPreferences(context);
                boolean autoStart = prefs.getBoolean("start_on_boot", false);
                if (autoStart) {
                    context.startService(new Intent(context, Overlay.class)
                            .putExtra("showOverlay", true));
                    Log.i(TAG, "Service started");
                } else {
                    Log.i(TAG, "Auto start disabled");
                }
            } else {
                Log.e(TAG, "Unknown action:"+action);
            }
        }
    }
}
