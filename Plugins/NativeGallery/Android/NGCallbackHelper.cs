#if UNITY_EDITOR || UNITY_ANDROID
using UnityEngine;

namespace NativeGalleryNamespace
{
	public class NGCallbackHelper : MonoBehaviour
	{
		private System.Action mainThreadAction = null;

		private void Awake()
		{
			DontDestroyOnLoad( gameObject );
		}

		private void Update()
		{
			if( mainThreadAction != null )
			{
				try
				{
					System.Action temp = mainThreadAction;
					mainThreadAction = null;
					temp();
				}
				finally
				{
					Destroy( gameObject );
				}
			}
		}

		public void CallOnMainThread( System.Action function )
		{
			mainThreadAction = function;
		}
	}
}
#endif