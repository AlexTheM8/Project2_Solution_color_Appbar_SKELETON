package com.example.solution_color;


import android.Manifest;
import android.content.Intent;

import androidx.preference.PreferenceManager;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;

import android.provider.MediaStore;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.library.bitmap_utilities.BitMap_Helpers;

import java.io.File;
import java.io.IOException;

import static androidx.core.content.FileProvider.getUriForFile;

public class MainActivity extends AppCompatActivity implements OnSharedPreferenceChangeListener {

    //these are constants and objects that I used, use them if you wish
    private static final String DEBUG_TAG = "CartoonActivity";
    private static final String ORIGINAL_FILE = "origfile.png";
    private static final String PROCESSED_FILE = "procfile.png";

    private static final int TAKE_PICTURE = 1;
//    private static final double SCALE_FROM_0_TO_255 = 2.55;
    private static final int DEFAULT_COLOR_PERCENT = 3;
    private static final int DEFAULT_BW_PERCENT = 15;

    //preferences
    private SharedPreferences myPreferences;
    private int saturation = DEFAULT_COLOR_PERCENT;
    private int bwPercent = DEFAULT_BW_PERCENT;
    private String shareSubject;
    private String shareText;

    //where images go
    private String originalImagePath;   //where orig image is
    private String processedImagePath;  //where processed image is
    private Uri outputFileUri;          //tells camera app where to store image

    //used to measure screen size
    int screenheight;
    int screenwidth;

    private ImageView myImage;

    //these guys will hog space
    Bitmap bmpOriginal;                 //original image
    Bitmap bmpThresholded;              //the black and white version of original image
    Bitmap bmpThresholdedColor;         //the colorized version of the black and white image

