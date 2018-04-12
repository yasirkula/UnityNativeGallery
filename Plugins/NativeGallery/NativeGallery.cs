using System;
using System.IO;
using UnityEngine;
using Object = UnityEngine.Object;
#if !UNITY_EDITOR && ( UNITY_ANDROID || UNITY_IOS )
using NativeGalleryNamespace;
#endif

public static class NativeGallery
{
	public struct ImageProperties
	{
		public readonly int width;
		public readonly int height;
		public readonly string mimeType;
		public readonly ImageOrientation orientation;

		public ImageProperties( int width, int height, string mimeType, ImageOrientation orientation )
		{
			this.width = width;
			this.height = height;
			this.mimeType = mimeType;
			this.orientation = orientation;
		}
	}

	public enum Permission { Denied = 0, Granted = 1, ShouldAsk = 2 };

	// EXIF orientation: http://sylvana.net/jpegcrop/exif_orientation.html (indices are reordered)
	public enum ImageOrientation { Unknown = -1, Normal = 0, Rotate90 = 1, Rotate180 = 2, Rotate270 = 3, FlipHorizontal = 4, Transpose = 5, FlipVertical = 6, Transverse = 7 };

	public delegate void MediaSaveCallback( string error );
	public delegate void MediaPickCallback( string path );
	public delegate void MediaPickMultipleCallback( string[] paths );

	#region Platform Specific Elements
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
	private static extern int _NativeGallery_CheckPermission();

	[System.Runtime.InteropServices.DllImport( "__Internal" )]
	private static extern int _NativeGallery_RequestPermission();

	[System.Runtime.InteropServices.DllImport( "__Internal" )]
	private static extern int _NativeGallery_CanOpenSettings();

	[System.Runtime.InteropServices.DllImport( "__Internal" )]
	private static extern void _NativeGallery_OpenSettings();

	[System.Runtime.InteropServices.DllImport( "__Internal" )]
	private static extern void _NativeGallery_ImageWriteToAlbum( string path, string album );

	[System.Runtime.InteropServices.DllImport( "__Internal" )]
	private static extern void _NativeGallery_VideoWriteToAlbum( string path, string album );

	[System.Runtime.InteropServices.DllImport( "__Internal" )]
	private static extern void _NativeGallery_PickImage( string imageSavePath, int maxSize );

	[System.Runtime.InteropServices.DllImport( "__Internal" )]
	private static extern void _NativeGallery_PickVideo();

	[System.Runtime.InteropServices.DllImport( "__Internal" )]
	private static extern string _NativeGallery_GetImageProperties( string path );

	[System.Runtime.InteropServices.DllImport( "__Internal" )]
	private static extern string _NativeGallery_LoadImageAtPath( string path, string temporaryFilePath, int maxSize );
#endif

#if !UNITY_EDITOR && ( UNITY_ANDROID || UNITY_IOS )
	private static string m_temporaryImagePath = null;
	private static string TemporaryImagePath
	{
		get
		{
			if( m_temporaryImagePath == null )
			{
				m_temporaryImagePath = Path.Combine( Application.temporaryCachePath, "__tmpImG" );
				Directory.CreateDirectory( Application.temporaryCachePath );
			}

			return m_temporaryImagePath;
		}
	}
#endif

#if !UNITY_EDITOR && UNITY_IOS
	private static string m_iOSSelectedImagePath = null;
	private static string IOSSelectedImagePath
	{
		get
		{
			if( m_iOSSelectedImagePath == null )
			{
				m_iOSSelectedImagePath = Path.Combine( Application.temporaryCachePath, "tmp.png" );
				Directory.CreateDirectory( Application.temporaryCachePath );
			}

			return m_iOSSelectedImagePath;
		}
	}
#endif
	#endregion

	#region Runtime Permissions
	public static Permission CheckPermission()
	{
#if !UNITY_EDITOR && UNITY_ANDROID
		Permission result = (Permission) AJC.CallStatic<int>( "CheckPermission", Context );
		if( result == Permission.Denied && (Permission) PlayerPrefs.GetInt( "NativeGalleryPermission", (int) Permission.ShouldAsk ) == Permission.ShouldAsk )
			result = Permission.ShouldAsk;

		return result;
#elif !UNITY_EDITOR && UNITY_IOS
		return (Permission) _NativeGallery_CheckPermission();
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
		return (Permission) _NativeGallery_RequestPermission();
#else
		return Permission.Granted;
#endif
	}

