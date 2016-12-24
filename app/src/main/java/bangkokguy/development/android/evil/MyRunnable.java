package bangkokguy.development.android.evil;

import android.util.Log;

/**---------------------------------------------------------------------------
 *
 */
class MyRunnable implements Runnable {

    private final static String TAG="MyRunnable";
    private final static boolean DEBUG=false;

    private OnFinishListener mOnFinishListener;
    private boolean isCanceled;
    private Overlay.DrawView barView;
    private Overlay o;

    private int loopCounter = 0;

    interface OnFinishListener { void onFinish(); }

    boolean isCanceled() { return isCanceled; }
    public OnFinishListener getOnFinishListener() { return mOnFinishListener; }
    void setOnFinishListener(OnFinishListener onFinishListener) { mOnFinishListener = onFinishListener; }
    void cancel() { isCanceled = true; }
    void start() { isCanceled = false; }
    void setBarView (Overlay.DrawView barView) { this.barView = barView; }
    void setContext (Overlay o) { this.o = o; }
    void setLoopCounter(int lc) { loopCounter = lc; }

    private void notifyFinish() {
        if (mOnFinishListener != null) {
            mOnFinishListener.onFinish();
        }
    }

    public void run() {
        //barView.setColor(o.argbLedColor(loopCounter));
        loopCounter=loopCounter+4;
        barView.invalidate();
        if(loopCounter>=96)loopCounter=0;
        if (!isCanceled) {
            if(DEBUG)Log.d(TAG,"myrunnable->cancel false");
            o.mHandler.postDelayed(this, 600);
        } else if(DEBUG)Log.d(TAG,"myrunnable->cancel true");
        notifyFinish();
    }
}