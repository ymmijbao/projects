package edu.berkeley.cs160.ymmijbao.prog2;

import java.util.ArrayList;
import java.util.List;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemSelectedListener;

public class FilterActivity extends Activity {
	
	private Spinner colorSelector; 
	
	/** Variables for the CustomView placed here for easy access throughout Class **/
	CustomView view;
    Paint transPaint;
   	Path path1 = new Path();
    Path path2 = new Path();
    Path path3 = new Path();
    Path pathUsed;
   	Bitmap colorBitmap;
   	Bitmap grayBitmap;
   	Bitmap brightBitmap;
   	Bitmap mutableBitmap;
   	Bitmap bottomBitmap;
    Canvas overlayCanvas;
    int x;
    int y;
    int brushId = 100;
    int clearId = 101;
    int brushWidth = 10;
    int brushCounter = 0;
    boolean restore = false;
    String selected;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
                
        view = new CustomView(this);
        initSpinner();
        TextView text = new TextView(this);
        text.setText("Select Filter:");
        text.setBackgroundColor(Color.DKGRAY);
        text.setTextColor(Color.LTGRAY);
               
        /**Setting up the Layout **/
        LinearLayout linLayout = new LinearLayout(this);
        linLayout.setOrientation(LinearLayout.VERTICAL);
        linLayout.addView(text);
        linLayout.addView(colorSelector);
        linLayout.addView(view);
        
        setContentView(linLayout);
                
