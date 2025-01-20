package com.getcapacitor.plugin.http;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import com.getcapacitor.JSObject;
import java.io.File;
import java.io.FileOutputStream;

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

    public static ImageResult resizeImage(Context context, File file, JSObject options) {
        try {
            int maxWidth = options.optInt("maxWidth", Integer.MAX_VALUE);
            int maxHeight = options.optInt("maxHeight", Integer.MAX_VALUE);
            int quality = options.optInt("quality", 80);
            String format = options.optString("format", "jpg");

            // Load bitmap with original size
            BitmapFactory.Options bmOptions = new BitmapFactory.Options();
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), bmOptions);

            // Calculate scaling
            float scale = Math.min(maxWidth / (float) bitmap.getWidth(), maxHeight / (float) bitmap.getHeight());
            scale = Math.min(scale, 1); // Don't upscale

            // Create scaled bitmap
            int scaledWidth = Math.round(bitmap.getWidth() * scale);
            int scaledHeight = Math.round(bitmap.getHeight() * scale);
            Bitmap resized = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true);

            // Create temp file
            File tempFile = File.createTempFile("resized", "." + format, context.getCacheDir());
            FileOutputStream out = new FileOutputStream(tempFile);

            // Write to file
            Bitmap.CompressFormat compressFormat = format.equals("png") ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG;
            resized.compress(compressFormat, quality, out);

            out.close();
            bitmap.recycle();
            resized.recycle();

            return new ImageResult(tempFile, scaledWidth, scaledHeight, tempFile.length());
        } catch (Exception e) {
            e.printStackTrace();
            // Get original dimensions even if resize fails
            BitmapFactory.Options bmOptions = new BitmapFactory.Options();
            bmOptions.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bmOptions);
            return new ImageResult(file, bmOptions.outWidth, bmOptions.outHeight, file.length());
        }
    }
}
