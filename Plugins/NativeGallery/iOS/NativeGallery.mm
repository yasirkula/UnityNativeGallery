#import <Foundation/Foundation.h>
#import <Photos/Photos.h>
#import <MobileCoreServices/UTCoreTypes.h>
#import <ImageIO/ImageIO.h>
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
+ (void)pickMedia:(BOOL)imageMode savePath:(NSString *)mediaSavePath;
+ (int)isMediaPickerBusy;
+ (char *)getImageProperties:(NSString *)path;
+ (char *)getVideoProperties:(NSString *)path;
+ (char *)loadImageAtPath:(NSString *)path tempFilePath:(NSString *)tempFilePath maximumSize:(int)maximumSize;
@end

@implementation UNativeGallery

static NSString *pickedMediaSavePath;
static NSString *resultPath;
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
		UnitySendMessage("NGMediaSaveCallbackiOS", "OnMediaSaveFailed", "Video format is not compatible with Photos");
		return;
	}
	
	void (^saveBlock)(ALAssetsGroup *assetCollection) = ^void(ALAssetsGroup *assetCollection) {
		void (^saveResultBlock)(NSURL *assetURL, NSError *error) = ^void(NSURL *assetURL, NSError *error) {
			[[NSFileManager defaultManager] removeItemAtPath:path error:nil];
			
			if (error.code == 0) {
				[library assetForURL:assetURL resultBlock:^(ALAsset *asset) {
					[assetCollection addAsset:asset];
					UnitySendMessage("NGMediaSaveCallbackiOS", "OnMediaSaveCompleted", "");
				} failureBlock:^(NSError* error) {
					NSLog(@"Error moving asset to album: %@", error);
					UnitySendMessage("NGMediaSaveCallbackiOS", "OnMediaSaveFailed", [self getCString:[error localizedDescription]]);
				}];
			}
			else {
				NSLog(@"Error creating asset: %@", error);
				UnitySendMessage("NGMediaSaveCallbackiOS", "OnMediaSaveFailed", [self getCString:[error localizedDescription]]);
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
										UnitySendMessage("NGMediaSaveCallbackiOS", "OnMediaSaveFailed", [self getCString:[error localizedDescription]]);
									}];
		}
	} failureBlock:^(NSError* error) {
		NSLog(@"Error listing albums: %@", error);
		[[NSFileManager defaultManager] removeItemAtPath:path error:nil];
		UnitySendMessage("NGMediaSaveCallbackiOS", "OnMediaSaveFailed", [self getCString:[error localizedDescription]]);
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
			[[NSFileManager defaultManager] removeItemAtPath:path error:nil];
			
			if (success)
				UnitySendMessage("NGMediaSaveCallbackiOS", "OnMediaSaveCompleted", "");
			else {
				NSLog(@"Error creating asset: %@", error);
				UnitySendMessage("NGMediaSaveCallbackiOS", "OnMediaSaveFailed", [self getCString:[error localizedDescription]]);
			}
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
				else {
					[[NSFileManager defaultManager] removeItemAtPath:path error:nil];
					UnitySendMessage("NGMediaSaveCallbackiOS", "OnMediaSaveFailed", "Album placeholder not found" );
				}
			}
			else {
				NSLog(@"Error creating album: %@", error);
				[[NSFileManager defaultManager] removeItemAtPath:path error:nil];
				UnitySendMessage("NGMediaSaveCallbackiOS", "OnMediaSaveFailed", [self getCString:[error localizedDescription]]);
			}
		}];
	}
}

