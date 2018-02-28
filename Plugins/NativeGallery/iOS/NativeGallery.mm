#import <Foundation/Foundation.h>
#import <Photos/Photos.h>
#import <MobileCoreServices/UTCoreTypes.h>
#if __IPHONE_OS_VERSION_MIN_REQUIRED < 80000
#import <AssetsLibrary/AssetsLibrary.h>
#endif

#ifdef UNITY_4_0 || UNITY_5_0
#import "iPhone_View.h"
#else
extern UIViewController* UnityGetGLViewController();
#endif

@interface UNativeGallery:NSObject
+ (int)checkPermission;
+ (int)requestPermission;
+ (int)canOpenSettings;
+ (void)openSettings;
+ (void)saveMedia:(NSString *)path albumName:(NSString *)album isImg:(BOOL)isImg;
+ (void)pickMedia:(BOOL)imageMode savePath:(NSString *)imageSavePath;
+ (int)isMediaPickerBusy;
@end

@implementation UNativeGallery

static NSString *pickedMediaSavePath;
static UIPopoverController *popup;
static UIImagePickerController *imagePicker;
static int imagePickerState = 0; // 0 -> none, 1 -> showing (always in this state on iPad), 2 -> finished

#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
+ (int)checkPermission {
#if __IPHONE_OS_VERSION_MIN_REQUIRED < 80000
	if ([[[UIDevice currentDevice] systemVersion] compare:@"8.0" options:NSNumericSearch] != NSOrderedAscending)
	{
#endif
		// version >= iOS 8: check permission using Photos framework
		PHAuthorizationStatus status = [PHPhotoLibrary authorizationStatus];
		if (status == PHAuthorizationStatusAuthorized)
			return 1;
		else if (status == PHAuthorizationStatusNotDetermined )
			return 2;
		else
			return 0;
#if __IPHONE_OS_VERSION_MIN_REQUIRED < 80000
	}
	else
	{
		// version < iOS 8: check permission using AssetsLibrary framework (Photos framework not available)
		ALAuthorizationStatus status = [ALAssetsLibrary authorizationStatus];
		if (status == ALAuthorizationStatusAuthorized)
			return 1;
		else if (status == ALAuthorizationStatusNotDetermined)
			return 2;
		else
			return 0;
	}
#endif
}
#pragma clang diagnostic pop

+ (int)requestPermission {
#if __IPHONE_OS_VERSION_MIN_REQUIRED < 80000
	if ([[[UIDevice currentDevice] systemVersion] compare:@"8.0" options:NSNumericSearch] != NSOrderedAscending)
	{
#endif
		// version >= iOS 8: request permission using Photos framework
		return [self requestPermissionNew];
#if __IPHONE_OS_VERSION_MIN_REQUIRED < 80000
	}
	else
	{
		// version < iOS 8: request permission using AssetsLibrary framework (Photos framework not available)
		return [self requestPermissionOld];
	}
#endif
}

// Credit: https://stackoverflow.com/a/25453667/2373034
+ (int)canOpenSettings {
	if (&UIApplicationOpenSettingsURLString != NULL)
		return 1;
	else
		return 0;
}

// Credit: https://stackoverflow.com/a/25453667/2373034
+ (void)openSettings {
	if (&UIApplicationOpenSettingsURLString != NULL)
		[[UIApplication sharedApplication] openURL:[NSURL URLWithString:UIApplicationOpenSettingsURLString]];
}

+ (void)saveMedia:(NSString *)path albumName:(NSString *)album isImg:(BOOL)isImg {
#if __IPHONE_OS_VERSION_MIN_REQUIRED < 80000
	if ([[[UIDevice currentDevice] systemVersion] compare:@"8.0" options:NSNumericSearch] != NSOrderedAscending)
	{
#endif
		// version >= iOS 8: save to specified album using Photos framework
		[self saveMediaNew:path albumName:album isImage:isImg];
#if __IPHONE_OS_VERSION_MIN_REQUIRED < 80000
	}
	else
	{
		// version < iOS 8: save using AssetsLibrary framework (Photos framework not available)
		[self saveMediaOld:path albumName:album isImage:isImg];
	}
#endif
}

