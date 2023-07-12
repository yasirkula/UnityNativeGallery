# Unity Native Gallery Plugin

**Available on Asset Store:** https://assetstore.unity.com/packages/tools/integration/native-gallery-for-android-ios-112630

**Forum Thread:** https://forum.unity.com/threads/native-gallery-for-android-ios-open-source.519619/

**Discord:** https://discord.gg/UJJt549AaV

**[GitHub Sponsors ☕](https://github.com/sponsors/yasirkula)**

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

NativeGallery no longer requires any manual setup on Android.

### iOS Setup

**IMPORTANT:** If you are targeting iOS 14 or later, you need to build your app with Xcode 12 or later to avoid any permission issues.

There are two ways to set up the plugin on iOS:

**a. Automated Setup for iOS**

- *(optional)* change the values of **Photo Library Usage Description** and **Photo Library Additions Usage Description** at *Project Settings/yasirkula/Native Gallery*
- *(Unity 2017.4 or earlier)* if your minimum *Deployment Target* (iOS Version) is at least 8.0, set the value of **Deployment Target Is 8.0 Or Above** to *true* at *Project Settings/yasirkula/Native Gallery*

**b. Manual Setup for iOS**

- see: https://github.com/yasirkula/UnityNativeGallery/wiki/Manual-Setup-for-iOS

## FAQ

- **How can I fetch the path of the saved image or the original path of the picked image on iOS?**

You can't. On iOS, these files are stored in an internal directory that we have no access to (I don't think there is even a way to fetch that internal path).

- **Can't access the Gallery, it says "java.lang.ClassNotFoundException: com.yasirkula.unity.NativeGallery" in Logcat**

If you are sure that your plugin is up-to-date, then enable **Custom Proguard File** option from *Player Settings* and add the following line to that file: `-keep class com.yasirkula.unity.* { *; }`

- **Android build fails, it says "error: attribute android:requestLegacyExternalStorage not found" in Console**

`android:requestLegacyExternalStorage` attribute in _AndroidManifest.xml_ fixes a rare UnauthorizedAccessException on Android 10 but requires you to update your Android SDK to at least **SDK 29**. If this isn't possible for you, you should open *NativeGallery.aar* with WinRAR or 7-Zip and then remove the `<application ... />` tag from _AndroidManifest.xml_.

- **Nothing happens when I try to access the Gallery on Android**

Make sure that you've set the **Write Permission** to **External (SDCard)** in *Player Settings*.

- **NativeGallery functions return Permission.Denied even though I've set "Write Permission" to "External (SDCard)"**

