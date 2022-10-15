package com.yasirkula.unity;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;
import android.util.Size;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

public class NativeGalleryUtils
{
	private static String secondaryStoragePath = null;
	private static int isXiaomiOrMIUI = 0; // 1: true, -1: false
	private static Uri contentUri = null;

	public static boolean IsXiaomiOrMIUI()
	{
		if( isXiaomiOrMIUI > 0 )
			return true;
		else if( isXiaomiOrMIUI < 0 )
			return false;

		if( "xiaomi".equalsIgnoreCase( Build.MANUFACTURER ) )
		{
			isXiaomiOrMIUI = 1;
			return true;
		}

		// Check if device is using MIUI
		// Credit: https://gist.github.com/Muyangmin/e8ec1002c930d8df3df46b306d03315d
		String line;
		BufferedReader inputStream = null;
		try
		{
			Process process = Runtime.getRuntime().exec( "getprop ro.miui.ui.version.name" );
			inputStream = new BufferedReader( new InputStreamReader( process.getInputStream() ), 1024 );
			line = inputStream.readLine();

			if( line != null && line.length() > 0 )
			{
				isXiaomiOrMIUI = 1;
				return true;
			}
			else
			{
				isXiaomiOrMIUI = -1;
				return false;
			}
		}
		catch( Exception e )
		{
			isXiaomiOrMIUI = -1;
			return false;
		}
		finally
		{
			if( inputStream != null )
			{
				try
				{
					inputStream.close();
				}
				catch( Exception e )
				{
				}
			}
		}
	}

