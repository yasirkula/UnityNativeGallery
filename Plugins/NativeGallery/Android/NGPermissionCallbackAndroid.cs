#if UNITY_EDITOR || UNITY_ANDROID
using UnityEngine;

namespace NativeGalleryNamespace
{
	public class NGPermissionCallbackAndroid : AndroidJavaProxy
	{
		private readonly NativeGallery.PermissionCallback callback;
		private readonly NGCallbackHelper callbackHelper;

		public NGPermissionCallbackAndroid( NativeGallery.PermissionCallback callback ) : base( "com.yasirkula.unity.NativeGalleryPermissionReceiver" )
		{
			this.callback = callback;
			callbackHelper = NGCallbackHelper.Create( true );
		}

		[UnityEngine.Scripting.Preserve]
		public void OnPermissionResult( int result )
		{
			callbackHelper.CallOnMainThread( () => callback( (NativeGallery.Permission) result ) );
		}
	}
}
#endif