	public static bool CanOpenSettings()
	{
#if !UNITY_EDITOR && UNITY_IOS
		return _NativeGallery_CanOpenSettings() == 1;
#else
		return true;
#endif
	}

	public static void OpenSettings()
	{
#if !UNITY_EDITOR && UNITY_ANDROID
		AJC.CallStatic( "OpenSettings", Context );
#elif !UNITY_EDITOR && UNITY_IOS
		_NativeGallery_OpenSettings();
#endif
	}
	#endregion

	#region Save Functions
	public static Permission SaveImageToGallery( byte[] mediaBytes, string album, string filenameFormatted, MediaSaveCallback callback = null )
	{
		return SaveToGallery( mediaBytes, album, filenameFormatted, true, callback );
	}

	public static Permission SaveImageToGallery( string existingMediaPath, string album, string filenameFormatted, MediaSaveCallback callback = null )
	{
		return SaveToGallery( existingMediaPath, album, filenameFormatted, true, callback );
	}

	public static Permission SaveImageToGallery( Texture2D image, string album, string filenameFormatted, MediaSaveCallback callback = null )
	{
		if( image == null )
			throw new ArgumentException( "Parameter 'image' is null!" );

		if( filenameFormatted.EndsWith( ".jpeg" ) || filenameFormatted.EndsWith( ".jpg" ) )
			return SaveToGallery( image.EncodeToJPG( 100 ), album, filenameFormatted, true, callback );
		else if( filenameFormatted.EndsWith( ".png" ) )
			return SaveToGallery( image.EncodeToPNG(), album, filenameFormatted, true, callback );
		else
			return SaveToGallery( image.EncodeToPNG(), album, filenameFormatted + ".png", true, callback );
	}

	public static Permission SaveVideoToGallery( byte[] mediaBytes, string album, string filenameFormatted, MediaSaveCallback callback = null )
	{
		return SaveToGallery( mediaBytes, album, filenameFormatted, false, callback );
	}

	public static Permission SaveVideoToGallery( string existingMediaPath, string album, string filenameFormatted, MediaSaveCallback callback = null )
	{
		return SaveToGallery( existingMediaPath, album, filenameFormatted, false, callback );
	}
	#endregion

	#region Load Functions
	public static bool CanSelectMultipleFilesFromGallery()
	{
#if !UNITY_EDITOR && UNITY_ANDROID
		return AJC.CallStatic<bool>( "CanSelectMultipleMedia" );
#else
		return false;
#endif
	}

	public static Permission GetImageFromGallery( MediaPickCallback callback, string title = "", string mime = "image/*", int maxSize = -1 )
	{
		return GetMediaFromGallery( callback, true, mime, title, maxSize );
	}

	public static Permission GetVideoFromGallery( MediaPickCallback callback, string title = "", string mime = "video/*" )
	{
		return GetMediaFromGallery( callback, false, mime, title, -1 );
	}

	public static Permission GetImagesFromGallery( MediaPickMultipleCallback callback, string title = "", string mime = "image/*", int maxSize = -1 )
	{
		return GetMultipleMediaFromGallery( callback, true, mime, title, maxSize );
	}

	public static Permission GetVideosFromGallery( MediaPickMultipleCallback callback, string title = "", string mime = "video/*" )
	{
		return GetMultipleMediaFromGallery( callback, false, mime, title, -1 );
	}

	public static bool IsMediaPickerBusy()
	{
#if !UNITY_EDITOR && UNITY_IOS
		return NGMediaReceiveCallbackiOS.IsBusy;
#else
		return false;
#endif
	}
	#endregion

	#region Internal Functions
	private static Permission SaveToGallery( byte[] mediaBytes, string album, string filenameFormatted, bool isImage, MediaSaveCallback callback )
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

