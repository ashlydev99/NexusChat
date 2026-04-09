package com.nexuschat.messenger.mms;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.model.UnitModelLoader;
import com.bumptech.glide.module.AppGlideModule;
import java.io.File;
import java.io.InputStream;
import com.nexuschat.messenger.contacts.avatars.ContactPhoto;
import com.nexuschat.messenger.glide.ContactPhotoLoader;
import com.nexuschat.messenger.mms.DecryptableStreamUriLoader.DecryptableUri;

@GlideModule
public class SignalGlideModule extends AppGlideModule {

  @Override
  public boolean isManifestParsingEnabled() {
    return false;
  }

  @Override
  public void applyOptions(@NonNull Context context, GlideBuilder builder) {
    builder.setLogLevel(Log.ERROR);
    //    builder.setDiskCache(new NoopDiskCacheFactory());
  }

  @Override
  public void registerComponents(
      @NonNull Context context, @NonNull Glide glide, @NonNull Registry registry) {
    // AttachmentSecret attachmentSecret =
    // AttachmentSecretProvider.getInstance(context).getOrCreateAttachmentSecret();
    // byte[]           secret           = attachmentSecret.getModernKey();

    registry.prepend(File.class, File.class, UnitModelLoader.Factory.getInstance());
    // registry.prepend(InputStream.class, new EncryptedCacheEncoder(secret, glide.getArrayPool()));
    // registry.prepend(File.class, Bitmap.class, new EncryptedBitmapCacheDecoder(secret, new
    // StreamBitmapDecoder(new Downsampler(registry.getImageHeaderParsers(),
    // context.getResources().getDisplayMetrics(), glide.getBitmapPool(), glide.getArrayPool()),
    // glide.getArrayPool())));
    // registry.prepend(File.class, GifDrawable.class, new EncryptedGifCacheDecoder(secret, new
    // StreamGifDecoder(registry.getImageHeaderParsers(), new ByteBufferGifDecoder(context,
    // registry.getImageHeaderParsers(), glide.getBitmapPool(), glide.getArrayPool()),
    // glide.getArrayPool())));

    // registry.prepend(Bitmap.class, new EncryptedBitmapResourceEncoder(secret));
    // registry.prepend(GifDrawable.class, new EncryptedGifDrawableResourceEncoder(secret));

    registry.append(ContactPhoto.class, InputStream.class, new ContactPhotoLoader.Factory(context));
    registry.append(
        DecryptableUri.class, InputStream.class, new DecryptableStreamUriLoader.Factory(context));
    // registry.replace(GlideUrl.class, InputStream.class, new OkHttpUrlLoader.Factory());
  }
}
