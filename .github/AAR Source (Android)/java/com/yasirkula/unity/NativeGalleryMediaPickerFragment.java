package com.yasirkula.unity;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileInputStream;
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

			int mediaTypeCount = 0;
			if( ( mediaType & NativeGallery.MEDIA_TYPE_IMAGE ) == NativeGallery.MEDIA_TYPE_IMAGE )
				mediaTypeCount++;
			if( ( mediaType & NativeGallery.MEDIA_TYPE_VIDEO ) == NativeGallery.MEDIA_TYPE_VIDEO )
				mediaTypeCount++;
			if( ( mediaType & NativeGallery.MEDIA_TYPE_AUDIO ) == NativeGallery.MEDIA_TYPE_AUDIO )
				mediaTypeCount++;

			Intent intent;
			if( !preferGetContent && !selectMultiple && mediaTypeCount == 1 && mediaType != NativeGallery.MEDIA_TYPE_AUDIO )
			{
				if( mediaType == NativeGallery.MEDIA_TYPE_IMAGE )
					intent = new Intent( Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI );
				else if( mediaType == NativeGallery.MEDIA_TYPE_VIDEO )
					intent = new Intent( Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI );
				else
					intent = new Intent( Intent.ACTION_PICK, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI );
			}
			else
			{
				intent = new Intent( mediaTypeCount > 1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT ? Intent.ACTION_OPEN_DOCUMENT : Intent.ACTION_GET_CONTENT );
				intent.addCategory( Intent.CATEGORY_OPENABLE );
				intent.addFlags( Intent.FLAG_GRANT_READ_URI_PERMISSION );

				if( selectMultiple && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 )
					intent.putExtra( Intent.EXTRA_ALLOW_MULTIPLE, true );

				if( mediaTypeCount > 1 )
				{
					mime = "*/*";

					if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT )
					{
						String[] mimetypes = new String[mediaTypeCount];
						int index = 0;
						if( ( mediaType & NativeGallery.MEDIA_TYPE_IMAGE ) == NativeGallery.MEDIA_TYPE_IMAGE )
							mimetypes[index++] = "image/*";
						if( ( mediaType & NativeGallery.MEDIA_TYPE_VIDEO ) == NativeGallery.MEDIA_TYPE_VIDEO )
							mimetypes[index++] = "video/*";
						if( ( mediaType & NativeGallery.MEDIA_TYPE_AUDIO ) == NativeGallery.MEDIA_TYPE_AUDIO )
							mimetypes[index++] = "audio/*";

						intent.putExtra( Intent.EXTRA_MIME_TYPES, mimetypes );
					}
				}
			}

			intent.setType( mime );

			if( title != null && title.length() > 0 )
				intent.putExtra( Intent.EXTRA_TITLE, title );

			startActivityForResult( Intent.createChooser( intent, title ), MEDIA_REQUEST_CODE );
		}
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

	private String getPathFromURI( Uri uri )
	{
		if( uri == null )
			return null;

		Log.d( "Unity", "Selected media uri: " + uri.toString() );

		String path = NativeGalleryUtils.GetPathFromURI( getActivity(), uri );
		if( path != null && path.length() > 0 )
		{
			// Check if file is accessible
			FileInputStream inputStream = null;
			try
			{
				inputStream = new FileInputStream( new File( path ) );
				inputStream.read();

				return path;
			}
			catch( Exception e )
			{
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

		// File path couldn't be determined, copy the file to an accessible temporary location
		return copyToTempFile( uri );
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