// Credit: https://stackoverflow.com/a/10531752/2373034
+ (void)pickMedia:(BOOL)imageMode savePath:(NSString *)mediaSavePath {
	imagePicker = [[UIImagePickerController alloc] init];
	imagePicker.delegate = self;
	imagePicker.allowsEditing = NO;
	imagePicker.sourceType = UIImagePickerControllerSourceTypePhotoLibrary;
	
	if (imageMode)
		imagePicker.mediaTypes = [NSArray arrayWithObject:(NSString *)kUTTypeImage];
	else
	{
		imagePicker.mediaTypes = [NSArray arrayWithObjects:(NSString *)kUTTypeMovie, (NSString *)kUTTypeVideo, nil];
		
#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 110000
		// Don't compress the picked video if possible
		if ([[[UIDevice currentDevice] systemVersion] compare:@"11.0" options:NSNumericSearch] != NSOrderedAscending)
			imagePicker.videoExportPreset = AVAssetExportPresetPassthrough;
#endif
	}
	
	pickedMediaSavePath = mediaSavePath;
	
	imagePickerState = 1;
	UIViewController *rootViewController = UnityGetGLViewController();
	if (UI_USER_INTERFACE_IDIOM() == UIUserInterfaceIdiomPhone) // iPhone
		[rootViewController presentViewController:imagePicker animated:YES completion:^{ imagePickerState = 0; }];
	else { // iPad
		popup = [[UIPopoverController alloc] initWithContentViewController:imagePicker];
		popup.delegate = self;
		[popup presentPopoverFromRect:CGRectMake( rootViewController.view.frame.size.width / 2, rootViewController.view.frame.size.height / 2, 1, 1 ) inView:rootViewController.view permittedArrowDirections:0 animated:YES];
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

// Credit: https://stackoverflow.com/a/4170099/2373034
+ (NSArray *)getImageMetadata:(NSString *)path {
	int width = 0;
	int height = 0;
	int orientation = -1;
	
	CGImageSourceRef imageSource = CGImageSourceCreateWithURL((__bridge CFURLRef)[NSURL fileURLWithPath:path], nil);
	if (imageSource != nil) {
		NSDictionary *options = [NSDictionary dictionaryWithObject:[NSNumber numberWithBool:NO] forKey:(__bridge NSString *)kCGImageSourceShouldCache];
		CFDictionaryRef imageProperties = CGImageSourceCopyPropertiesAtIndex(imageSource, 0, (__bridge CFDictionaryRef)options);
		CFRelease(imageSource);
		
		CGFloat widthF = 0.0f, heightF = 0.0f;
		if (imageProperties != nil) {
			if (CFDictionaryContainsKey(imageProperties, kCGImagePropertyPixelWidth))
				CFNumberGetValue((CFNumberRef)CFDictionaryGetValue(imageProperties, kCGImagePropertyPixelWidth), kCFNumberCGFloatType, &widthF);
			
			if (CFDictionaryContainsKey(imageProperties, kCGImagePropertyPixelHeight))
				CFNumberGetValue((CFNumberRef)CFDictionaryGetValue(imageProperties, kCGImagePropertyPixelHeight), kCFNumberCGFloatType, &heightF);
			
			if (CFDictionaryContainsKey(imageProperties, kCGImagePropertyOrientation)) {
				CFNumberGetValue((CFNumberRef)CFDictionaryGetValue(imageProperties, kCGImagePropertyOrientation), kCFNumberIntType, &orientation);
				
				if (orientation > 4) { // landscape image
					CGFloat temp = widthF;
					widthF = heightF;
					heightF = temp;
				}
			}
			
			CFRelease(imageProperties);
		}
		
		width = (int)roundf(widthF);
		height = (int)roundf(heightF);
	}
	
	return [[NSArray alloc] initWithObjects:[NSNumber numberWithInt:width], [NSNumber numberWithInt:height], [NSNumber numberWithInt:orientation], nil];
}

+ (char *)getImageProperties:(NSString *)path {
	NSArray *metadata = [self getImageMetadata:path];
	
	int orientationUnity;
	int orientation = [metadata[2] intValue];
	
	// To understand the magic numbers, see ImageOrientation enum in NativeGallery.cs
	// and http://sylvana.net/jpegcrop/exif_orientation.html
	if (orientation == 1)
		orientationUnity = 0;
	else if (orientation == 2)
		orientationUnity = 4;
	else if (orientation == 3)
		orientationUnity = 2;
	else if (orientation == 4)
		orientationUnity = 6;
	else if (orientation == 5)
		orientationUnity = 5;
	else if (orientation == 6)
		orientationUnity = 1;
	else if (orientation == 7)
		orientationUnity = 7;
	else if (orientation == 8)
		orientationUnity = 3;
	else
		orientationUnity = -1;
	
	return [self getCString:[NSString stringWithFormat:@"%d>%d> >%d", [metadata[0] intValue], [metadata[1] intValue], orientationUnity]];
}

+ (char *)getVideoProperties:(NSString *)path {
	CGSize size = CGSizeZero;
	float rotation = 0;
	long long duration = 0;
	
	AVURLAsset *asset = [AVURLAsset URLAssetWithURL:[NSURL fileURLWithPath:path] options:nil];
	if (asset != nil) {
		duration = (long long) round(CMTimeGetSeconds([asset duration]) * 1000);
		CGAffineTransform transform = [asset preferredTransform];
		NSArray<AVAssetTrack *>* videoTracks = [asset tracksWithMediaType:AVMediaTypeVideo];
		if (videoTracks != nil && [videoTracks count] > 0) {
			size = [[videoTracks objectAtIndex:0] naturalSize];
			transform = [[videoTracks objectAtIndex:0] preferredTransform];
		}
		
		rotation = atan2(transform.b, transform.a) * (180.0 / M_PI);
	}
	
	return [self getCString:[NSString stringWithFormat:@"%d>%d>%lld>%f", (int)roundf(size.width), (int)roundf(size.height), duration, rotation]];
}

+ (UIImage *)scaleImage:(UIImage *)image maxSize:(int)maxSize {
	CGFloat width = image.size.width;
	CGFloat height = image.size.height;
	
	UIImageOrientation orientation = image.imageOrientation;
	if (width <= maxSize && height <= maxSize && orientation != UIImageOrientationDown &&
		orientation != UIImageOrientationLeft && orientation != UIImageOrientationRight &&
		orientation != UIImageOrientationLeftMirrored && orientation != UIImageOrientationRightMirrored &&
		orientation != UIImageOrientationUpMirrored && orientation != UIImageOrientationDownMirrored)
		return image;
	
	CGFloat scaleX = 1.0f;
	CGFloat scaleY = 1.0f;
	if (width > maxSize)
		scaleX = maxSize / width;
	if (height > maxSize)
		scaleY = maxSize / height;
	
	// Credit: https://github.com/mbcharbonneau/UIImage-Categories/blob/master/UIImage%2BAlpha.m
	CGImageAlphaInfo alpha = CGImageGetAlphaInfo(image.CGImage);
	BOOL hasAlpha = alpha == kCGImageAlphaFirst || alpha == kCGImageAlphaLast || alpha == kCGImageAlphaPremultipliedFirst || alpha == kCGImageAlphaPremultipliedLast;
	
	CGFloat scaleRatio = scaleX < scaleY ? scaleX : scaleY;
	CGRect imageRect = CGRectMake(0, 0, width * scaleRatio, height * scaleRatio);
	UIGraphicsBeginImageContextWithOptions(imageRect.size, !hasAlpha, image.scale);
	[image drawInRect:imageRect];
	image = UIGraphicsGetImageFromCurrentImageContext();
	UIGraphicsEndImageContext();
	
	return image;
}

+ (char *)loadImageAtPath:(NSString *)path tempFilePath:(NSString *)tempFilePath maximumSize:(int)maximumSize {
	// Check if the image can be loaded by Unity without requiring a conversion to PNG
	// Credit: https://stackoverflow.com/a/12048937/2373034
	NSString *extension = [path pathExtension];
	BOOL conversionNeeded = [extension caseInsensitiveCompare:@"jpg"] != NSOrderedSame && [extension caseInsensitiveCompare:@"jpeg"] != NSOrderedSame && [extension caseInsensitiveCompare:@"png"] != NSOrderedSame;

	if (!conversionNeeded) {
		// Check if the image needs to be processed at all
		NSArray *metadata = [self getImageMetadata:path];
		int orientationInt = [metadata[2] intValue];  // 1: correct orientation, [1,8]: valid orientation range
		if (orientationInt == 1 && [metadata[0] intValue] <= maximumSize && [metadata[1] intValue] <= maximumSize)
			return [self getCString:path];
	}
	
	UIImage *image = [UIImage imageWithContentsOfFile:path];
	if (image == nil)
		return [self getCString:path];
	
	UIImage *scaledImage = [self scaleImage:image maxSize:maximumSize];
	if (conversionNeeded || scaledImage != image) {
		if (![UIImagePNGRepresentation(scaledImage) writeToFile:tempFilePath atomically:YES]) {
			NSLog(@"Error creating scaled image");
			return [self getCString:path];
		}
		
		return [self getCString:tempFilePath];
	}
	else
		return [self getCString:path];
}

#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
+ (void)imagePickerController:(UIImagePickerController *)picker didFinishPickingMediaWithInfo:(NSDictionary *)info {
	resultPath = nil;
	
	if ([info[UIImagePickerControllerMediaType] isEqualToString:(NSString *)kUTTypeImage]) { // image picked
		// On iOS 8.0 or later, try to obtain the raw data of the image (which allows picking gifs properly or preserving metadata)
		if ([[[UIDevice currentDevice] systemVersion] compare:@"8.0" options:NSNumericSearch] != NSOrderedAscending) {
			PHAsset *asset = nil;
#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 110000
			if ([[[UIDevice currentDevice] systemVersion] compare:@"11.0" options:NSNumericSearch] != NSOrderedAscending) {
				// Try fetching the source image via UIImagePickerControllerImageURL
				NSURL *mediaUrl = info[UIImagePickerControllerImageURL];
				if (mediaUrl != nil) {
					NSString *imagePath = [mediaUrl path];
					if (imagePath != nil && [[NSFileManager defaultManager] fileExistsAtPath:imagePath]) {
						NSError *error;
						NSString *newPath = [pickedMediaSavePath stringByAppendingPathExtension:[imagePath pathExtension]];
						
						if (![[NSFileManager defaultManager] fileExistsAtPath:newPath] || [[NSFileManager defaultManager] removeItemAtPath:newPath error:&error]) {
							if ([[NSFileManager defaultManager] copyItemAtPath:imagePath toPath:newPath error:&error]) {
								resultPath = newPath;
								NSLog(@"Copied source image from UIImagePickerControllerImageURL");
							}
							else
								NSLog(@"Error copying image: %@", error);
						}
						else
							NSLog(@"Error deleting existing image: %@", error);
					}
				}
				
				if (resultPath == nil)
					asset = info[UIImagePickerControllerPHAsset];
			}
#endif
			
			if (resultPath == nil) {
				if (asset == nil) {
					NSURL *mediaUrl = info[UIImagePickerControllerReferenceURL] ?: info[UIImagePickerControllerMediaURL];
					if (mediaUrl != nil)
						asset = [[PHAsset fetchAssetsWithALAssetURLs:[NSArray arrayWithObject:mediaUrl] options:nil] firstObject];
				}
				
				if (asset != nil) {
					PHImageRequestOptions *options = [[PHImageRequestOptions alloc] init];
					options.synchronous = YES;
					options.version = PHImageRequestOptionsVersionCurrent;
					
#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 130000
					if ([[[UIDevice currentDevice] systemVersion] compare:@"13.0" options:NSNumericSearch] != NSOrderedAscending) {
						[[PHImageManager defaultManager] requestImageDataAndOrientationForAsset:asset options:options resultHandler:^(NSData *imageData, NSString *dataUTI, CGImagePropertyOrientation orientation, NSDictionary *imageInfo) {
							if (imageData != nil)
								[self trySaveSourceImage:imageData withInfo:imageInfo];
							else
								NSLog(@"Couldn't fetch raw image data");
						}];
					}
					else {
#endif
						[[PHImageManager defaultManager] requestImageDataForAsset:asset options:options resultHandler:^(NSData *imageData, NSString *dataUTI, UIImageOrientation orientation, NSDictionary *imageInfo) {
							if (imageData != nil)
								[self trySaveSourceImage:imageData withInfo:imageInfo];
							else
								NSLog(@"Couldn't fetch raw image data");
						}];
#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 130000
					}
#endif
				}
			}
		}
		
		if (resultPath == nil) {
			// Temporarily save image as PNG
			UIImage *image = info[UIImagePickerControllerOriginalImage];
			if (image != nil) {
				resultPath = [pickedMediaSavePath stringByAppendingPathExtension:@"png"];
				if (![UIImagePNGRepresentation(image) writeToFile:resultPath atomically:YES]) {
					NSLog(@"Error creating PNG image");
					resultPath = nil;
				}
			}
			else
				NSLog(@"Error fetching original image from picker");
		}
	}
	else { // video picked
		NSURL *mediaUrl = info[UIImagePickerControllerMediaURL] ?: info[UIImagePickerControllerReferenceURL];
		if (mediaUrl != nil) {
			resultPath = [mediaUrl path];
			
			// On iOS 13, picked file becomes unreachable as soon as the UIImagePickerController disappears,
			// in that case, copy the video to a temporary location
			if ([[[UIDevice currentDevice] systemVersion] compare:@"13.0" options:NSNumericSearch] != NSOrderedAscending) {
				NSError *error;
				NSString *newPath = [pickedMediaSavePath stringByAppendingPathExtension:[resultPath pathExtension]];
				
				if (![[NSFileManager defaultManager] fileExistsAtPath:newPath] || [[NSFileManager defaultManager] removeItemAtPath:newPath error:&error]) {
					if ([[NSFileManager defaultManager] copyItemAtPath:resultPath toPath:newPath error:&error])
						resultPath = newPath;
					else {
						NSLog(@"Error copying video: %@", error);
						resultPath = nil;
					}
				}
				else {
					NSLog(@"Error deleting existing video: %@", error);
					resultPath = nil;
				}
			}
		}
	}
	
	popup = nil;
	imagePicker = nil;
	imagePickerState = 2;
	UnitySendMessage("NGMediaReceiveCallbackiOS", "OnMediaReceived", [self getCString:resultPath]);
	
	[picker dismissViewControllerAnimated:NO completion:nil];
}
#pragma clang diagnostic pop

+ (void)imagePickerControllerDidCancel:(UIImagePickerController *)picker
{
	popup = nil;
	imagePicker = nil;
	UnitySendMessage("NGMediaReceiveCallbackiOS", "OnMediaReceived", "");
	
	[picker dismissViewControllerAnimated:YES completion:nil];
}

+ (void)popoverControllerDidDismissPopover:(UIPopoverController *)popoverController {
	popup = nil;
	imagePicker = nil;
	UnitySendMessage("NGMediaReceiveCallbackiOS", "OnMediaReceived", "");
}

+ (void)trySaveSourceImage:(NSData *)imageData withInfo:(NSDictionary *)info {
	NSString *filePath = info[@"PHImageFileURLKey"];
	if (filePath != nil) // filePath can actually be an NSURL, convert it to NSString
		filePath = [NSString stringWithFormat:@"%@", filePath];
	
	if (filePath == nil || [filePath length] == 0)
	{
		filePath = info[@"PHImageFileUTIKey"];
		if (filePath != nil)
			filePath = [NSString stringWithFormat:@"%@", filePath];
	}
	
	if (filePath == nil || [filePath length] == 0)
		resultPath = pickedMediaSavePath;
	else
		resultPath = [pickedMediaSavePath stringByAppendingPathExtension:[filePath pathExtension]];
	
	NSError *error;
	if (![[NSFileManager defaultManager] fileExistsAtPath:resultPath] || [[NSFileManager defaultManager] removeItemAtPath:resultPath error:&error]) {
		if (![imageData writeToFile:resultPath atomically:YES]) {
			NSLog(@"Error copying source image to file");
			resultPath = nil;
		}
	}
	else {
		NSLog(@"Error deleting existing image: %@", error);
		resultPath = nil;
	}
}

// Credit: https://stackoverflow.com/a/37052118/2373034
+ (char *)getCString:(NSString *)source {
	if (source == nil)
		source = @"";
	
	const char *sourceUTF8 = [source UTF8String];
	char *result = (char*) malloc(strlen(sourceUTF8) + 1);
	strcpy(result, sourceUTF8);
	
	return result;
}

@end

extern "C" int _NativeGallery_CheckPermission() {
	return [UNativeGallery checkPermission];
}

extern "C" int _NativeGallery_RequestPermission() {
	return [UNativeGallery requestPermission];
}

extern "C" int _NativeGallery_CanOpenSettings() {
	return [UNativeGallery canOpenSettings];
}

extern "C" void _NativeGallery_OpenSettings() {
	[UNativeGallery openSettings];
}

extern "C" void _NativeGallery_ImageWriteToAlbum(const char* path, const char* album) {
	[UNativeGallery saveMedia:[NSString stringWithUTF8String:path] albumName:[NSString stringWithUTF8String:album] isImg:YES];
}

extern "C" void _NativeGallery_VideoWriteToAlbum(const char* path, const char* album) {
	[UNativeGallery saveMedia:[NSString stringWithUTF8String:path] albumName:[NSString stringWithUTF8String:album] isImg:NO];
}

extern "C" void _NativeGallery_PickImage(const char* imageSavePath) {
	[UNativeGallery pickMedia:YES savePath:[NSString stringWithUTF8String:imageSavePath]];
}

extern "C" void _NativeGallery_PickVideo(const char* videoSavePath) {
	[UNativeGallery pickMedia:NO savePath:[NSString stringWithUTF8String:videoSavePath]];
}

extern "C" int _NativeGallery_IsMediaPickerBusy() {
	return [UNativeGallery isMediaPickerBusy];
}

extern "C" char* _NativeGallery_GetImageProperties(const char* path) {
	return [UNativeGallery getImageProperties:[NSString stringWithUTF8String:path]];
}

extern "C" char* _NativeGallery_GetVideoProperties(const char* path) {
	return [UNativeGallery getVideoProperties:[NSString stringWithUTF8String:path]];
}

extern "C" char* _NativeGallery_LoadImageAtPath(const char* path, const char* temporaryFilePath, int maxSize) {
	return [UNativeGallery loadImageAtPath:[NSString stringWithUTF8String:path] tempFilePath:[NSString stringWithUTF8String:temporaryFilePath] maximumSize:maxSize];
}