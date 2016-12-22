package bangkokguy.development.android.evil;

import android.util.Log;

/**---------------------------------------------------------------------------
 *
 */
class MyRunnable implements Runnable {

    private final static String TAG="MyRunnable";

    private OnFinishListener mOnFinishListener;
    private boolean isCanceled;
    private String mMessage;
    private Overlay.DrawView barView;
    private Overlay o;

    private int loopCounter = 0;

    interface OnFinishListener { void onFinish(); }

    boolean getCancelState() { return isCanceled; }
    public OnFinishListener getOnFinishListener() { return mOnFinishListener; }
    public String getMessage() { return mMessage; }

    void DoStuffRequest(String message) { mMessage = message; }
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
        //Log.d(TAG, "run---"+Integer.toString(loopCounter++));
        barView.setColor(o.argbLedColor(loopCounter));
        loopCounter=loopCounter+10;
        barView.invalidate();
        if(loopCounter==100)loopCounter=0;
        if (!isCanceled) {
            Log.d(TAG,"myrunnable->cancel false");
            o.mHandler.postDelayed(this, 3600);
        } else Log.d(TAG,"myrunnable->cancel true");
        notifyFinish();
    }
}