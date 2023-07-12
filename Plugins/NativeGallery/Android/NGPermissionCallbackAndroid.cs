#if UNITY_EDITOR || UNITY_ANDROID
using System.Threading;
using UnityEngine;

namespace NativeGalleryNamespace
{
	public class NGPermissionCallbackAndroid : AndroidJavaProxy
	{
		private object threadLock;
		public int Result { get; private set; }

		public NGPermissionCallbackAndroid( object threadLock ) : base( "com.yasirkula.unity.NativeGalleryPermissionReceiver" )
		{
			Result = -1;
			this.threadLock = threadLock;
		}

		public void OnPermissionResult( int result )
		{
			Result = result;

			lock( threadLock )
			{
				Monitor.Pulse( threadLock );
			}
		}
	}

	public class NGPermissionCallbackAsyncAndroid : AndroidJavaProxy
	{
		private readonly NativeGallery.PermissionCallback callback;
		private readonly NGCallbackHelper callbackHelper;

		public NGPermissionCallbackAsyncAndroid( NativeGallery.PermissionCallback callback ) : base( "com.yasirkula.unity.NativeGalleryPermissionReceiver" )
		{
			this.callback = callback;
			callbackHelper = new GameObject( "NGCallbackHelper" ).AddComponent<NGCallbackHelper>();
		}

		public void OnPermissionResult( int result )
		{
			callbackHelper.CallOnMainThread( () => callback( (NativeGallery.Permission) result ) );
		}
	}
}
#endif