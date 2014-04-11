package com.ymmijbao.clapcam;

import java.io.ByteArrayOutputStream;
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.hardware.Camera.ShutterCallback;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore.Images;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.hardware.Camera.PictureCallback;
import be.hogent.tarsos.dsp.AudioEvent;
import be.hogent.tarsos.dsp.onsets.OnsetHandler;
import be.hogent.tarsos.dsp.onsets.PercussionOnsetDetector;

public class MainActivity extends Activity {

	private Camera camera;
	private CamPreview preview;
	private FrameLayout framePreview;
	private ImageButton sendButton;
	private ImageButton flipCamButton;
	private ImageButton helpButton;
	private byte[] buffer;
	private boolean isRecording = false;
	private int CAMERA_IN_USE = 1;
	private AudioRecord recorder;
	private Uri uriToImage;
	private be.hogent.tarsos.dsp.AudioFormat tarsosFormat;
	private PercussionOnsetDetector detector;
	private PictureCallback pictureCallback;
	private ShutterCallback shutterCallback;
	private static final int SAMPLE_RATE = 16000;
	private static final int REAR_FACING_CAMERA = 0;
	private static final int FRONT_FACING_CAMERA = 1;
	private static final int ORIENTATION_LANDSCAPE = 2;
	private static final int ORIENTATION_PORTRAIT = 1;

	@Override 
	protected void onPause() {
		super.onPause();
		destroyCamera();
	}
	
	protected void onResume() {
		super.onResume();
		if (camera != null) {
			if (getResources().getConfiguration().orientation == ORIENTATION_LANDSCAPE) {
				camera.setDisplayOrientation(0);
			} else if (getResources().getConfiguration().orientation == ORIENTATION_PORTRAIT) {
				camera.setDisplayOrientation(90);
			}
		} 
	}
			
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
				
		/** Hide the unnecessary Action Bar **/
		View decorView = getWindow().getDecorView();
		decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
		ActionBar actionBar = getActionBar();
		actionBar.hide();
		
		flipCamButton = (ImageButton) findViewById(R.id.flip_camera);
		flipCamButton.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				camera.stopPreview();
				camera.release();
				camera = null;
				
				if (CAMERA_IN_USE == REAR_FACING_CAMERA) {
					CAMERA_IN_USE = FRONT_FACING_CAMERA;
				} else if (CAMERA_IN_USE == FRONT_FACING_CAMERA) {
					CAMERA_IN_USE = REAR_FACING_CAMERA;				
				}
				
