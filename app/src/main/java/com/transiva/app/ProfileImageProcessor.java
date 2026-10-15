package com.transiva.app;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import androidx.exifinterface.media.ExifInterface;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** Bounded decoding and EXIF correction before the user chooses their crop. */
public final class ProfileImageProcessor {
    private ProfileImageProcessor() { }
    public static Bitmap readOrientedBitmap(ContentResolver resolver, Uri uri) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
        try (InputStream stream = resolver.openInputStream(uri)) { BitmapFactory.decodeStream(stream, null, bounds); }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IllegalArgumentException("Foto tidak dapat dibaca");
        int sample = 1;
        while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample; options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap;
        try (InputStream stream = resolver.openInputStream(uri)) { bitmap = BitmapFactory.decodeStream(stream, null, options); }
        if (bitmap == null) throw new IllegalStateException("Foto tidak dapat dibaca");
        int orientation = ExifInterface.ORIENTATION_NORMAL;
        try (InputStream stream = resolver.openInputStream(uri)) {
            if (stream != null) orientation = new ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (Exception ignored) { }
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: matrix.setScale(-1, 1); break;
            case ExifInterface.ORIENTATION_ROTATE_180: matrix.setRotate(180); break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL: matrix.setScale(1, -1); break;
            case ExifInterface.ORIENTATION_TRANSPOSE: matrix.setRotate(90); matrix.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_ROTATE_90: matrix.setRotate(90); break;
            case ExifInterface.ORIENTATION_TRANSVERSE: matrix.setRotate(-90); matrix.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_ROTATE_270: matrix.setRotate(-90); break;
            default: return bitmap;
        }
        Bitmap oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (oriented != bitmap) bitmap.recycle();
        return oriented;
    }
    public static byte[] encodeWebp(Bitmap bitmap) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!bitmap.compress(Bitmap.CompressFormat.WEBP, 86, output)) throw new IllegalStateException("Foto tidak dapat diproses");
        return output.toByteArray();
    }
    public static byte[] createSquareWebp(ContentResolver resolver, Uri uri) throws Exception {
        Bitmap bitmap = readOrientedBitmap(resolver, uri);
        int side = Math.min(bitmap.getWidth(), bitmap.getHeight());
        Bitmap square = Bitmap.createBitmap(bitmap, (bitmap.getWidth()-side)/2, (bitmap.getHeight()-side)/2, side, side);
        Bitmap resized = Bitmap.createScaledBitmap(square, 720, 720, true);
        try { return encodeWebp(resized); }
        finally {
            if (resized != square) resized.recycle();
            if (square != bitmap) square.recycle();
            bitmap.recycle();
        }
    }
}
