package vn.tien.eraserphoto;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuffXfermode;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import vn.tien.eraserphoto.removewithtensorflow.ImageSegmentationModelExecutor;
import vn.tien.eraserphoto.removewithtensorflow.ModelExecutionResult;

import static android.graphics.PorterDuff.Mode.DST_OUT;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "kaka";
    private PaintView mPaintView;
    private Button mBtnNormal, mBtnClear, mBtnEraser, mBtnAuto, mBtnGallery;
    private SeekBar mSeekBar, mSbFeature;
    private ImageView mImageView;

    private ModelExecutionResult mModelExecutionResult;
    private ImageSegmentationModelExecutor imageSegmentationModel;
    private Boolean useGPU = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mPaintView = findViewById(R.id.paintView);
        mBtnNormal = findViewById(R.id.btn_normal);
        mBtnClear = findViewById(R.id.btn_clear);
        mSeekBar = findViewById(R.id.seek_bar);
        mSbFeature = findViewById(R.id.sb_feature);
        mBtnNormal.setOnClickListener(this);
        mBtnClear.setOnClickListener(this);
        mBtnEraser = findViewById(R.id.btn_eraser);
        mImageView = findViewById(R.id.image_view);
        mBtnEraser.setOnClickListener(this);
        mBtnAuto = findViewById(R.id.btn_auto);
        mBtnAuto.setOnClickListener(this);
        mBtnGallery = findViewById(R.id.gallery);
        mBtnGallery.setOnClickListener(this);

        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.girl);
        Bitmap bitmap1 = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        mPaintView.setBitmapOriginal(bitmap1);
        mPaintView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                mPaintView.setSizeBitmap(mPaintView.getWidth(), mPaintView.getHeight());
                mPaintView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });


        imageSegmentationModel = new ImageSegmentationModelExecutor(this, useGPU);


        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                mPaintView.setStrokeWidth(i);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        mSbFeature.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                mPaintView.setDistanceCursor(i);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_normal:
                mPaintView.normal();
                break;
            case R.id.btn_clear:
                mPaintView.setRestore(true);
                break;
            case R.id.btn_eraser:
                mImageView.setImageBitmap(mPaintView.getBitmapResult());
                break;
            case R.id.btn_auto:
                autoEraser();
                break;
            case R.id.gallery:
                chooseImage();
                break;
        }
    }

    private void chooseImage() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, 11);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == 11) {
            mPaintView.setBitmapOriginal(reduceSizeBitmapFromUri(data.getData()));
            mPaintView.setSizeBitmap(mPaintView.getWidth(), mPaintView.getHeight());
        }
    }

    private Bitmap reduceSizeBitmapFromUri(Uri uri) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        Bitmap bitmap = null;
        try {
            InputStream stream = getContentResolver().openInputStream(uri);
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(stream, null, options);
            options.inSampleSize = calculateInSampleSize(options, 1024,
                    1024);
            options.inJustDecodeBounds = false;
            stream.close();
            bitmap = BitmapFactory.decodeStream(getContentResolver()
                            .openInputStream(uri),
                    null, options);
            stream.close();
            Matrix matrix = new Matrix();
            try {
                ExifInterface ei = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    ei = new ExifInterface(getContentResolver().openInputStream(uri));
                }
                int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_UNDEFINED);
                switch (orientation) {
                    case ExifInterface.ORIENTATION_ROTATE_90:
                        matrix.postRotate(90);
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_180:
                        matrix.postRotate(180);
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_270:
                        matrix.postRotate(270);
                        break;
                    case ExifInterface.ORIENTATION_NORMAL:
                    default:
                        break;
                }
                bitmap = Bitmap.createBitmap(bitmap, 0, 0,
                        bitmap.getWidth(), bitmap.getHeight(),
                        matrix, true);
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException exception) {
            exception.printStackTrace();
        }
        return bitmap;
    }

    private int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    private void autoEraser() {
        mModelExecutionResult = imageSegmentationModel.execute(mPaintView.getBitmapOriginal());

        Bitmap bmsrauto = Bitmap.createScaledBitmap(mModelExecutionResult.bitmapResult,
                mPaintView.getBitmapOriginal().getWidth(),
                mPaintView.getBitmapOriginal().getHeight(), true);
        Canvas canvas = new Canvas();
        Bitmap colorbitmap = Bitmap.createBitmap(mPaintView.getBitmapOriginal().getWidth(),
                mPaintView.getBitmapOriginal().getHeight(), Bitmap.Config.ARGB_8888);
        colorbitmap.eraseColor(getColor(R.color.red_alpha));
        colorbitmap.setHasAlpha(true);
        canvas.setBitmap(colorbitmap);
        Paint paint = new Paint();
        paint.setXfermode(new PorterDuffXfermode(DST_OUT));
        paint.setFilterBitmap(true);
        paint.setAntiAlias(true);
        canvas.drawBitmap(bmsrauto, 0, 0, paint);
        mPaintView.setBitmapDraw(colorbitmap);
    }
}