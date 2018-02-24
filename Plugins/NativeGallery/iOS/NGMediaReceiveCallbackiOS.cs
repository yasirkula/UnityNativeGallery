using UnityEngine;

namespace NativeGalleryNamespace
{
	public class NGMediaReceiveCallbackiOS : MonoBehaviour
	{
		private static NGMediaReceiveCallbackiOS instance;
		private NativeGallery.MediaPickCallback callback;

		public static bool IsBusy { get; private set; }

		public static void Initialize( NativeGallery.MediaPickCallback callback )
		{
			if( IsBusy )
				return;

			if( instance == null )
			{
				instance = new GameObject( "NGMediaReceiveCallbackiOS" ).AddComponent<NGMediaReceiveCallbackiOS>();
				DontDestroyOnLoad( instance.gameObject );
			}

			instance.callback = callback;

			IsBusy = true;
		}

		public void OnMediaReceived( string path )
		{
			if( string.IsNullOrEmpty( path ) )
				path = null;

			if( callback != null )
			{
				callback( path );
				callback = null;
			}
			
			IsBusy = false;
		}
	}
}