# Unity Native Gallery Plugin

**Available on Asset Store:** https://assetstore.unity.com/packages/tools/integration/native-gallery-for-android-ios-112630

**Forum Thread:** https://forum.unity.com/threads/native-gallery-for-android-ios-open-source.519619/

**[Support the Developer ☕](https://yasirkula.itch.io/unity3d)**

This plugin helps you save your images and/or videos to device **Gallery** on Android and **Photos** on iOS. It is also possible to pick an image or video from Gallery/Photos.

## INSTALLATION

There are 5 ways to install this plugin:

- import [NativeGallery.unitypackage](https://github.com/yasirkula/UnityNativeGallery/releases) via *Assets-Import Package*
- clone/[download](https://github.com/yasirkula/UnityNativeGallery/archive/master.zip) this repository and move the *Plugins* folder to your Unity project's *Assets* folder
- import it from [Asset Store](https://assetstore.unity.com/packages/tools/integration/native-gallery-for-android-ios-112630)
- *(via Package Manager)* add the following line to *Packages/manifest.json*:
  - `"com.yasirkula.nativegallery": "https://github.com/yasirkula/UnityNativeGallery.git",`
- *(via [OpenUPM](https://openupm.com))* after installing [openupm-cli](https://github.com/openupm/openupm-cli), run the following command:
  - `openupm add com.yasirkula.nativegallery`

### Android Setup

Set **Write Permission** to **External (SDCard)** in **Player Settings**. Alternatively, if your app won't be saving media to the Gallery but instead just reading media from it, you can add `READ_EXTERNAL_STORAGE` permission to your AndroidManifest.

### iOS Setup

There are two ways to set up the plugin on iOS:

**a. Automated Setup for iOS**

- *(optional)* change the value of **PHOTO_LIBRARY_USAGE_DESCRIPTION** in *Plugins/NativeGallery/Editor/NGPostProcessBuild.cs*
- *(Unity 2017.4 or earlier)* if your minimum *Deployment Target* (iOS Version) is at least 8.0, set the value of **MINIMUM_TARGET_8_OR_ABOVE** to *true* in *NGPostProcessBuild.cs*

**b. Manual Setup for iOS**

- see: https://github.com/yasirkula/UnityNativeGallery/wiki/Manual-Setup-for-iOS

## FAQ

- **How can I fetch the path of the saved image or the original path of the picked image?**

You can't. On iOS, these files are stored in an internal directory that we have no access to (I don't think there is even a way to fetch that internal path). On Android, with Storage Access Framework, the absolute path is hidden behind the SAF-layer. There are some tricks here and there to convert SAF-path to absolute path but they don't work in all cases (most of the snippets that can be found on Stackoverflow can't return the absolute path if the file is stored on external SD card).

- **Can't access the Gallery, it says "java.lang.ClassNotFoundException: com.yasirkula.unity.NativeGallery" in Logcat**

If your project uses ProGuard, try adding the following line to ProGuard filters: `-keep class com.yasirkula.unity.* { *; }`

- **Nothing happens when I try to access the Gallery on Android**

Make sure that you've set the **Write Permission** to **External (SDCard)** in *Player Settings*.

- **Saving image/video doesn't work properly**

Make sure that the *filename* parameter of the Save function includes the file's extension, as well

## HOW TO

### A. Saving Media To Gallery/Photos

`NativeGallery.SaveImageToGallery( byte[] mediaBytes, string album, string filename, MediaSaveCallback callback = null )`: use this function if you have the raw bytes of the image. 
- On Android, your images are saved at **DCIM/album/filename**. On iOS, the image will be saved in the corresponding album. Make sure that the *filename* parameter includes the file's extension, as well
- **MediaSaveCallback** takes a string parameter which stores an error string if something goes wrong while saving the image/video, or *null* if it is saved successfully. This parameter is optional

**IMPORTANT:** NativeGallery will never overwrite existing media on the Gallery. If there is a name conflict, NativeGallery will ensure a unique filename. So don't put `{0}` in *filename* anymore (for new users, putting {0} in filename was recommended in order to ensure unique filenames in earlier versions, this is no longer necessary).

`NativeGallery.SaveImageToGallery( string existingMediaPath, string album, string filename, MediaSaveCallback callback = null )`: use this function if the image is already saved on disk. Enter the file's path to **existingMediaPath**.

`NativeGallery.SaveImageToGallery( Texture2D image, string album, string filename, MediaSaveCallback callback = null )`: use this function to easily save a **Texture2D** to Gallery/Photos. If filename ends with "*.jpeg*" or "*.jpg*", texture will be saved as JPEG; otherwise, it will be saved as PNG.

`NativeGallery.SaveVideoToGallery( byte[] mediaBytes, string album, string filename, MediaSaveCallback callback = null )`: use this function if you have the raw bytes of the video. This function works similar to its *SaveImageToGallery* equivalent.

`NativeGallery.SaveVideoToGallery( string existingMediaPath, string album, string filename, MediaSaveCallback callback = null )`: use this function if the video is already saved on disk. This function works similar to its *SaveImageToGallery* equivalent.

### B. Retrieving Media From Gallery/Photos

`NativeGallery.GetImageFromGallery( MediaPickCallback callback, string title = "", string mime = "image/*" )`: prompts the user to select an image from Gallery/Photos.
- This operation is **asynchronous**! After user selects an image or cancels the operation, the **callback** is called (on main thread). **MediaPickCallback** takes a *string* parameter which stores the path of the selected image, or *null* if nothing is selected
- **title** determines the title of the image picker dialog on Android. Has no effect on iOS
- **mime** filters the available images on Android. For example, to request a *JPEG* image from the user, mime can be set as "image/jpeg". Setting multiple mime types is not possible (in that case, you should leave mime as "image/\*"). Has no effect on iOS

`NativeGallery.GetVideoFromGallery( MediaPickCallback callback, string title = "", string mime = "video/*" )`: prompts the user to select a video from Gallery/Photos. This function works similar to its *GetImageFromGallery* equivalent.

`NativeGallery.GetImagesFromGallery( MediaPickMultipleCallback callback, string title = "", string mime = "image/*" )`: prompts the user to select one or more images from Gallery/Photos. **MediaPickMultipleCallback** takes a *string[]* parameter which stores the path(s) of the selected image(s)/video(s), or *null* if nothing is selected. Selecting multiple files from gallery is only available on *Android 18* and later (iOS not supported). Call *CanSelectMultipleFilesFromGallery()* to see if this feature is available.

`NativeGallery.GetVideosFromGallery( MediaPickMultipleCallback callback, string title = "", string mime = "video/*" )`: prompts the user to select one or more videos from Gallery/Photos. This function works similar to its *GetImagesFromGallery* equivalent.

`NativeGallery.CanSelectMultipleFilesFromGallery()`: returns true if selecting multiple images/videos from Gallery/Photos is possible on this device.

`NativeGallery.IsMediaPickerBusy()`: returns true if the user is currently picking media from Gallery/Photos. In that case, another GetImageFromGallery or GetVideoFromGallery request will simply be ignored.

Almost all of these functions return a *NativeGallery.Permission* value. More details about it is available below.

### C. Runtime Permissions

Beginning with *6.0 Marshmallow*, Android apps must request runtime permissions before accessing certain services, similar to iOS. There are two functions to handle permissions with this plugin:

`NativeGallery.Permission NativeGallery.CheckPermission()`: checks whether the app has access to Gallery/Photos or not.

**NativeGallery.Permission** is an enum that can take 3 values: 
- **Granted**: we have the permission to access Gallery/Photos
- **ShouldAsk**: we don't have permission yet, but we can ask the user for permission via *RequestPermission* function (see below). On Android, as long as the user doesn't select "Don't ask again" while denying the permission, ShouldAsk is returned
- **Denied**: we don't have permission and we can't ask the user for permission. In this case, user has to give the permission from Settings. This happens when user denies the permission on iOS (can't request permission again on iOS), when user selects "Don't ask again" while denying the permission on Android or when user is not allowed to give that permission (parental controls etc.)

`NativeGallery.Permission NativeGallery.RequestPermission()`: requests permission to access Gallery/Photos from the user and returns the result. It is recommended to show a brief explanation before asking the permission so that user understands why the permission is needed and doesn't click Deny or worse, "Don't ask again". Note that the SaveImageToGallery/SaveVideoToGallery and GetImageFromGallery/GetVideoFromGallery functions call RequestPermission internally and execute only if the permission is granted (the result of RequestPermission is also returned).

`NativeGallery.OpenSettings()`: opens the settings for this app, from where the user can manually grant permission in case current permission state is *Permission.Denied* (on Android, the necessary permission is named *Storage* and on iOS, the necessary permission is named *Photos*).

`bool NativeGallery.CanOpenSettings()`: on iOS versions prior to 8.0, opening settings from within app is not possible and in this case, this function returns *false*. Otherwise, it returns *true*.

### D. Utility Functions

`NativeGallery.ImageProperties NativeGallery.GetImageProperties( string imagePath )`: returns an *ImageProperties* instance that holds the width, height, mime type and EXIF orientation information of an image file without creating a *Texture2D* object. Mime type will be *null*, if it can't be determined

`NativeGallery.VideoProperties NativeGallery.GetVideoProperties( string videoPath )`: returns a *VideoProperties* instance that holds the width, height, duration (in milliseconds) and rotation information of a video file. To play a video in correct orientation, you should rotate it by *rotation* degrees clockwise. For a 90-degree or 270-degree rotated video, values of *width* and *height* should be swapped to get the display size of the video.

`Texture2D NativeGallery.LoadImageAtPath( string imagePath, int maxSize = -1, bool markTextureNonReadable = true, bool generateMipmaps = true, bool linearColorSpace = false )`: creates a Texture2D from the specified image file in correct orientation and returns it. Returns *null*, if something goes wrong.
- **maxSize** determines the maximum size of the returned Texture2D in pixels. Larger textures will be down-scaled. If untouched, its value will be set to *SystemInfo.maxTextureSize*. It is recommended to set a proper maxSize for better performance
- **markTextureNonReadable** marks the generated texture as non-readable for better memory usage. If you plan to modify the texture later (e.g. *GetPixels*/*SetPixels*), set its value to *false*
- **generateMipmaps** determines whether texture should have mipmaps or not
- **linearColorSpace** determines whether texture should be in linear color space or sRGB color space

## EXAMPLE CODE

The following code has three functions:
- if you click the left one-third of the screen, it captures the screenshot of the game and saves it to Gallery/Photos
- if you click the middle one-third of the screen, it picks an image from Gallery/Photos and puts it on a temporary quad that is placed in front of the camera
- if you click the right one-third of the screen, it picks a video from Gallery/Photos and plays it

```csharp
void Update()
{
	if( Input.GetMouseButtonDown( 0 ) )
	{
		if( Input.mousePosition.x < Screen.width / 3 )
		{
			// Take a screenshot and save it to Gallery/Photos
			StartCoroutine( TakeScreenshotAndSave() );
		}
		else
		{
			// Don't attempt to pick media from Gallery/Photos if
			// another media pick operation is already in progress
			if( NativeGallery.IsMediaPickerBusy() )
				return;
				
			if( Input.mousePosition.x < Screen.width * 2 / 3 )
			{
				// Pick a PNG image from Gallery/Photos
				// If the selected image's width and/or height is greater than 512px, down-scale the image
				PickImage( 512 );
			}
			else
			{
				// Pick a video from Gallery/Photos
				PickVideo();
			}
		}
	}
}

private IEnumerator TakeScreenshotAndSave()
{
	yield return new WaitForEndOfFrame();

	Texture2D ss = new Texture2D( Screen.width, Screen.height, TextureFormat.RGB24, false );
	ss.ReadPixels( new Rect( 0, 0, Screen.width, Screen.height ), 0, 0 );
	ss.Apply();

	// Save the screenshot to Gallery/Photos
	Debug.Log( "Permission result: " + NativeGallery.SaveImageToGallery( ss, "GalleryTest", "Image.png" ) );
	
	// To avoid memory leaks
	Destroy( ss );
}

private void PickImage( int maxSize )
{
	NativeGallery.Permission permission = NativeGallery.GetImageFromGallery( ( path ) =>
	{
		Debug.Log( "Image path: " + path );
		if( path != null )
		{
			// Create Texture from selected image
			Texture2D texture = NativeGallery.LoadImageAtPath( path, maxSize );
			if( texture == null )
			{
				Debug.Log( "Couldn't load texture from " + path );
				return;
			}

			// Assign texture to a temporary quad and destroy it after 5 seconds
			GameObject quad = GameObject.CreatePrimitive( PrimitiveType.Quad );
			quad.transform.position = Camera.main.transform.position + Camera.main.transform.forward * 2.5f;
			quad.transform.forward = Camera.main.transform.forward;
			quad.transform.localScale = new Vector3( 1f, texture.height / (float) texture.width, 1f );
			
			Material material = quad.GetComponent<Renderer>().material;
			if( !material.shader.isSupported ) // happens when Standard shader is not included in the build
				material.shader = Shader.Find( "Legacy Shaders/Diffuse" );

			material.mainTexture = texture;
				
			Destroy( quad, 5f );

			// If a procedural texture is not destroyed manually, 
			// it will only be freed after a scene change
			Destroy( texture, 5f );
		}
	}, "Select a PNG image", "image/png" );

	Debug.Log( "Permission result: " + permission );
}

private void PickVideo()
{
	NativeGallery.Permission permission = NativeGallery.GetVideoFromGallery( ( path ) =>
	{
		Debug.Log( "Video path: " + path );
		if( path != null )
		{
			// Play the selected video
			Handheld.PlayFullScreenMovie( "file://" + path );
		}
	}, "Select a video" );

	Debug.Log( "Permission result: " + permission );
}
```
