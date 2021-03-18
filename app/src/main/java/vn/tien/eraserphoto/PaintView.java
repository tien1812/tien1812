package vn.tien.eraserphoto;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.support.annotation.NonNull;
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
    private Bitmap mBitmapZoom;
    private Canvas mCanvas;
    private Paint mBitmapPaint = new Paint(Paint.DITHER_FLAG);
    private boolean isRestore;
    private Paint mPaintCursor;
    private int mDistanceCursor = 200;
    private Bitmap mBmZoom;
    private boolean isMove;
    private int currentMode;
    private float downX;
    private float downY;
    private PointF midPoint;
    private float oldDistance;
    private Matrix moveMatrix;
    private Matrix downMatrix;
    private final float[] matrixValues = new float[9];
    private int mWidthPx;

    public PaintView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public void setBitmapDraw(Bitmap bitmapDraw) {
        mBitmapDraw = bitmapDraw;
        mCanvas.setBitmap(mBitmapDraw);
        invalidate();
    }

    private void init() {
        moveMatrix = new Matrix();
        downMatrix = new Matrix();
        this.oldDistance = 0.0F;
        this.currentMode = 0;
        midPoint = new PointF();

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
        mWidthPx = wPx;
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
        mBitmapZoom = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888);
        mCanvas.setBitmap(mBitmapDraw);
        mBitmapFullView = Bitmap.createBitmap(newWidth + newWidth / 6, newHeight +
                newHeight / 6, Bitmap.Config.ARGB_8888);
        invalidate();
    }

    public void setBitmapOriginal(Bitmap bitmapOriginal) {
        mBitmapOriginal = bitmapOriginal;
        moveMatrix = new Matrix();
        downMatrix = new Matrix();
        mCanvas = new Canvas();
        removePaths.clear();
        restorePaths.clear();
        normal();
        invalidate();
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
        if (currentMode == 2) {
            canvas.setMatrix(moveMatrix);
            canvas.drawBitmap(mBitmapOriginal, 0, 0, null);
            canvas.drawBitmap(mBitmapDraw, 0, 0, mBitmapPaint);
        } else {
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
            float x = mX * getMatrixScale(moveMatrix) +
                    getMatrixValue(moveMatrix, Matrix.MTRANS_X);
            float y = mY * getMatrixScale(moveMatrix) + getMatrixValue(moveMatrix, Matrix.MTRANS_Y);
            float yCursor = (mY + mDistanceCursor) * getMatrixScale(moveMatrix) + getMatrixValue(moveMatrix, Matrix.MTRANS_Y);
            canvas.drawBitmap(mBitmapOriginal, moveMatrix, null);
            canvas.drawBitmap(mBitmapDraw, moveMatrix, mBitmapPaint);
            canvas.drawCircle(x,
                    y, strokeWidth / 2 * getMatrixValue(moveMatrix, Matrix.MSCALE_X), mPaintCursor);
            canvas.drawCircle(x, yCursor, 20, mPaintCursor);
        }
        canvas.restore();
    }

    public Bitmap getBitmapOriginal() {
        return mBitmapOriginal;
    }

    private void drawZoomCircle(Canvas canvas, float x, float y) {
        Canvas canvas1 = new Canvas(mBitmapFullView);
        canvas1.drawBitmap(mBitmapOriginal, moveMatrix, null);
        canvas1.drawBitmap(mBitmapDraw, moveMatrix, null);
        canvas1.setMatrix(moveMatrix);
        if (isMove) {
            if (x > mBitmapOriginal.getWidth()
                    || y > mBitmapOriginal.getHeight()) {
                return;
            }
            mBmZoom = Bitmap.createBitmap(mBitmapFullView,
                    (int) (Math.abs(x - mBitmapOriginal.getWidth() / 6)),
                    (int) (Math.abs(y - mBitmapOriginal.getWidth() / 6)),
                    mBitmapOriginal.getWidth() / 3, mBitmapOriginal.getWidth() / 3);

            int left;
            if (x < mBitmapOriginal.getWidth() / 2) {
                left = mWidthPx - 20 - mBmZoom.getWidth();
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

    private float getMatrixScale(@NonNull Matrix matrix) {
        return (float) Math.sqrt(Math.pow(getMatrixValue(matrix, Matrix.MSCALE_X), 2) + Math.pow(
                getMatrixValue(matrix, Matrix.MSKEW_Y), 2));
    }

    private float getMatrixValue(@NonNull Matrix matrix, int valueIndex) {
        matrix.getValues(matrixValues);
        return matrixValues[valueIndex];
    }

    private void handleCurrentMode(@NonNull MotionEvent event) {
        switch (this.currentMode) {
            case 0:
                downMatrix.set(moveMatrix);
            default:
                break;
            case 2:
                float newDistance = this.calculateDistance(event);
                this.moveMatrix.set(this.downMatrix);
                this.moveMatrix.postScale(newDistance / this.oldDistance,
                        newDistance / this.oldDistance, this.midPoint.x, this.midPoint.y);
                this.moveMatrix.postTranslate(event.getX() - this.downX, event.getY() -
                        this.downY);
                break;
        }
    }

    private void touchStart(float x, float y, MotionEvent event) {
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

        this.downX = event.getX();
        this.downY = event.getY();
        this.midPoint = this.calculateMidPoint(event);
        this.oldDistance = this.calculateDistance(this.midPoint.x, this.midPoint.y,
                this.downX, this.downY);
        this.downMatrix.set(moveMatrix);
    }

    @NonNull
    private PointF calculateMidPoint(@Nullable MotionEvent event) {
        if (event != null && event.getPointerCount() >= 2) {
            float x = (event.getX(0) + event.getX(1)) / 2.0F;
            float y = (event.getY(0) + event.getY(1)) / 2.0F;
            this.midPoint.set(x, y);
            return this.midPoint;
        } else {
            this.midPoint.set(0.0F, 0.0F);
            return this.midPoint;
        }
    }


    private float calculateDistance(@Nullable MotionEvent event) {
        return event != null && event.getPointerCount() >= 2 ? this.calculateDistance(event.getX(0), event.getY(0), event.getX(1), event.getY(1)) : 0.0F;
    }

    private float calculateDistance(float x1, float y1, float x2, float y2) {
        double x = (x1 - x2);
        double y = (y1 - y2);
        return (float) Math.sqrt(x * x + y * y);
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
        this.currentMode = 0;
    }

    public Bitmap getBitmapResult() {
        Canvas canvas = new Canvas(mBitmapOriginal);

        // vì bitmap draw là màu red alpha lên phải set cho các pixel màu k phải là alpha thì mới xoá đc .
        //nếu màu k phải alpha thì k cần phải viết đoạn này
        int[] allpixels = new int[mBitmapDraw.getHeight() * mBitmapDraw.getWidth()];
        mBitmapDraw.getPixels(allpixels, 0, mBitmapDraw.getWidth(), 0, 0, mBitmapDraw.getWidth(), mBitmapDraw.getHeight());
        for (int i = 0; i < allpixels.length; i++) {
            if (allpixels[i] != Color.TRANSPARENT) {
                allpixels[i] = Color.WHITE;
            }
        }
        mBitmapDraw.setPixels(allpixels, 0, mBitmapDraw.getWidth(), 0, 0,
                mBitmapDraw.getWidth(), mBitmapDraw.getHeight());
        //


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

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                isMove = true;
                touchStart(x, y - mDistanceCursor, event);
                invalidate();
                break;
            case MotionEvent.ACTION_MOVE:
                touchMove(x, y - mDistanceCursor);
                handleCurrentMode(event);
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
                isMove = false;
                touchUp();
                invalidate();
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                this.oldDistance = this.calculateDistance(event);
                this.midPoint = this.calculateMidPoint(event);
                this.currentMode = 2;
                break;
            case MotionEvent.ACTION_POINTER_UP:
                this.currentMode = 0;
                break;
        }
        return true;
    }

}

