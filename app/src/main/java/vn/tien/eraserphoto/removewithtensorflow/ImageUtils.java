package vn.tien.eraserphoto.removewithtensorflow;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.media.ExifInterface;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ImageUtils {
    private static Matrix decodeExifOrientation(int orientation ) {
        Matrix matrix = new Matrix();

        // Apply transformation corresponding to declared EXIF orientation
        switch (orientation) {
            case ExifInterface.ORIENTATION_NORMAL:
                break;
            case ExifInterface.ORIENTATION_UNDEFINED :
                break;
            case ExifInterface.ORIENTATION_ROTATE_90 :
                matrix.postRotate(90F);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180 :
                matrix.postRotate(180F);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270 :
                matrix.postRotate(270F);
                break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL :
                matrix.postScale(-1F, 1F);
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.postScale(1F, -1F);
            case ExifInterface.ORIENTATION_TRANSPOSE :
                matrix.postScale(-1F, 1F);
                matrix.postRotate(270F);
                break;

            case ExifInterface.ORIENTATION_TRANSVERSE :
                matrix.postScale(-1F, 1F);
                matrix.postRotate(90F);
                break;

            // Error out if the EXIF orientation is invalid
            default:
                throw new IllegalArgumentException("Invalid orientation: "+orientation);
        }

        // Return the resulting matrix
        return matrix;
    }

    /*
     * sets the Exif orientation of an image.
     * this method is used to fix the exit of pictures taken by the camera
     *
     * @param filePath - The image file to change
     * @param value - the orientation of the file
     */
    static  void setExifOrientation(
             String filePath,
             String value
    ) {
        try {
            ExifInterface exif = new ExifInterface(filePath);

            exif.setAttribute(
                    ExifInterface.TAG_ORIENTATION, value
            );
            exif.saveAttributes();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Transforms rotation and mirroring information into one of the [ExifInterface] constants */
        int computeExifOrientation(int rotationDegrees, Boolean mirrored) {
        if(rotationDegrees == 0 && !mirrored) return ExifInterface.ORIENTATION_NORMAL;
        if(rotationDegrees == 0 && mirrored) return ExifInterface.ORIENTATION_FLIP_HORIZONTAL;
        if(rotationDegrees == 180 && !mirrored) return ExifInterface.ORIENTATION_ROTATE_180;
        if(rotationDegrees == 180 && mirrored) return ExifInterface.ORIENTATION_FLIP_VERTICAL;
        if(rotationDegrees == 270 && mirrored) return ExifInterface.ORIENTATION_TRANSVERSE;
        if(rotationDegrees == 90 && !mirrored) return ExifInterface.ORIENTATION_ROTATE_90;
        if(rotationDegrees == 90 && mirrored)return ExifInterface.ORIENTATION_TRANSPOSE;
        if(rotationDegrees == 270 && mirrored)return ExifInterface.ORIENTATION_ROTATE_270;
        if(rotationDegrees == 270 && !mirrored)return ExifInterface.ORIENTATION_TRANSVERSE;
        return ExifInterface.ORIENTATION_UNDEFINED;
    }

    /*
     * Decode a bitmap from a file and apply the transformations described in its EXIF data
     *
     * @param file - The image file to be read using [BitmapFactory.decodeFile]
     */
    public static Bitmap decodeBitmap(File file  ) throws IOException {
        // First, decode EXIF data and retrieve transformation matrix
        ExifInterface exif =new ExifInterface(file.getAbsolutePath());
        Matrix transformation =
                decodeExifOrientation(
                        exif.getAttributeInt(
                                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90
                        )
                );

        // Read bitmap using factory methods, and transform it using EXIF data
        BitmapFactory.Options options = new BitmapFactory.Options();
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        return Bitmap.createBitmap(
                BitmapFactory.decodeFile(file.getAbsolutePath()),
                0, 0, bitmap.getWidth(), bitmap.getHeight(), transformation, true
        );
    }

    static Bitmap scaleBitmapAndKeepRatio(
            Bitmap targetBmp,
            int reqHeightInPixels,
            int reqWidthInPixels
    ) {
        if (targetBmp.getHeight() == reqHeightInPixels && targetBmp.getWidth() == reqWidthInPixels) {
            return targetBmp;
        }
        Matrix matrix = new Matrix();
        matrix.setRectToRect(
                new RectF(
                        0f, 0f,
                        (float)targetBmp.getWidth(),
                        (float)targetBmp.getHeight()
                ),
                new RectF(
                        0f, 0f,
                        (float) reqWidthInPixels,
                        (float)reqHeightInPixels
                ),
                Matrix.ScaleToFit.FILL
        );
        return Bitmap.createBitmap(
                targetBmp, 0, 0,
                targetBmp.getWidth(),
                targetBmp.getHeight(), matrix, true
        );
    }

    /*
    float mean = 0.0f,
            float std = 255.0f
     */
    static ByteBuffer bitmapToByteBuffer(
            Bitmap bitmapIn,
            int  width,
            int height,
            float mean ,
            float std
    ) {
        Bitmap bitmap = scaleBitmapAndKeepRatio(bitmapIn, width, height);
        ByteBuffer inputImage = ByteBuffer.allocateDirect(1 * width * height * 3 * 4);
        inputImage.order(ByteOrder.nativeOrder());
        inputImage.rewind();

        int[] intValues = new int[width * height];
        bitmap.getPixels(intValues, 0, width, 0, 0, width, height);
        int pixel = 0;
        for (int y = 0; y< height;y++) {
            for (int x = 0;x< width;x++) {
                int value = intValues[pixel++];

                // Normalize channel values to [-1.0, 1.0]. This requirement varies by
                // model. For example, some models might require values to be normalized
                // to the range [0.0, 1.0] instead.
                inputImage.putFloat(((value >> 16 & 0xFF) - mean) / std);
                inputImage.putFloat(((value >> 8 & 0xFF) - mean) / std);
                inputImage.putFloat(((value & 0xFF) - mean) / std);
            }
        }

        inputImage.rewind();
        return inputImage;
    }

    static Bitmap createEmptyBitmap(int imageWidth, int imageHeigth, int color) {
        Bitmap ret = Bitmap.createBitmap(imageWidth, imageHeigth, Bitmap.Config.RGB_565);
        if (color != 0) {
            ret.eraseColor(color);
        }
        return ret;
    }
}