			SaveToGalleryInternal( path, album, isImage, callback );
		}

		return result;
	}

	private static Permission SaveToGallery( string existingMediaPath, string album, string filenameFormatted, bool isImage, MediaSaveCallback callback )
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

			SaveToGalleryInternal( path, album, isImage, callback );
		}

		return result;
	}

	private static void SaveToGalleryInternal( string path, string album, bool isImage, MediaSaveCallback callback )
	{
#if !UNITY_EDITOR && UNITY_ANDROID
		AJC.CallStatic( "MediaScanFile", Context, path );

		if( callback != null )
			callback( null );

		Debug.Log( "Saving to gallery: " + path );
#elif !UNITY_EDITOR && UNITY_IOS
		NGMediaSaveCallbackiOS.Initialize( callback );
		if( isImage )
			_NativeGallery_ImageWriteToAlbum( path, album );
		else
			_NativeGallery_VideoWriteToAlbum( path, album );

		Debug.Log( "Saving to Pictures: " + Path.GetFileName( path ) );
#else
		if( callback != null )
			callback( null );
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

	private static Permission GetMediaFromGallery( MediaPickCallback callback, bool imageMode, string mime, string title, int maxSize )
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
			{
				if( maxSize <= 0 )
					maxSize = SystemInfo.maxTextureSize;

				_NativeGallery_PickImage( IOSSelectedImagePath, maxSize );
			}
			else
				_NativeGallery_PickVideo();
#else
			if( callback != null )
				callback( null );
#endif
		}

		return result;
	}

	private static Permission GetMultipleMediaFromGallery( MediaPickMultipleCallback callback, bool imageMode, string mime, string title, int maxSize )
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
	#endregion

	#region Utility Functions
	public static Texture2D LoadImageAtPath( string imagePath, int maxSize = -1, bool markTextureNonReadable = true, 
		bool generateMipmaps = true, bool linearColorSpace = false )
	{
		if( string.IsNullOrEmpty( imagePath ) )
			throw new ArgumentException( "Parameter 'imagePath' is null or empty!" );

		if( !File.Exists( imagePath ) )
			throw new FileNotFoundException( "File not found at " + imagePath );
		
		if( maxSize <= 0 )
			maxSize = SystemInfo.maxTextureSize;

#if !UNITY_EDITOR && UNITY_ANDROID
		string loadPath = AJC.CallStatic<string>( "LoadImageAtPath", Context, imagePath, TemporaryImagePath, maxSize );
#elif !UNITY_EDITOR && UNITY_IOS
		string loadPath = _NativeGallery_LoadImageAtPath( imagePath, TemporaryImagePath, maxSize );
#else
		string loadPath = imagePath;
#endif

		String extension = Path.GetExtension( imagePath ).ToLowerInvariant();
		TextureFormat format = ( extension == ".jpg" || extension == ".jpeg" ) ? TextureFormat.RGB24 : TextureFormat.RGBA32;

		Texture2D result = new Texture2D( 2, 2, format, generateMipmaps, linearColorSpace );

		try
		{
			if( !result.LoadImage( File.ReadAllBytes( loadPath ), markTextureNonReadable ) )
			{
				Object.DestroyImmediate( result );
				return null;
			}
		}
		catch( Exception e )
		{
			Debug.LogException( e );

			Object.DestroyImmediate( result );
			return null;
		}
		finally
		{
			if( loadPath != imagePath )
			{
				try
				{
					File.Delete( loadPath );
				}
				catch { }
			}
		}

		return result;
	}

	public static ImageProperties GetImageProperties( string imagePath )
	{
		if( !File.Exists( imagePath ) )
			throw new FileNotFoundException( "File not found at " + imagePath );

#if !UNITY_EDITOR && UNITY_ANDROID
		string value = AJC.CallStatic<string>( "GetImageProperties", Context, imagePath );
#elif !UNITY_EDITOR && UNITY_IOS
		string value = _NativeGallery_GetImageProperties( imagePath );
#else
		string value = null;
#endif

		int width = 0, height = 0;
		string mimeType = null;
		ImageOrientation orientation = ImageOrientation.Unknown;
		if( !string.IsNullOrEmpty( value ) )
		{
			string[] properties = value.Split( '>' );
			if( properties != null && properties.Length >= 4 )
			{
				if( !int.TryParse( properties[0].Trim(), out width ) )
					width = 0;
				if( !int.TryParse( properties[1].Trim(), out height ) )
					height = 0;

				mimeType = properties[2].Trim();
				if( mimeType.Length == 0 )
				{
					String extension = Path.GetExtension( imagePath ).ToLowerInvariant();
					if( extension == ".png" )
						mimeType = "image/png";
					else if( extension == ".jpg" || extension == ".jpeg" )
						mimeType = "image/jpeg";
					else if( extension == ".gif" )
						mimeType = "image/gif";
					else if( extension == ".bmp" )
						mimeType = "image/bmp";
					else
						mimeType = null;
				}

				int orientationInt;
				if( int.TryParse( properties[3].Trim(), out orientationInt ) )
					orientation = (ImageOrientation) orientationInt;

#if !UNITY_EDITOR && UNITY_IOS
				if( orientation == ImageOrientation.Unknown ) // selected media is saved in correct orientation on iOS
					orientation = ImageOrientation.Normal;
#endif
			}
		}

		return new ImageProperties( width, height, mimeType, orientation );
	}
	#endregion
}