	// Credit: https://stackoverflow.com/a/36714242/2373034
	// Credit: https://github.com/saparkhid/AndroidFileNamePicker
	//No need to worry about API related errors as they are all handled in the if checks
	@SuppressLint("NewApi")
	public static String GetPathFromURI(Context context, Uri uri )
	{
		if( uri == null )
			return null;

		// check here to KITKAT or new version
		final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
		String selection = null;
		String[] selectionArgs = null;
		// DocumentProvider

		if (isKitKat) {
			// ExternalStorageProvider

			if (isExternalStorageDocument(uri)) {
				final String docId = DocumentsContract.getDocumentId(uri);
				final String[] split = docId.split(":");
				final String type = split[0];

				String fullPath = getPathFromExtSD(split);

				if (fullPath == null || !fileExists(fullPath)) {
					Log.d("Unity", "Copy files as a fallback");
					fullPath = null;
				}

				if (fullPath != "") {
					return fullPath;
				} else {
					return null;
				}
			}


			// DownloadsProvider

			if (isDownloadsDocument(uri)) {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
					final String id;
					Cursor cursor = null;
					try {
						cursor = context.getContentResolver().query(uri, new String[] {
								MediaStore.MediaColumns.DISPLAY_NAME
						}, null, null, null);
						if (cursor != null && cursor.moveToFirst()) {
							String fileName = cursor.getString(0);
							String path = Environment.getExternalStorageDirectory().toString() + "/Download/" + fileName;
							if (!TextUtils.isEmpty(path)) {
								return path;
							}
						}
					} finally {
						if (cursor != null)
							cursor.close();
					}
					id = DocumentsContract.getDocumentId(uri);

					if (!TextUtils.isEmpty(id)) {
						if (id.startsWith("raw:")) {
							return id.replaceFirst("raw:", "");
						}
						String[] contentUriPrefixesToTry = new String[] {
								"content://downloads/public_downloads",
								"content://downloads/my_downloads"
						};

						for (String contentUriPrefix: contentUriPrefixesToTry) {
							try {
								final Uri contentUri = ContentUris.withAppendedId(Uri.parse(contentUriPrefix), Long.valueOf(id));

								return getDataColumn(context, contentUri, null, null);
							} catch (NumberFormatException e) {
								//In Android 8 and Android P the id is not a number
								return uri.getPath().replaceFirst("^/document/raw:", "").replaceFirst("^raw:", "");
							}
						}
					}
				} else {
					final String id = DocumentsContract.getDocumentId(uri);

					if (id.startsWith("raw:")) {
						return id.replaceFirst("raw:", "");
					}
					try {
						contentUri = ContentUris.withAppendedId(
								Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
					} catch (NumberFormatException e) {
						e.printStackTrace();
					}

					if (contentUri != null)
						return getDataColumn(context, contentUri, null, null);
				}
			}


			// MediaProvider
			if (isMediaDocument(uri)) {
				final String docId = DocumentsContract.getDocumentId(uri);
				final String[] split = docId.split(":");
				final String type = split[0];

				Log.d("Unity", "MEDIA DOCUMENT TYPE: " + type);

				Uri contentUri = null;

				if ("image".equals(type)) {
					contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
				} else if ("video".equals(type)) {
					contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
				} else if ("audio".equals(type)) {
					contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
				} else if ("document".equals(type)) {
					contentUri = MediaStore.Files.getContentUri(MediaStore.getVolumeName(uri));
				}

				selection = "_id=?";
				selectionArgs = new String[] {
						split[1]
				};


				return getDataColumn(context, contentUri, selection, selectionArgs);
			}

			if (isGoogleDriveUri(uri)) {
				return getDriveFilePath(uri, context);
			}

			if (isWhatsAppFile(uri)) {
				return null;
			}

			if ("content".equalsIgnoreCase(uri.getScheme())) {
				if (isGooglePhotosUri(uri)) {
					return uri.getLastPathSegment();
				}

				if (isGoogleDriveUri(uri)) {
					return getDriveFilePath(uri, context);
				}

				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
					return null;
				} else {
					return getDataColumn(context, uri, null, null);
				}

			}

			if ("file".equalsIgnoreCase(uri.getScheme())) {
				return uri.getPath();
			}
		} else {
			if (isWhatsAppFile(uri)) {
				return null;
			}

			if ("content".equalsIgnoreCase(uri.getScheme())) {
				String[] projection = {
						MediaStore.Images.Media.DATA
				};
				Cursor cursor = null;

				try {
					cursor = context.getContentResolver()
							.query(uri, projection, selection, selectionArgs, null);
					int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);

					if (cursor.moveToFirst()) {
						return cursor.getString(column_index);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		return null;
	}

	public static boolean WriteFileToStream( File file, OutputStream out )
	{
		try
		{
			InputStream in = new FileInputStream( file );
			try
			{
				byte[] buf = new byte[1024];
				int len;
				while( ( len = in.read( buf ) ) > 0 )
					out.write( buf, 0, len );
			}
			finally
			{
				try
				{
					in.close();
				}
				catch( Exception e )
				{
					Log.e( "Unity", "Exception:", e );
				}
			}
		}
		catch( Exception e )
		{
			Log.e( "Unity", "Exception:", e );
			return false;
		}
		finally
		{
			try
			{
				out.close();
			}
			catch( Exception e )
			{
				Log.e( "Unity", "Exception:", e );
			}
		}

		return true;
	}

	private static BitmapFactory.Options GetImageMetadata( final String path )
	{
		try
		{
			BitmapFactory.Options result = new BitmapFactory.Options();
			result.inJustDecodeBounds = true;
			BitmapFactory.decodeFile( path, result );

			return result;
		}
		catch( Exception e )
		{
			Log.e( "Unity", "Exception:", e );
			return null;
		}
	}

	// Credit: https://stackoverflow.com/a/30572852/2373034
	public static int GetImageOrientation( Context context, final String path )
	{
		try
		{
			ExifInterface exif = new ExifInterface( path );
			int orientationEXIF = exif.getAttributeInt( ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED );
			if( orientationEXIF != ExifInterface.ORIENTATION_UNDEFINED )
				return orientationEXIF;
		}
		catch( Exception e )
		{
		}

		Cursor cursor = null;
		try
		{
			cursor = context.getContentResolver().query( Uri.fromFile( new File( path ) ), new String[] { MediaStore.Images.Media.ORIENTATION }, null, null, null );
			if( cursor != null && cursor.moveToFirst() )
			{
				int orientation = cursor.getInt( cursor.getColumnIndex( MediaStore.Images.Media.ORIENTATION ) );
				if( orientation == 90 )
					return ExifInterface.ORIENTATION_ROTATE_90;
				if( orientation == 180 )
					return ExifInterface.ORIENTATION_ROTATE_180;
				if( orientation == 270 )
					return ExifInterface.ORIENTATION_ROTATE_270;

				return ExifInterface.ORIENTATION_NORMAL;
			}
		}
		catch( Exception e )
		{
		}
		finally
		{
			if( cursor != null )
				cursor.close();
		}

		return ExifInterface.ORIENTATION_UNDEFINED;
	}

	// Credit: https://gist.github.com/aviadmini/4be34097dfdb842ae066fae48501ed41
	private static Matrix GetImageOrientationCorrectionMatrix( final int orientation, final float scale )
	{
		Matrix matrix = new Matrix();

		switch( orientation )
		{
			case ExifInterface.ORIENTATION_ROTATE_270:
			{
				matrix.postRotate( 270 );
				matrix.postScale( scale, scale );

				break;
			}
			case ExifInterface.ORIENTATION_ROTATE_180:
			{
				matrix.postRotate( 180 );
				matrix.postScale( scale, scale );

				break;
			}
			case ExifInterface.ORIENTATION_ROTATE_90:
			{
				matrix.postRotate( 90 );
				matrix.postScale( scale, scale );

				break;
			}
			case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
			{
				matrix.postScale( -scale, scale );
				break;
			}
			case ExifInterface.ORIENTATION_FLIP_VERTICAL:
			{
				matrix.postScale( scale, -scale );
				break;
			}
			case ExifInterface.ORIENTATION_TRANSPOSE:
			{
				matrix.postRotate( 90 );
				matrix.postScale( -scale, scale );

				break;
			}
			case ExifInterface.ORIENTATION_TRANSVERSE:
			{
				matrix.postRotate( 270 );
				matrix.postScale( -scale, scale );

				break;
			}
			default:
			{
				matrix.postScale( scale, scale );
				break;
			}
		}

		return matrix;
	}

	public static String LoadImageAtPath( Context context, String path, final String temporaryFilePath, final int maxSize )
	{
		BitmapFactory.Options metadata = GetImageMetadata( path );
		if( metadata == null )
			return path;

		boolean shouldCreateNewBitmap = false;
		if( metadata.outWidth > maxSize || metadata.outHeight > maxSize )
			shouldCreateNewBitmap = true;

		if( metadata.outMimeType != null && !metadata.outMimeType.equals( "image/jpeg" ) && !metadata.outMimeType.equals( "image/png" ) )
			shouldCreateNewBitmap = true;

		int orientation = GetImageOrientation( context, path );
		if( orientation != ExifInterface.ORIENTATION_NORMAL && orientation != ExifInterface.ORIENTATION_UNDEFINED )
			shouldCreateNewBitmap = true;

		if( shouldCreateNewBitmap )
		{
			Bitmap bitmap = null;
			FileOutputStream out = null;

			try
			{
				// Credit: https://developer.android.com/topic/performance/graphics/load-bitmap.html
				int sampleSize = 1;
				int halfHeight = metadata.outHeight / 2;
				int halfWidth = metadata.outWidth / 2;
				while( ( halfHeight / sampleSize ) >= maxSize || ( halfWidth / sampleSize ) >= maxSize )
					sampleSize *= 2;

				BitmapFactory.Options options = new BitmapFactory.Options();
				options.inSampleSize = sampleSize;
				options.inJustDecodeBounds = false;
				bitmap = BitmapFactory.decodeFile( path, options );

				float scaleX = 1f, scaleY = 1f;
				if( bitmap.getWidth() > maxSize )
					scaleX = maxSize / (float) bitmap.getWidth();
				if( bitmap.getHeight() > maxSize )
					scaleY = maxSize / (float) bitmap.getHeight();

				// Create a new bitmap if it should be scaled down or if its orientation is wrong
				float scale = scaleX < scaleY ? scaleX : scaleY;
				if( scale < 1f || ( orientation != ExifInterface.ORIENTATION_NORMAL && orientation != ExifInterface.ORIENTATION_UNDEFINED ) )
				{
					Matrix transformationMatrix = GetImageOrientationCorrectionMatrix( orientation, scale );
					Bitmap transformedBitmap = Bitmap.createBitmap( bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), transformationMatrix, true );
					if( transformedBitmap != bitmap )
					{
						bitmap.recycle();
						bitmap = transformedBitmap;
					}
				}

				out = new FileOutputStream( temporaryFilePath );
				if( metadata.outMimeType == null || !metadata.outMimeType.equals( "image/jpeg" ) )
					bitmap.compress( Bitmap.CompressFormat.PNG, 100, out );
				else
					bitmap.compress( Bitmap.CompressFormat.JPEG, 100, out );

				path = temporaryFilePath;
			}
			catch( Exception e )
			{
				Log.e( "Unity", "Exception:", e );

				try
				{
					File temporaryFile = new File( temporaryFilePath );
					if( temporaryFile.exists() )
						temporaryFile.delete();
				}
				catch( Exception e2 )
				{
				}
			}
			finally
			{
				if( bitmap != null )
					bitmap.recycle();

				try
				{
					if( out != null )
						out.close();
				}
				catch( Exception e )
				{
				}
			}
		}

		return path;
	}

	public static String GetImageProperties( Context context, final String path )
	{
		BitmapFactory.Options metadata = GetImageMetadata( path );
		if( metadata == null )
			return "";

		int width = metadata.outWidth;
		int height = metadata.outHeight;

		String mimeType = metadata.outMimeType;
		if( mimeType == null )
			mimeType = "";

		int orientationUnity;
		int orientation = GetImageOrientation( context, path );
		if( orientation == ExifInterface.ORIENTATION_UNDEFINED )
			orientationUnity = -1;
		else if( orientation == ExifInterface.ORIENTATION_NORMAL )
			orientationUnity = 0;
		else if( orientation == ExifInterface.ORIENTATION_ROTATE_90 )
			orientationUnity = 1;
		else if( orientation == ExifInterface.ORIENTATION_ROTATE_180 )
			orientationUnity = 2;
		else if( orientation == ExifInterface.ORIENTATION_ROTATE_270 )
			orientationUnity = 3;
		else if( orientation == ExifInterface.ORIENTATION_FLIP_HORIZONTAL )
			orientationUnity = 4;
		else if( orientation == ExifInterface.ORIENTATION_TRANSPOSE )
			orientationUnity = 5;
		else if( orientation == ExifInterface.ORIENTATION_FLIP_VERTICAL )
			orientationUnity = 6;
		else if( orientation == ExifInterface.ORIENTATION_TRANSVERSE )
			orientationUnity = 7;
		else
			orientationUnity = -1;

		if( orientation == ExifInterface.ORIENTATION_ROTATE_90 || orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
				orientation == ExifInterface.ORIENTATION_TRANSPOSE || orientation == ExifInterface.ORIENTATION_TRANSVERSE )
		{
			int temp = width;
			width = height;
			height = temp;
		}

		return width + ">" + height + ">" + mimeType + ">" + orientationUnity;
	}

	@TargetApi( Build.VERSION_CODES.JELLY_BEAN_MR1 )
	public static String GetVideoProperties( Context context, final String path )
	{
		MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
		try
		{
			metadataRetriever.setDataSource( path );

			String width = metadataRetriever.extractMetadata( MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH );
			String height = metadataRetriever.extractMetadata( MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT );
			String duration = metadataRetriever.extractMetadata( MediaMetadataRetriever.METADATA_KEY_DURATION );
			String rotation = "0";
			if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 )
				rotation = metadataRetriever.extractMetadata( MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION );

			if( width == null )
				width = "0";
			if( height == null )
				height = "0";
			if( duration == null )
				duration = "0";
			if( rotation == null )
				rotation = "0";

			return width + ">" + height + ">" + duration + ">" + rotation;
		}
		catch( Exception e )
		{
			Log.e( "Unity", "Exception:", e );
			return "";
		}
		finally
		{
			metadataRetriever.release();
		}
	}

