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
import static android.graphics.PorterDuff.Mode.SRC_OVER;

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
    private Bitmap mBitmapOriginal;
    private Bitmap mBitmapFullView;
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
        float ratio = (float) mBitmapOriginal.getWidth() / mBitmapOriginal.getHeight();
        Log.d(TAG, "init: " + mBitmapOriginal.getWidth());
        Log.d(TAG, "init: " + mBitmapOriginal.getHeight());
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
        mBitmapOriginal = Bitmap.createScaledBitmap(mBitmapOriginal, newWidth, newHeight,
                true);
        mCanvas.setBitmap(mBitmapDraw);
        mBitmapFullView = Bitmap.createBitmap(newWidth + newWidth / 6, newHeight +
                newHeight / 6, Bitmap.Config.ARGB_8888);
        invalidate();
    }

    public void setBitmapOriginal(Bitmap bitmapOriginal) {
        mBitmapOriginal = bitmapOriginal;
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
        canvas.drawBitmap(mBitmapOriginal, 0, 0, null);
        canvas.drawBitmap(mBitmapDraw, 0, 0, mBitmapPaint);
        canvas.drawCircle(mX, mY, strokeWidth / 2, mPaintCursor);
        canvas.drawCircle(mX, mY + mDistanceCursor, 20, mPaintCursor);

        drawZoomCircle(canvas);
        canvas.restore();
    }

    private void drawZoomCircle(Canvas canvas) {
        Canvas canvas1 = new Canvas(mBitmapFullView);
        canvas1.drawBitmap(mBitmapOriginal, 0, 0, null);
        canvas1.drawBitmap(mBitmapDraw, 0, 0, null);
        if (isMove) {
            if (mX > mBitmapOriginal.getWidth()
                    || mY > mBitmapOriginal.getHeight()) {
                return;
            }
            mBmZoom = Bitmap.createBitmap(mBitmapFullView,
                    (int) Math.abs(mX - mBitmapOriginal.getWidth() / 6),
                    (int) Math.abs(mY - mBitmapOriginal.getWidth() / 6),
                    mBitmapOriginal.getWidth() / 3, mBitmapOriginal.getWidth() / 3);

            int left;
            if (mX < mBitmapOriginal.getWidth() / 2) {
                left = mBitmapOriginal.getWidth() - 20 - mBmZoom.getWidth();
            } else left = 20;
            canvas.drawBitmap(drawCircleZoomBitmap(mBmZoom), left, 20, null);
        }
    }

    private Bitmap drawCircleZoomBitmap(Bitmap bitmap) {
        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(),
                bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint();
        Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        paint.setAntiAlias(true);
        canvas.drawCircle(bitmap.getWidth() / 2, bitmap.getHeight() / 2, bitmap.getWidth() / 2, paint);
        paint.setXfermode(new PorterDuffXfermode(SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);
        paint.setXfermode(new PorterDuffXfermode(SRC_OVER));
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
        Canvas canvas = new Canvas(mBitmapOriginal);
        int[] allpixels = new int[mBitmapDraw.getHeight() * mBitmapDraw.getWidth()];
        mBitmapDraw.getPixels(allpixels, 0, mBitmapDraw.getWidth(), 0, 0, mBitmapDraw.getWidth(), mBitmapDraw.getHeight());
        for (int i = 0; i < allpixels.length; i++) {
            if (allpixels[i] != Color.TRANSPARENT) {
                allpixels[i] = Color.WHITE;
            }
        }
        mBitmapDraw.setPixels(allpixels, 0, mBitmapDraw.getWidth(), 0, 0,
                mBitmapDraw.getWidth(), mBitmapDraw.getHeight());
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setXfermode(new PorterDuffXfermode(DST_OUT));
        canvas.drawBitmap(mBitmapDraw, 0, 0, paint);
        return mBitmapOriginal;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                isMove = true;
                touchStart(x, y - mDistanceCursor);
                invalidate();
                break;
            case MotionEvent.ACTION_MOVE:
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
