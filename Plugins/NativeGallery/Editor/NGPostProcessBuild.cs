#if UNITY_IOS
using UnityEditor;
using UnityEditor.Callbacks;
using System.IO;
using UnityEditor.iOS.Xcode;
#endif

public class NGPostProcessBuild
{
	private const bool ENABLED = true;

	private const string PHOTO_LIBRARY_USAGE_DESCRIPTION = "Save media to Photos";
	private const bool MINIMUM_TARGET_8_OR_ABOVE = false;

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
				pbxProject.AddBuildProperty( targetGUID, "OTHER_LDFLAGS", "-framework Photos" );
				pbxProject.AddBuildProperty( targetGUID, "OTHER_LDFLAGS", "-framework MobileCoreServices" );
				pbxProject.AddBuildProperty( targetGUID, "OTHER_LDFLAGS", "-framework ImageIO" );
#if !UNITY_2018_1_OR_NEWER
			}
			else
			{
				pbxProject.AddBuildProperty( targetGUID, "OTHER_LDFLAGS", "-weak_framework Photos" );
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
			rootDict.SetString( "NSPhotoLibraryAddUsageDescription", PHOTO_LIBRARY_USAGE_DESCRIPTION );

			File.WriteAllText( plistPath, plist.WriteToString() );
		}
	}
#pragma warning restore 0162
#endif
}