package com.yasirkula.unity;

/**
 * Created by yasirkula on 23.02.2018.
 */

import android.app.Activity;
import android.app.Fragment;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import java.io.File;

public class NativeGalleryMediaPickerFragment extends Fragment
{
	private static final int MEDIA_REQUEST_CODE = 987455;

	private final NativeGalleryMediaReceiver mediaReceiver;
	private final boolean imageMode;
	private final String mime;
	private final String title;

	private static String secondaryStoragePath = null;

	public NativeGalleryMediaPickerFragment()
	{
		mediaReceiver = null;
		imageMode = false;
		mime = null;
		title = null;
	}

	public NativeGalleryMediaPickerFragment( final NativeGalleryMediaReceiver mediaReceiver, boolean imageMode, String mime, String title )
	{
		this.mediaReceiver = mediaReceiver;
		this.imageMode = imageMode;
		this.mime = mime;
		this.title = title;
	}

	@Override
	public void onCreate( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );
		if( mediaReceiver == null )
		{
			getFragmentManager().beginTransaction().remove( this ).commit();
		}
		else
		{
			Intent intent;
			if( imageMode )
				intent = new Intent( Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI );
			else
				intent = new Intent( Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI );

			intent.setType( mime );

			if( title.length() > 0 )
				intent.putExtra( Intent.EXTRA_TITLE, title );

			startActivityForResult( Intent.createChooser( intent, title ), MEDIA_REQUEST_CODE );
		}
	}

	// Credit: https://stackoverflow.com/a/36714242/2373034
	private String getPathFromURI( Uri uri )
	{
		String selection = null;
		String[] selectionArgs = null;

		if( Build.VERSION.SDK_INT >= 19 && DocumentsContract.isDocumentUri( getActivity().getApplicationContext(), uri ) )
		{
			if( "com.android.externalstorage.documents".equals( uri.getAuthority() ) )
			{
				final String docId = DocumentsContract.getDocumentId( uri );
				final String[] split = docId.split( ":" );

				if( "primary".equalsIgnoreCase( split[0] ) )
					return Environment.getExternalStorageDirectory() + File.separator + split[1];

				return getSecondaryStoragePathFor( split[1] );
			}
			else if( "com.android.providers.downloads.documents".equals( uri.getAuthority() ) )
			{
				final String id = DocumentsContract.getDocumentId( uri );
				uri = ContentUris.withAppendedId(
						Uri.parse( "content://downloads/public_downloads" ), Long.valueOf( id ) );
			}
			else if( "com.android.providers.media.documents".equals( uri.getAuthority() ) )
			{
				final String docId = DocumentsContract.getDocumentId( uri );
				final String[] split = docId.split( ":" );
				final String type = split[0];
				if( "image".equals( type ) )
					uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
				else if( "video".equals( type ) )
					uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
				else if( "audio".equals( type ) )
					uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

				selection = "_id=?";
				selectionArgs = new String[] { split[1] };
			}
		}

		if( "content".equalsIgnoreCase( uri.getScheme() ) )
		{
			String[] projection = { MediaStore.Images.Media.DATA };
			Cursor cursor;

			try
			{
				cursor = getActivity().getContentResolver()
						.query( uri, projection, selection, selectionArgs, null );
				int column_index = cursor.getColumnIndexOrThrow( MediaStore.Images.Media.DATA );
				if( cursor.moveToFirst() )
					return cursor.getString( column_index );
			}
			catch( Exception e )
			{
			}
		}
		else if( "file".equalsIgnoreCase( uri.getScheme() ) )
			return uri.getPath();

		return null;
	}

	private String getSecondaryStoragePathFor( String localPath )
	{
		if( secondaryStoragePath == null )
		{
			String primaryPath = Environment.getExternalStorageDirectory().getAbsolutePath();

			// Try paths saved at system environments
			// Credit: https://stackoverflow.com/a/32088396/2373034
			String strSDCardPath = System.getenv( "SECONDARY_STORAGE" );
			if( strSDCardPath == null || strSDCardPath.length() == 0 )
				strSDCardPath = System.getenv( "EXTERNAL_SDCARD_STORAGE" );

			if( strSDCardPath != null && strSDCardPath.length() > 0 )
			{
				if( !strSDCardPath.contains( ":" ) )
					strSDCardPath += ":";

				String[] externalPaths = strSDCardPath.split( ":" );
				for( int i = 0; i < externalPaths.length; i++ )
				{
					String path = externalPaths[i];
					if( path != null && path.length() > 0 )
					{
						File file = new File( path );
						if( file.exists() && file.isDirectory() && file.canRead() && !file.getAbsolutePath().equalsIgnoreCase( primaryPath ) )
						{
							String absolutePath = file.getAbsolutePath() + File.separator + localPath;
							if( new File( absolutePath ).exists() )
							{
								secondaryStoragePath = file.getAbsolutePath();
								return absolutePath;
							}
						}
					}
				}
			}

			// Try most common possible paths
			// Credit: https://gist.github.com/PauloLuan/4bcecc086095bce28e22
			String[] possibleRoots = new String[] { "/storage", "/mnt", "/storage/removable",
					"/removable", "/data", "/mnt/media_rw", "/mnt/sdcard0" };
			for( String root : possibleRoots )
			{
				try
				{
					File fileList[] = new File( root ).listFiles();
					for( File file : fileList )
					{
						if( file.exists() && file.isDirectory() && file.canRead() && !file.getAbsolutePath().equalsIgnoreCase( primaryPath ) )
						{
							String absolutePath = file.getAbsolutePath() + File.separator + localPath;
							if( new File( absolutePath ).exists() )
							{
								secondaryStoragePath = file.getAbsolutePath();
								return absolutePath;
							}
						}
					}
				}
				catch( Exception e )
				{
				}
			}

			secondaryStoragePath = "_NulL_";
		}
		else if( !secondaryStoragePath.equals( "_NulL_" ) )
			return secondaryStoragePath + File.separator + localPath;

		return null;
	}

	@Override
	public void onActivityResult( int requestCode, int resultCode, Intent data )
	{
		if( requestCode != MEDIA_REQUEST_CODE )
			return;

		String result;
		if( resultCode != Activity.RESULT_OK )
			result = "";
		else
		{
			result = getPathFromURI( data.getData() );
			if( result == null )
				result = "";
		}

		if( result.length() > 0 && !( new File( result ).exists() ) )
			result = "";

		mediaReceiver.OnMediaReceived( result );
		getFragmentManager().beginTransaction().remove( this ).commit();
	}
}