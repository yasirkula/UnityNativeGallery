using System;
using System.IO;
using UnityEngine;
using NativeGalleryNamespace;

public static class NativeGallery
{
	public enum Permission { Denied = 0, Granted = 1, ShouldAsk = 2 };

	public delegate void MediaPickCallback( string path );
	public delegate void MediaPickMultipleCallback( string[] paths );

#if !UNITY_EDITOR && UNITY_ANDROID
	private static AndroidJavaClass m_ajc = null;
	private static AndroidJavaClass AJC
	{
		get
		{
			if( m_ajc == null )
				m_ajc = new AndroidJavaClass( "com.yasirkula.unity.NativeGallery" );

			return m_ajc;
		}
	}

	private static AndroidJavaObject m_context = null;
	private static AndroidJavaObject Context
	{
		get
		{
			if( m_context == null )
			{
				using( AndroidJavaObject unityClass = new AndroidJavaClass( "com.unity3d.player.UnityPlayer" ) )
				{
					m_context = unityClass.GetStatic<AndroidJavaObject>( "currentActivity" );
				}
			}

			return m_context;
		}
	}
#elif !UNITY_EDITOR && UNITY_IOS
	[System.Runtime.InteropServices.DllImport( "__Internal" )]
	private static extern int _CheckPermission();

	[System.Runtime.InteropServices.DllImport( "__Internal" )]
	private static extern int _RequestPermission();

	[System.Runtime.InteropServices.DllImport( "__Internal" )]
	private static extern int _CanOpenSettings();

	[System.Runtime.InteropServices.DllImport( "__Internal" )]
	private static extern void _OpenSettings();

	[System.Runtime.InteropServices.DllImport( "__Internal" )]
	private static extern void _ImageWriteToAlbum( string path, string album );

	[System.Runtime.InteropServices.DllImport( "__Internal" )]
	private static extern void _VideoWriteToAlbum( string path, string album );

	[System.Runtime.InteropServices.DllImport( "__Internal" )]
	private static extern void _PickImage( string imageSavePath );

	[System.Runtime.InteropServices.DllImport( "__Internal" )]
	private static extern void _PickVideo();
#endif

	public static Permission CheckPermission()
	{
#if !UNITY_EDITOR && UNITY_ANDROID
		Permission result = (Permission) AJC.CallStatic<int>( "CheckPermission", Context );
		if( result == Permission.Denied && (Permission) PlayerPrefs.GetInt( "NativeGalleryPermission", (int) Permission.ShouldAsk ) == Permission.ShouldAsk )
			result = Permission.ShouldAsk;

		return result;
#elif !UNITY_EDITOR && UNITY_IOS
		return (Permission) _CheckPermission();
#else
		return Permission.Granted;
#endif
	}

	public static Permission RequestPermission()
	{
#if !UNITY_EDITOR && UNITY_ANDROID
		object threadLock = new object();
		lock( threadLock )
		{
			NGPermissionCallbackAndroid nativeCallback = new NGPermissionCallbackAndroid( threadLock );

			AJC.CallStatic( "RequestPermission", Context, nativeCallback, PlayerPrefs.GetInt( "NativeGalleryPermission", (int) Permission.ShouldAsk ) );

			if( nativeCallback.Result == -1 )
				System.Threading.Monitor.Wait( threadLock );

			if( (Permission) nativeCallback.Result != Permission.ShouldAsk && PlayerPrefs.GetInt( "NativeGalleryPermission", -1 ) != nativeCallback.Result )
			{
				PlayerPrefs.SetInt( "NativeGalleryPermission", nativeCallback.Result );
				PlayerPrefs.Save();
			}

			return (Permission) nativeCallback.Result;
		}
#elif !UNITY_EDITOR && UNITY_IOS
		return (Permission) _RequestPermission();
#else
		return Permission.Granted;
#endif
	}

	public static bool CanOpenSettings()
	{
#if !UNITY_EDITOR && UNITY_IOS
		return _CanOpenSettings() == 1;
#else
		return true;
#endif
	}

	public static void OpenSettings()
	{
#if !UNITY_EDITOR && UNITY_ANDROID
		AJC.CallStatic( "OpenSettings", Context );
#elif !UNITY_EDITOR && UNITY_IOS
		_OpenSettings();
#endif
	}

