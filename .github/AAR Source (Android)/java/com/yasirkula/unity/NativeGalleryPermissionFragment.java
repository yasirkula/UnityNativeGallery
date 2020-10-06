package com.yasirkula.unity;

// Original work Copyright (c) 2017 Yury Habets
// Modified work Copyright 2018 yasirkula
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Fragment;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

@TargetApi( Build.VERSION_CODES.M )
public class NativeGalleryPermissionFragment extends Fragment
{
	public static final String READ_PERMISSION_ONLY = "NG_ReadOnly";
	private static final int PERMISSIONS_REQUEST_CODE = 123655;

	private final NativeGalleryPermissionReceiver permissionReceiver;

	public NativeGalleryPermissionFragment()
	{
		permissionReceiver = null;
	}

	public NativeGalleryPermissionFragment( final NativeGalleryPermissionReceiver permissionReceiver )
	{
		this.permissionReceiver = permissionReceiver;
	}

	@Override
	public void onCreate( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );
		if( permissionReceiver == null )
			getFragmentManager().beginTransaction().remove( this ).commit();
		else
		{
			boolean readPermissionOnly = getArguments().getBoolean( READ_PERMISSION_ONLY );
			String[] permissions = readPermissionOnly ? new String[] { Manifest.permission.READ_EXTERNAL_STORAGE } :
					new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE };
			requestPermissions( permissions, PERMISSIONS_REQUEST_CODE );
		}
	}

	@Override
	public void onRequestPermissionsResult( int requestCode, String[] permissions, int[] grantResults )
	{
		if( requestCode != PERMISSIONS_REQUEST_CODE )
			return;

		if( permissionReceiver == null )
		{
			Log.e( "Unity", "Fragment data got reset while asking permissions!" );

			getFragmentManager().beginTransaction().remove( this ).commit();
			return;
		}

		// 0 -> denied, must go to settings
		// 1 -> granted
		// 2 -> denied, can ask again
		int result = 1;
		if( permissions.length == 0 || grantResults.length == 0 )
			result = 2;
		else
		{
			for( int i = 0; i < permissions.length && i < grantResults.length; ++i )
			{
				if( grantResults[i] == PackageManager.PERMISSION_DENIED )
				{
					if( !shouldShowRequestPermissionRationale( permissions[i] ) )
					{
						result = 0;
						break;
					}

					result = 2;
				}
			}
		}

		permissionReceiver.OnPermissionResult( result );
		getFragmentManager().beginTransaction().remove( this ).commit();

		// Resolves a bug in Unity 2019 where the calling activity
		// doesn't resume automatically after the fragment finishes
		// Credit: https://stackoverflow.com/a/12409215/2373034
		try
		{
			Intent resumeUnityActivity = new Intent( getActivity(), getActivity().getClass() );
			resumeUnityActivity.setFlags( Intent.FLAG_ACTIVITY_REORDER_TO_FRONT );
			getActivity().startActivityIfNeeded( resumeUnityActivity, 0 );
		}
		catch( Exception e )
		{
			Log.e( "Unity", "Exception (resume):", e );
		}
	}
}