package vn.tien.eraserphoto.removewithtensorflow;

import android.graphics.Bitmap;

import java.util.Set;

public class ModelExecutionResult {
    public Bitmap bitmapResult;
    public Bitmap bitmapOriginal;
    public Bitmap bitmapMaskOnly;
    public String executionLog;
    public Set<Integer> itemsFound;

    public ModelExecutionResult(Bitmap bitmapResult, Bitmap bitmapOriginal, Bitmap bitmapMaskOnly, String executionLog, Set<Integer> itemsFound) {
        this.bitmapResult = bitmapResult;
        this.bitmapOriginal = bitmapOriginal;
        this.bitmapMaskOnly = bitmapMaskOnly;
        this.executionLog = executionLog;
        this.itemsFound = itemsFound;
    }
}