// Credit: https://stackoverflow.com/a/26933380/2373034
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
+ (int)requestPermissionOld {
#if __IPHONE_OS_VERSION_MIN_REQUIRED < 80000
	ALAuthorizationStatus status = [ALAssetsLibrary authorizationStatus];

	if (status == ALAuthorizationStatusAuthorized) {
		return 1;
	}
	else if (status == ALAuthorizationStatusNotDetermined) {
		__block BOOL authorized = NO;
		ALAssetsLibrary *lib = [[ALAssetsLibrary alloc] init];
		
		dispatch_semaphore_t sema = dispatch_semaphore_create(0);
		[lib enumerateGroupsWithTypes:ALAssetsGroupAll usingBlock:^(ALAssetsGroup *group, BOOL *stop) {
			*stop = YES;
			authorized = YES;
			dispatch_semaphore_signal(sema);
		} failureBlock:^(NSError *error) {
			dispatch_semaphore_signal(sema);
		}];
		dispatch_semaphore_wait(sema, DISPATCH_TIME_FOREVER);
		
		if (authorized)
			return 1;
		else
			return 0;
	}
	else {
		return 0;
	}
#endif

	return 0;
}
#pragma clang diagnostic pop

// Credit: https://stackoverflow.com/a/32989022/2373034
+ (int)requestPermissionNew {
	PHAuthorizationStatus status = [PHPhotoLibrary authorizationStatus];

	if (status == PHAuthorizationStatusAuthorized) {
		return 1;
	}
	else if (status == PHAuthorizationStatusNotDetermined) {
		__block BOOL authorized = NO;
		
		dispatch_semaphore_t sema = dispatch_semaphore_create(0);
		[PHPhotoLibrary requestAuthorization:^(PHAuthorizationStatus status) {
			authorized = (status == PHAuthorizationStatusAuthorized);
			dispatch_semaphore_signal(sema);
		}];
		dispatch_semaphore_wait(sema, DISPATCH_TIME_FOREVER);
		
		if (authorized)
			return 1;
		else
			return 0;
	}
	else {
		return 0;
	}
}

// Credit: https://stackoverflow.com/a/22056664/2373034
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
+ (void)saveMediaOld:(NSString *)path albumName:(NSString *)album isImage:(BOOL)isImage {
#if __IPHONE_OS_VERSION_MIN_REQUIRED < 80000
	ALAssetsLibrary *library = [[ALAssetsLibrary alloc] init];
	
	if (!isImage && ![library videoAtPathIsCompatibleWithSavedPhotosAlbum:[NSURL fileURLWithPath:path]])
	{
		[[NSFileManager defaultManager] removeItemAtPath:path error:nil];
		return;
	}
	
	void (^saveBlock)(ALAssetsGroup *assetCollection) = ^void(ALAssetsGroup *assetCollection) {
		void (^saveResultBlock)(NSURL *assetURL, NSError *error) = ^void(NSURL *assetURL, NSError *error) {
			[[NSFileManager defaultManager] removeItemAtPath:path error:nil];
				
			if (error.code == 0) {
				[library assetForURL:assetURL resultBlock:^(ALAsset *asset) { 
					[assetCollection addAsset:asset];
				} failureBlock:^(NSError* error) {
					NSLog(@"Error moving asset to album: %@", error);
				}];
			}
			else {
				NSLog(@"Error creating asset: %@", error);
			}
		};
		
		if (!isImage)
			[library writeImageDataToSavedPhotosAlbum:[NSData dataWithContentsOfFile:path] metadata:nil completionBlock:saveResultBlock];
		else
			[library writeVideoAtPathToSavedPhotosAlbum:[NSURL fileURLWithPath:path] completionBlock:saveResultBlock];
	};

	__block BOOL albumFound = NO;
	[library enumerateGroupsWithTypes:ALAssetsGroupAlbum usingBlock:^(ALAssetsGroup *group, BOOL *stop) {
		if ([[group valueForProperty:ALAssetsGroupPropertyName] isEqualToString:album]) {
			*stop = YES;
			albumFound = YES;
			saveBlock(group);
		}
		else if (group == nil && albumFound==NO) { // Album doesn't exist
			[library addAssetsGroupAlbumWithName:album resultBlock:^(ALAssetsGroup *group) {
				saveBlock(group);
			}
			failureBlock:^(NSError *error) {
				NSLog(@"Error creating album: %@", error);
				[[NSFileManager defaultManager] removeItemAtPath:path error:nil];
			}];
		}
	} failureBlock:^(NSError* error) {
		NSLog(@"Error listing albums: %@", error);
		[[NSFileManager defaultManager] removeItemAtPath:path error:nil];
	}];
#endif
}
#pragma clang diagnostic pop

