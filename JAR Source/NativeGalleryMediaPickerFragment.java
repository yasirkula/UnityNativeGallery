package com.yasirkula.unity;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

/**
 * Created by yasirkula on 23.02.2018.
 */

public class NativeGalleryMediaPickerFragment extends Fragment
{
	private static final int MEDIA_REQUEST_CODE = 987455;

	public static final String MEDIA_TYPE_ID = "NGMP_MEDIA_TYPE";
	public static final String SELECT_MULTIPLE_ID = "NGMP_MULTIPLE";
	public static final String SAVE_PATH_ID = "NGMP_SAVE_PATH";
	public static final String MIME_ID = "NGMP_MIME";
	public static final String TITLE_ID = "NGMP_TITLE";

	public static boolean preferGetContent = false;
	public static boolean tryPreserveFilenames = false; // When enabled, app's cache will fill more quickly since most of the images will have a unique filename (less chance of overwriting old files)

	private final NativeGalleryMediaReceiver mediaReceiver;
	private boolean selectMultiple;
	private String savePathDirectory, savePathFilename;

	private ArrayList<String> savedFiles;

	private static String secondaryStoragePath = null;

	public NativeGalleryMediaPickerFragment()
	{
		mediaReceiver = null;
	}

	public NativeGalleryMediaPickerFragment( final NativeGalleryMediaReceiver mediaReceiver )
	{
		this.mediaReceiver = mediaReceiver;
	}

