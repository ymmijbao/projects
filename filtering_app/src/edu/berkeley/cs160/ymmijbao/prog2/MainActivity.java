package edu.berkeley.cs160.ymmijbao.prog2;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

/** Following code courtesy of http://viralpatel.net/blogs/pick-image-from-galary-android-app/ **/

public class MainActivity extends Activity {

	private static int RESULT_LOAD_IMAGE = 1;
	String picturePath;
	ImageView imageView;
	 
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
                
        RelativeLayout layout= (RelativeLayout) findViewById(R.id.homepage);
        layout.setBackgroundColor(Color.DKGRAY);
         
        Button buttonLoadImage = (Button) findViewById(R.id.buttonLoadPicture);
        buttonLoadImage.setOnClickListener(new View.OnClickListener() {
             
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(i, RESULT_LOAD_IMAGE);                
            }
        });
        
        Button buttonConfirm = (Button) findViewById(R.id.buttonConfirmPicture);
        buttonConfirm.setOnClickListener(new View.OnClickListener() {
        	
        	@Override
            public void onClick(View v) {
        		
        		if (imageView == null) {
            		Toast.makeText(getApplicationContext(), "Please first select an image", Toast.LENGTH_SHORT).show();
        		} else {
        		
	                Intent i = new Intent(MainActivity.this, FilterActivity.class);
	                i.putExtra("path", picturePath);
	                startActivity(i);
	                finish();
        		}
            }           
        });
    }
     
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
                 
        if (requestCode == RESULT_LOAD_IMAGE && resultCode == RESULT_OK && data != null) {
        	
            Uri selectedImage = data.getData();
            String[] filePathColumn = { MediaStore.Images.Media.DATA };
 
            Cursor cursor = getContentResolver().query(selectedImage, filePathColumn, null, null, null);
            cursor.moveToFirst();
 
            int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
            picturePath = cursor.getString(columnIndex);
            cursor.close();
             
            imageView = (ImageView) findViewById(R.id.imgView);
            imageView.setImageBitmap(BitmapFactory.decodeFile(picturePath));
        }
    }
}