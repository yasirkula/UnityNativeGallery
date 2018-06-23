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

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Fragment;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

@TargetApi( Build.VERSION_CODES.M )
public class NativeGalleryPermissionFragment extends Fragment
{
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
		{
			getFragmentManager().beginTransaction().remove( this ).commit();
		}
		else
		{
			requestPermissions( new String[]
					{ Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE },
					PERMISSIONS_REQUEST_CODE );
		}
	}

	@Override
	public void onRequestPermissionsResult( int requestCode, String[] permissions, int[] grantResults )
	{
		if( requestCode != PERMISSIONS_REQUEST_CODE )
			return;

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

		if( permissionReceiver != null )
			permissionReceiver.OnPermissionResult( result );

		getFragmentManager().beginTransaction().remove( this ).commit();
	}
}