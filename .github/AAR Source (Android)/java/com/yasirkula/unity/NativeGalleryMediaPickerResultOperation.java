package com.yasirkula.unity;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

public class NativeGalleryMediaPickerResultOperation
{
	private final Context context;
	private final NativeGalleryMediaReceiver mediaReceiver;
	private final Intent data;
	private final boolean selectMultiple;
	private final String savePathDirectory, savePathFilename;
	private ArrayList<String> savedFiles;

	public boolean finished, sentResult;
	public int progress;

	private boolean cancelled;
	private String unityResult;

	public NativeGalleryMediaPickerResultOperation( final Context context, final NativeGalleryMediaReceiver mediaReceiver, final Intent data, final boolean selectMultiple, final String savePathDirectory, final String savePathFilename )
	{
		this.context = context;
		this.mediaReceiver = mediaReceiver;
		this.data = data;
		this.selectMultiple = selectMultiple;
		this.savePathDirectory = savePathDirectory;
		this.savePathFilename = savePathFilename;
	}

	public void execute()
	{
		unityResult = "";
		progress = -1;

		try
		{
			if( !selectMultiple || data.getClipData() == null )
			{
				unityResult = getPathFromURI( data.getData() );
				if( unityResult == null || ( unityResult.length() > 0 && !( new File( unityResult ).exists() ) ) )
					unityResult = "";
			}
			else
			{
				boolean isFirstResult = true;
				for( int i = 0, count = data.getClipData().getItemCount(); i < count; i++ )
				{
					if( cancelled )
						return;

					String _unityResult = getPathFromURI( data.getClipData().getItemAt( i ).getUri() );
					if( _unityResult != null && _unityResult.length() > 0 && new File( _unityResult ).exists() )
					{
						if( isFirstResult )
						{
							unityResult += _unityResult;
							isFirstResult = false;
						}
						else
							unityResult += ">" + _unityResult;
					}
				}
			}
		}
		catch( Exception e )
		{
			Log.e( "Unity", "Exception:", e );
		}
		finally
		{
			progress = 100;
			finished = true;
		}
	}

	public void cancel()
	{
		if( cancelled || finished )
			return;

		Log.d( "Unity", "Cancelled NativeGalleryMediaPickerResultOperation!" );

		cancelled = true;
		unityResult = "";
	}

	public void sendResultToUnity()
	{
		if( sentResult )
			return;

		sentResult = true;

		if( mediaReceiver == null )
			Log.d( "Unity", "NativeGalleryMediaPickerResultOperation.mediaReceiver became null!" );
		else
		{
			if( selectMultiple )
				mediaReceiver.OnMultipleMediaReceived( unityResult );
			else
				mediaReceiver.OnMediaReceived( unityResult );
		}
	}

	private String getPathFromURI( Uri uri )
	{
		if( uri == null )
			return null;

		Log.d( "Unity", "Selected media uri: " + uri.toString() );

		String path = NativeGalleryUtils.GetPathFromURI( context, uri );
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
		ContentResolver resolver = context.getContentResolver();
		Cursor returnCursor = null;
		String filename = null;
		long fileSize = -1, copiedBytes = 0;

		try
		{
			returnCursor = resolver.query( uri, null, null, null, null );
			if( returnCursor != null && returnCursor.moveToFirst() )
			{
				filename = returnCursor.getString( returnCursor.getColumnIndex( OpenableColumns.DISPLAY_NAME ) );
				fileSize = returnCursor.getLong( returnCursor.getColumnIndex( OpenableColumns.SIZE ) );
			}
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
		int filenameExtensionIndex = filename.lastIndexOf( '.' );
		if( filenameExtensionIndex > 0 && filenameExtensionIndex < filename.length() - 1 )
			extension = filename.substring( filenameExtensionIndex );
		else
		{
			String mime = resolver.getType( uri );
			if( mime != null )
			{
				String mimeExtension = MimeTypeMap.getSingleton().getExtensionFromMimeType( mime );
				if( mimeExtension != null && mimeExtension.length() > 0 )
					extension = "." + mimeExtension;
			}
		}

		if( extension == null )
			extension = ".tmp";

		if( !NativeGalleryMediaPickerFragment.tryPreserveFilenames )
			filename = savePathFilename;
		else if( filename.endsWith( extension ) )
			filename = filename.substring( 0, filename.length() - extension.length() );

		try
		{
			InputStream input = resolver.openInputStream( uri );
			if( input == null )
				return null;

			if( fileSize < 0 )
			{
				try
				{
					fileSize = input.available();
				}
				catch( Exception e )
				{
				}

				if( fileSize < 0 )
					fileSize = 0;
			}

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
				progress = ( fileSize > 0 ) ? 0 : -1;

				byte[] buf = new byte[4096];
				int len;
				while( ( len = input.read( buf ) ) > 0 )
				{
					if( cancelled )
						break;

					output.write( buf, 0, len );

					if( fileSize > 0 )
					{
						copiedBytes += len;

						progress = (int) ( ( (double) copiedBytes / fileSize ) * 100 );
						if( progress > 100 )
							progress = 100;
					}
				}

				if( cancelled )
				{
					output.close();
					output = null;

					tempFile.delete();
				}
				else if( fileSize > 0 )
					progress = 100;

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
}