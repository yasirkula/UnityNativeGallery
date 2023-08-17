package com.yasirkula.unity;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.app.RemoteAction;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Created by yasirkula on 22.06.2017.
 */

public class NativeGallery
{
	public static final int MEDIA_TYPE_IMAGE = 1;
	public static final int MEDIA_TYPE_VIDEO = 2;
	public static final int MEDIA_TYPE_AUDIO = 4;

	public static boolean overwriteExistingMedia = false;
	public static boolean mediaSaveOmitDCIM = false; // If set to true, 'directoryName' on Android 29+ must start with either "DCIM/" or ["Pictures/", "Movies/", "Music/", "Alarms/", "Notifications/", "Audiobooks/", "Podcasts/", "Ringtones/"]
	public static boolean PermissionFreeMode = false; // true: Permissions for reading/writing media elements won't be requested

	public static String SaveMedia( Context context, int mediaType, String filePath, String directoryName )
	{
		File originalFile = new File( filePath );
		if( !originalFile.exists() )
		{
			Log.e( "Unity", "Original media file is missing or inaccessible!" );
			return "";
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

		if( mediaType == MEDIA_TYPE_IMAGE )
		{
			int imageOrientation = NativeGalleryUtils.GetImageOrientation( context, filePath );
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
		if( mediaType == MEDIA_TYPE_IMAGE )
			externalContentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
		else if( mediaType == MEDIA_TYPE_VIDEO )
			externalContentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
		else
			externalContentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

		// Android 10 restricts our access to the raw filesystem, use MediaStore to save media in that case
		if( android.os.Build.VERSION.SDK_INT >= 29 )
		{
			values.put( MediaStore.MediaColumns.RELATIVE_PATH, mediaSaveOmitDCIM ? ( directoryName + "/" ) : ( ( ( mediaType != MEDIA_TYPE_AUDIO ) ? "DCIM/" : "Music/" ) + directoryName + "/" ) );
			values.put( MediaStore.MediaColumns.DATE_TAKEN, System.currentTimeMillis() );

			// While using MediaStore to save media, filename collisions are automatically handled by the OS.
			// However, there is a hard limit of 32 collisions: https://android.googlesource.com/platform/frameworks/base/+/oreo-release/core/java/android/os/FileUtils.java#618
			// When that limit is reached, an "IllegalStateException: Failed to build unique file" exception is thrown.
			// If that happens, we'll have a fallback scenario (i == 1 below). In this scenario, we'll simply add a
			// timestamp to the filename
			for( int i = 0; i < 2; i++ )
			{
				values.put( MediaStore.MediaColumns.IS_PENDING, true );

				if( i == 1 )
				{
					String filenameWithoutExtension = ( extension.length() > 0 && filename.length() > extension.length() ) ? filename.substring( 0, filename.length() - extension.length() - 1 ) : filename;
					String newFilename = filenameWithoutExtension + " " + new SimpleDateFormat( "yyyy-MM-dd'T'HH.mm.ss" ).format( new Date() ); // ISO 8601 standard
					if( extension.length() > 0 )
						newFilename += "." + extension;

					values.put( MediaStore.MediaColumns.TITLE, newFilename );
					values.put( MediaStore.MediaColumns.DISPLAY_NAME, newFilename );
				}

				Uri uri = null;
				if( !overwriteExistingMedia )
					uri = context.getContentResolver().insert( externalContentUri, values );
				else
				{
					Cursor cursor = null;
					try
					{
						String selection = MediaStore.MediaColumns.RELATIVE_PATH + "=? AND " + MediaStore.MediaColumns.DISPLAY_NAME + "=?";
						String[] selectionArgs = new String[] { values.getAsString( MediaStore.MediaColumns.RELATIVE_PATH ), values.getAsString( MediaStore.MediaColumns.DISPLAY_NAME ) };
						cursor = context.getContentResolver().query( externalContentUri, new String[] { "_id" }, selection, selectionArgs, null );
						if( cursor != null && cursor.moveToFirst() )
						{
							uri = ContentUris.withAppendedId( externalContentUri, cursor.getLong( cursor.getColumnIndex( "_id" ) ) );
							Log.d( "Unity", "Overwriting existing media" );
						}
					}
					catch( Exception e )
					{
						Log.e( "Unity", "Couldn't overwrite existing media's metadata:", e );
					}
					finally
					{
						if( cursor != null )
							cursor.close();
					}

					if( uri == null )
						uri = context.getContentResolver().insert( externalContentUri, values );
				}

				if( uri != null )
				{
					try
					{
						if( NativeGalleryUtils.WriteFileToStream( originalFile, context.getContentResolver().openOutputStream( uri ) ) )
						{
							values.put( MediaStore.MediaColumns.IS_PENDING, false );
							context.getContentResolver().update( uri, values, null, null );

							Log.d( "Unity", "Saved media to: " + uri.toString() );

							try
							{
								// Refresh the Gallery. This actually shouldn't have been necessary as ACTION_MEDIA_SCANNER_SCAN_FILE
								// is deprecated with the message "Callers should migrate to inserting items directly into MediaStore,
								// where they will be automatically scanned after each mutation" but apparently, some phones just don't
								// want to abide by the rules, ugh... (see: https://github.com/yasirkula/UnityNativeGallery/issues/265)
								Intent mediaScanIntent = new Intent( Intent.ACTION_MEDIA_SCANNER_SCAN_FILE );
								mediaScanIntent.setData( uri );
								context.sendBroadcast( mediaScanIntent );
							}
							catch( Exception e )
							{
								Log.e( "Unity", "Exception:", e );
							}

							String path = NativeGalleryUtils.GetPathFromURI( context, uri );
							return path != null && path.length() > 0 ? path : uri.toString();
						}
					}
					catch( IllegalStateException e )
					{
						if( i == 1 )
							Log.e( "Unity", "Exception:", e );

						context.getContentResolver().delete( uri, null, null );
					}
					catch( Exception e )
					{
						Log.e( "Unity", "Exception:", e );

						// Not strongly-typing RecoverableSecurityException here because Android Studio warns that
						// it would result in a crash on Android 18 or earlier
						if( overwriteExistingMedia && e.getClass().getName().equals( "android.app.RecoverableSecurityException" ) )
						{
							try
							{
								RemoteAction remoteAction = (RemoteAction) e.getClass().getMethod( "getUserAction" ).invoke( e );
								context.startIntentSender( remoteAction.getActionIntent().getIntentSender(), null, 0, 0, 0 );
							}
							catch( Exception e2 )
							{
								Log.e( "Unity", "RecoverableSecurityException failure:", e2 );
								return "";
							}

							String path = NativeGalleryUtils.GetPathFromURI( context, uri );
							return path != null && path.length() > 0 ? path : uri.toString();
						}

						context.getContentResolver().delete( uri, null, null );
						return "";
					}
				}

				if( overwriteExistingMedia )
					break;
			}
		}
		else
		{
			File directory = new File( mediaSaveOmitDCIM ? Environment.getExternalStorageDirectory() : Environment.getExternalStoragePublicDirectory( Environment.DIRECTORY_DCIM ), directoryName );
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

				if( overwriteExistingMedia )
					break;
			} while( file.exists() );

			try
			{
				if( NativeGalleryUtils.WriteFileToStream( originalFile, new FileOutputStream( file ) ) )
				{
					values.put( MediaStore.MediaColumns.DATA, file.getAbsolutePath() );

					if( !overwriteExistingMedia )
						context.getContentResolver().insert( externalContentUri, values );
					else
					{
						Uri existingMediaUri = null;
						Cursor cursor = null;
						try
						{
							cursor = context.getContentResolver().query( externalContentUri, new String[] { "_id" }, MediaStore.MediaColumns.DATA + "=?", new String[] { values.getAsString( MediaStore.MediaColumns.DATA ) }, null );
							if( cursor != null && cursor.moveToFirst() )
							{
								existingMediaUri = ContentUris.withAppendedId( externalContentUri, cursor.getLong( cursor.getColumnIndex( "_id" ) ) );
								Log.d( "Unity", "Overwriting existing media" );
							}
						}
						catch( Exception e )
						{
							Log.e( "Unity", "Couldn't overwrite existing media's metadata:", e );
						}
						finally
						{
							if( cursor != null )
								cursor.close();
						}

						if( existingMediaUri == null )
							context.getContentResolver().insert( externalContentUri, values );
						else
							context.getContentResolver().update( existingMediaUri, values, null, null );
					}

					Log.d( "Unity", "Saved media to: " + file.getPath() );

					// Refresh the Gallery
					Intent mediaScanIntent = new Intent( Intent.ACTION_MEDIA_SCANNER_SCAN_FILE );
					mediaScanIntent.setData( Uri.fromFile( file ) );
					context.sendBroadcast( mediaScanIntent );

					return file.getAbsolutePath();
				}
			}
			catch( Exception e )
			{
				Log.e( "Unity", "Exception:", e );
			}
		}

		return "";
	}

	public static void MediaDeleteFile( Context context, String path, int mediaType )
	{
		if( mediaType == MEDIA_TYPE_IMAGE )
			context.getContentResolver().delete( MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Images.Media.DATA + "=?", new String[] { path } );
		else if( mediaType == MEDIA_TYPE_VIDEO )
			context.getContentResolver().delete( MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media.DATA + "=?", new String[] { path } );
		else
			context.getContentResolver().delete( MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, MediaStore.Audio.Media.DATA + "=?", new String[] { path } );
	}

	public static void PickMedia( Context context, final NativeGalleryMediaReceiver mediaReceiver, int mediaType, boolean selectMultiple, String savePath, String mime, String title )
	{
		if( CheckPermission( context, true, mediaType ) != 1 )
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
	public static int CheckPermission( Context context, final boolean readPermission, final int mediaType )
	{
		if( Build.VERSION.SDK_INT < Build.VERSION_CODES.M )
			return 1;

		if( PermissionFreeMode )
			return 1;

		if( !readPermission )
		{
			if( android.os.Build.VERSION.SDK_INT >= 29 ) // On Android 10 and later, saving to Gallery doesn't require any permissions
				return 1;
			else if( context.checkSelfPermission( Manifest.permission.WRITE_EXTERNAL_STORAGE ) != PackageManager.PERMISSION_GRANTED )
				return 0;
		}

		if( Build.VERSION.SDK_INT < 33 || context.getApplicationInfo().targetSdkVersion < 33 )
		{
			if( context.checkSelfPermission( Manifest.permission.READ_EXTERNAL_STORAGE ) != PackageManager.PERMISSION_GRANTED )
				return 0;
		}
		else if( Build.VERSION.SDK_INT < 34 )
		{
			// On Android 14+ (34), partial media access permission is introduced which we want to avoid because they're
			// confusing for the end user and media access permission shouldn't have been necessary in the first place for
			// the Intents we're using. They were there to avoid edge cases in some problematic devices:
			// https://developer.android.com/about/versions/14/changes/partial-photo-video-access
			// We're hoping that by now, those problematic devices have resolved their issues.
			if( ( mediaType & MEDIA_TYPE_IMAGE ) == MEDIA_TYPE_IMAGE && context.checkSelfPermission( "android.permission.READ_MEDIA_IMAGES" ) != PackageManager.PERMISSION_GRANTED )
				return 0;
			if( ( mediaType & MEDIA_TYPE_VIDEO ) == MEDIA_TYPE_VIDEO && context.checkSelfPermission( "android.permission.READ_MEDIA_VIDEO" ) != PackageManager.PERMISSION_GRANTED )
				return 0;
			if( ( mediaType & MEDIA_TYPE_AUDIO ) == MEDIA_TYPE_AUDIO && context.checkSelfPermission( "android.permission.READ_MEDIA_AUDIO" ) != PackageManager.PERMISSION_GRANTED )
				return 0;
		}

		return 1;
	}

	// Credit: https://github.com/Over17/UnityAndroidPermissions/blob/0dca33e40628f1f279decb67d901fd444b409cd7/src/UnityAndroidPermissions/src/main/java/com/unity3d/plugin/UnityAndroidPermissions.java
	public static void RequestPermission( Context context, final NativeGalleryPermissionReceiver permissionReceiver, final boolean readPermission, final int mediaType, final int lastCheckResult )
	{
		if( CheckPermission( context, readPermission, mediaType ) == 1 )
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
		bundle.putBoolean( NativeGalleryPermissionFragment.READ_PERMISSION_ONLY, readPermission );
		bundle.putInt( NativeGalleryPermissionFragment.MEDIA_TYPE_ID, mediaType );

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

	public static boolean CanSelectMultipleMediaTypes()
	{
		return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
	}

	public static String GetMimeTypeFromExtension( String extension )
	{
		if( extension == null || extension.length() == 0 )
			return "";

		String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension( extension.toLowerCase( Locale.ENGLISH ) );
		return mime != null ? mime : "";
	}

	public static String LoadImageAtPath( Context context, String path, final String temporaryFilePath, final int maxSize )
	{
		return NativeGalleryUtils.LoadImageAtPath( context, path, temporaryFilePath, maxSize );
	}

	public static String GetImageProperties( Context context, final String path )
	{
		return NativeGalleryUtils.GetImageProperties( context, path );
	}

	@TargetApi( Build.VERSION_CODES.JELLY_BEAN_MR1 )
	public static String GetVideoProperties( Context context, final String path )
	{
		return NativeGalleryUtils.GetVideoProperties( context, path );
	}

	@TargetApi( Build.VERSION_CODES.Q )
	public static String GetVideoThumbnail( Context context, final String path, final String savePath, final boolean saveAsJpeg, int maxSize, double captureTime )
	{
		return NativeGalleryUtils.GetVideoThumbnail( context, path, savePath, saveAsJpeg, maxSize, captureTime );
	}
}