// Credit: https://stackoverflow.com/a/39909129/2373034
+ (void)saveMediaNew:(NSString *)path albumName:(NSString *)album isImage:(BOOL)isImage {
	void (^saveBlock)(PHAssetCollection *assetCollection) = ^void(PHAssetCollection *assetCollection) {
		[[PHPhotoLibrary sharedPhotoLibrary] performChanges:^{
			PHAssetChangeRequest *assetChangeRequest;
			if (isImage)
				assetChangeRequest = [PHAssetChangeRequest creationRequestForAssetFromImageAtFileURL:[NSURL fileURLWithPath:path]];
			else
				assetChangeRequest = [PHAssetChangeRequest creationRequestForAssetFromVideoAtFileURL:[NSURL fileURLWithPath:path]];
			
			PHAssetCollectionChangeRequest *assetCollectionChangeRequest = [PHAssetCollectionChangeRequest changeRequestForAssetCollection:assetCollection];
			[assetCollectionChangeRequest addAssets:@[[assetChangeRequest placeholderForCreatedAsset]]];

		} completionHandler:^(BOOL success, NSError *error) {
			if (!success) {
				NSLog(@"Error creating asset: %@", error);
			}
			
			[[NSFileManager defaultManager] removeItemAtPath:path error:nil];
		}];
	};

	PHFetchOptions *fetchOptions = [[PHFetchOptions alloc] init];
	fetchOptions.predicate = [NSPredicate predicateWithFormat:@"localizedTitle = %@", album];
	PHFetchResult *fetchResult = [PHAssetCollection fetchAssetCollectionsWithType:PHAssetCollectionTypeAlbum subtype:PHAssetCollectionSubtypeAny options:fetchOptions];
	if (fetchResult.count > 0) {
		saveBlock(fetchResult.firstObject);
	} 
	else {
		__block PHObjectPlaceholder *albumPlaceholder;
		[[PHPhotoLibrary sharedPhotoLibrary] performChanges:^{
			PHAssetCollectionChangeRequest *changeRequest = [PHAssetCollectionChangeRequest creationRequestForAssetCollectionWithTitle:album];
			albumPlaceholder = changeRequest.placeholderForCreatedAssetCollection;

		} completionHandler:^(BOOL success, NSError *error) {
			if (success) {
				PHFetchResult *fetchResult = [PHAssetCollection fetchAssetCollectionsWithLocalIdentifiers:@[albumPlaceholder.localIdentifier] options:nil];
				if (fetchResult.count > 0)
					saveBlock(fetchResult.firstObject);
				else
					[[NSFileManager defaultManager] removeItemAtPath:path error:nil];
			} 
			else {
				NSLog(@"Error creating album: %@", error);
				[[NSFileManager defaultManager] removeItemAtPath:path error:nil];
			}
		}];
	}
}

// Credit: https://stackoverflow.com/a/10531752/2373034
+ (void)pickMedia:(BOOL)imageMode savePath:(NSString *)imageSavePath {
	imagePicker = [[UIImagePickerController alloc] init];
    imagePicker.delegate = self;
    imagePicker.allowsEditing = NO;
	imagePicker.sourceType = UIImagePickerControllerSourceTypePhotoLibrary;
	
	if (imageMode)
		imagePicker.mediaTypes = [NSArray arrayWithObject:(NSString *)kUTTypeImage];
	else
		imagePicker.mediaTypes = [NSArray arrayWithObjects:(NSString *)kUTTypeMovie, (NSString *)kUTTypeVideo, nil];
	
	pickedMediaSavePath = imageSavePath;
	
	imagePickerState = 1;
	UIViewController *rootViewController = UnityGetGLViewController();
	if (UI_USER_INTERFACE_IDIOM() == UIUserInterfaceIdiomPhone) // iPhone
		[rootViewController presentViewController:imagePicker animated:YES completion:^{ imagePickerState = 0; }];
	else { // iPad
		popup = [[UIPopoverController alloc] initWithContentViewController:imagePicker];
		popup.delegate = self;
		[popup presentPopoverFromRect:CGRectMake( rootViewController.view.frame.size.width / 2, rootViewController.view.frame.size.height / 4, 0, 0 ) inView:rootViewController.view permittedArrowDirections:UIPopoverArrowDirectionAny animated:YES];
	}
}

