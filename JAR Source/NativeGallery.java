package com.yasirkula.unity;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;

import java.io.File;

/**
 * Created by yasirkula on 22.06.2017.
 */

public class NativeGallery
{
	public static String GetMediaPath( String directoryName )
	{
		String path = Environment.getExternalStoragePublicDirectory( Environment.DIRECTORY_DCIM ).getAbsolutePath();
		if( path.charAt( path.length() - 1 ) != File.separatorChar )
			path = path + File.separator;

		if( directoryName.charAt( directoryName.length() - 1 ) == '/' || directoryName.charAt( directoryName.length() - 1 ) == '\\' )
			directoryName = directoryName.substring( 0, directoryName.length() - 1 );

		path += directoryName + File.separatorChar;

		new File( path ).mkdirs();
		return path;
	}

	public static void MediaScanFile( Context context, String path )
	{
		MediaScannerConnection.scanFile( context, new String[] { path }, null, null );
	}

	public static void MediaDeleteFile( Context context, String path, boolean isImage )
	{
		if( isImage )
			context.getContentResolver().delete( MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Images.Media.DATA + "=?", new String[] { path } );
		else
			context.getContentResolver().delete( MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media.DATA + "=?", new String[] { path } );
	}

	public static void PickMedia( Context context, final NativeGalleryMediaReceiver mediaReceiver, boolean imageMode, boolean selectMultiple, String mime, String title )
	{
		if( CheckPermission( context ) != 1 )
		{
			if( !selectMultiple )
				mediaReceiver.OnMediaReceived( "" );
			else
				mediaReceiver.OnMultipleMediaReceived( "" );

			return;
		}

		final Fragment request = new NativeGalleryMediaPickerFragment( mediaReceiver, imageMode, selectMultiple, mime, title );
		( (Activity) context ).getFragmentManager().beginTransaction().add( 0, request ).commit();
	}

	public static int CheckPermission( Context context )
	{
		if( Build.VERSION.SDK_INT < Build.VERSION_CODES.M )
			return 1;

		return CheckPermissionInternal( context );
	}

	// Credit: https://github.com/Over17/UnityAndroidPermissions/blob/0dca33e40628f1f279decb67d901fd444b409cd7/src/UnityAndroidPermissions/src/main/java/com/unity3d/plugin/UnityAndroidPermissions.java
	@TargetApi( Build.VERSION_CODES.M )
	private static int CheckPermissionInternal( Context context )
	{
		if( context.checkSelfPermission( Manifest.permission.WRITE_EXTERNAL_STORAGE ) == PackageManager.PERMISSION_GRANTED &&
				context.checkSelfPermission( Manifest.permission.READ_EXTERNAL_STORAGE ) == PackageManager.PERMISSION_GRANTED )
			return 1;

		return 0;
	}

	// Credit: https://github.com/Over17/UnityAndroidPermissions/blob/0dca33e40628f1f279decb67d901fd444b409cd7/src/UnityAndroidPermissions/src/main/java/com/unity3d/plugin/UnityAndroidPermissions.java
	public static void RequestPermission( Context context, final NativeGalleryPermissionReceiver permissionReceiver, final int lastCheckResult )
	{
		if( CheckPermission( context ) == 1 )
		{
			permissionReceiver.OnPermissionResult( 1 );
			return;
		}

		if( lastCheckResult == 0 ) // If user clicked "Don't ask again" before, don't bother asking them again
		{
			permissionReceiver.OnPermissionResult( 0 );
			return;
		}

		final Fragment request = new NativeGalleryPermissionFragment( permissionReceiver );
		( (Activity) context ).getFragmentManager().beginTransaction().add( 0, request ).commit();
	}

	// Credit: https://stackoverflow.com/a/35456817/2373034
	public static void OpenSettings( Context context )
	{
		Intent intent = new Intent();
		intent.setAction( Settings.ACTION_APPLICATION_DETAILS_SETTINGS );
		Uri uri = Uri.fromParts( "package", context.getPackageName(), null );
		intent.setData( uri );
		context.startActivity( intent );
	}

	public static boolean CanSelectMultipleMedia()
	{
		return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2;
	}

	// Credit: https://stackoverflow.com/a/16440953/2373034
	public static String GetImageProperties( String path )
	{
		try
		{
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inJustDecodeBounds = true;
			BitmapFactory.decodeFile( path, options );

			String mimeType = options.outMimeType;
			if( mimeType == null )
				mimeType = "";

			return options.outWidth + ">" + options.outHeight + ">" + mimeType;
		}
		catch( Exception e )
		{
			Log.e( "Unity", "Exception:", e );
			return "";
		}
	}
}