using System.Collections;
using UnityEngine;

public class NativeGalleryTest : MonoBehaviour
{
	void Update()
	{
		transform.Rotate( 0, 90 * Time.deltaTime, 0 );
		if( Input.GetMouseButtonDown( 0 ) )
			StartCoroutine( TakeSS() );
	}

	bool b = false;
	private IEnumerator TakeSS()
	{
		yield return new WaitForEndOfFrame();

		Texture2D ss = new Texture2D( Screen.width, Screen.height, TextureFormat.RGB24, false );
		ss.ReadPixels( new Rect( 0, 0, Screen.width, Screen.height ), 0, 0 );
		ss.Apply();

		// not overwritten on ios
		if( !b )
			NativeGallery.SaveToGallery( ss, "GalleryTest", "overwrite" );
		else
			NativeGallery.SaveToGallery( ss, "GalleryTest", "my img {0}.jpeg" );

		b = !b;
	}
}
