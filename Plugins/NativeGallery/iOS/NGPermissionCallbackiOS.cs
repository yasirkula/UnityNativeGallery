#if UNITY_EDITOR || UNITY_IOS
using UnityEngine;

namespace NativeGalleryNamespace
{
	public class NGPermissionCallbackiOS : MonoBehaviour
	{
		private static NGPermissionCallbackiOS instance;
		private NativeGallery.PermissionCallback callback;

		public static void Initialize( NativeGallery.PermissionCallback callback )
		{
			if( instance == null )
			{
				instance = new GameObject( "NGPermissionCallbackiOS" ).AddComponent<NGPermissionCallbackiOS>();
				DontDestroyOnLoad( instance.gameObject );
			}
			else if( instance.callback != null )
				instance.callback( NativeGallery.Permission.ShouldAsk );

			instance.callback = callback;
		}

		[UnityEngine.Scripting.Preserve]
		public void OnPermissionRequested( string message )
		{
			NativeGallery.PermissionCallback _callback = callback;
			callback = null;

			if( _callback != null )
				_callback( (NativeGallery.Permission) int.Parse( message ) );
		}
	}
}
#endif