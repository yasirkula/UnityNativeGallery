using System.IO;
using UnityEditor;
using UnityEngine;
#if UNITY_IOS
using UnityEditor.Callbacks;
using UnityEditor.iOS.Xcode;
#endif

namespace NativeGalleryNamespace
{
	[System.Serializable]
	public class Settings
	{
		private const string SAVE_PATH = "ProjectSettings/NativeGallery.json";

		public bool AutomatedSetup = true;
		public string PhotoLibraryUsageDescription = "The app requires access to Photos to interact with it.";
		public string PhotoLibraryAdditionsUsageDescription = "The app requires access to Photos to save media to it.";
		public bool DontAskLimitedPhotosPermissionAutomaticallyOnIos14 = true; // See: https://mackuba.eu/2020/07/07/photo-library-changes-ios-14/

		private static Settings m_instance = null;
		public static Settings Instance
		{
			get
			{
				if( m_instance == null )
				{
					try
					{
						if( File.Exists( SAVE_PATH ) )
							m_instance = JsonUtility.FromJson<Settings>( File.ReadAllText( SAVE_PATH ) );
						else
							m_instance = new Settings();
					}
					catch( System.Exception e )
					{
						Debug.LogException( e );
						m_instance = new Settings();
					}
				}

				return m_instance;
			}
		}

		public void Save()
		{
			File.WriteAllText( SAVE_PATH, JsonUtility.ToJson( this, true ) );
		}

		[SettingsProvider]
		public static SettingsProvider CreatePreferencesGUI()
		{
			return new SettingsProvider( "Project/yasirkula/Native Gallery", SettingsScope.Project )
			{
				guiHandler = ( searchContext ) => PreferencesGUI(),
				keywords = new System.Collections.Generic.HashSet<string>() { "Native", "Gallery", "Android", "iOS" }
			};
		}

		public static void PreferencesGUI()
		{
			EditorGUI.BeginChangeCheck();

			Instance.AutomatedSetup = EditorGUILayout.Toggle( "Automated Setup", Instance.AutomatedSetup );

			EditorGUI.BeginDisabledGroup( !Instance.AutomatedSetup );
			Instance.PhotoLibraryUsageDescription = EditorGUILayout.DelayedTextField( "Photo Library Usage Description", Instance.PhotoLibraryUsageDescription );
			Instance.PhotoLibraryAdditionsUsageDescription = EditorGUILayout.DelayedTextField( "Photo Library Additions Usage Description", Instance.PhotoLibraryAdditionsUsageDescription );
			Instance.DontAskLimitedPhotosPermissionAutomaticallyOnIos14 = EditorGUILayout.Toggle( new GUIContent( "Don't Ask Limited Photos Permission Automatically", "See: https://mackuba.eu/2020/07/07/photo-library-changes-ios-14/. It's recommended to keep this setting enabled" ), Instance.DontAskLimitedPhotosPermissionAutomaticallyOnIos14 );
			EditorGUI.EndDisabledGroup();

			if( EditorGUI.EndChangeCheck() )
				Instance.Save();
		}
	}

	public class NGPostProcessBuild
	{
#if UNITY_IOS
		[PostProcessBuild( 1 )]
		public static void OnPostprocessBuild( BuildTarget target, string buildPath )
		{
			if( !Settings.Instance.AutomatedSetup )
				return;

			if( target == BuildTarget.iOS )
			{
				string pbxProjectPath = PBXProject.GetPBXProjectPath( buildPath );
				string plistPath = Path.Combine( buildPath, "Info.plist" );

				PBXProject pbxProject = new PBXProject();
				pbxProject.ReadFromFile( pbxProjectPath );

				string targetGUID = pbxProject.GetUnityFrameworkTargetGuid();
				pbxProject.AddFrameworkToProject( targetGUID, "PhotosUI.framework", true );
				pbxProject.AddFrameworkToProject( targetGUID, "Photos.framework", false );
				pbxProject.AddFrameworkToProject( targetGUID, "MobileCoreServices.framework", false );
				pbxProject.AddFrameworkToProject( targetGUID, "ImageIO.framework", false );

				File.WriteAllText( pbxProjectPath, pbxProject.WriteToString() );

				PlistDocument plist = new PlistDocument();
				plist.ReadFromString( File.ReadAllText( plistPath ) );

				PlistElementDict rootDict = plist.root;
				if( !string.IsNullOrEmpty( Settings.Instance.PhotoLibraryUsageDescription ) )
					rootDict.SetString( "NSPhotoLibraryUsageDescription", Settings.Instance.PhotoLibraryUsageDescription );
				if( !string.IsNullOrEmpty( Settings.Instance.PhotoLibraryAdditionsUsageDescription ) )
					rootDict.SetString( "NSPhotoLibraryAddUsageDescription", Settings.Instance.PhotoLibraryAdditionsUsageDescription );
				if( Settings.Instance.DontAskLimitedPhotosPermissionAutomaticallyOnIos14 )
					rootDict.SetBoolean( "PHPhotoLibraryPreventAutomaticLimitedAccessAlert", true );

				File.WriteAllText( plistPath, plist.WriteToString() );
			}
		}
#endif
	}
}