				framePreview.removeView(preview);
				camera = initCamera(CAMERA_IN_USE);
			}
		});
		
		helpButton = (ImageButton) findViewById(R.id.help);
		helpButton.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				createHelpMessage();
			}
		});
		
		/** Setting up the recorder and pitch processor **/
		isRecording = true;

		sendButton = (ImageButton) findViewById(R.id.send);
		sendButton.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				Intent shareIntent = new Intent();
				shareIntent.setAction(Intent.ACTION_SEND);
				shareIntent.putExtra(Intent.EXTRA_STREAM, uriToImage);
				shareIntent.setType("image/jpeg");

				sendButton.setVisibility(View.INVISIBLE);

				startActivity(shareIntent);
			}
		});
		
		int minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
		buffer = new byte[minBufSize];
		recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBufSize);

		pictureCallback = new PictureCallback() {

			 @Override
			 public void onPictureTaken(byte[] arg0, Camera arg1) {
				 Bitmap picture = BitmapFactory.decodeByteArray(arg0, 0, arg0.length);
				 Matrix matrix = new Matrix();
				 
				 /** Rotate the taken Bitmap based on whether it was taken in landscape or portrait mode **/
				 if (getResources().getConfiguration().orientation == ORIENTATION_LANDSCAPE) {
					 matrix.postRotate(0);
				 } else if ((getResources().getConfiguration().orientation == ORIENTATION_PORTRAIT) && (CAMERA_IN_USE == FRONT_FACING_CAMERA)) {
					 matrix.postRotate(270);
				 } else if ((getResources().getConfiguration().orientation == ORIENTATION_PORTRAIT) && (CAMERA_IN_USE == REAR_FACING_CAMERA)) {
					 matrix.postRotate(90);
				 }
				 
				 picture = Bitmap.createBitmap(picture, 0, 0, picture.getWidth(), picture.getHeight(), matrix, true);
				 uriToImage = getImageUri(getApplicationContext(), picture);
			 }
			 
			 public Uri getImageUri(Context inContext, Bitmap inImage) {	
				 ByteArrayOutputStream bytes = new ByteArrayOutputStream();
				 inImage.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
				 String path = Images.Media.insertImage(inContext.getContentResolver(), inImage, setDate(), null);
				 Uri imageUri = Uri.parse(path);

				 return imageUri;
			 }
			 
			 public String setDate() {
				 Calendar cal = Calendar.getInstance();
				 String day = Integer.toString(cal.get(Calendar.DAY_OF_MONTH));
				 String month = Integer.toString(cal.get(Calendar.MONTH) + 1);
				 String year = Integer.toString(cal.get(Calendar.YEAR));
				 String hour = Integer.toString(cal.get(Calendar.HOUR_OF_DAY));
				 String minute = Integer.toString(cal.get(Calendar.MINUTE));
				 String second = Integer.toString(cal.get(Calendar.SECOND));
				 
				 String finalString = "CC_IMG" + year + month + day + "_" + hour + ":" + minute + ":" + second;
				 
				 return finalString;
			 }
		};

		shutterCallback = new ShutterCallback() {
	        public void onShutter() {
	            AudioManager mgr = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
	            mgr.playSoundEffect(AudioManager.FLAG_PLAY_SOUND);
	        }
	    };
	    
	    /** Setting up the Camera **/
	    camera = initCamera(CAMERA_IN_USE);
	    if (camera != null) {
	    	listen();
	    }
	}
	
	public Camera initCamera(int camera_type) {
		Camera cam = null;
		preview = null;
		
		try {
			cam = Camera.open(camera_type);
		} catch (Exception e) {
			// Do Nothing
		}
		
		if (cam != null) {
			if (getResources().getConfiguration().orientation == ORIENTATION_LANDSCAPE) {
				cam.setDisplayOrientation(0);
			} else if (getResources().getConfiguration().orientation == ORIENTATION_PORTRAIT) {
				cam.setDisplayOrientation(90);
			}
			
			preview = new CamPreview(getApplicationContext(), cam);
			framePreview = (FrameLayout) findViewById(R.id.camera_preview);
			framePreview.addView(preview);
		}
		
		return cam;
	}
	
	public void destroyCamera() {
		if (camera != null) {
			isRecording = false;
			camera.stopPreview();
			camera.release();
			camera = null;
		}
	}
	
	public void createHelpMessage() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage("CLAP CAM INFO:\n\nClap twice (within 2 seconds of each other) to take a picture.\n\nA hand icon will pop up at the bottom center of the screen to indicate that the app has registered the clap.");
		builder.setCancelable(true);
		builder.setPositiveButton("OK", new OkOnClickListener());
		AlertDialog dialog = builder.create();
		dialog.show();
	}
		
	@Override
	public void onBackPressed() {			    
	    if (isRecording == false) {
	    	startActivity(new Intent(this, MainActivity.class));
	    }
	} 
	
	/** Set up the microphone to start recording and listening **/
	public void listen() {
		ClapOnsetHandler handler = new ClapOnsetHandler();
		recorder.startRecording();
		tarsosFormat = new be.hogent.tarsos.dsp.AudioFormat((float) SAMPLE_RATE, 16, 1, true, false);		
		detector = new PercussionOnsetDetector(SAMPLE_RATE, buffer.length/2, (OnsetHandler) handler, 80, 25);

		Thread bgListenThread = new Thread(new Runnable() {

			@Override
			public void run() {
				while (isRecording) {
					int bufResult = recorder.read(buffer, 0, buffer.length);
					AudioEvent audioEvent = new AudioEvent(tarsosFormat, bufResult);
					audioEvent.setFloatBufferWithByteBuffer(buffer);
					detector.process(audioEvent);
				}

				recorder.stop();
			}
		});

		bgListenThread.start();
	}

	public class ClapOnsetHandler implements OnsetHandler {

		private int twoClaps = 0;
		private TextView countdown = (TextView) findViewById(R.id.countdown);
		private Timer countdownTimer = new Timer();
		private Timer clapTimer = new Timer();
		private ImageView clapIndicator = (ImageView) findViewById(R.id.clap_indicator);
		
		@Override
		public void handleOnset(double time, double salience) {
			twoClaps++;
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					clapIndicator.setVisibility(View.VISIBLE);
					
				}
			});

			/** Note: Figure out a way to do this cleaner **/

			TimerTask clapperIndicator = new TimerTask() {

				@Override
				public void run() {
					runOnUiThread(new Runnable() {

						@Override
						public void run() {
							clapIndicator.setVisibility(View.INVISIBLE);
						}
					});
				}
			};
			
			clapTimer.schedule(clapperIndicator, 500);
			
			TimerTask clapperTask = new TimerTask() {

				@Override
				public void run() {
					twoClaps = 0;
				}
			};
			
			clapTimer.schedule(clapperTask, 2000);
			
			if (twoClaps == 2) {
				while (!countdown.getText().toString().equals("1")) {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {							
							countdown.setVisibility(View.VISIBLE);
						}
					});
					
					TimerTask countdownTask = new TimerTask() {
		
						@Override
						public void run() {
							runOnUiThread(new Runnable() {
	
								@Override
								public void run() {
									if (countdown.getText().toString().equals("3")) countdown.setText("2");
								}
							});
						}
					};
								
					countdownTimer.schedule(countdownTask, 1000);
					
					TimerTask countdownTask2 = new TimerTask() {

						@Override
						public void run() {
							runOnUiThread(new Runnable() {

								@Override
								public void run() {
									if (countdown.getText().toString().equals("2")) countdown.setText("1");
								}
							});
						}
					};
					countdownTimer.schedule(countdownTask2, 2000);
					
					TimerTask countdownTask3 = new TimerTask() {

						@Override
						public void run() {
							runOnUiThread(new Runnable() {

								@Override
								public void run() {
									countdown.setVisibility(View.INVISIBLE);
								}
								
							});
						}
					};
					countdownTimer.schedule(countdownTask3, 2100);
				}					
												
				camera.takePicture(shutterCallback, null, pictureCallback);
				isRecording = false;
				twoClaps = 0;

				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						sendButton.setVisibility(View.VISIBLE);
						flipCamButton.setVisibility(View.INVISIBLE);
					}
				});
			}
		}
	}
			
	/** Class that is responsible for setting up the preview for the camera **/
	public class CamPreview extends SurfaceView implements SurfaceHolder.Callback {
		private static final String TAG = "CAMERA";
		private SurfaceHolder sHolder;
		private Camera camera;

		public CamPreview(Context context, Camera camera) {
			super(context);
			this.camera = camera;
			sHolder = getHolder();
			sHolder.addCallback(this);
		}

		public void surfaceCreated(SurfaceHolder holder) {
			try {
				camera.setPreviewDisplay(holder);
				camera.startPreview();
			} catch (Exception e) {
	            Log.d(TAG, "Error setting camera preview: " + e.getMessage());
			}
		}

		@Override
		public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {	
			// TODO Auto-generated method stub
		}

		@Override
		public void surfaceDestroyed(SurfaceHolder holder) {
			// TODO Auto-generated method stub
		}
	}
	
	private final class OkOnClickListener implements DialogInterface.OnClickListener {
		public void onClick(DialogInterface dialog, int which) {
			// Clicking the button already exits the dialog
		}
	} 
}