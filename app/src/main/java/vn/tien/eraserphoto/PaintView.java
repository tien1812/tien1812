package vn.tien.eraserphoto;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;

import static android.graphics.PorterDuff.Mode.CLEAR;
import static android.graphics.PorterDuff.Mode.DST_OUT;
import static android.graphics.PorterDuff.Mode.SRC;
import static android.graphics.PorterDuff.Mode.SRC_IN;

public class PaintView extends View {
    private static final String TAG = "kaka";
    public static int BRUSH_SIZE = 50;
    private static final float TOUCH_TOLERANCE = 4;
    private float mX, mY;
    private Path mPath;
    private Paint mPaint;
    private ArrayList<FingerPath> removePaths = new ArrayList<>();
    private ArrayList<FingerPath> restorePaths = new ArrayList<>();
    private int currentColor;
    private int strokeWidth;
    private Bitmap mBitmapResult;
    private Bitmap mBitmapDraw;
    private Canvas mCanvas;
    private Paint mBitmapPaint = new Paint(Paint.DITHER_FLAG);
    private boolean isRestore;
    private Paint mPaintCursor;
    private int mDistanceCursor = 200;
    private Bitmap mBmZoom;
    private boolean isMove;

    public PaintView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setDither(true);
        mPaint.setColor(getResources().getColor(R.color.red_alpha, getContext().getTheme()));
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setXfermode(null);
        mPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
        mPaint.setAlpha(0xff);
        currentColor = Color.argb(40, 255, 0, 0);
        strokeWidth = BRUSH_SIZE;

        mPaintCursor = new Paint();
        mPaintCursor.setColor(getResources().getColor(R.color.purple_700));
        mPaint.setAntiAlias(true);
        mPaint.setDither(true);
    }

    public void setSizeBitmap(int wPx, int hPx) {
        int newWidth, newHeight;
        float ratio = (float) mBitmapResult.getWidth() / mBitmapResult.getHeight();
        Log.d(TAG, "init: " + mBitmapResult.getWidth());
        Log.d(TAG, "init: " + mBitmapResult.getHeight());
        Log.d(TAG, "init: " + ratio);
        if (ratio >= 1) {
            newWidth = wPx;
            newHeight = (int) (wPx / ratio);
        } else {
            newHeight = hPx;
            newWidth = (int) (hPx * ratio);
        }
        Log.d(TAG, "init: " + newWidth);
        Log.d(TAG, "init: " + newHeight);
        mBitmapDraw = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888);
        mBitmapResult = Bitmap.createScaledBitmap(mBitmapResult, newWidth, newHeight,
                true);
        mCanvas.setBitmap(mBitmapDraw);
        invalidate();
    }

    public void setBitmapResult(Bitmap bitmapResult) {
        mBitmapResult = bitmapResult;
        mCanvas = new Canvas();
    }

    public void setDistanceCursor(int distanceCursor) {
        mDistanceCursor = distanceCursor;
        invalidate();
    }

    public void setRestore(boolean restore) {
        isRestore = restore;
    }

    public void normal() {
        isRestore = false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();
        if (!isRestore) {
            for (FingerPath fp : removePaths) {
                mPaint.setXfermode(new PorterDuffXfermode(SRC));
                mPaint.setColor(getResources().getColor(R.color.red_alpha, getContext().getTheme()));
                mPaint.setStrokeWidth(fp.strokeWidth);
                mPaint.setMaskFilter(null);
                mCanvas.drawPath(fp.path, mPaint);
            }
        } else {
            for (FingerPath fp : restorePaths) {
                mPaint.setXfermode(new PorterDuffXfermode(CLEAR));
                mPaint.setStrokeWidth(fp.strokeWidth);
                mPaint.setMaskFilter(null);
                mCanvas.drawPath(fp.path, mPaint);
            }
        }
        canvas.drawBitmap(mBitmapResult, 0, 0, null);
        canvas.drawBitmap(mBitmapDraw, 0, 0, mBitmapPaint);
        canvas.drawCircle(mX, mY, strokeWidth / 2, mPaintCursor);
        canvas.drawCircle(mX, mY + mDistanceCursor, 20, mPaintCursor);
        if (isMove) {
            if (mX > mBitmapResult.getWidth() - mBitmapResult.getWidth() / 6
                    || mY > mBitmapResult.getHeight() - mBitmapResult.getWidth() / 6) {
                return;
            }
            mBmZoom = Bitmap.createBitmap(mBitmapResult,
                    (int) Math.abs(mX - mBitmapResult.getWidth() / 6),
                    (int) Math.abs(mY - mBitmapResult.getWidth() / 6),
                    mBitmapResult.getWidth() / 3, mBitmapResult.getWidth() / 3);
            canvas.drawBitmap(drawCircleZoomBitmap(mBmZoom), 20, 20, null);
        }
        canvas.restore();
    }

    private Bitmap drawCircleZoomBitmap(Bitmap bitmap) {
        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint();
        Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        paint.setAntiAlias(true);
        canvas.drawCircle(bitmap.getWidth() / 2, bitmap.getHeight() / 2, bitmap.getWidth() / 2, paint);
        paint.setXfermode(new PorterDuffXfermode(SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);
        return output;
    }

    public void setStrokeWidth(int strokeWidth) {
        this.strokeWidth = strokeWidth;
        invalidate();
    }

    private void touchStart(float x, float y) {
        mPath = new Path();
        if (!isRestore) {
            FingerPath fp = new FingerPath(currentColor, strokeWidth, mPath);
            removePaths.add(fp);
        } else {
            FingerPath fp = new FingerPath(currentColor, strokeWidth, mPath);
            restorePaths.add(fp);
        }
        mPath.reset();
        mPath.moveTo(x, y);
        mX = x;
        mY = y;
    }

    private void touchMove(float x, float y) {
        float dx = Math.abs(x - mX);
        float dy = Math.abs(y - mY);

        if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
            mPath.quadTo(mX, mY, (x + mX) / 2, (y + mY) / 2);
            mX = x;
            mY = y;
        }
    }

    private void touchUp() {
        mPath.lineTo(mX, mY);
        removePaths.clear();
        restorePaths.clear();
    }

    public Bitmap getBitmapResult() {
        Canvas canvas = new Canvas(mBitmapResult);
        int[] allpixels = new int[mBitmapDraw.getHeight() * mBitmapDraw.getWidth()];
        mBitmapDraw.getPixels(allpixels, 0, mBitmapDraw.getWidth(), 0, 0, mBitmapDraw.getWidth(), mBitmapDraw.getHeight());
        for (int i = 0; i < allpixels.length; i++) {
            if (allpixels[i] != Color.TRANSPARENT) {
                allpixels[i] = Color.GRAY;
            }
        }
        mBitmapDraw.setPixels(allpixels, 0, mBitmapDraw.getWidth(), 0, 0,
                mBitmapDraw.getWidth(), mBitmapDraw.getHeight());
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setXfermode(new PorterDuffXfermode(DST_OUT));
        canvas.drawBitmap(mBitmapDraw, 0, 0, paint);
        return mBitmapResult;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchStart(x, y - mDistanceCursor);
                invalidate();
                break;
            case MotionEvent.ACTION_MOVE:
                isMove = true;
                touchMove(x, y - mDistanceCursor);
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
                isMove = false;
                touchUp();
                invalidate();
                mBmZoom.recycle();
                break;
        }
        return true;
    }

}
