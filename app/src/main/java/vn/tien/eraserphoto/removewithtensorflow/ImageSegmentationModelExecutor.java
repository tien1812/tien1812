package vn.tien.eraserphoto.removewithtensorflow;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.SystemClock;
import android.support.v4.graphics.ColorUtils;
import android.util.Log;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import vn.tien.eraserphoto.R;

public class ImageSegmentationModelExecutor {
    public static class Triple {
        Bitmap A;
        Bitmap B;
        Set<Integer> C;

        public Triple(Bitmap a, Bitmap b, Set<Integer> c) {
            A = a;
            B = b;
            C = c;
        }
    }

    private static final String TAG = "ImageSegmentationMExec";
    private static final String imageSegmentationModel = "deeplabv3_257_mv_gpu.tflite";
    private static final int imageSize = 257;
    static final int NUM_CLASSES = 21;
    private static final float IMAGE_MEAN = 127.5f;
    private static final float IMAGE_STD = 127.5f;

    private static final int[] segmentColors = new int[NUM_CLASSES];
    private static final String[] labelsArrays = {
            "background", "aeroplane", "bicycle", "bird", "boat", "bottle", "bus",
            "car", "cat", "chair", "cow", "dining table", "dog", "horse", "motorbike",
            "person", "potted plant", "sheep", "sofa", "train", "tv"
    };

    private static final int getrandom(Random random) {
        return (int) (255 * random.nextFloat());
    }

    Context context;
    private boolean useGPU = false;
    private GpuDelegate gpuDelegate = null;

    private ByteBuffer segmentationMasks;
    private Interpreter interpreter;

    private long fullTimeExecutionTime = 0L;
    private long preprocessTime = 0L;
    private long imageSegmentationTime = 0L;
    private long maskFlatteningTime = 0L;

    private int numberThreads = 4;

    public ImageSegmentationModelExecutor(Context context, boolean useGPU) {
        this.context = context;
        this.useGPU = useGPU;
        interpreter = getInterpreter(this.context, this.imageSegmentationModel, useGPU);
        interpreter.resizeInput(0, new int[]{1, imageSize, imageSize, 3});
        //interpreter.resetVariableTensors();
        segmentationMasks = ByteBuffer.allocateDirect(1 * imageSize * imageSize * NUM_CLASSES * 4);
        segmentationMasks.order(ByteOrder.nativeOrder());
        Random rd = new Random(System.currentTimeMillis());
        segmentColors[0] = Color.TRANSPARENT;
        for (int i = 1; i < NUM_CLASSES; i++) {
            segmentColors[i] = Color.argb(
                    (128),
                    getrandom(
                            rd
                    ),
                    getrandom(
                            rd
                    ),
                    getrandom(
                            rd
                    )
            );
        }
    }

    public ModelExecutionResult execute(Bitmap data) {
        try {
            fullTimeExecutionTime = SystemClock.uptimeMillis();

            preprocessTime = SystemClock.uptimeMillis();
            Bitmap scaledBitmap =
                    ImageUtils.scaleBitmapAndKeepRatio(
                            data,
                            imageSize, imageSize
                    );

            ByteBuffer contentArray =
                    ImageUtils.bitmapToByteBuffer(
                            scaledBitmap,
                            imageSize,
                            imageSize,
                            IMAGE_MEAN,
                            IMAGE_STD
                    );
            preprocessTime = SystemClock.uptimeMillis() - preprocessTime;

            imageSegmentationTime = SystemClock.uptimeMillis();
            interpreter.run(contentArray, segmentationMasks);
            imageSegmentationTime = SystemClock.uptimeMillis() - imageSegmentationTime;

            maskFlatteningTime = SystemClock.uptimeMillis();
            Bitmap maskImageApplied = null;
            Bitmap maskOnly = null;
            Set<Integer> itensFound = null;
            Triple a = new Triple(maskImageApplied, maskOnly, itensFound);
            a =
                    convertBytebufferMaskToBitmap(
                            segmentationMasks, imageSize, imageSize, scaledBitmap,
                            segmentColors
                    );
            maskFlatteningTime = SystemClock.uptimeMillis() - maskFlatteningTime;

            fullTimeExecutionTime = SystemClock.uptimeMillis() - fullTimeExecutionTime;

            return new ModelExecutionResult(
                    a.A,
                    scaledBitmap,
                    a.B,
                    formatExecutionLog(),
                    a.C
            );
        } catch (Exception e) {
            String exceptionLog = e.getMessage();
            Log.d(TAG, exceptionLog);

            Bitmap emptyBitmap =
                    ImageUtils.createEmptyBitmap(
                            imageSize,
                            imageSize,
                            0
                    );
            return new ModelExecutionResult(
                    emptyBitmap,
                    emptyBitmap,
                    emptyBitmap,
                    exceptionLog,
                    new HashSet<Integer>(0)
            );
        }
    }