	public static Permission SaveImageToGallery( byte[] mediaBytes, string album, string filenameFormatted )
	{
		return SaveToGallery( mediaBytes, album, filenameFormatted, true );
	}

	public static Permission SaveImageToGallery( string existingMediaPath, string album, string filenameFormatted )
	{
		return SaveToGallery( existingMediaPath, album, filenameFormatted, true );
	}

	public static Permission SaveImageToGallery( Texture2D image, string album, string filenameFormatted )
	{
		if( image == null )
			throw new ArgumentException( "Parameter 'image' is null!" );

		if( filenameFormatted.EndsWith( ".jpeg" ) || filenameFormatted.EndsWith( ".jpg" ) )
			return SaveToGallery( image.EncodeToJPG( 100 ), album, filenameFormatted, true );
		else if( filenameFormatted.EndsWith( ".png" ) )
			return SaveToGallery( image.EncodeToPNG(), album, filenameFormatted, true );
		else
			return SaveToGallery( image.EncodeToPNG(), album, filenameFormatted + ".png", true );
	}

	public static Permission SaveVideoToGallery( byte[] mediaBytes, string album, string filenameFormatted )
	{
		return SaveToGallery( mediaBytes, album, filenameFormatted, false );
	}

	public static Permission SaveVideoToGallery( string existingMediaPath, string album, string filenameFormatted )
	{
		return SaveToGallery( existingMediaPath, album, filenameFormatted, false );
	}

	public static bool CanSelectMultipleFilesFromGallery()
	{
#if !UNITY_EDITOR && UNITY_ANDROID
		return AJC.CallStatic<bool>( "CanSelectMultipleMedia" );
#else
		return false;
#endif
	}

	public static Permission GetImageFromGallery( MediaPickCallback callback, string title = "", string mime = "image/*" )
	{
		return GetMediaFromGallery( callback, true, mime, title );
	}

	public static Permission GetVideoFromGallery( MediaPickCallback callback, string title = "", string mime = "video/*" )
	{
		return GetMediaFromGallery( callback, false, mime, title );
	}

	public static Permission GetImagesFromGallery( MediaPickMultipleCallback callback, string title = "", string mime = "image/*" )
	{
		return GetMultipleMediaFromGallery( callback, true, mime, title );
	}

	public static Permission GetVideosFromGallery( MediaPickMultipleCallback callback, string title = "", string mime = "video/*" )
	{
		return GetMultipleMediaFromGallery( callback, false, mime, title );
	}

	public static bool IsMediaPickerBusy()
	{
#if !UNITY_EDITOR && UNITY_IOS
		return NGMediaReceiveCallbackiOS.IsBusy;
#else
		return false;
#endif
	}

	private static Permission SaveToGallery( byte[] mediaBytes, string album, string filenameFormatted, bool isImage )
	{
		Permission result = RequestPermission();
		if( result == Permission.Granted )
		{
			if( mediaBytes == null || mediaBytes.Length == 0 )
				throw new ArgumentException( "Parameter 'mediaBytes' is null or empty!" );

			if( album == null || album.Length == 0 )
				throw new ArgumentException( "Parameter 'album' is null or empty!" );

			if( filenameFormatted == null || filenameFormatted.Length == 0 )
				throw new ArgumentException( "Parameter 'filenameFormatted' is null or empty!" );

			string path = GetSavePath( album, filenameFormatted );

			File.WriteAllBytes( path, mediaBytes );

			SaveToGalleryInternal( path, album, isImage );
		}

		return result;
	}

	private static Permission SaveToGallery( string existingMediaPath, string album, string filenameFormatted, bool isImage )
	{
		Permission result = RequestPermission();
		if( result == Permission.Granted )
		{
			if( !File.Exists( existingMediaPath ) )
				throw new FileNotFoundException( "File not found at " + existingMediaPath );

			if( album == null || album.Length == 0 )
				throw new ArgumentException( "Parameter 'album' is null or empty!" );

			if( filenameFormatted == null || filenameFormatted.Length == 0 )
				throw new ArgumentException( "Parameter 'filenameFormatted' is null or empty!" );

			string path = GetSavePath( album, filenameFormatted );

			File.Copy( existingMediaPath, path, true );

			SaveToGalleryInternal( path, album, isImage );
		}

		return result;
	}

