= Native Gallery for Android & iOS =

Online documentation & example code available at: https://github.com/yasirkula/UnityNativeGallery
E-mail: yasirkula@gmail.com

1. ABOUT
This plugin helps you interact with Gallery/Photos on Android & iOS.

2. HOW TO
for Android: set "Write Permission" to "External (SDCard)" in Player Settings
for iOS: there are two ways to set up the plugin on iOS:

a. Automated Setup for iOS
- change the value of PHOTO_LIBRARY_USAGE_DESCRIPTION in Plugins/NativeGallery/Editor/NGPostProcessBuild.cs (optional)
- if your minimum Deployment Target (iOS Version) is at least 8.0, set the value of MINIMUM_TARGET_8_OR_ABOVE to true in NGPostProcessBuild.cs

b. Manual Setup for iOS
- set the value of ENABLED to false in NGPostProcessBuild.cs
- build your project
- enter a Photo Library Usage Description to Info.plist in Xcode
- also enter a "Photo Library Additions Usage Description" to Info.plist in Xcode, if exists
- insert "-weak_framework Photos -framework AssetsLibrary -framework MobileCoreServices -framework ImageIO" to the "Other Linker Flags" of Unity-iPhone Target (if your Deployment Target is at least 8.0, it is sufficient to insert "-framework Photos -framework MobileCoreServices -framework ImageIO")
- lastly, remove Photos.framework from Link Binary With Libraries of Unity-iPhone Target in Build Phases, if exists

3. FAQ
- Can't access the Gallery, it says "java.lang.ClassNotFoundException: com.yasirkula.unity.NativeGallery" in Logcat
If your project uses ProGuard, try adding the following line to ProGuard filters: -keep class com.yasirkula.unity.* { *; }

- Nothing happens when I try to access the Gallery on Android
Make sure that you've set the "Write Permission" to "External (SDCard)" in Player Settings.

- Saving image/video doesn't work properly
Make sure that the "filename" parameter of the Save function includes the file's extension, as well

4. SCRIPTING API
Please see the online documentation for a more in-depth documentation of the Scripting API: https://github.com/yasirkula/UnityNativeGallery

enum NativeGallery.Permission { Denied = 0, Granted = 1, ShouldAsk = 2 };
enum NativeGallery.ImageOrientation { Unknown = -1, Normal = 0, Rotate90 = 1, Rotate180 = 2, Rotate270 = 3, FlipHorizontal = 4, Transpose = 5, FlipVertical = 6, Transverse = 7 }; // EXIF orientation: http://sylvana.net/jpegcrop/exif_orientation.html (indices are reordered)

delegate void MediaSaveCallback( string error );
delegate void NativeGallery.MediaPickCallback( string path );
delegate void MediaPickMultipleCallback( string[] paths );

//// Saving Media To Gallery/Photos ////

// On Android, your images are saved at DCIM/album/filename. On iOS, the image will be saved in the corresponding album.
// NOTE: Make sure that the filename parameter includes the file's extension, as well
// IMPORTANT: NativeGallery will never overwrite existing media on the Gallery. If there is a name conflict, NativeGallery will ensure a unique filename. So don't put '{0}' in filename anymore (for new users, putting {0} in filename was recommended in order to ensure unique filenames in earlier versions, this is no longer necessary).
// MediaSaveCallback takes a string parameter which stores an error string if something goes wrong while saving the image/video, or null if it is saved successfully
NativeGallery.Permission NativeGallery.SaveImageToGallery( byte[] mediaBytes, string album, string filename, MediaSaveCallback callback = null );
NativeGallery.Permission NativeGallery.SaveImageToGallery( string existingMediaPath, string album, string filename, MediaSaveCallback callback = null );
NativeGallery.Permission NativeGallery.SaveImageToGallery( Texture2D image, string album, string filename, MediaSaveCallback callback = null );
NativeGallery.Permission NativeGallery.SaveVideoToGallery( byte[] mediaBytes, string album, string filename, MediaSaveCallback callback = null );
NativeGallery.Permission NativeGallery.SaveVideoToGallery( string existingMediaPath, string album, string filename, MediaSaveCallback callback = null );


//// Retrieving Media From Gallery/Photos ////

// This operation is asynchronous! After user selects an image/video or cancels the operation, the callback is called (on main thread)
// MediaPickCallback takes a string parameter which stores the path of the selected image/video, or null if nothing is selected
// MediaPickMultipleCallback takes a string[] parameter which stores the path(s) of the selected image(s)/video(s), or null if nothing is selected
// title: determines the title of the image picker dialog on Android. Has no effect on iOS
// mime: filters the available images/videos on Android. For example, to request a JPEG image from the user, mime can be set as "image/jpeg". Setting multiple mime types is not possible (in that case, you should leave mime as is). Has no effect on iOS
NativeGallery.Permission NativeGallery.GetImageFromGallery( MediaPickCallback callback, string title = "", string mime = "image/*" );
NativeGallery.Permission NativeGallery.GetVideoFromGallery( MediaPickCallback callback, string title = "", string mime = "video/*" );
NativeGallery.Permission NativeGallery.GetImagesFromGallery( MediaPickMultipleCallback callback, string title = "", string mime = "image/*" );
NativeGallery.Permission NativeGallery.GetVideosFromGallery( MediaPickMultipleCallback callback, string title = "", string mime = "video/*" );

// Returns true if selecting multiple images/videos from Gallery/Photos is possible on this device (only available on Android 18 and later; iOS not supported)
bool NativeGallery.CanSelectMultipleFilesFromGallery();

// Returns true if the user is currently picking media from Gallery/Photos. In that case, another GetImageFromGallery or GetVideoFromGallery request will simply be ignored
bool NativeGallery.IsMediaPickerBusy();


//// Runtime Permissions ////

// Interacting with Gallery/Photos is only possible when permission state is Permission.Granted. Most of the functions request permission internally (and return the result) but you can also check/request the permissions manually
NativeGallery.Permission NativeGallery.CheckPermission();
NativeGallery.Permission NativeGallery.RequestPermission();

// If permission state is Permission.Denied, user must grant the necessary permission (Storage on Android and Photos on iOS) manually from the Settings. These functions help you open the Settings directly from within the app
void NativeGallery.OpenSettings();
bool NativeGallery.CanOpenSettings();


//// Utility Functions ////

// maxSize: determines the maximum size of the returned Texture2D in pixels. Larger textures will be down-scaled. If untouched, its value will be set to SystemInfo.maxTextureSize. It is recommended to set a proper maxSize for better performance
// markTextureNonReadable: marks the generated texture as non-readable for better memory usage. If you plan to modify the texture later (e.g. GetPixels/SetPixels), set its value to false
// generateMipmaps: determines whether texture should have mipmaps or not
// linearColorSpace: determines whether texture should be in linear color space or sRGB color space
Texture2D NativeGallery.LoadImageAtPath( string imagePath, int maxSize = -1, bool markTextureNonReadable = true, bool generateMipmaps = true, bool linearColorSpace = false ): creates a Texture2D from the specified image file in correct orientation and returns it. Returns null, if something goes wrong

NativeGallery.ImageProperties NativeGallery.GetImageProperties( string imagePath ): returns an ImageProperties instance that holds the width, height and mime type information of an image file without creating a Texture2D object. Mime type will be null, if it can't be determined