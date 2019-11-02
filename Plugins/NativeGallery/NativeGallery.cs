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

	public struct VideoProperties
	{
		public readonly int width;
		public readonly int height;
		public readonly long duration;
		public readonly float rotation;

		public VideoProperties( int width, int height, long duration, float rotation )
		{
			this.width = width;
			this.height = height;
			this.duration = duration;
			this.rotation = rotation;
		}
	}

	public enum Permission { Denied = 0, Granted = 1, ShouldAsk = 2 };
	private enum MediaType { Image = 0, Video = 1, Audio = 2 };

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
	private static extern void _NativeGallery_PickImage( string imageSavePath );

	[System.Runtime.InteropServices.DllImport( "__Internal" )]
	private static extern void _NativeGallery_PickVideo( string videoSavePath );

	[System.Runtime.InteropServices.DllImport( "__Internal" )]
	private static extern string _NativeGallery_GetImageProperties( string path );

	[System.Runtime.InteropServices.DllImport( "__Internal" )]
	private static extern string _NativeGallery_GetVideoProperties( string path );

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
				m_temporaryImagePath = Path.Combine( Application.temporaryCachePath, "tmpImg" );
				Directory.CreateDirectory( Application.temporaryCachePath );
			}

			return m_temporaryImagePath;
		}
	}

	private static string m_selectedImagePath = null;
	private static string SelectedImagePath
	{
		get
		{
			if( m_selectedImagePath == null )
			{
				m_selectedImagePath = Path.Combine( Application.temporaryCachePath, "pickedImg" );
				Directory.CreateDirectory( Application.temporaryCachePath );
			}

			return m_selectedImagePath;
		}
	}

	private static string m_selectedVideoPath = null;
	private static string SelectedVideoPath
	{
		get
		{
			if( m_selectedVideoPath == null )
			{
				m_selectedVideoPath = Path.Combine( Application.temporaryCachePath, "pickedVideo" );
				Directory.CreateDirectory( Application.temporaryCachePath );
			}

			return m_selectedVideoPath;
		}
	}

	private static string m_selectedAudioPath = null;
	private static string SelectedAudioPath
	{
		get
		{
			if( m_selectedAudioPath == null )
			{
				m_selectedAudioPath = Path.Combine( Application.temporaryCachePath, "pickedAudio" );
				Directory.CreateDirectory( Application.temporaryCachePath );
			}

			return m_selectedAudioPath;
		}
	}
