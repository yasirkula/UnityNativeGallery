using System.Threading;
using UnityEngine;

namespace NativeGalleryNamespace
{
	public class NGMediaReceiveCallbackAndroid
#if UNITY_ANDROID
	: AndroidJavaProxy
	{
		private object threadLock;
		public string Path { get; private set; }

		public NGMediaReceiveCallbackAndroid( object threadLock ) : base( "com.yasirkula.unity.NativeGalleryMediaReceiver" )
		{
			Path = string.Empty;
			this.threadLock = threadLock;
		}

		public void OnMediaReceived( string path )
		{
			Path = path;

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