	private static void SaveToGalleryInternal( string path, string album, bool isImage )
	{
#if !UNITY_EDITOR && UNITY_ANDROID
		AJC.CallStatic( "MediaScanFile", Context, path );

		Debug.Log( "Saving to gallery: " + path );
#elif !UNITY_EDITOR && UNITY_IOS
		if( isImage )
			_ImageWriteToAlbum( path, album );
		else
			_VideoWriteToAlbum( path, album );

		Debug.Log( "Saving to Pictures: " + Path.GetFileName( path ) );
#endif
	}

	private static string GetSavePath( string album, string filenameFormatted )
	{
		string saveDir;
#if !UNITY_EDITOR && UNITY_ANDROID
		saveDir = AJC.CallStatic<string>( "GetMediaPath", album );
#else
		saveDir = Application.persistentDataPath;
#endif
		
		if( filenameFormatted.Contains( "{0}" ) )
		{
			int fileIndex = 0;
			string path;
			do
			{
				path = Path.Combine( saveDir, string.Format( filenameFormatted, ++fileIndex ) );
			} while( File.Exists( path ) );

			return path;
		}

		saveDir = Path.Combine( saveDir, filenameFormatted );

#if !UNITY_EDITOR && UNITY_IOS
		// iOS internally copies images/videos to Photos directory of the system,
		// but the process is async. The redundant file is deleted by objective-c code
		// automatically after the media is saved but while it is being saved, the file
		// should NOT be overwritten. Therefore, always ensure a unique filename on iOS
		if( File.Exists( saveDir ) )
		{
			return GetSavePath( album,
				Path.GetFileNameWithoutExtension( filenameFormatted ) + " {0}" + Path.GetExtension( filenameFormatted ) );
		}
#endif

		return saveDir;
	}

	private static Permission GetMediaFromGallery( MediaPickCallback callback, bool imageMode, string mime, string title )
	{
		Permission result = RequestPermission();
		if( result == Permission.Granted && !IsMediaPickerBusy() )
		{
#if !UNITY_EDITOR && UNITY_ANDROID
			object threadLock = new object();
			lock( threadLock )
			{
				NGMediaReceiveCallbackAndroid nativeCallback = new NGMediaReceiveCallbackAndroid( threadLock );

				AJC.CallStatic( "PickMedia", Context, nativeCallback, imageMode, false, mime, title );

				if( string.IsNullOrEmpty( nativeCallback.Path ) )
					System.Threading.Monitor.Wait( threadLock );

				string path = nativeCallback.Path;
				if( string.IsNullOrEmpty( path ) )
					path = null;

				if( callback != null )
					callback( path );
			}
#elif !UNITY_EDITOR && UNITY_IOS
			NGMediaReceiveCallbackiOS.Initialize( callback );
			if( imageMode )
				_PickImage( Path.Combine( Application.temporaryCachePath, "tmp.png" ) );
			else
				_PickVideo();
#else
			if( callback != null )
				callback( null );
#endif
		}

		return result;
	}

	private static Permission GetMultipleMediaFromGallery( MediaPickMultipleCallback callback, bool imageMode, string mime, string title )
	{
		Permission result = RequestPermission();
		if( result == Permission.Granted && !IsMediaPickerBusy() )
		{
			if( CanSelectMultipleFilesFromGallery() )
			{
#if !UNITY_EDITOR && UNITY_ANDROID
				object threadLock = new object();
				lock( threadLock )
				{
					NGMediaReceiveCallbackAndroid nativeCallback = new NGMediaReceiveCallbackAndroid( threadLock );

					AJC.CallStatic( "PickMedia", Context, nativeCallback, imageMode, true, mime, title );

					if( nativeCallback.Paths == null )
						System.Threading.Monitor.Wait( threadLock );

					string[] paths = nativeCallback.Paths;
					if( paths != null && paths.Length == 0 )
						paths = null;

					if( callback != null )
						callback( paths );
				}
#else
				if( callback != null )
					callback( null );
#endif
			}
			else if( callback != null )
				callback( null );
		}

		return result;
	}
}