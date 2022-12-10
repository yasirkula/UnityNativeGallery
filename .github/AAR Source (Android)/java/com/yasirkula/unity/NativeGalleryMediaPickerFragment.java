package com.yasirkula.unity;

import android.app.Activity;
import android.app.Fragment;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

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
	public static boolean showProgressbar = true; // When enabled, a progressbar will be displayed while selected file(s) are copied (if necessary) to the destination directory
	public static boolean useDefaultGalleryApp = false; // false: Intent.createChooser is used to pick the Gallery app

	private final NativeGalleryMediaReceiver mediaReceiver;
	private boolean selectMultiple;
	private String savePathDirectory, savePathFilename;

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
			onActivityResult( MEDIA_REQUEST_CODE, Activity.RESULT_CANCELED, null );
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

			Intent intent = null;
			if( !preferGetContent && !selectMultiple && mediaTypeCount == 1 && mediaType != NativeGallery.MEDIA_TYPE_AUDIO )
			{
				intent = new Intent( Intent.ACTION_PICK );

				if( mediaType == NativeGallery.MEDIA_TYPE_IMAGE )
					intent.setDataAndType( MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mime );
				else if( mediaType == NativeGallery.MEDIA_TYPE_VIDEO )
					intent.setDataAndType( MediaStore.Video.Media.EXTERNAL_CONTENT_URI, mime );
				else
					intent.setDataAndType( MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mime );
			}

			if( intent == null )
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

				intent.setType( mime );
			}

			if( title != null && title.length() > 0 )
				intent.putExtra( Intent.EXTRA_TITLE, title );

			try
			{
				//  MIUI devices have issues with Intent.createChooser on at least Android 11 (https://stackoverflow.com/questions/67785661/taking-and-picking-photos-on-poco-x3-with-android-11-does-not-work)
				if( useDefaultGalleryApp || ( Build.VERSION.SDK_INT == 30 && NativeGalleryUtils.IsXiaomiOrMIUI() ) )
					startActivityForResult( intent, MEDIA_REQUEST_CODE );
				else
					startActivityForResult( Intent.createChooser( intent, title ), MEDIA_REQUEST_CODE );
			}
			catch( ActivityNotFoundException e )
			{
				Toast.makeText( getActivity(), "No apps can perform this action.", Toast.LENGTH_LONG ).show();
				onActivityResult( MEDIA_REQUEST_CODE, Activity.RESULT_CANCELED, null );
			}
		}
	}

	@Override
	public void onActivityResult( int requestCode, int resultCode, Intent data )
	{
		if( requestCode != MEDIA_REQUEST_CODE )
			return;

		NativeGalleryMediaPickerResultFragment resultFragment = null;

		if( mediaReceiver == null )
			Log.d( "Unity", "NativeGalleryMediaPickerFragment.mediaReceiver became null!" );
		else if( resultCode != Activity.RESULT_OK || data == null )
		{
			if( !selectMultiple )
				mediaReceiver.OnMediaReceived( "" );
			else
				mediaReceiver.OnMultipleMediaReceived( "" );
		}
		else
		{
			NativeGalleryMediaPickerResultOperation resultOperation = new NativeGalleryMediaPickerResultOperation( getActivity(), mediaReceiver, data, selectMultiple, savePathDirectory, savePathFilename );
			if( showProgressbar )
				resultFragment = new NativeGalleryMediaPickerResultFragment( resultOperation );
			else
			{
				resultOperation.execute();
				resultOperation.sendResultToUnity();
			}
		}

		if( resultFragment == null )
			getFragmentManager().beginTransaction().remove( this ).commit();
		else
			getFragmentManager().beginTransaction().remove( this ).add( 0, resultFragment ).commit();
	}
}