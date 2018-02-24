using System.Threading;
using UnityEngine;

namespace NativeGalleryNamespace
{
	public class NGPermissionCallbackAndroid
#if UNITY_ANDROID
	: AndroidJavaProxy
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
#else
	{ }
#endif
}