package com.yasirkula.unity;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

// Handler usage reference: https://stackoverflow.com/a/6242292/2373034
public class NativeGalleryMediaPickerResultFragment extends DialogFragment
{
	public static int uiUpdateInterval = 100;
	public static String progressBarLabel = "Please wait...";

	private final NativeGalleryMediaPickerResultOperation resultOperation;

	private ProgressBar progressBar;

	private final Handler uiUpdateHandler = new Handler( Looper.getMainLooper() );
	private final Runnable progressBarUpdateTask = new Runnable()
	{
		@Override
		public void run()
		{
			if( resultOperation.finished )
			{
				resultOperation.sendResultToUnity();
				dismissAllowingStateLoss();
			}
			else
			{
				try
				{
					if( progressBar != null )
					{
						if( resultOperation.progress >= 0 )
						{
							if( progressBar.isIndeterminate() )
								progressBar.setIndeterminate( false );

							progressBar.setProgress( resultOperation.progress );
						}
						else if( !progressBar.isIndeterminate() )
							progressBar.setIndeterminate( true );
					}
				}
				finally
				{
					uiUpdateHandler.postDelayed( progressBarUpdateTask, uiUpdateInterval );
				}
			}
		}
	};

	public NativeGalleryMediaPickerResultFragment()
	{
		resultOperation = null;
	}

	public NativeGalleryMediaPickerResultFragment( final NativeGalleryMediaPickerResultOperation resultOperation )
	{
		this.resultOperation = resultOperation;
	}

	@Override
	public void onCreate( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );
		setRetainInstance( true ); // Required to preserve threads and stuff in case the configuration changes (e.g. orientation change)

		new Thread( new Runnable()
		{
			@Override
			public void run()
			{
				resultOperation.execute();
			}
		} ).start();
	}

	@Override
	public Dialog onCreateDialog( Bundle savedInstanceState )
	{
		// Credit: https://stackoverflow.com/a/49272722/2373034
		LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams( LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT );
		layoutParams.gravity = Gravity.CENTER;

		LinearLayout layout = new LinearLayout( getActivity() );
		layout.setOrientation( LinearLayout.VERTICAL );
		layout.setPadding( 30, 30, 30, 30 );
		layout.setGravity( Gravity.CENTER );
		layout.setLayoutParams( layoutParams );

		layoutParams = new LinearLayout.LayoutParams( LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT );
		layoutParams.gravity = Gravity.CENTER;
		layoutParams.width = (int) ( 175 * getActivity().getResources().getDisplayMetrics().density );

		progressBar = new ProgressBar( getActivity(), null, android.R.attr.progressBarStyleHorizontal );
		progressBar.setIndeterminate( true );
		progressBar.setPadding( 0, 30, 0, 0 );
		progressBar.setLayoutParams( layoutParams );

		if( progressBarLabel != null && progressBarLabel.length() > 0 )
		{
			layoutParams = new LinearLayout.LayoutParams( LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT );
			layoutParams.gravity = Gravity.CENTER;

			TextView progressBarText = new TextView( getActivity() );
			progressBarText.setText( progressBarLabel );
			progressBarText.setTextColor( Color.BLACK );
			progressBarText.setTextSize( 20 );
			progressBarText.setLayoutParams( layoutParams );

			layout.addView( progressBarText );
		}

		layout.addView( progressBar );

		AlertDialog dialog = new AlertDialog.Builder( getActivity() )
				.setNegativeButton( android.R.string.cancel, new DialogInterface.OnClickListener()
				{
					@Override
					public void onClick( DialogInterface dialog, int which )
					{
						resultOperation.cancel();
						resultOperation.sendResultToUnity();

						dismissAllowingStateLoss();
					}
				} )
				.setCancelable( false )
				.setView( layout ).create();

		dialog.setCancelable( false );
		dialog.setCanceledOnTouchOutside( false );

		return dialog;
	}

	@Override
	public void onActivityCreated( Bundle savedInstanceState )
	{
		super.onActivityCreated( savedInstanceState );
		progressBarUpdateTask.run();
	}

	@Override
	public void onDetach()
	{
		progressBar = null;
		uiUpdateHandler.removeCallbacks( progressBarUpdateTask );

		super.onDetach();
	}

	@Override
	public void onDismiss( DialogInterface dialog )
	{
		super.onDismiss( dialog );
		resultOperation.sendResultToUnity();
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
		resultOperation.sendResultToUnity();
	}
}