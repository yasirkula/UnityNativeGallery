using UnityEngine;

namespace NativeGalleryNamespace
{
	public class NGCallbackHelper : MonoBehaviour
	{
		private void Awake()
		{
			DontDestroyOnLoad( gameObject );
		}
	}
}