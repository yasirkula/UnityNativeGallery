#if !UNITY_EDITOR && UNITY_IOS
using UnityEngine;

namespace NativeGalleryNamespace
{
	public class NGMediaSaveCallbackiOS : MonoBehaviour
	{
		private static NGMediaSaveCallbackiOS instance;
		private NativeGallery.MediaSaveCallback callback;

		public static void Initialize( NativeGallery.MediaSaveCallback callback )
		{
			if( instance == null )
			{
				instance = new GameObject( "NGMediaSaveCallbackiOS" ).AddComponent<NGMediaSaveCallbackiOS>();
				DontDestroyOnLoad( instance.gameObject );
			}
			else if( instance.callback != null )
				instance.callback( null );

			instance.callback = callback;
		}
		
		public void OnMediaSaveCompleted( string message )
		{
			if( callback != null )
			{
				callback( null );
				callback = null;
			}
		}

		public void OnMediaSaveFailed( string error )
		{
			if( string.IsNullOrEmpty( error ) )
				error = "Unknown error";

			if( callback != null )
			{
				callback( error );
				callback = null;
			}
		}
	}
}
#endif