    private String formatExecutionLog() {
        StringBuilder sb = new StringBuilder();
        sb.append(R.string.input_image_size + imageSize + " x " + imageSize + "\n");
        sb.append(R.string.gpu_enable + "" + useGPU + "\n");
        sb.append("" + R.string.number_of_threads + numberThreads + "\n");
        sb.append("" + R.string.pre_process_execution_time + preprocessTime + " ms\n");
        sb.append("" + R.string.model_execution_time + imageSegmentationTime + " ms\n");
        sb.append("" + R.string.mask_flatten_time + maskFlatteningTime + "ms\n");
        sb.append("" + R.string.full_execution_time + fullTimeExecutionTime + "ms\n");
        return sb.toString();
    }

    private Triple convertBytebufferMaskToBitmap(
            ByteBuffer inputBuffer,
            int imageWidth,
            int imageHeight,
            Bitmap backgroundImage,
            int[] colors
    ) {
        Bitmap maskBitmap = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888);
        Bitmap resultBitmap = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888);
        Bitmap scaledBackgroundImage =
                ImageUtils.scaleBitmapAndKeepRatio(
                        backgroundImage,
                        imageWidth,
                        imageHeight
                );
        int[][] mSegmentBits = new int[imageWidth][imageHeight];
        HashSet<Integer> itemsFound = new HashSet<Integer>();
        inputBuffer.rewind();

        for (int y = 0; y < imageHeight; y++) {
            for (int x = 0; x < imageWidth; x++) {
                float maxVal = 0;
                mSegmentBits[x][y] = 0;

                for (int c = 0; c < NUM_CLASSES; c++) {
                    float value = inputBuffer
                            .getFloat((y * imageWidth * NUM_CLASSES + x * NUM_CLASSES + c) * 4);
                    if (c == 0 || value > maxVal) {
                        maxVal = value;
                        mSegmentBits[x][y] = c;
                    }
                }

                itemsFound.add(mSegmentBits[x][y]);
                if (mSegmentBits[x][y] == 15) {
                    int newPixelColor = ColorUtils.compositeColors(
                            colors[mSegmentBits[x][y]],
                            scaledBackgroundImage.getPixel(x, y)
                    );
                    resultBitmap.setPixel(x, y, scaledBackgroundImage.getPixel(x, y));
                    maskBitmap.setPixel(x, y, colors[mSegmentBits[x][y]]);
                }
            }
        }
        return new Triple(resultBitmap, maskBitmap, itemsFound);
    }

    void close() {
        interpreter.close();
        if (gpuDelegate != null) {
            gpuDelegate.close();
        }
    }

    private Interpreter getInterpreter(
            Context context,
            String modelName,
            Boolean useGpu
    ) {
        Interpreter.Options tfliteOptions = new Interpreter.Options();
        tfliteOptions.setNumThreads(numberThreads);

        gpuDelegate = null;
        if (useGpu) {
            gpuDelegate = new GpuDelegate();
            tfliteOptions.addDelegate(gpuDelegate);
        }
        return new Interpreter(loadModelFile(context, modelName), tfliteOptions);
    }

    private MappedByteBuffer loadModelFile(Context context, String modelFile) {
        try {
            AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelFile);
            FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
            FileChannel fileChannel = inputStream.getChannel();
            long startOffset = fileDescriptor.getStartOffset();
            long declaredLength = fileDescriptor.getDeclaredLength();
            MappedByteBuffer retFile = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
            fileDescriptor.close();
            return retFile;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
