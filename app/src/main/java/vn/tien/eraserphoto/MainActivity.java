package vn.tien.eraserphoto;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "kaka";
    private PaintView mPaintView;
    private Button mBtnNormal, mBtnClear, mBtnEraser;
    private SeekBar mSeekBar, mSbFeature;
    private ImageView mImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mPaintView = (PaintView) findViewById(R.id.paintView);
        mBtnNormal = findViewById(R.id.btn_normal);
        mBtnClear = findViewById(R.id.btn_clear);
        mSeekBar = findViewById(R.id.seek_bar);
        mSbFeature = findViewById(R.id.sb_feature);
        mBtnNormal.setOnClickListener(this);
        mBtnClear.setOnClickListener(this);
        mBtnEraser = findViewById(R.id.btn_eraser);
        mImageView = findViewById(R.id.image_view);
        mBtnEraser.setOnClickListener(this);

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
        }
    }
}