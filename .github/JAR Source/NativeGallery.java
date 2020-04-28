package com.yasirkula.unity;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/**
 * Created by yasirkula on 22.06.2017.
 */

public class NativeGallery
{
	public static void SaveMedia( Context context, int mediaType, String filePath, String directoryName )
	{
		File originalFile = new File( filePath );
		if( !originalFile.exists() )
		{
			Log.e( "Unity", "Original media file is missing or inaccessible!" );
			return;
		}

		int pathSeparator = filePath.lastIndexOf( '/' );
		int extensionSeparator = filePath.lastIndexOf( '.' );
		String filename = pathSeparator >= 0 ? filePath.substring( pathSeparator + 1 ) : filePath;
		String extension = extensionSeparator >= 0 ? filePath.substring( extensionSeparator + 1 ) : "";

		// Credit: https://stackoverflow.com/a/31691791/2373034
		String mimeType = extension.length() > 0 ? MimeTypeMap.getSingleton().getMimeTypeFromExtension( extension.toLowerCase( Locale.ENGLISH ) ) : null;

		ContentValues values = new ContentValues();
		values.put( MediaStore.MediaColumns.TITLE, filename );
		values.put( MediaStore.MediaColumns.DISPLAY_NAME, filename );
		values.put( MediaStore.MediaColumns.DATE_ADDED, System.currentTimeMillis() / 1000 );

		if( mimeType != null && mimeType.length() > 0 )
			values.put( MediaStore.MediaColumns.MIME_TYPE, mimeType );

		if( mediaType == 0 )
		{
			int imageOrientation = GetImageOrientation( context, filePath );
			switch( imageOrientation )
			{
				case ExifInterface.ORIENTATION_ROTATE_270:
				case ExifInterface.ORIENTATION_TRANSVERSE:
				{
					values.put( MediaStore.Images.Media.ORIENTATION, 270 );
					break;
				}
				case ExifInterface.ORIENTATION_ROTATE_180:
				{
					values.put( MediaStore.Images.Media.ORIENTATION, 180 );
					break;
				}
				case ExifInterface.ORIENTATION_ROTATE_90:
				case ExifInterface.ORIENTATION_TRANSPOSE:
				{
					values.put( MediaStore.Images.Media.ORIENTATION, 90 );
					break;
				}
			}
		}

		Uri externalContentUri;
		if( mediaType == 0 )
			externalContentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
		else if( mediaType == 1 )
			externalContentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
		else
			externalContentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

		// Android 10 restricts our access to the raw filesystem, use MediaStore to save media in that case
		if( android.os.Build.VERSION.SDK_INT >= 29 )
		{
			values.put( MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/" + directoryName );
			values.put( MediaStore.MediaColumns.DATE_TAKEN, System.currentTimeMillis() );
			values.put( MediaStore.MediaColumns.IS_PENDING, true );

			Uri uri = context.getContentResolver().insert( externalContentUri, values );
			if( uri != null )
			{
				try
				{
					if( WriteFileToStream( originalFile, context.getContentResolver().openOutputStream( uri ) ) )
					{
						values.put( MediaStore.MediaColumns.IS_PENDING, false );
						context.getContentResolver().update( uri, values, null, null );
					}
				}
				catch( Exception e )
				{
					Log.e( "Unity", "Exception:", e );
					context.getContentResolver().delete( uri, null, null );
				}
			}
		}
		else
		{
			File directory = new File( Environment.getExternalStoragePublicDirectory( Environment.DIRECTORY_DCIM ), directoryName );
			directory.mkdirs();

			File file;
			int fileIndex = 1;
			String filenameWithoutExtension = ( extension.length() > 0 && filename.length() > extension.length() ) ? filename.substring( 0, filename.length() - extension.length() - 1 ) : filename;
			String newFilename = filename;
			do
			{
				file = new File( directory, newFilename );
				newFilename = filenameWithoutExtension + fileIndex++;
				if( extension.length() > 0 )
					newFilename += "." + extension;
			} while( file.exists() );

			try
			{
				if( WriteFileToStream( originalFile, new FileOutputStream( file ) ) )
				{
					values.put( MediaStore.MediaColumns.DATA, file.getAbsolutePath() );
					context.getContentResolver().insert( externalContentUri, values );

					Log.d( "Unity", "Saved media to: " + file.getPath() );

					// Refresh the Gallery
					Intent mediaScanIntent = new Intent( Intent.ACTION_MEDIA_SCANNER_SCAN_FILE );
					mediaScanIntent.setData( Uri.fromFile( file ) );
					context.sendBroadcast( mediaScanIntent );
				}
			}
			catch( Exception e )
			{
				Log.e( "Unity", "Exception:", e );
			}
		}
	}

	private static boolean WriteFileToStream( File file, OutputStream out )
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

	public static void MediaDeleteFile( Context context, String path, int mediaType )
	{
		if( mediaType == 0 )
			context.getContentResolver().delete( MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Images.Media.DATA + "=?", new String[] { path } );
		else if( mediaType == 1 )
			context.getContentResolver().delete( MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media.DATA + "=?", new String[] { path } );
		else
			context.getContentResolver().delete( MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, MediaStore.Audio.Media.DATA + "=?", new String[] { path } );
	}

	public static void PickMedia( Context context, final NativeGalleryMediaReceiver mediaReceiver, int mediaType, boolean selectMultiple, String savePath, String mime, String title )
	{
		if( CheckPermission( context, true ) != 1 )
		{
			if( !selectMultiple )
				mediaReceiver.OnMediaReceived( "" );
			else
				mediaReceiver.OnMultipleMediaReceived( "" );

			return;
		}

		Bundle bundle = new Bundle();
		bundle.putInt( NativeGalleryMediaPickerFragment.MEDIA_TYPE_ID, mediaType );
		bundle.putBoolean( NativeGalleryMediaPickerFragment.SELECT_MULTIPLE_ID, selectMultiple );
		bundle.putString( NativeGalleryMediaPickerFragment.SAVE_PATH_ID, savePath );
		bundle.putString( NativeGalleryMediaPickerFragment.MIME_ID, mime );
		bundle.putString( NativeGalleryMediaPickerFragment.TITLE_ID, title );

		final Fragment request = new NativeGalleryMediaPickerFragment( mediaReceiver );
		request.setArguments( bundle );

		( (Activity) context ).getFragmentManager().beginTransaction().add( 0, request ).commit();
	}

	@TargetApi( Build.VERSION_CODES.M )
	public static int CheckPermission( Context context, final boolean readPermissionOnly )
	{
		if( Build.VERSION.SDK_INT < Build.VERSION_CODES.M )
			return 1;

		if( context.checkSelfPermission( Manifest.permission.READ_EXTERNAL_STORAGE ) == PackageManager.PERMISSION_GRANTED )
		{
			if( readPermissionOnly || context.checkSelfPermission( Manifest.permission.WRITE_EXTERNAL_STORAGE ) == PackageManager.PERMISSION_GRANTED )
				return 1;
		}

		return 0;
	}

	// Credit: https://github.com/Over17/UnityAndroidPermissions/blob/0dca33e40628f1f279decb67d901fd444b409cd7/src/UnityAndroidPermissions/src/main/java/com/unity3d/plugin/UnityAndroidPermissions.java
	public static void RequestPermission( Context context, final NativeGalleryPermissionReceiver permissionReceiver, final boolean readPermissionOnly, final int lastCheckResult )
	{
		if( CheckPermission( context, readPermissionOnly ) == 1 )
		{
			permissionReceiver.OnPermissionResult( 1 );
			return;
		}

		if( lastCheckResult == 0 ) // If user clicked "Don't ask again" before, don't bother asking them again
		{
			permissionReceiver.OnPermissionResult( 0 );
			return;
		}

		Bundle bundle = new Bundle();
		bundle.putBoolean( NativeGalleryPermissionFragment.READ_PERMISSION_ONLY, readPermissionOnly );

		final Fragment request = new NativeGalleryPermissionFragment( permissionReceiver );
		request.setArguments( bundle );

		( (Activity) context ).getFragmentManager().beginTransaction().add( 0, request ).commit();
	}

	// Credit: https://stackoverflow.com/a/35456817/2373034
	public static void OpenSettings( Context context )
	{
		Uri uri = Uri.fromParts( "package", context.getPackageName(), null );

		Intent intent = new Intent();
		intent.setAction( Settings.ACTION_APPLICATION_DETAILS_SETTINGS );
		intent.setData( uri );

		context.startActivity( intent );
	}

	public static boolean CanSelectMultipleMedia()
	{
		return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2;
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
	private static int GetImageOrientation( Context context, final String path )
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
}