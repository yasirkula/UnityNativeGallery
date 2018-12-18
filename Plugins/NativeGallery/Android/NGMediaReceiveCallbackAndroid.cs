#if !UNITY_EDITOR && UNITY_ANDROID
using System.Collections;
using UnityEngine;

namespace NativeGalleryNamespace
{
	public class NGMediaReceiveCallbackAndroid : AndroidJavaProxy
	{
		private NativeGallery.MediaPickCallback callback;
		private NativeGallery.MediaPickMultipleCallback callbackMultiple;

		public NGMediaReceiveCallbackAndroid( NativeGallery.MediaPickCallback callback, NativeGallery.MediaPickMultipleCallback callbackMultiple ) : base( "com.yasirkula.unity.NativeGalleryMediaReceiver" )
		{
			this.callback = callback;
			this.callbackMultiple = callbackMultiple;
		}

		public void OnMediaReceived( string path )
		{
			NGCallbackHelper coroutineHolder = new GameObject( "NGCallbackHelper" ).AddComponent<NGCallbackHelper>();
			coroutineHolder.StartCoroutine( MediaReceiveCoroutine( coroutineHolder.gameObject, path ) );
		}

		public void OnMultipleMediaReceived( string paths )
		{
			string[] result = null;
			if( !string.IsNullOrEmpty( paths ) )
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

				result = pathsSplit;
			}

			NGCallbackHelper coroutineHolder = new GameObject( "NGCallbackHelper" ).AddComponent<NGCallbackHelper>();
			coroutineHolder.StartCoroutine( MediaReceiveMultipleCoroutine( coroutineHolder.gameObject, result ) );
		}

		private IEnumerator MediaReceiveCoroutine( GameObject obj, string path )
		{
			yield return null;

			if( string.IsNullOrEmpty( path ) )
				path = null;

			try
			{
				if( callback != null )
					callback( path );
			}
			finally
			{
				Object.Destroy( obj );
			}
		}

		private IEnumerator MediaReceiveMultipleCoroutine( GameObject obj, string[] paths )
		{
			yield return null;

			if( paths != null && paths.Length == 0 )
				paths = null;

			try
			{
				if( callbackMultiple != null )
					callbackMultiple( paths );
			}
			finally
			{
				Object.Destroy( obj );
			}
		}
	}
}
#endif