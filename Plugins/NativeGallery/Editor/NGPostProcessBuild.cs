using System.IO;
using UnityEditor;
using UnityEngine;
#if UNITY_IOS
using UnityEditor.Callbacks;
using UnityEditor.iOS.Xcode;
#endif

namespace NativeGalleryNamespace
{
	public class NGPostProcessBuild
	{
		private const bool ENABLED = true;

		private const string PHOTO_LIBRARY_USAGE_DESCRIPTION = "The app requires access to Photos to interact with it.";
		private const string PHOTO_LIBRARY_ADDITIONS_USAGE_DESCRIPTION = "The app requires access to Photos to save media to it.";
		private const bool DONT_ASK_LIMITED_PHOTOS_PERMISSION_AUTOMATICALLY_ON_IOS14 = true; // See: https://mackuba.eu/2020/07/07/photo-library-changes-ios-14/
#if !UNITY_2018_1_OR_NEWER
		private const bool MINIMUM_TARGET_8_OR_ABOVE = false;
#endif

		[InitializeOnLoadMethod]
		public static void ValidatePlugin()
		{
			string jarPath = "Assets/Plugins/NativeGallery/Android/NativeGallery.jar";
			if( File.Exists( jarPath ) )
			{
				Debug.Log( "Deleting obsolete " + jarPath );
				AssetDatabase.DeleteAsset( jarPath );
			}
		}

#if UNITY_IOS
#pragma warning disable 0162
		[PostProcessBuild]
		public static void OnPostprocessBuild( BuildTarget target, string buildPath )
		{
			if( !ENABLED )
				return;

			if( target == BuildTarget.iOS )
			{
				string pbxProjectPath = PBXProject.GetPBXProjectPath( buildPath );
				string plistPath = Path.Combine( buildPath, "Info.plist" );

				PBXProject pbxProject = new PBXProject();
				pbxProject.ReadFromFile( pbxProjectPath );

#if UNITY_2019_3_OR_NEWER
				string targetGUID = pbxProject.GetUnityFrameworkTargetGuid();
#else
				string targetGUID = pbxProject.TargetGuidByName( PBXProject.GetUnityTargetName() );
#endif

				// Minimum supported iOS version on Unity 2018.1 and later is 8.0
#if !UNITY_2018_1_OR_NEWER
				if( MINIMUM_TARGET_8_OR_ABOVE )
				{
#endif
					pbxProject.AddBuildProperty( targetGUID, "OTHER_LDFLAGS", "-weak_framework PhotosUI" );
					pbxProject.AddBuildProperty( targetGUID, "OTHER_LDFLAGS", "-framework Photos" );
					pbxProject.AddBuildProperty( targetGUID, "OTHER_LDFLAGS", "-framework MobileCoreServices" );
					pbxProject.AddBuildProperty( targetGUID, "OTHER_LDFLAGS", "-framework ImageIO" );
#if !UNITY_2018_1_OR_NEWER
				}
				else
				{
					pbxProject.AddBuildProperty( targetGUID, "OTHER_LDFLAGS", "-weak_framework Photos" );
					pbxProject.AddBuildProperty( targetGUID, "OTHER_LDFLAGS", "-weak_framework PhotosUI" );
					pbxProject.AddBuildProperty( targetGUID, "OTHER_LDFLAGS", "-framework AssetsLibrary" );
					pbxProject.AddBuildProperty( targetGUID, "OTHER_LDFLAGS", "-framework MobileCoreServices" );
					pbxProject.AddBuildProperty( targetGUID, "OTHER_LDFLAGS", "-framework ImageIO" );
				}
#endif

				pbxProject.RemoveFrameworkFromProject( targetGUID, "Photos.framework" );

				File.WriteAllText( pbxProjectPath, pbxProject.WriteToString() );

				PlistDocument plist = new PlistDocument();
				plist.ReadFromString( File.ReadAllText( plistPath ) );

				PlistElementDict rootDict = plist.root;
				rootDict.SetString( "NSPhotoLibraryUsageDescription", PHOTO_LIBRARY_USAGE_DESCRIPTION );
				rootDict.SetString( "NSPhotoLibraryAddUsageDescription", PHOTO_LIBRARY_ADDITIONS_USAGE_DESCRIPTION );
				if( DONT_ASK_LIMITED_PHOTOS_PERMISSION_AUTOMATICALLY_ON_IOS14 )
					rootDict.SetBoolean( "PHPhotoLibraryPreventAutomaticLimitedAccessAlert", true );

				File.WriteAllText( plistPath, plist.WriteToString() );
			}
		}
#pragma warning restore 0162
#endif
	}
}