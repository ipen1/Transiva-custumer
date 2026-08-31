package com.transiva.app;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** Profile photo decode/crop/resize pipeline kept outside ProfileActivity. */
public final class ProfileImageProcessor {
    private ProfileImageProcessor() { }
    public static byte[] createSquareWebp(ContentResolver resolver, Uri uri) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
        try (InputStream stream = resolver.openInputStream(uri)) { BitmapFactory.decodeStream(stream, null, bounds); }
        int sample = 1;
        while (bounds.outWidth / sample > 1400 || bounds.outHeight / sample > 1400) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Math.max(1, sample); options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap;
        try (InputStream stream = resolver.openInputStream(uri)) { bitmap = BitmapFactory.decodeStream(stream, null, options); }
        if (bitmap == null) throw new IllegalStateException("Foto tidak dapat dibaca");
        int side = Math.min(bitmap.getWidth(), bitmap.getHeight());
        Bitmap square = Bitmap.createBitmap(bitmap, (bitmap.getWidth()-side)/2, (bitmap.getHeight()-side)/2, side, side);
        Bitmap resized = Bitmap.createScaledBitmap(square, 720, 720, true);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        resized.compress(Bitmap.CompressFormat.WEBP, 86, output);
        if (square != bitmap) square.recycle();
        if (resized != square) resized.recycle();
        if (!bitmap.isRecycled()) bitmap.recycle();
        return output.toByteArray();
    }
}
