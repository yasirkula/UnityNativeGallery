using System;
using System.IO;
using UnityEngine;

public static class NativeGallery
{
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
#elif !UNITY_EDITOR && UNITY_IOS
    [System.Runtime.InteropServices.DllImport( "__Internal" )]
    private static extern void _ScreenshotWriteToAlbum(string path);

    [System.Runtime.InteropServices.DllImport( "__Internal" )]
    private static extern void _VideoWriteToAlbum(string path);
#endif

	public static void SaveToGallery( byte[] mediaBytes, string directoryName, string filenameFormatted, bool isImage )
	{
		if( mediaBytes == null || mediaBytes.Length == 0 )
			throw new ArgumentException( "Parameter 'mediaBytes' is null or empty!" );

		if( directoryName == null || directoryName.Length == 0 )
			throw new ArgumentException( "Parameter 'directoryName' is null or empty!" );

		if( filenameFormatted == null || filenameFormatted.Length == 0 )
			throw new ArgumentException( "Parameter 'filenameFormatted' is null or empty!" );

		string path = GetSavePath( directoryName, filenameFormatted );

		File.WriteAllBytes( path, mediaBytes );

		SaveToGallery( path, isImage );
	}

	public static void SaveToGallery( string existingMediaPath, string directoryName, string filenameFormatted, bool isImage )
	{
		if( !File.Exists( existingMediaPath ) )
			throw new FileNotFoundException( "File not found at " + existingMediaPath );

		if( directoryName == null || directoryName.Length == 0 )
			throw new ArgumentException( "Parameter 'directoryName' is null or empty!" );

		if( filenameFormatted == null || filenameFormatted.Length == 0 )
			throw new ArgumentException( "Parameter 'filenameFormatted' is null or empty!" );

		string path = GetSavePath( directoryName, filenameFormatted );

		if( File.Exists( path ) )
			File.Delete( path );

		File.Move( existingMediaPath, path );

		SaveToGallery( path, isImage );
	}

	public static void SaveToGallery( Texture2D image, string directoryName, string filenameFormatted )
	{
		if( image == null )
			throw new ArgumentException( "Parameter 'image' is null!" );

		if( filenameFormatted.EndsWith( ".jpeg" ) || filenameFormatted.EndsWith( ".jpg" ) )
			SaveToGallery( image.EncodeToJPG( 100 ), directoryName, filenameFormatted, true );
		else if( filenameFormatted.EndsWith( ".png" ) )
			SaveToGallery( image.EncodeToPNG(), directoryName, filenameFormatted, true );
		else
			SaveToGallery( image.EncodeToPNG(), directoryName, filenameFormatted + ".png", true );
	}

	public static void DeleteFromGallery( string path, bool isImage )
	{
		if( !File.Exists( path ) )
			throw new FileNotFoundException( "File not found at " + path );

		File.Delete( path );

#if !UNITY_EDITOR && UNITY_ANDROID
        using( AndroidJavaClass unityClass = new AndroidJavaClass( "com.unity3d.player.UnityPlayer" ) )
		using( AndroidJavaObject context = unityClass.GetStatic<AndroidJavaObject>( "currentActivity" ) )
		{
			AJC.CallStatic( "MediaDeleteFile", context, path, isImage );
		}
#endif

		Debug.Log( "Deleted from gallery: " + path );
	}

	private static void SaveToGallery( string path, bool isImage )
	{
#if !UNITY_EDITOR && UNITY_ANDROID
        using( AndroidJavaClass unityClass = new AndroidJavaClass( "com.unity3d.player.UnityPlayer" ) )
		using( AndroidJavaObject context = unityClass.GetStatic<AndroidJavaObject>( "currentActivity" ) )
		{
			AJC.CallStatic( "MediaScanFile", context, path );
		}
#elif !UNITY_EDITOR && UNITY_IOS
		if( isImage )
	        _ScreenshotWriteToAlbum( path );
		else
			_VideoWriteToAlbum( path );
#endif

		Debug.Log( "Saved to gallery: " + path );
	}

	private static string GetSavePath( string directoryName, string filenameFormatted )
	{
		string saveDir;
#if !UNITY_EDITOR && UNITY_ANDROID
		saveDir = AJC.CallStatic<string>( "GetMediaPath", directoryName );
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

		return Path.Combine( saveDir, filenameFormatted );
	}
}