	@TargetApi( Build.VERSION_CODES.Q )
	public static String GetVideoThumbnail( Context context, final String path, final String savePath, final boolean saveAsJpeg, int maxSize, double captureTime )
	{
		Bitmap bitmap = null;
		FileOutputStream out = null;

		try
		{
			if( captureTime < 0.0 && maxSize <= 1024 )
			{
				try
				{
					if( Build.VERSION.SDK_INT < Build.VERSION_CODES.Q )
						bitmap = ThumbnailUtils.createVideoThumbnail( path, maxSize > 512 ? MediaStore.Video.Thumbnails.FULL_SCREEN_KIND : MediaStore.Video.Thumbnails.MINI_KIND );
					else
						bitmap = ThumbnailUtils.createVideoThumbnail( new File( path ), maxSize > 512 ? new Size( 1024, 786 ) : new Size( 512, 384 ), null );
				}
				catch( Exception e )
				{
					Log.e( "Unity", "Exception:", e );
				}
			}

			if( bitmap == null )
			{
				MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
				try
				{
					metadataRetriever.setDataSource( path );

					try
					{
						int width = Integer.parseInt( metadataRetriever.extractMetadata( MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH ) );
						int height = Integer.parseInt( metadataRetriever.extractMetadata( MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT ) );
						if( maxSize > width && maxSize > height )
							maxSize = width > height ? width : height;
					}
					catch( Exception e )
					{
					}

					if( captureTime < 0.0 )
						captureTime = 0.0;
					else
					{
						try
						{
							double duration = Long.parseLong( metadataRetriever.extractMetadata( MediaMetadataRetriever.METADATA_KEY_DURATION ) ) / 1000.0;
							if( captureTime > duration )
								captureTime = duration;
						}
						catch( Exception e )
						{
						}
					}

					long frameTime = (long) ( captureTime * 1000000.0 );
					if( Build.VERSION.SDK_INT < 27 )
						bitmap = metadataRetriever.getFrameAtTime( frameTime, MediaMetadataRetriever.OPTION_CLOSEST_SYNC );
					else
						bitmap = metadataRetriever.getScaledFrameAtTime( frameTime, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, maxSize, maxSize );
				}
				finally
				{
					metadataRetriever.release();
				}
			}

			if( bitmap == null )
				return "";

			out = new FileOutputStream( savePath );
			if( saveAsJpeg )
				bitmap.compress( Bitmap.CompressFormat.JPEG, 100, out );
			else
				bitmap.compress( Bitmap.CompressFormat.PNG, 100, out );

			return savePath;
		}
		catch( Exception e )
		{
			Log.e( "Unity", "Exception:", e );
			return "";
		}
		finally
		{
			if( bitmap != null )
				bitmap.recycle();

			try
			{
				if( out != null )
					out.close();
			}
			catch( Exception e )
			{
			}
		}
	}