Declare the `WRITE_EXTERNAL_STORAGE` permission manually in your [**Plugins/Android/AndroidManifest.xml** file](https://answers.unity.com/questions/982710/where-is-the-manifest-file-in-unity.html) with the `tools:node="replace"` attribute as follows: `<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" tools:node="replace"/>` (you'll need to add the `xmlns:tools="http://schemas.android.com/tools"` attribute to the `<manifest ...>` element).

- **Saving image/video doesn't work properly**

Make sure that the *filename* parameter of the Save function includes the file's extension, as well

## HOW TO

### A. Saving Media To Gallery/Photos

`NativeGallery.SaveImageToGallery( byte[] mediaBytes, string album, string filename, MediaSaveCallback callback = null )`: use this function if you have the raw bytes of the image. 
- On Android, your images/videos are saved at **DCIM/album/filename**. On iOS 14+, the image/video will be saved to the default Photos album (i.e. *album* parameter will be ignored). On earlier iOS versions, the image/video will be saved to the target album. Make sure that the *filename* parameter includes the file's extension, as well
- **MediaSaveCallback** takes `bool success` and `string path` parameters. If the image/video is saved successfully, *success* becomes *true*. On Android, *path* stores where the image/video was saved to (is *null* on iOS). If the raw filepath can't be determined, an abstract Storage Access Framework path will be returned (*File.Exists* returns *false* for that path)

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

`NativeGallery.GetAudioFromGallery( MediaPickCallback callback, string title = "", string mime = "audio/*" )`: prompts the user to select an audio file. This function works similar to its *GetImageFromGallery* equivalent. Works on Android only.

`NativeGallery.GetMixedMediaFromGallery( MediaPickCallback callback, MediaType mediaTypes, string title = "" )`: prompts the user to select an image/video/audio file. This function is available on Android 19 and later and all iOS versions. Selecting audio files is not supported on iOS. 
- **mediaTypes** is the bitwise OR'ed media types that will be displayed in the file picker dialog (e.g. to pick an image or video, use `MediaType.Image | MediaType.Video`)

`NativeGallery.GetImagesFromGallery( MediaPickMultipleCallback callback, string title = "", string mime = "image/*" )`: prompts the user to select one or more images from Gallery/Photos. **MediaPickMultipleCallback** takes a *string[]* parameter which stores the path(s) of the selected image(s)/video(s), or *null* if nothing is selected. Selecting multiple files from gallery is only available on *Android 18* and later and *iOS 14* and later. Call *CanSelectMultipleFilesFromGallery()* to see if this feature is available.

`NativeGallery.GetVideosFromGallery( MediaPickMultipleCallback callback, string title = "", string mime = "video/*" )`: prompts the user to select one or more videos from Gallery/Photos. This function works similar to its *GetImagesFromGallery* equivalent.

`NativeGallery.GetAudiosFromGallery( MediaPickMultipleCallback callback, string title = "", string mime = "audio/*" )`: prompts the user to select one or more audio files. This function works similar to its *GetImagesFromGallery* equivalent. Works on Android only.

`NativeGallery.GetMixedMediasFromGallery( MediaPickMultipleCallback callback, MediaType mediaTypes, string title = "" )`: prompts the user to select one or more image/video/audio files. Selecting audio files is not supported on iOS.

`NativeGallery.CanSelectMultipleFilesFromGallery()`: returns *true* if selecting multiple images/videos from Gallery/Photos is possible on this device.

`NativeGallery.CanSelectMultipleMediaTypesFromGallery()`: returns *true* if *GetMixedMediaFromGallery*/*GetMixedMediasFromGallery* functions are supported on this device.

`NativeGallery.IsMediaPickerBusy()`: returns *true* if the user is currently picking media from Gallery/Photos. In that case, another GetImageFromGallery, GetVideoFromGallery or GetAudioFromGallery request will simply be ignored.

Almost all of these functions return a *NativeGallery.Permission* value. More details about it is available below.

### C. Runtime Permissions

Beginning with *6.0 Marshmallow*, Android apps must request runtime permissions before accessing certain services, similar to iOS. Note that NativeGallery doesn't require any permissions for picking images/videos from Photos on iOS 11+, picking images/videos from Gallery on Android 34+ and saving images/videos to Gallery on Android 29+, so no permission dialog will be shown in these cases and the permission functions will return *Permission.Granted*.

There are two functions to handle permissions with this plugin:

`NativeGallery.Permission NativeGallery.CheckPermission( PermissionType permissionType, MediaType mediaTypes )`: checks whether the app has access to Gallery/Photos or not. **PermissionType** can be either **Read** (for *GetImageFromGallery/GetVideoFromGallery* functions) or **Write** (for *SaveImageToGallery/SaveVideoToGallery* functions).
- **mediaTypes** determines for which media type(s) we're checking the permission for. Has no effect on iOS

**NativeGallery.Permission** is an enum that can take 3 values: 
- **Granted**: we have the permission to access Gallery/Photos
- **ShouldAsk**: we don't have permission yet, but we can ask the user for permission via *RequestPermission* function (see below). On Android, as long as the user doesn't select "Don't ask again" while denying the permission, ShouldAsk is returned
- **Denied**: we don't have permission and we can't ask the user for permission. In this case, user has to give the permission from Settings. This happens when user denies the permission on iOS (can't request permission again on iOS), when user selects "Don't ask again" while denying the permission on Android or when user is not allowed to give that permission (parental controls etc.)

`NativeGallery.Permission NativeGallery.RequestPermission( PermissionType permissionType, MediaType mediaTypes )`: requests permission to access Gallery/Photos from the user and returns the result. It is recommended to show a brief explanation before asking the permission so that user understands why the permission is needed and doesn't click Deny or worse, "Don't ask again". Note that the SaveImageToGallery/SaveVideoToGallery and GetImageFromGallery/GetVideoFromGallery functions call RequestPermission internally and execute only if the permission is granted (the result of RequestPermission is also returned).

`void NativeGallery.RequestPermissionAsync( PermissionCallback callback, PermissionType permissionType, MediaType mediaTypes )`: Asynchronous variant of *RequestPermission*. Unlike RequestPermission, this function doesn't freeze the app unnecessarily before the permission dialog is displayed. So it's recommended to call this function instead.
- **PermissionCallback** takes `NativeGallery.Permission permission` parameter

`Task<NativeGallery.Permission> NativeGallery.RequestPermissionAsync( PermissionType permissionType, MediaType mediaTypes )`: Another asynchronous variant of *RequestPermission* (requires Unity 2018.4 or later).

`NativeGallery.OpenSettings()`: opens the settings for this app, from where the user can manually grant permission in case current permission state is *Permission.Denied* (on Android, the necessary permission is named *Storage* and on iOS, the necessary permission is named *Photos*).

`bool NativeGallery.CanOpenSettings()`: on iOS versions prior to 8.0, opening settings from within app is not possible and in this case, this function returns *false*. Otherwise, it returns *true*.

### D. Utility Functions

`NativeGallery.ImageProperties NativeGallery.GetImageProperties( string imagePath )`: returns an *ImageProperties* instance that holds the width, height, mime type and EXIF orientation information of an image file without creating a *Texture2D* object. Mime type will be *null*, if it can't be determined

`NativeGallery.VideoProperties NativeGallery.GetVideoProperties( string videoPath )`: returns a *VideoProperties* instance that holds the width, height, duration (in milliseconds) and rotation information of a video file. To play a video in correct orientation, you should rotate it by *rotation* degrees clockwise. For a 90-degree or 270-degree rotated video, values of *width* and *height* should be swapped to get the display size of the video.

`NativeGallery.MediaType NativeGallery.GetMediaTypeOfFile( string path )`: returns the media type of the file at the specified path: *Image*, *Video*, *Audio* or neither of these (if media type can't be determined)

`Texture2D NativeGallery.LoadImageAtPath( string imagePath, int maxSize = -1, bool markTextureNonReadable = true, bool generateMipmaps = true, bool linearColorSpace = false )`: creates a Texture2D from the specified image file in correct orientation and returns it. Returns *null*, if something goes wrong.
- **maxSize** determines the maximum size of the returned Texture2D in pixels. Larger textures will be down-scaled. If untouched, its value will be set to *SystemInfo.maxTextureSize*. It is recommended to set a proper maxSize for better performance
- **markTextureNonReadable** marks the generated texture as non-readable for better memory usage. If you plan to modify the texture later (e.g. *GetPixels*/*SetPixels*), set its value to *false*
- **generateMipmaps** determines whether texture should have mipmaps or not
- **linearColorSpace** determines whether texture should be in linear color space or sRGB color space

