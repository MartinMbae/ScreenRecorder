package studios.sperry.screenrecorder;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaScannerConnection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.hbisoft.hbrecorder.HBRecorder;
import com.hbisoft.hbrecorder.HBRecorderListener;

import java.io.ByteArrayOutputStream;
import java.io.File;


public class MainActivity extends AppCompatActivity implements HBRecorderListener {
    //Permissions
    private static final int SCREEN_RECORD_REQUEST_CODE = 777;
    private static final int PERMISSION_REQ_ID_RECORD_AUDIO = 22;
    private static final int PERMISSION_REQ_ID_WRITE_EXTERNAL_STORAGE = PERMISSION_REQ_ID_RECORD_AUDIO + 1;
    //Reference to checkboxes and radio buttons
    boolean wasHDSelected = true;
    boolean isAudioEnabled = true;
    //Should custom settings be used
    Switch custom_settings_switch;
    private boolean hasPermissions = false;
    //Declare HBRecorder
    private HBRecorder hbRecorder;
    //Start/Stop Button
    private Button startbtn;
    //HD/SD quality
    private RadioGroup radioGroup;
    //Should record/show audio/notification
    private CheckBox recordAudioCheckBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        createFolder();
        initViews();
        mOnClickListeners();
        radioGroupCheckListener();
        recordAudioCheckBoxListener();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            //Init HBRecorder
            hbRecorder = new HBRecorder(this, this);
            hbRecorder.setOutputPath(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES) + "/HBRecorder");
            hbRecorder.setAudioBitrate(128000);
            hbRecorder.setAudioSamplingRate(44100);

            //When the user returns to the application, some UI changes might be necessary,
            //check if recording is in progress and make changes accordingly
            if (hbRecorder.isBusyRecording()) {
                startbtn.setText(R.string.stop_recording);
            }
        }

    }

    //Create Folder
    private void createFolder() {
        File f1 = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "HBRecorder");
        if (!f1.exists()) {
            if (f1.mkdirs()) {
                Log.i("Folder ", "created");
            }
        }
    }

    //Init Views
    private void initViews() {
        startbtn = findViewById(R.id.button_start);
        radioGroup = findViewById(R.id.radio_group);
        recordAudioCheckBox = findViewById(R.id.audio_check_box);
        custom_settings_switch = findViewById(R.id.custom_settings_switch);
    }

    //Start Button OnClickListener
    private void mOnClickListeners() {
        startbtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    //first check if permissions was granted
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO, PERMISSION_REQ_ID_RECORD_AUDIO) && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, PERMISSION_REQ_ID_WRITE_EXTERNAL_STORAGE)) {
                        hasPermissions = true;
                    }
                    if (hasPermissions) {
                        //check if recording is in progress
                        //and stop it if it is
                        if (hbRecorder.isBusyRecording()) {
                            hbRecorder.stopScreenRecording();
                            startbtn.setText(R.string.start_recording);
                        }
                        //else start recording
                        else {
                            startRecordingScreen();
                        }
                    }
                } else {
                    showLongToast("This library requires API 21>");
                }
            }
        });
    }

    //Check if HD/SD Video should be recorded
    private void radioGroupCheckListener() {
        radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int checkedId) {
                switch (checkedId) {
                    case R.id.hd_button:
                        //Ser HBRecorder to HD
                        wasHDSelected = true;

                        break;
                    case R.id.sd_button:
                        //Ser HBRecorder to SD
                        wasHDSelected = false;
                        break;
                }
            }
        });
    }

    //Check if audio should be recorded
    private void recordAudioCheckBoxListener() {
        recordAudioCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                //Enable/Disable audio
                isAudioEnabled = isChecked;
            }
        });
    }

    //Listener for when the recording is saved successfully
    //This will be called after the file was created
    @Override
    public void HBRecorderOnComplete() {
        startbtn.setText(R.string.start_recording);
        showLongToast("Saved Successfully");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            refreshGallery();
        }

    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void refreshGallery() {
        MediaScannerConnection.scanFile(this,
                new String[]{hbRecorder.getFilePath()}, null,
                new MediaScannerConnection.OnScanCompletedListener() {
                    public void onScanCompleted(String path, Uri uri) {
                        Log.i("ExternalStorage", "Scanned " + path + ":");
                        Log.i("ExternalStorage", "-> uri=" + uri);
                    }
                });
    }

    @Override
    public void HBRecorderOnError(int errorCode, String reason) {
        // Error 38 happens when
        // - the selected video encoder is not supported
        // - the output format is not supported
        // - if another app is using the microphone

        //It is best to use device default

        if (errorCode == 38) {
            showLongToast("Some settings are not supported by your device");
        } else {
            showLongToast("HBRecorderOnError - See Log");
            Log.e("HBRecorderOnError", reason);
        }

        startbtn.setText(R.string.start_recording);

    }

    //Start recording screen
    //It is important to call it like this
    //hbRecorder.startScreenRecording(data); should only be called in onActivityResult
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void startRecordingScreen() {

        quickSettings();
        MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        Intent permissionIntent = mediaProjectionManager != null ? mediaProjectionManager.createScreenCaptureIntent() : null;
        startActivityForResult(permissionIntent, SCREEN_RECORD_REQUEST_CODE);
        startbtn.setText(R.string.stop_recording);
    }


    //Get/Set the selected settings
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void quickSettings() {
        hbRecorder.recordHDVideo(wasHDSelected);
        hbRecorder.isAudioEnabled(isAudioEnabled);
        //Customise Notification
        hbRecorder.setNotificationSmallIcon(drawable2ByteArray(R.drawable.icon));
        hbRecorder.setNotificationTitle("Recording your screen");
        hbRecorder.setNotificationDescription("Drag down to stop the recording");
        if (doesSupportEncoder("h264")) {
            hbRecorder.setVideoEncoder("H264");
        } else {
            hbRecorder.setVideoEncoder("DEFAULT");
        }
        hbRecorder.setVideoBitrate(12000000); //def
    }


    private boolean doesSupportEncoder(String encoder) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (codecInfo.isEncoder()) {
                if (codecInfo.getName() != null) {
                    if (codecInfo.getName().contains(encoder)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    //Check if permissions was granted
    private boolean checkSelfPermission(String permission, int requestCode) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{permission}, requestCode);
            return false;
        }
        return true;
    }

    //Handle permissions
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQ_ID_RECORD_AUDIO:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, PERMISSION_REQ_ID_WRITE_EXTERNAL_STORAGE);
                } else {
                    hasPermissions = false;
                    showLongToast("No permission for " + Manifest.permission.RECORD_AUDIO);
                }
                break;
            case PERMISSION_REQ_ID_WRITE_EXTERNAL_STORAGE:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    hasPermissions = true;
                    //Permissions was provided
                    //Start screen recording
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        startRecordingScreen();
                    }
                } else {
                    hasPermissions = false;
                    showLongToast("No permission for " + Manifest.permission.WRITE_EXTERNAL_STORAGE);
                }
                break;
            default:
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (requestCode == SCREEN_RECORD_REQUEST_CODE) {
                if (resultCode == RESULT_OK) {
                    //Start screen recording
                    hbRecorder.startScreenRecording(data, resultCode, this);

                }
            }
        }
    }

    //Show Toast
    private void showLongToast(final String msg) {
        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }

    //drawable to byte[]
    private byte[] drawable2ByteArray(@DrawableRes int drawableId) {
        Bitmap icon = BitmapFactory.decodeResource(getResources(), drawableId);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        icon.compress(Bitmap.CompressFormat.PNG, 100, stream);
        return stream.toByteArray();
    }
}