	private static String getPathFromExtSD(String[] pathData) {
		final String type = pathData[0];
		final String relativePath = File.separator + pathData[1];
		String fullPath = "";


		Log.d("Unity", "MEDIA EXTSD TYPE: " + type);
		Log.d("Unity", "Relative path: " + relativePath);
		// on my Sony devices (4.4.4 & 5.1.1), `type` is a dynamic string
		// something like "71F8-2C0A", some kind of unique id per storage
		// don't know any API that can get the root path of that storage based on its id.
		//
		// so no "primary" type, but let the check here for other devices
		if ("primary".equalsIgnoreCase(type)) {
			fullPath = Environment.getExternalStorageDirectory() + relativePath;
			if (fileExists(fullPath)) {
				return fullPath;
			}
		}

		if ("home".equalsIgnoreCase(type)) {
			fullPath = "/storage/emulated/0/Documents" + relativePath;
			if (fileExists(fullPath)) {
				return fullPath;
			}
		}

		// Environment.isExternalStorageRemovable() is `true` for external and internal storage
		// so we cannot relay on it.
		//
		// instead, for each possible path, check if file exists
		// we'll start with secondary storage as this could be our (physically) removable sd card
		fullPath = System.getenv("SECONDARY_STORAGE") + relativePath;
		if (fileExists(fullPath)) {
			return fullPath;
		}

		fullPath = System.getenv("EXTERNAL_STORAGE") + relativePath;
		if (fileExists(fullPath)) {
			return fullPath;
		}

		return null;
	}

