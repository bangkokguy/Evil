package bangkokguy.development.android.evil;

import android.util.Log;

/**---------------------------------------------------------------------------
 *
 */
class MyRunnable implements Runnable {

    private final static String TAG="MyRunnable";
    private final static boolean DEBUG=false;

    private OnFinishListener    mOnFinishListener;
    private boolean             isCanceled;
    private Overlay.DrawView    barView;
    private Overlay             o;

    interface OnFinishListener { void onFinish(); }

    boolean isCanceled() { return isCanceled; }
    OnFinishListener getOnFinishListener() { return mOnFinishListener; }
    void setOnFinishListener(OnFinishListener onFinishListener) { mOnFinishListener = onFinishListener; }
    void cancel() { isCanceled = true; }
    void start() { isCanceled = false; }
    void setBarView (Overlay.DrawView barView) { this.barView = barView; }
    void setContext (Overlay o) { this.o = o; }

    private void notifyFinish() {
            try {mOnFinishListener.onFinish();}
            catch (NullPointerException e) {Log.e(TAG, "null pointer in notifyfinish");}
    }

    public void run() {
        barView.invalidate();
        if (!isCanceled) {
            if(DEBUG)Log.d(TAG,"myrunnable->cancel false");
            if(o.mHandler!=null)o.mHandler.postDelayed(this, 300);
        } else if(DEBUG)Log.d(TAG,"myrunnable->cancel true");
        notifyFinish();
    }
}