    private static final int PERMISSION_REQUEST_STARTUP = 0;
    private static final String[] PERMISSIONS = new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar myToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);

        //dont display these
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        FloatingActionButton fab = findViewById(R.id.buttonTakePicture);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!verifyPermissions())
                    return;
                doTakePicture();

            }
        });

        //get the default image
        myImage = findViewById(R.id.imageView1);

        myPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        myPreferences.registerOnSharedPreferenceChangeListener(this);
        getPrefValues(myPreferences);

        // Fetch screen height and width,
        DisplayMetrics metrics = this.getResources().getDisplayMetrics();
        screenheight = metrics.heightPixels;
        screenwidth = metrics.widthPixels;

        setUpFileSystem();
    }

    private void setImage() {
        //prefer to display processed image if available
        bmpThresholded = Camera_Helpers.loadAndScaleImage(processedImagePath, screenheight, screenwidth);
        if (bmpThresholded != null) {
            myImage.setImageBitmap(bmpThresholded);
            Log.d(DEBUG_TAG, "setImage: myImage.setImageBitmap(bmpThresholded) set");
            return;
        }

        //otherwise fall back to unprocessd photo
        bmpOriginal = Camera_Helpers.loadAndScaleImage(originalImagePath, screenheight, screenwidth);
        if (bmpOriginal != null) {
            myImage.setImageBitmap(bmpOriginal);
            Log.d(DEBUG_TAG, "setImage: myImage.setImageBitmap(bmpOriginal) set");
            return;
        }

        //worst case get from default image
        //save this for restoring
        bmpOriginal = BitMap_Helpers.copyBitmap(myImage.getDrawable());
        Log.d(DEBUG_TAG, "setImage: bmpOriginal copied");
    }

    private void getPrefValues(SharedPreferences settings) {
        if (settings == null)
            settings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        shareSubject = settings.getString(getString(R.string.share_subject_key), getString(R.string.shareTitle));
        shareText = settings.getString(getString(R.string.share_text_key), getString(R.string.sharemessage));
        bwPercent = settings.getInt(getString(R.string.sketchiness_seekbar_key), DEFAULT_BW_PERCENT);
        saturation = settings.getInt(getString(R.string.saturation_seekbar_key), DEFAULT_COLOR_PERCENT);
        SharedPreferences.Editor edit = myPreferences.edit();
        edit.putString(getString(R.string.share_subject_key), shareSubject);
        edit.putString(getString(R.string.share_text_key), shareText);
        edit.putInt(getString(R.string.sketchiness_seekbar_key), bwPercent);
        edit.putInt(getString(R.string.saturation_seekbar_key), saturation);
        edit.commit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }


    private void setUpFileSystem() {
        verifyPermissions();
        //get some paths
        // Create the File where the photo should go
        File photoFile = createImageFile(ORIGINAL_FILE);
        originalImagePath = photoFile.getAbsolutePath();

        File processedfile = createImageFile(PROCESSED_FILE);
        processedImagePath = processedfile.getAbsolutePath();

        //worst case get from default image
        //save this for restoring
        if (bmpOriginal == null)
            bmpOriginal = BitMap_Helpers.copyBitmap(myImage.getDrawable());

        setImage();
    }

    private File createImageFile(final String fn) {
        try {
            File temp = new File(getExternalMediaDirs()[0], fn);
            temp.createNewFile();
            return temp;
        } catch (IOException e) {
            Log.e(DEBUG_TAG, "createImageFile: IOException " + fn);
            return null;
        }
    }

    //DUMP for students
    ////////////////////////////////////////////////////////////////////////////////////////////////////////
    // permissions

    /***
     * callback from requestPermissions
     * @param permsRequestCode  user defined code passed to requestpermissions used to identify what callback is coming in
     * @param permissions       list of permissions requested
     * @param grantResults      //results of those requests
     */
    @Override
    public void onRequestPermissionsResult(int permsRequestCode, String[] permissions, int[] grantResults) {
        //STARTUP PERMISSIONS
        if (permsRequestCode == PERMISSION_REQUEST_STARTUP) {
            for (String perm : permissions) {
                if (perm.equals(Manifest.permission.CAMERA)) {
                    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        Snackbar.make(findViewById(android.R.id.content), R.string.camera_permission_granted,
                                Snackbar.LENGTH_SHORT)
                                .show();
                    } else {
                        Snackbar.make(findViewById(android.R.id.content), R.string.camera_permission_denied,
                                Snackbar.LENGTH_SHORT)
                                .show();
                    }
                }
                if (perm.equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    if (grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                        Snackbar.make(findViewById(android.R.id.content), R.string.write_permission_granted,
                                Snackbar.LENGTH_SHORT)
                                .show();
                    } else {
                        Snackbar.make(findViewById(android.R.id.content), R.string.write_permission_denied,
                                Snackbar.LENGTH_SHORT)
                                .show();
                    }
                }
                if (perm.equals(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    if (grantResults[2] == PackageManager.PERMISSION_GRANTED) {
                        Snackbar.make(findViewById(android.R.id.content), R.string.read_permission_granted,
                                Snackbar.LENGTH_SHORT)
                                .show();
                    } else {
                        Snackbar.make(findViewById(android.R.id.content), R.string.read_permission_denied,
                                Snackbar.LENGTH_SHORT)
                                .show();
                    }
                }
            }
        }

    }

    //DUMP for students

    /**
     * Verify that the specific list of permissions requested have been granted, otherwise ask for
     * these permissions.  Note this is coarse in that I assume I need them all
     */
    private boolean verifyPermissions() {
        for (String perm : PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_REQUEST_STARTUP);
                return false;
            }
        }
        return true;
    }

    //take a picture and store it on external storage
    public void doTakePicture() {
        if (!verifyPermissions())
            return;

        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = createImageFile(ORIGINAL_FILE);

            if (photoFile != null) {
                outputFileUri = getUriForFile(this, "com.example.solution_color.fileprovider", photoFile);
                originalImagePath = photoFile.getAbsolutePath();
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri);
                startActivityForResult(cameraIntent, TAKE_PICTURE);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == TAKE_PICTURE && resultCode == RESULT_OK) {
            bmpOriginal = Camera_Helpers.loadAndScaleImage(originalImagePath, screenheight, screenwidth);
            myImage.setImageBitmap(bmpOriginal);
            scanSavedMediaFile(originalImagePath);
        }
    }

    /**
     * delete original and processed images, then rescan media paths to pick up that they are gone.
     */
    private void doReset() {
        //do we have needed permissions?
        if (!verifyPermissions())
            return;
        //delete the files
        Camera_Helpers.delSavedImage(originalImagePath);
        Camera_Helpers.delSavedImage(processedImagePath);
        bmpThresholded = null;
        bmpOriginal = null;

        myImage.setImageResource(R.drawable.gutters);
        myImage.setScaleType(ImageView.ScaleType.FIT_CENTER);//what the hell? why both
        myImage.setScaleType(ImageView.ScaleType.FIT_XY);

        //worst case get from default image
        //save this for restoring
        bmpOriginal = BitMap_Helpers.copyBitmap(myImage.getDrawable());

        scanSavedMediaFile(originalImagePath);
        scanSavedMediaFile(processedImagePath);
    }

    public void doSketch() {
        //do we have needed permissions?
        if (!verifyPermissions())
            return;

        //sketchify the image
        if (bmpOriginal == null) {
            Log.e(DEBUG_TAG, "doSketch: bmpOriginal = null");
            return;
        }
        bmpThresholded = BitMap_Helpers.thresholdBmp(bmpOriginal, bwPercent);

        //set image
        myImage.setImageBitmap(bmpThresholded);

        //save to file for possible email
        Camera_Helpers.saveProcessedImage(bmpThresholded, processedImagePath);
        scanSavedMediaFile(processedImagePath);
    }

    public void doColorize() {
        //do we have needed permissions?
        if (!verifyPermissions())
            return;

        //colorize the image
        if (bmpOriginal == null) {
            Log.e(DEBUG_TAG, "doColorize: bmpOriginal = null");
            return;
        }
        //if not thresholded yet then do nothing
        if (bmpThresholded == null) {
            Log.e(DEBUG_TAG, "doColorize: bmpThresholded not thresholded yet");
            return;
        }

        //otherwise color the bitmap
        bmpThresholdedColor = BitMap_Helpers.colorBmp(bmpOriginal, saturation);

        //takes the thresholded image and overlays it over the color one
        //so edges are well defined
        BitMap_Helpers.merge(bmpThresholdedColor, bmpThresholded);

        //set background to new image
        myImage.setImageBitmap(bmpThresholdedColor);

        //save to file for possible email
        Camera_Helpers.saveProcessedImage(bmpThresholdedColor, processedImagePath);
        scanSavedMediaFile(processedImagePath);
    }

    public void doShare() {
        //do we have needed permissions?
        if (!verifyPermissions())
            return;
        //TODO Case of no processed image
        Intent share = new Intent(Intent.ACTION_SEND);
        File photo = new File(processedImagePath);

        share.putExtra(Intent.EXTRA_SUBJECT, shareSubject);
        share.putExtra(Intent.EXTRA_TEXT, shareText);

        share.putExtra(Intent.EXTRA_STREAM, getUriForFile(MainActivity.this, "com.example.solution_color.fileprovider", photo));
        share.setType("image/png");
        startActivity(Intent.createChooser(share, null));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch (id) {
            case R.id.action_reset:
                doReset();
                break;
            case R.id.action_sketch:
                doSketch();
                break;
            case R.id.action_colorize:
                doColorize();
                break;
            case R.id.action_share:
                doShare();
                break;
            case R.id.action_settings:
                Intent settings = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(settings);
                break;
        }


        return true;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences pref, String key) {
        getPrefValues(pref);
    }

    /**
     * Notifies the OS to index the new image, so it shows up in Gallery.
     * see https://www.programcreek.com/java-api-examples/index.php?api=android.media.MediaScannerConnection
     */
    private void scanSavedMediaFile(final String path) {
        // silly array hack so closure can reference scannerConnection[0] before it's created
        final MediaScannerConnection[] scannerConnection = new MediaScannerConnection[1];
        try {
            MediaScannerConnection.MediaScannerConnectionClient scannerClient = new MediaScannerConnection.MediaScannerConnectionClient() {
                public void onMediaScannerConnected() {
                    scannerConnection[0].scanFile(path, null);
                }

                @Override
                public void onScanCompleted(String path, Uri uri) {

                }

            };
            scannerConnection[0] = new MediaScannerConnection(this, scannerClient);
            scannerConnection[0].connect();
        } catch (Exception ignored) {
        }
    }
}