	private static String getDriveFilePath(Uri uri, Context context) {
		Uri returnUri = uri;
		Cursor returnCursor = context.getContentResolver().query(returnUri, null, null, null, null);
		/*
		 * Get the column indexes of the data in the Cursor,
		 *     * move to the first row in the Cursor, get the data,
		 *     * and display it.
		 * */
		int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
		int sizeIndex = returnCursor.getColumnIndex(OpenableColumns.SIZE);
		returnCursor.moveToFirst();
		String name = (returnCursor.getString(nameIndex));
		String size = (Long.toString(returnCursor.getLong(sizeIndex)));
		File file = new File(context.getCacheDir(), name);
		try {
			InputStream inputStream = context.getContentResolver().openInputStream(uri);
			FileOutputStream outputStream = new FileOutputStream(file);
			int read = 0;
			int maxBufferSize = 1 * 1024 * 1024;
			int bytesAvailable = inputStream.available();

			//int bufferSize = 1024;
			int bufferSize = Math.min(bytesAvailable, maxBufferSize);

			final byte[] buffers = new byte[bufferSize];
			while ((read = inputStream.read(buffers)) != -1) {
				outputStream.write(buffers, 0, read);
			}
			Log.e("Unity", "Size " + file.length());
			inputStream.close();
			outputStream.close();
			Log.e("Unity", "Path " + file.getPath());
			Log.e("Unity", "Size " + file.length());
		} catch (Exception e) {
			Log.e("Unity", e.getMessage());
		}

		return file.getPath();
	}

