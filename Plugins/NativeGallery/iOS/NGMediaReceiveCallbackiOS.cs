#if !UNITY_EDITOR && UNITY_IOS
using UnityEngine;

namespace NativeGalleryNamespace
{
	public class NGMediaReceiveCallbackiOS : MonoBehaviour
	{
		private static NGMediaReceiveCallbackiOS instance;
		private NativeGallery.MediaPickCallback callback;

		private float nextBusyCheckTime;

		public static bool IsBusy { get; private set; }
		
		[System.Runtime.InteropServices.DllImport( "__Internal" )]
		private static extern int _NativeGallery_IsMediaPickerBusy();

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

			instance.nextBusyCheckTime = Time.realtimeSinceStartup + 1f;
			IsBusy = true;
		}
		
		private void Update()
		{
			if( IsBusy )
			{
				if( Time.realtimeSinceStartup >= nextBusyCheckTime )
				{
					nextBusyCheckTime = Time.realtimeSinceStartup + 1f;

					if( _NativeGallery_IsMediaPickerBusy() == 0 )
					{
						if( callback != null )
						{
							callback( null );
							callback = null;
						}

						IsBusy = false;
					}
				}
			}
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
#endif