+ (int)isMediaPickerBusy {
	if (imagePickerState == 2)
		return 1;
	
	if (imagePicker != nil) {
		if (imagePickerState == 1 || [imagePicker presentingViewController] == UnityGetGLViewController())
			return 1;
		else {
			imagePicker = nil;
			return 0;
		}
	}
	else
		return 0;
}

+ (void)imagePickerController:(UIImagePickerController *)picker didFinishPickingMediaWithInfo:(NSDictionary *)info {
	NSString *path;
	if ([info[UIImagePickerControllerMediaType] isEqualToString:(NSString *)kUTTypeImage]) { // image picked
		// Temporarily save image as PNG
		UIImage *image = info[UIImagePickerControllerOriginalImage];
		if (image == nil)
			path = nil;
		else {
			[UIImagePNGRepresentation(image) writeToFile:pickedMediaSavePath atomically:YES];
			path = pickedMediaSavePath;
		}
	}
	else { // video picked
		NSURL *mediaUrl = info[UIImagePickerControllerMediaURL] ?: info[UIImagePickerControllerReferenceURL];
		if (mediaUrl == nil)
			path = nil;
		else
			path = [mediaUrl path];
	}

	if (path == nil)
		path = @"";
		
	// Credit: https://stackoverflow.com/a/37052118/2373034
	const char *pathUTF8 = [path UTF8String];
	char *result = (char*) malloc(strlen(pathUTF8) + 1);
	strcpy(result, pathUTF8);

	popup = nil;
	imagePicker = nil;
	imagePickerState = 2;
	UnitySendMessage("NGMediaReceiveCallbackiOS", "OnMediaReceived", result);

	[picker dismissViewControllerAnimated:YES completion:nil];
}

+ (void)imagePickerControllerDidCancel:(UIImagePickerController *)picker
{
	popup = nil;
	imagePicker = nil;
	UnitySendMessage("NGMediaReceiveCallbackiOS", "OnMediaReceived", "");
	
	[picker dismissViewControllerAnimated:YES completion:nil];
}

+ (void) popoverControllerDidDismissPopover:(UIPopoverController *) popoverController {
	popup = nil;
	imagePicker = nil;
    UnitySendMessage("NGMediaReceiveCallbackiOS", "OnMediaReceived", "");
}

@end

extern "C" int _CheckPermission() {
	return [UNativeGallery checkPermission];
}

extern "C" int _RequestPermission() {
	return [UNativeGallery requestPermission];
}

extern "C" int _CanOpenSettings() {
	return [UNativeGallery canOpenSettings];
}

extern "C" void _OpenSettings() {
	[UNativeGallery openSettings];
}

extern "C" void _ImageWriteToAlbum(const char* path, const char* album) {
	[UNativeGallery saveMedia:[NSString stringWithUTF8String:path] albumName:[NSString stringWithUTF8String:album] isImg:YES];
}

extern "C" void _VideoWriteToAlbum(const char* path, const char* album) {
	[UNativeGallery saveMedia:[NSString stringWithUTF8String:path] albumName:[NSString stringWithUTF8String:album] isImg:NO];
}

extern "C" void _PickImage(const char* imageSavePath) {
	[UNativeGallery pickMedia:YES savePath:[NSString stringWithUTF8String:imageSavePath]];
}

extern "C" void _PickVideo() {
	[UNativeGallery pickMedia:NO savePath:nil];
}

extern "C" int _IsMediaPickerBusy() {
	return [UNativeGallery isMediaPickerBusy];
}
