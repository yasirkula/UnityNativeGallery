package com.yasirkula.unity;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.File;

/**
 * Created by yasirkula on 22.06.2017.
 */

public class NativeGallery
{
    public static String GetMediaPath( String directoryName )
    {
        String path = Environment.getExternalStoragePublicDirectory( Environment.DIRECTORY_DCIM ).getAbsolutePath();
        if( path.charAt( path.length() - 1 ) != File.separatorChar )
            path = path + File.separator;

        if( directoryName.charAt( directoryName.length() - 1 ) == '/' || directoryName.charAt( directoryName.length() - 1 ) == '\\' )
            directoryName = directoryName.substring( 0, directoryName.length() - 1 );

        path += directoryName + File.separatorChar;

        new File( path ).mkdirs();
        return path;
    }

    public static void MediaScanFile( Context context, String path )
    {
        MediaScannerConnection.scanFile( context, new String[] { path }, null, null );
    }

    public static void MediaDeleteFile( Context context, String path, boolean isImage )
    {
        if( isImage )
            context.getContentResolver().delete( MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Images.Media.DATA + "=?", new String[] { path } );
        else
            context.getContentResolver().delete( MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media.DATA + "=?", new String[] { path } );
    }
}