        colorSelector.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
				selected = colorSelector.getSelectedItem().toString();
				colorChanger(selected);				
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
				// TODO Auto-generated method stub
			}
		});
    } 
    
    public void colorChanger(String selected) {
    	Bitmap overlayBitmap;
    	
    	if (restore) {
    		mutableBitmap = colorBitmap.copy(Config.ARGB_8888, true);
    		restore = false;
    	}
		
		if (selected.equals("Grayscale to Normal")) {
			overlayBitmap = overlay(grayBitmap, mutableBitmap);
			overlayBitmap.setHasAlpha(true);
			mutableBitmap.recycle();
			mutableBitmap = overlayBitmap;
			overlayCanvas = new Canvas(mutableBitmap);
			bottomBitmap = colorBitmap;
			pathUsed.reset();
			
		} else if (selected.equals("Normal to Grayscale")) {
			overlayBitmap = overlay(colorBitmap, mutableBitmap);
			overlayBitmap.setHasAlpha(true);
			mutableBitmap.recycle();
			mutableBitmap = overlayBitmap.copy(Config.ARGB_8888, true);
			overlayCanvas = new Canvas(mutableBitmap);
			bottomBitmap = grayBitmap;
			pathUsed.reset();
			
		} else if (selected.equals("Normal to Bright")) {
			overlayBitmap = overlay(brightBitmap, mutableBitmap);
			overlayBitmap.setHasAlpha(true);
			mutableBitmap.recycle();
			mutableBitmap = overlayBitmap.copy(Config.ARGB_8888, true);
			overlayCanvas = new Canvas(mutableBitmap);
			bottomBitmap = brightBitmap;
			pathUsed.reset();
			
		} else if (selected.equals("Bright to Normal")) {
			overlayBitmap = overlay(brightBitmap, mutableBitmap);
			overlayBitmap.setHasAlpha(true);
			mutableBitmap.recycle();
			mutableBitmap = overlayBitmap.copy(Config.ARGB_8888, true);
			overlayCanvas = new Canvas(mutableBitmap);
			bottomBitmap = colorBitmap;
			pathUsed.reset();
		}
    	
    }
    
	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        menu.add(2, clearId, 0, "Clear").setIcon(R.drawable.ic_action_delete).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);        
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	int itemId = item.getItemId();
    	    	
    	switch (itemId) {
    		case R.id.choice_10:
    			brushWidth = 10;
    			pathUsed = path1;
                transPaint.setStrokeWidth(brushWidth);
    			item.setChecked(true);
        		Toast.makeText(this, "10pt brush selected", Toast.LENGTH_SHORT).show();
        		break;
    		case R.id.choice_20:
    			brushWidth = 20;
    			pathUsed = path2;
    			transPaint.setStrokeWidth(brushWidth);
    			item.setChecked(true);
        		Toast.makeText(this, "20pt brush selected", Toast.LENGTH_SHORT).show();
        		break;
    		case R.id.choice_40:
    			brushWidth = 40;
    			pathUsed = path3;
    			transPaint.setStrokeWidth(brushWidth);
    			item.setChecked(true);
        		Toast.makeText(this, "40pt brush selected", Toast.LENGTH_SHORT).show();
        		break;
    	}
    	
    	if (itemId == clearId) {
    		restore = true;
    		view.invalidate();
    		Toast.makeText(this, "Canvas cleared", Toast.LENGTH_SHORT).show();
    	}
    	
    	return true;
    }
    
    public void initSpinner() {
        colorSelector = new Spinner(this);
    	
        List<String> list = new ArrayList<String>();
        list.add("Normal to Grayscale");
        list.add("Grayscale to Normal");
        list.add("Normal to Bright");
        list.add("Bright to Normal");
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, list);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        colorSelector.setAdapter(adapter);
    }
    
    
    public class CustomView extends View {
        public CustomView(Context context) {
        	super(context);    
        	this.setBackgroundColor(Color.DKGRAY);
                
            if (android.os.Build.VERSION.SDK_INT >= 11) {
                setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            }
            
            Intent intent = getIntent();
            String imagePath = intent.getStringExtra("path");
            
            colorBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeFile(imagePath), 800, 540, false);
            grayBitmap = toGrayscale(colorBitmap);
            brightBitmap = toBrightness(colorBitmap, 2, 10);
            mutableBitmap = colorBitmap.copy(Config.ARGB_8888, true);
            colorBitmap.setHasAlpha(true);
            grayBitmap.setHasAlpha(true);
            brightBitmap.setHasAlpha(true);
            
            transPaint = new Paint();  
            transPaint.setStyle(Paint.Style.STROKE);
            transPaint.setStrokeCap(Paint.Cap.ROUND);
            transPaint.setStrokeWidth(brushWidth);
            transPaint.setColor(Color.WHITE);
            transPaint.setXfermode(new PorterDuffXfermode(Mode.CLEAR)); 
                        
            pathUsed = path1;
        }
        
        @Override
        public boolean onTouchEvent(MotionEvent event) {
        	
        	switch (event.getAction()) {
        		case MotionEvent.ACTION_DOWN:
        			x = (int) event.getX();
        			y = (int) event.getY();
        			pathUsed.moveTo(x, y);
        			invalidate();
        			break;
        		case MotionEvent.ACTION_MOVE:
        			x = (int) event.getX();
        			y = (int) event.getY();
        			pathUsed.lineTo(x, y);
        			pathUsed.moveTo(x, y);
        			invalidate();
        			break;
        		case MotionEvent.ACTION_UP:
        			x = (int) event.getX();
        			y = (int) event.getY();
        			pathUsed.moveTo(x, y);
        			invalidate();
        			break;
        	}
    			
        	return true;
        }

        @Override
        public void onDraw(Canvas canvas){
        	super.onDraw(canvas);
        	
        	canvas.drawBitmap(bottomBitmap, 0, 0, null);
        	overlayCanvas.drawPath(pathUsed, transPaint);
        	
        	if (restore) {
        		colorChanger(selected);
        	}
        	
    		canvas.drawBitmap(mutableBitmap, 0, 0, null);

        }
            
        /** Method courtesy of: StackOverFlow user Ieparlon
         * URL: http://stackoverflow.com/questions/3373860/convert-a-bitmap-to-grayscale-in-android/3391061#3391061 **/
            
        public Bitmap toGrayscale(Bitmap bmpOriginal) {        
        	int height = bmpOriginal.getHeight();
        	int width = bmpOriginal.getWidth();    

        	Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        	Canvas c = new Canvas(bmpGrayscale);
        	Paint paint = new Paint();
        	ColorMatrix cm = new ColorMatrix();
        	cm.setSaturation(0);
        	ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        	paint.setColorFilter(f);
        	c.drawBitmap(bmpOriginal, 0, 0, paint);
    		    
        	return bmpGrayscale;
        }
        
        public Bitmap toBrightness(Bitmap bmp, float contrast, float brightness) {
            ColorMatrix cm = new ColorMatrix(new float[]
                    {
                        contrast, 0, 0, 0, brightness,
                        0, contrast, 0, 0, brightness,
                        0, 0, contrast, 0, brightness,
                        0, 0, 0, 1, 0
                    });

            Bitmap bmpBright = Bitmap.createBitmap(bmp.getWidth(), bmp.getHeight(), bmp.getConfig());

            Canvas canvas = new Canvas(bmpBright);

            Paint paint = new Paint();
            paint.setColorFilter(new ColorMatrixColorFilter(cm));
            canvas.drawBitmap(bmp, 0, 0, paint);

            return bmpBright;
        }
        
    }
    
    public Bitmap overlay(Bitmap bottom, Bitmap top) {
        Bitmap bmOverlay = Bitmap.createBitmap(bottom.getWidth(), bottom.getHeight(), bottom.getConfig());
        Canvas canvas = new Canvas(bmOverlay);
        canvas.drawBitmap(bottom, new Matrix(), null);
        canvas.drawBitmap(top, 0, 0, null);
        return bmOverlay;
    }
}