	@Override
	public void onCreate( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );
		if( mediaReceiver == null )
			getFragmentManager().beginTransaction().remove( this ).commit();
		else
		{
			int mediaType = getArguments().getInt( MEDIA_TYPE_ID );
			String mime = getArguments().getString( MIME_ID );
			String title = getArguments().getString( TITLE_ID );
			selectMultiple = getArguments().getBoolean( SELECT_MULTIPLE_ID );
			String savePath = getArguments().getString( SAVE_PATH_ID );

			int pathSeparator = savePath.lastIndexOf( '/' );
			savePathFilename = pathSeparator >= 0 ? savePath.substring( pathSeparator + 1 ) : savePath;
			savePathDirectory = pathSeparator > 0 ? savePath.substring( 0, pathSeparator ) : getActivity().getCacheDir().getAbsolutePath();

			Intent intent;
			if( !preferGetContent && !selectMultiple )
			{
				if( mediaType == 0 )
					intent = new Intent( Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI );
				else if( mediaType == 1 )
					intent = new Intent( Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI );
				else
					intent = new Intent( Intent.ACTION_PICK, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI );
			}
			else
			{
				intent = new Intent( Intent.ACTION_GET_CONTENT );
				intent.addCategory( Intent.CATEGORY_OPENABLE );

				if( selectMultiple )
					allowMultipleMedia( intent );
			}

			intent.setType( mime );

			if( title != null && title.length() > 0 )
				intent.putExtra( Intent.EXTRA_TITLE, title );

			startActivityForResult( Intent.createChooser( intent, title ), MEDIA_REQUEST_CODE );
		}
	}

	@TargetApi( Build.VERSION_CODES.JELLY_BEAN_MR2 )
	private void allowMultipleMedia( Intent intent )
	{
		intent.putExtra( Intent.EXTRA_ALLOW_MULTIPLE, true );
	}

	// Credit: https://stackoverflow.com/a/47023265/2373034
	@TargetApi( Build.VERSION_CODES.JELLY_BEAN_MR2 )
	private void fetchPathsOfMultipleMedia( ArrayList<String> result, Intent data )
	{
		if( data.getClipData() != null )
		{
			int count = data.getClipData().getItemCount();
			for( int i = 0; i < count; i++ )
			{
				result.add( getPathFromURI( data.getClipData().getItemAt( i ).getUri() ) );
			}
		}
		else if( data.getData() != null )
		{
			result.add( getPathFromURI( data.getData() ) );
		}
	}

	// Credit: https://stackoverflow.com/a/36714242/2373034
	private String getPathFromURI( Uri uri )
	{
		if( uri == null )
			return null;

		Log.d( "Unity", "Selected media uri: " + uri.toString() );

		// Android 10 restricts our access to the raw filesystem, copy the file to an accessible temporary location
		if( android.os.Build.VERSION.SDK_INT >= 29 && !Environment.isExternalStorageLegacy() )
			return copyToTempFile( uri );

		String selection = null;
		String[] selectionArgs = null;

		try
		{
			if( Build.VERSION.SDK_INT >= 19 && DocumentsContract.isDocumentUri( getActivity().getApplicationContext(), uri ) )
			{
				if( "com.android.externalstorage.documents".equals( uri.getAuthority() ) )
				{
					final String docId = DocumentsContract.getDocumentId( uri );
					final String[] split = docId.split( ":" );

					if( "primary".equalsIgnoreCase( split[0] ) )
						return Environment.getExternalStorageDirectory() + File.separator + split[1];
					else if( "raw".equalsIgnoreCase( split[0] ) ) // https://stackoverflow.com/a/51874578/2373034
						return split[1];

					return getSecondaryStoragePathFor( split[1] );
				}
				else if( "com.android.providers.downloads.documents".equals( uri.getAuthority() ) )
				{
					final String id = DocumentsContract.getDocumentId( uri );
					if( id.startsWith( "raw:" ) ) // https://stackoverflow.com/a/51874578/2373034
						return id.substring( 4 );

					uri = ContentUris.withAppendedId( Uri.parse( "content://downloads/public_downloads" ), Long.valueOf( id ) );
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
					else if( "raw".equals( type ) ) // https://stackoverflow.com/a/51874578/2373034
						return split[1];

					selection = "_id=?";
					selectionArgs = new String[] { split[1] };
				}
			}

			if( "content".equalsIgnoreCase( uri.getScheme() ) )
			{
				String[] projection = { MediaStore.Images.Media.DATA };
				Cursor cursor = null;

				try
				{
					cursor = getActivity().getContentResolver().query( uri, projection, selection, selectionArgs, null );
					if( cursor != null )
					{
						int column_index = cursor.getColumnIndexOrThrow( MediaStore.Images.Media.DATA );
						if( cursor.moveToFirst() )
						{
							String columnValue = cursor.getString( column_index );
							if( columnValue != null && columnValue.length() > 0 )
								return columnValue;
						}
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
			}
			else if( "file".equalsIgnoreCase( uri.getScheme() ) )
				return uri.getPath();

			// File path couldn't be determined, try to copy the selected file to a temporary path and use it instead
			return copyToTempFile( uri );
		}
		catch( Exception e )
		{
			Log.e( "Unity", "Exception:", e );
			return null;
		}
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

	private String copyToTempFile( Uri uri )
	{
		// Credit: https://developer.android.com/training/secure-file-sharing/retrieve-info.html#RetrieveFileInfo
		ContentResolver resolver = getActivity().getContentResolver();
		Cursor returnCursor = null;
		String filename = null;

		try
		{
			returnCursor = resolver.query( uri, null, null, null, null );
			if( returnCursor != null && returnCursor.moveToFirst() )
				filename = returnCursor.getString( returnCursor.getColumnIndex( OpenableColumns.DISPLAY_NAME ) );
		}
		catch( Exception e )
		{
			Log.e( "Unity", "Exception:", e );
		}
		finally
		{
			if( returnCursor != null )
				returnCursor.close();
		}

		if( filename == null || filename.length() < 3 )
			filename = "temp";

		String extension = null;
		String mime = resolver.getType( uri );
		if( mime != null )
		{
			String mimeExtension = MimeTypeMap.getSingleton().getExtensionFromMimeType( mime );
			if( mimeExtension != null && mimeExtension.length() > 0 )
				extension = "." + mimeExtension;
		}

		if( extension == null )
		{
			int filenameExtensionIndex = filename.lastIndexOf( '.' );
			if( filenameExtensionIndex > 0 && filenameExtensionIndex < filename.length() - 1 )
				extension = filename.substring( filenameExtensionIndex );
			else
				extension = ".tmp";
		}

		if( !tryPreserveFilenames )
			filename = savePathFilename;
		else if( filename.endsWith( extension ) )
			filename = filename.substring( 0, filename.length() - extension.length() );

		try
		{
			InputStream input = resolver.openInputStream( uri );
			if( input == null )
				return null;

			String fullName = filename + extension;
			if( savedFiles != null )
			{
				int n = 1;
				for( int i = 0; i < savedFiles.size(); i++ )
				{
					if( savedFiles.get( i ).equals( fullName ) )
					{
						n++;
						fullName = filename + n + extension;
						i = -1;
					}
				}
			}

			File tempFile = new File( savePathDirectory, fullName );
			OutputStream output = null;
			try
			{
				output = new FileOutputStream( tempFile, false );

				byte[] buf = new byte[4096];
				int len;
				while( ( len = input.read( buf ) ) > 0 )
				{
					output.write( buf, 0, len );
				}

				if( selectMultiple )
				{
					if( savedFiles == null )
						savedFiles = new ArrayList<String>();

					savedFiles.add( fullName );
				}

				return tempFile.getAbsolutePath();
			}
			finally
			{
				if( output != null )
					output.close();

				input.close();
			}
		}
		catch( Exception e )
		{
			Log.e( "Unity", "Exception:", e );
		}

		return null;
	}

	@Override
	public void onActivityResult( int requestCode, int resultCode, Intent data )
	{
		if( requestCode != MEDIA_REQUEST_CODE )
			return;

		if( !selectMultiple )
		{
			String result;
			if( resultCode != Activity.RESULT_OK || data == null )
				result = "";
			else
			{
				result = getPathFromURI( data.getData() );
				if( result == null )
					result = "";
			}

			if( result.length() > 0 && !( new File( result ).exists() ) )
				result = "";

			if( mediaReceiver != null )
				mediaReceiver.OnMediaReceived( result );
		}
		else
		{
			ArrayList<String> result = new ArrayList<String>();
			if( resultCode == Activity.RESULT_OK && data != null )
				fetchPathsOfMultipleMedia( result, data );

			for( int i = result.size() - 1; i >= 0; i-- )
			{
				if( result.get( i ) == null || result.get( i ).length() == 0 || !( new File( result.get( i ) ).exists() ) )
					result.remove( i );
			}

			String resultCombined = "";
			for( int i = 0; i < result.size(); i++ )
			{
				if( i == 0 )
					resultCombined += result.get( i );
				else
					resultCombined += ">" + result.get( i );
			}

			if( mediaReceiver != null )
				mediaReceiver.OnMultipleMediaReceived( resultCombined );
		}

		getFragmentManager().beginTransaction().remove( this ).commit();
	}
}