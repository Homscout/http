package com.getcapacitor.plugin.http;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.format.Formatter;
import android.util.Log;
import com.getcapacitor.JSObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class ImageUtils {

    public static class ImageResult {

        public final File file;
        public final int width;
        public final int height;
        public final long fileSize;

        public ImageResult(File file, int width, int height, long fileSize) {
            this.file = file;
            this.width = width;
            this.height = height;
            this.fileSize = fileSize;
        }
    }

    public static ImageResult resizeImage(Context context, File file, JSObject options, String id) {
        try {
            int maxWidth = options.optInt("maxWidth", Integer.MAX_VALUE);
            int maxHeight = options.optInt("maxHeight", Integer.MAX_VALUE);
            int quality = options.optInt("quality", 80);
            String format = options.optString("format", "jpg");

            // First pass: decode only the dimensions (no memory allocation for pixels)
            BitmapFactory.Options bmOptions = new BitmapFactory.Options();
            bmOptions.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bmOptions);

            // Check if image is already smaller than max dimensions
            if (bmOptions.outWidth <= maxWidth && bmOptions.outHeight <= maxHeight) {
                // Image is already small enough, no need to resize
                Log.d("ImageUtils", "📦 Image " + id + " is already smaller than max dimensions, skipping resize");
                return getImageResult(file);
            }

            // Calculate optimal sample size
            bmOptions.inSampleSize = calculateInSampleSize(bmOptions, maxWidth, maxHeight);

            // Second pass: decode with sample size (reduced memory usage)
            bmOptions.inJustDecodeBounds = false;
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), bmOptions);

            if (bitmap == null) {
                throw new IOException("Failed to decode image file: " + file.getAbsolutePath());
            }

            // Calculate if further scaling is needed
            float scale = Math.min(maxWidth / (float) bitmap.getWidth(), maxHeight / (float) bitmap.getHeight());
            scale = Math.min(scale, 1); // Don't upscale

            // Set default dimensions
            int scaledWidth = bitmap.getWidth();
            int scaledHeight = bitmap.getHeight();
            Bitmap resized;

            // Create a new bitmap if scaling is necessary
            if (scale < 1) {
                scaledWidth = Math.round(bitmap.getWidth() * scale);
                scaledHeight = Math.round(bitmap.getHeight() * scale);
                resized = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true);
                // Recycle the original immediately after creating the scaled version
                bitmap.recycle();
            } else {
                // No scaling needed, use the original
                resized = bitmap;
            }

            // Create temp file
            File tempFile = File.createTempFile("resized", "." + format, context.getCacheDir());

            // Use try-with-resources to ensure proper resource cleanup
            try (FileOutputStream out = new FileOutputStream(tempFile)) {
                Bitmap.CompressFormat compressFormat = format.equals("png") ?
                        Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG;
                resized.compress(compressFormat, quality, out);
                out.flush();
            }

            // Recycle bitmap after saving
            resized.recycle();

            Log.d("ImageUtils", "📦 Resized image " + id + " to " + Formatter.formatShortFileSize(context, tempFile.length()));

            return new ImageResult(tempFile, scaledWidth, scaledHeight, tempFile.length());
        } catch (Exception e) {
            e.printStackTrace();
            // Get original dimensions even if resize fails
            return getImageResult(file);
        }
    }

    /**
     * Calculate the optimal inSampleSize value to load an image at a reduced resolution.
     * This helps avoid OutOfMemoryError when dealing with large images.
     */
    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Original height and width of the image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    public static ImageResult getImageResult(File file) {
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bmOptions);
        return new ImageResult(file, bmOptions.outWidth, bmOptions.outHeight, file.length());
    }
}