#endif
	#endregion

	#region Runtime Permissions
	public static Permission CheckPermission( bool readPermissionOnly = false )
	{
#if !UNITY_EDITOR && UNITY_ANDROID
		Permission result = (Permission) AJC.CallStatic<int>( "CheckPermission", Context, readPermissionOnly );
		if( result == Permission.Denied && (Permission) PlayerPrefs.GetInt( "NativeGalleryPermission", (int) Permission.ShouldAsk ) == Permission.ShouldAsk )
			result = Permission.ShouldAsk;

		return result;
#elif !UNITY_EDITOR && UNITY_IOS
		return (Permission) _NativeGallery_CheckPermission();
#else
		return Permission.Granted;
#endif
	}

	public static Permission RequestPermission( bool readPermissionOnly = false )
	{
#if !UNITY_EDITOR && UNITY_ANDROID
		object threadLock = new object();
		lock( threadLock )
		{
			NGPermissionCallbackAndroid nativeCallback = new NGPermissionCallbackAndroid( threadLock );

			AJC.CallStatic( "RequestPermission", Context, nativeCallback, readPermissionOnly, PlayerPrefs.GetInt( "NativeGalleryPermission", (int) Permission.ShouldAsk ) );

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
	public static Permission SaveImageToGallery( byte[] mediaBytes, string album, string filename, MediaSaveCallback callback = null )
	{
		return SaveToGallery( mediaBytes, album, filename, MediaType.Image, callback );
	}

	public static Permission SaveImageToGallery( string existingMediaPath, string album, string filename, MediaSaveCallback callback = null )
	{
		return SaveToGallery( existingMediaPath, album, filename, MediaType.Image, callback );
	}

	public static Permission SaveImageToGallery( Texture2D image, string album, string filename, MediaSaveCallback callback = null )
	{
		if( image == null )
			throw new ArgumentException( "Parameter 'image' is null!" );

		if( filename.EndsWith( ".jpeg" ) || filename.EndsWith( ".jpg" ) )
			return SaveToGallery( GetTextureBytes( image, true ), album, filename, MediaType.Image, callback );
		else if( filename.EndsWith( ".png" ) )
			return SaveToGallery( GetTextureBytes( image, false ), album, filename, MediaType.Image, callback );
		else
			return SaveToGallery( GetTextureBytes( image, false ), album, filename + ".png", MediaType.Image, callback );
	}

	public static Permission SaveVideoToGallery( byte[] mediaBytes, string album, string filename, MediaSaveCallback callback = null )
	{
		return SaveToGallery( mediaBytes, album, filename, MediaType.Video, callback );
	}

	public static Permission SaveVideoToGallery( string existingMediaPath, string album, string filename, MediaSaveCallback callback = null )
	{
		return SaveToGallery( existingMediaPath, album, filename, MediaType.Video, callback );
	}

	private static Permission SaveAudioToGallery( byte[] mediaBytes, string album, string filename, MediaSaveCallback callback = null )
	{
		return SaveToGallery( mediaBytes, album, filename, MediaType.Audio, callback );
	}

	private static Permission SaveAudioToGallery( string existingMediaPath, string album, string filename, MediaSaveCallback callback = null )
	{
		return SaveToGallery( existingMediaPath, album, filename, MediaType.Audio, callback );
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

	public static Permission GetImageFromGallery( MediaPickCallback callback, string title = "", string mime = "image/*" )
	{
		return GetMediaFromGallery( callback, MediaType.Image, mime, title );
	}

	public static Permission GetVideoFromGallery( MediaPickCallback callback, string title = "", string mime = "video/*" )
	{
		return GetMediaFromGallery( callback, MediaType.Video, mime, title );
	}

	private static Permission GetAudioFromGallery( MediaPickCallback callback, string title = "", string mime = "audio/*" )
	{
		return GetMediaFromGallery( callback, MediaType.Audio, mime, title );
	}

	public static Permission GetImagesFromGallery( MediaPickMultipleCallback callback, string title = "", string mime = "image/*" )
	{
		return GetMultipleMediaFromGallery( callback, MediaType.Image, mime, title );
	}

	public static Permission GetVideosFromGallery( MediaPickMultipleCallback callback, string title = "", string mime = "video/*" )
	{
		return GetMultipleMediaFromGallery( callback, MediaType.Video, mime, title );
	}

	private static Permission GetAudiosFromGallery( MediaPickMultipleCallback callback, string title = "", string mime = "audio/*" )
	{
		return GetMultipleMediaFromGallery( callback, MediaType.Audio, mime, title );
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
	private static Permission SaveToGallery( byte[] mediaBytes, string album, string filename, MediaType mediaType, MediaSaveCallback callback )
	{
		Permission result = RequestPermission( false );
		if( result == Permission.Granted )
		{
			if( mediaBytes == null || mediaBytes.Length == 0 )
				throw new ArgumentException( "Parameter 'mediaBytes' is null or empty!" );

			if( album == null || album.Length == 0 )
				throw new ArgumentException( "Parameter 'album' is null or empty!" );

			if( filename == null || filename.Length == 0 )
				throw new ArgumentException( "Parameter 'filename' is null or empty!" );

			if( string.IsNullOrEmpty( Path.GetExtension( filename ) ) )
				Debug.LogWarning( "'filename' doesn't have an extension, this might result in unexpected behaviour!" );

			string path = GetTemporarySavePath( filename );
#if UNITY_EDITOR
			Debug.Log( "SaveToGallery called successfully in the Editor" );
#else
			File.WriteAllBytes( path, mediaBytes );
#endif

			SaveToGalleryInternal( path, album, mediaType, callback );
		}

		return result;
	}

	private static Permission SaveToGallery( string existingMediaPath, string album, string filename, MediaType mediaType, MediaSaveCallback callback )
	{
		Permission result = RequestPermission( false );
		if( result == Permission.Granted )
		{
			if( !File.Exists( existingMediaPath ) )
				throw new FileNotFoundException( "File not found at " + existingMediaPath );

			if( album == null || album.Length == 0 )
				throw new ArgumentException( "Parameter 'album' is null or empty!" );

			if( filename == null || filename.Length == 0 )
				throw new ArgumentException( "Parameter 'filename' is null or empty!" );

			if( string.IsNullOrEmpty( Path.GetExtension( filename ) ) )
			{
				string originalExtension = Path.GetExtension( existingMediaPath );
				if( string.IsNullOrEmpty( originalExtension ) )
					Debug.LogWarning( "'filename' doesn't have an extension, this might result in unexpected behaviour!" );
				else
					filename += originalExtension;
			}

			string path = GetTemporarySavePath( filename );
#if UNITY_EDITOR
			Debug.Log( "SaveToGallery called successfully in the Editor" );
#else
			File.Copy( existingMediaPath, path, true );
#endif

			SaveToGalleryInternal( path, album, mediaType, callback );
		}

		return result;
	}

	private static void SaveToGalleryInternal( string path, string album, MediaType mediaType, MediaSaveCallback callback )
	{
#if !UNITY_EDITOR && UNITY_ANDROID
		AJC.CallStatic( "SaveMedia", Context, (int) mediaType, path, album );

		File.Delete( path );

		if( callback != null )
			callback( null );
#elif !UNITY_EDITOR && UNITY_IOS
		if( mediaType == MediaType.Audio )
		{
			if( callback != null )
				callback( "Saving audio files is not supported on iOS" );

			return;
		}

		Debug.Log( "Saving to Pictures: " + Path.GetFileName( path ) );

		NGMediaSaveCallbackiOS.Initialize( callback );
		if( mediaType == MediaType.Image )
			_NativeGallery_ImageWriteToAlbum( path, album );
		else if( mediaType == MediaType.Video )
			_NativeGallery_VideoWriteToAlbum( path, album );
#else
		if( callback != null )
			callback( null );
#endif
	}

	private static string GetTemporarySavePath( string filename )
	{
		string saveDir = Path.Combine( Application.persistentDataPath, "NGallery" );
		Directory.CreateDirectory( saveDir );

#if !UNITY_EDITOR && UNITY_IOS
		// Ensure a unique temporary filename on iOS:
		// iOS internally copies images/videos to Photos directory of the system,
		// but the process is async. The redundant file is deleted by objective-c code
		// automatically after the media is saved but while it is being saved, the file
		// should NOT be overwritten. Therefore, always ensure a unique filename on iOS
		string path = Path.Combine( saveDir, filename );
		if( File.Exists( path ) )
		{
			int fileIndex = 0;
			string filenameWithoutExtension = Path.GetFileNameWithoutExtension( filename );
			string extension = Path.GetExtension( filename );

			do
			{
				path = Path.Combine( saveDir, string.Concat( filenameWithoutExtension, ++fileIndex, extension ) );
			} while( File.Exists( path ) );
		}

		return path;
#else
		return Path.Combine( saveDir, filename );
#endif
	}

	private static Permission GetMediaFromGallery( MediaPickCallback callback, MediaType mediaType, string mime, string title )
	{
		Permission result = RequestPermission( true );
		if( result == Permission.Granted && !IsMediaPickerBusy() )
		{
#if !UNITY_EDITOR && UNITY_ANDROID
			string savePath;
			if( mediaType == MediaType.Image )
				savePath = SelectedImagePath;
			else if( mediaType == MediaType.Video )
				savePath = SelectedVideoPath;
			else
				savePath = SelectedAudioPath;

			AJC.CallStatic( "PickMedia", Context, new NGMediaReceiveCallbackAndroid( callback, null ), (int) mediaType, false, savePath, mime, title );
#elif !UNITY_EDITOR && UNITY_IOS
			NGMediaReceiveCallbackiOS.Initialize( callback );
			if( mediaType == MediaType.Image )
				_NativeGallery_PickImage( SelectedImagePath );
			else if( mediaType == MediaType.Video )
				_NativeGallery_PickVideo( SelectedVideoPath );
			else if( callback != null ) // Selecting audio files is not supported on iOS
				callback( null );
#else
			if( callback != null )
				callback( null );
#endif
		}

		return result;
	}

	private static Permission GetMultipleMediaFromGallery( MediaPickMultipleCallback callback, MediaType mediaType, string mime, string title )
	{
		Permission result = RequestPermission( true );
		if( result == Permission.Granted && !IsMediaPickerBusy() )
		{
			if( CanSelectMultipleFilesFromGallery() )
			{
#if !UNITY_EDITOR && UNITY_ANDROID
				string savePath;
				if( mediaType == MediaType.Image )
					savePath = SelectedImagePath;
				else if( mediaType == MediaType.Video )
					savePath = SelectedVideoPath;
				else
					savePath = SelectedAudioPath;

				AJC.CallStatic( "PickMedia", Context, new NGMediaReceiveCallbackAndroid( null, callback ), (int) mediaType, true, savePath, mime, title );
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

	private static byte[] GetTextureBytes( Texture2D texture, bool isJpeg )
	{
		try
		{
			return isJpeg ? texture.EncodeToJPG( 100 ) : texture.EncodeToPNG();
		}
		catch( UnityException )
		{
			return GetTextureBytesFromCopy( texture, isJpeg );
		}
		catch( ArgumentException )
		{
			return GetTextureBytesFromCopy( texture, isJpeg );
		}

#pragma warning disable 0162
		return null;
#pragma warning restore 0162
	}

	private static byte[] GetTextureBytesFromCopy( Texture2D texture, bool isJpeg )
	{
		// Texture is marked as non-readable, create a readable copy and save it instead
		Debug.LogWarning( "Saving non-readable textures is slower than saving readable textures" );

		Texture2D sourceTexReadable = null;
		RenderTexture rt = RenderTexture.GetTemporary( texture.width, texture.height );
		RenderTexture activeRT = RenderTexture.active;

		try
		{
			Graphics.Blit( texture, rt );
			RenderTexture.active = rt;

			sourceTexReadable = new Texture2D( texture.width, texture.height, texture.format, false );
			sourceTexReadable.ReadPixels( new Rect( 0, 0, texture.width, texture.height ), 0, 0, false );
			sourceTexReadable.Apply( false, false );
		}
		catch( Exception e )
		{
			Debug.LogException( e );

			Object.DestroyImmediate( sourceTexReadable );
			return null;
		}
		finally
		{
			RenderTexture.active = activeRT;
			RenderTexture.ReleaseTemporary( rt );
		}

		try
		{
			return isJpeg ? sourceTexReadable.EncodeToJPG( 100 ) : sourceTexReadable.EncodeToPNG();
		}
		catch( Exception e )
		{
			Debug.LogException( e );
			return null;
		}
		finally
		{
			Object.DestroyImmediate( sourceTexReadable );
		}
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
			}
		}

		return new ImageProperties( width, height, mimeType, orientation );
	}

	public static VideoProperties GetVideoProperties( string videoPath )
	{
		if( !File.Exists( videoPath ) )
			throw new FileNotFoundException( "File not found at " + videoPath );

#if !UNITY_EDITOR && UNITY_ANDROID
		string value = AJC.CallStatic<string>( "GetVideoProperties", Context, videoPath );
#elif !UNITY_EDITOR && UNITY_IOS
		string value = _NativeGallery_GetVideoProperties( videoPath );
#else
		string value = null;
#endif

		int width = 0, height = 0;
		long duration = 0L;
		float rotation = 0f;
		if( !string.IsNullOrEmpty( value ) )
		{
			string[] properties = value.Split( '>' );
			if( properties != null && properties.Length >= 4 )
			{
				if( !int.TryParse( properties[0].Trim(), out width ) )
					width = 0;
				if( !int.TryParse( properties[1].Trim(), out height ) )
					height = 0;
				if( !long.TryParse( properties[2].Trim(), out duration ) )
					duration = 0L;
				if( !float.TryParse( properties[3].Trim(), out rotation ) )
					rotation = 0f;
			}
		}

		if( rotation == -90f )
			rotation = 270f;

		return new VideoProperties( width, height, duration, rotation );
	}
	#endregion
}