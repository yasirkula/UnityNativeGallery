#if !UNITY_EDITOR && UNITY_ANDROID
using System.Threading;
using UnityEngine;

namespace NativeGalleryNamespace
{
	public class NGMediaReceiveCallbackAndroid : AndroidJavaProxy
	{
		private object threadLock;

		public string Path { get; private set; }
		public string[] Paths { get; private set; }

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

		public void OnMultipleMediaReceived( string paths )
		{
			if( string.IsNullOrEmpty( paths ) )
				Paths = new string[0];
			else
			{
				string[] pathsSplit = paths.Split( '>' );

				int validPathCount = 0;
				for( int i = 0; i < pathsSplit.Length; i++ )
				{
					if( !string.IsNullOrEmpty( pathsSplit[i] ) )
						validPathCount++;
				}

				if( validPathCount == 0 )
					pathsSplit = new string[0];
				else if( validPathCount != pathsSplit.Length )
				{
					string[] validPaths = new string[validPathCount];
					for( int i = 0, j = 0; i < pathsSplit.Length; i++ )
					{
						if( !string.IsNullOrEmpty( pathsSplit[i] ) )
							validPaths[j++] = pathsSplit[i];
					}

					pathsSplit = validPaths;
				}

				Paths = pathsSplit;
			}
			
			lock( threadLock )
			{
				Monitor.Pulse( threadLock );
			}
		}
	}
}
#endif