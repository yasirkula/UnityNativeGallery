#if UNITY_EDITOR || UNITY_ANDROID
using UnityEngine;

namespace NativeGalleryNamespace
{
	public class NGMediaReceiveCallbackAndroid : AndroidJavaProxy
	{
		private readonly NativeGallery.MediaPickCallback callback;
		private readonly NativeGallery.MediaPickMultipleCallback callbackMultiple;

		private readonly NGCallbackHelper callbackHelper;

		public NGMediaReceiveCallbackAndroid( NativeGallery.MediaPickCallback callback, NativeGallery.MediaPickMultipleCallback callbackMultiple ) : base( "com.yasirkula.unity.NativeGalleryMediaReceiver" )
		{
			this.callback = callback;
			this.callbackMultiple = callbackMultiple;
			callbackHelper = new GameObject( "NGCallbackHelper" ).AddComponent<NGCallbackHelper>();
		}

		[UnityEngine.Scripting.Preserve]
		public void OnMediaReceived( string path )
		{
			callbackHelper.CallOnMainThread( () => callback( !string.IsNullOrEmpty( path ) ? path : null ) );
		}

		[UnityEngine.Scripting.Preserve]
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

			callbackHelper.CallOnMainThread( () => callbackMultiple( ( result != null && result.Length > 0 ) ? result : null ) );
		}
	}
}
#endif