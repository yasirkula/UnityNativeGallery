#import <Foundation/Foundation.h>

extern "C" void _ScreenshotWriteToAlbum(const char* path) {
    UIImage *image = [UIImage imageWithContentsOfFile:[NSString stringWithUTF8String:path]];
    UIImageWriteToSavedPhotosAlbum(image, nil, nil, nil);
}

extern "C" void _VideoWriteToAlbum(const char* path) {
	NSString *videoPath = [NSString stringWithUTF8String:path];
    UISaveVideoAtPathToSavedPhotosAlbum(videoPath, nil, nil, nil);
}