	private static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
		Cursor cursor = null;
		final String column = "_data";
		final String[] projection = {
				column
		};

		try {
			cursor = context.getContentResolver().query(uri, projection,
					selection, selectionArgs, null);

			if (cursor != null && cursor.moveToFirst()) {
				final int index = cursor.getColumnIndexOrThrow(column);
				return cursor.getString(index);
			}
		} finally {
			if (cursor != null)
				cursor.close();
		}

		return null;
	}

	private static boolean fileExists(String filePath) {
		File file = new File(filePath);

		return file.exists();
	}

	// A bunch of helper methods to make the code a little cleaner

	private static boolean isExternalStorageDocument(Uri uri) {
		return "com.android.externalstorage.documents".equals(uri.getAuthority());
	}

	private static boolean isDownloadsDocument(Uri uri) {
		return "com.android.providers.downloads.documents".equals(uri.getAuthority());
	}

	private static boolean isMediaDocument(Uri uri) {
		return "com.android.providers.media.documents".equals(uri.getAuthority());
	}

	private static boolean isGooglePhotosUri(Uri uri) {
		return "com.google.android.apps.photos.content".equals(uri.getAuthority());
	}

	public static boolean isWhatsAppFile(Uri uri) {
		return "com.whatsapp.provider.media".equals(uri.getAuthority());
	}

	private static boolean isGoogleDriveUri(Uri uri) {
		return "com.google.android.apps.docs.storage".equals(uri.getAuthority()) || "com.google.android.apps.docs.storage.legacy".equals(uri.getAuthority());
	}
}