`async Task<Texture2D> NativeGallery.LoadImageAtPathAsync( string imagePath, int maxSize = -1, bool markTextureNonReadable = true, bool generateMipmaps = true, bool linearColorSpace = false )`: asynchronous variant of *LoadImageAtPath* (requires Unity 2018.4 or later). Works best when *linearColorSpace* is *false*. It's also slightly faster when *generateMipmaps* is *false*. Note that it isn't possible to load multiple images simultaneously using this function.

`Texture2D NativeGallery.GetVideoThumbnail( string videoPath, int maxSize = -1, double captureTimeInSeconds = -1.0, bool markTextureNonReadable = true, bool generateMipmaps = true, bool linearColorSpace = false )`: creates a Texture2D thumbnail from a video file and returns it. Returns *null*, if something goes wrong.
- **maxSize** determines the maximum size of the returned Texture2D in pixels. Larger thumbnails will be down-scaled. If untouched, its value will be set to *SystemInfo.maxTextureSize*. It is recommended to set a proper maxSize for better performance
- **captureTimeInSeconds** determines the frame of the video that the thumbnail is captured from. If untouched, OS will decide this value
- **markTextureNonReadable** (see *LoadImageAtPath*)

`async Task<Texture2D> NativeGallery.GetVideoThumbnailAsync( string videoPath, int maxSize = -1, double captureTimeInSeconds = -1.0, bool markTextureNonReadable = true, bool generateMipmaps = true, bool linearColorSpace = false )`: asynchronous variant of *GetVideoThumbnail* (requires Unity 2018.4 or later). Works best when *linearColorSpace* is *false*. It's also slightly faster when *generateMipmaps* is *false*. Note that it isn't possible to generate multiple video thumbnails simultaneously using this function.

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

// Example code doesn't use this function but it is here for reference. It's recommended to ask for permissions manually using the
// RequestPermissionAsync methods prior to calling NativeGallery functions
private async void RequestPermissionAsynchronously( NativeGallery.PermissionType permissionType, NativeGallery.MediaType mediaTypes )
{
	NativeGallery.Permission permission = await NativeGallery.RequestPermissionAsync( permissionType, mediaTypes );
	Debug.Log( "Permission result: " + permission );
}

private IEnumerator TakeScreenshotAndSave()
{
	yield return new WaitForEndOfFrame();

	Texture2D ss = new Texture2D( Screen.width, Screen.height, TextureFormat.RGB24, false );
	ss.ReadPixels( new Rect( 0, 0, Screen.width, Screen.height ), 0, 0 );
	ss.Apply();

	// Save the screenshot to Gallery/Photos
	NativeGallery.Permission permission = NativeGallery.SaveImageToGallery( ss, "GalleryTest", "Image.png", ( success, path ) => Debug.Log( "Media save result: " + success + " " + path ) );

	Debug.Log( "Permission result: " + permission );

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
	} );

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

// Example code doesn't use this function but it is here for reference
private void PickImageOrVideo()
{
	if( NativeGallery.CanSelectMultipleMediaTypesFromGallery() )
	{
		NativeGallery.Permission permission = NativeGallery.GetMixedMediaFromGallery( ( path ) =>
		{
			Debug.Log( "Media path: " + path );
			if( path != null )
			{
				// Determine if user has picked an image, video or neither of these
				switch( NativeGallery.GetMediaTypeOfFile( path ) )
				{
					case NativeGallery.MediaType.Image: Debug.Log( "Picked image" ); break;
					case NativeGallery.MediaType.Video: Debug.Log( "Picked video" ); break;
					default: Debug.Log( "Probably picked something else" ); break;
				}
			}
		}, NativeGallery.MediaType.Image | NativeGallery.MediaType.Video, "Select an image or video" );

		Debug.Log( "Permission result: " + permission );
	}
}
```
