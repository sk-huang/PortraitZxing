package com.zxing.sk.myzxing;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.Result;
import com.zxing.sk.myzxing.camera.CameraManager;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

public class CaptureActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    private static final String TAG = "zxing";
    private boolean hasSurface;
    private InactivityTimer inactivityTimer;
    private BeepManager beepManager;
    private AmbientLightManager ambientLightManager;

    private CameraManager cameraManager;
//    private CaptureActivityHandler handler;
//    private Result savedResultToShow;

    private ViewfinderView viewfinderView;
    private CaptureActivityHandler handler;

    private IntentSource source;
    private Result lastResult;
    private String sourceUrl;

    private Collection<BarcodeFormat> decodeFormats;
    private Map<DecodeHintType,?> decodeHints;
    private String characterSet;

    private static final long BULK_MODE_SCAN_DELAY_MS = 1000L;
    private Result savedResultToShow;

    ViewfinderView getViewfinderView() {
        return viewfinderView;
    }

    public Handler getHandler() {
        return handler;
    }

    CameraManager getCameraManager() {
        return cameraManager;
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_main);


        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.capture);

        hasSurface = false;
        inactivityTimer = new InactivityTimer(this);
        beepManager = new BeepManager(this);
        ambientLightManager = new AmbientLightManager(this);

    }


    @Override
    protected void onResume() {
        super.onResume();


        // CameraManager must be initialized here, not in onCreate(). This is necessary because we don't
        // want to open the camera driver and measure the screen size if we're going to show the help on
        // first launch. That led to bugs where the scanning rectangle was the wrong size and partially
        // off screen.
        cameraManager = new CameraManager(getApplication());

        viewfinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);
        viewfinderView.setCameraManager(cameraManager);


        handler = null;
        lastResult = null;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

//        if (prefs.getBoolean(PreferencesActivity.KEY_DISABLE_AUTO_ORIENTATION, true)) {
            setRequestedOrientation(getCurrentOrientation());
//        } else {
//            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
//        }

//        resetStatusView();


        beepManager.updatePrefs();
        ambientLightManager.start(cameraManager);

        inactivityTimer.onResume();

//        Intent intent = getIntent();


        source = IntentSource.NONE;
        sourceUrl = null;
//        scanFromWebPageManager = null;
        decodeFormats = null;
        characterSet = null;

//        if (intent != null) {
//
//            String action = intent.getAction();
//            String dataString = intent.getDataString();
//
//            if (Intents.Scan.ACTION.equals(action)) {
//
//                // Scan the formats the intent requested, and return the result to the calling activity.
//                source = IntentSource.NATIVE_APP_INTENT;
//                decodeFormats = DecodeFormatManager.parseDecodeFormats(intent);
//                decodeHints = DecodeHintManager.parseDecodeHints(intent);
//
//                if (intent.hasExtra(Intents.Scan.WIDTH) && intent.hasExtra(Intents.Scan.HEIGHT)) {
//                    int width = intent.getIntExtra(Intents.Scan.WIDTH, 0);
//                    int height = intent.getIntExtra(Intents.Scan.HEIGHT, 0);
//                    if (width > 0 && height > 0) {
//                        cameraManager.setManualFramingRect(width, height);
//                    }
//                }
//
//                if (intent.hasExtra(Intents.Scan.CAMERA_ID)) {
//                    int cameraId = intent.getIntExtra(Intents.Scan.CAMERA_ID, -1);
//                    if (cameraId >= 0) {
//                        cameraManager.setManualCameraId(cameraId);
//                    }
//                }
//
//                String customPromptMessage = intent.getStringExtra(Intents.Scan.PROMPT_MESSAGE);
//                if (customPromptMessage != null) {
//                    statusView.setText(customPromptMessage);
//                }
//
//            } else if (dataString != null &&
//                    dataString.contains("http://www.google") &&
//                    dataString.contains("/m/products/scan")) {
//
//                // Scan only products and send the result to mobile Product Search.
//                source = IntentSource.PRODUCT_SEARCH_LINK;
//                sourceUrl = dataString;
//                decodeFormats = DecodeFormatManager.PRODUCT_FORMATS;
//
//            } else if (isZXingURL(dataString)) {
//
//                // Scan formats requested in query string (all formats if none specified).
//                // If a return URL is specified, send the results there. Otherwise, handle it ourselves.
//                source = IntentSource.ZXING_LINK;
//                sourceUrl = dataString;
//                Uri inputUri = Uri.parse(dataString);
//                scanFromWebPageManager = new ScanFromWebPageManager(inputUri);
//                decodeFormats = DecodeFormatManager.parseDecodeFormats(inputUri);
//                // Allow a sub-set of the hints to be specified by the caller.
//                decodeHints = DecodeHintManager.parseDecodeHints(inputUri);
//
//            }

//            characterSet = intent.getStringExtra(Intents.Scan.CHARACTER_SET);

//        }

        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
        SurfaceHolder surfaceHolder = surfaceView.getHolder();
        if (hasSurface) {
            // The activity was paused but not stopped, so the surface still exists. Therefore
            // surfaceCreated() won't be called, so init the camera here.
            initCamera(surfaceHolder);
        } else {
            // Install the callback and wait for surfaceCreated() to init the camera.
            surfaceHolder.addCallback(this);
        }
    }


    @Override
    protected void onPause() {
        if (handler != null) {
            handler.quitSynchronously();
            handler = null;
        }
        inactivityTimer.onPause();
        ambientLightManager.stop();
        beepManager.close();
        cameraManager.closeDriver();
        //historyManager = null; // Keep for onActivityResult
        if (!hasSurface) {
            SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
            SurfaceHolder surfaceHolder = surfaceView.getHolder();
            surfaceHolder.removeCallback(this);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        inactivityTimer.shutdown();
        super.onDestroy();
    }


    private void sendReplyMessage(int id, Object arg, long delayMS) {
        if (handler != null) {
            Message message = Message.obtain(handler, id, arg);
            if (delayMS > 0L) {
                handler.sendMessageDelayed(message, delayMS);
            } else {
                handler.sendMessage(message);
            }
        }
    }


    private int getCurrentOrientation() {
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            switch (rotation) {
                case Surface.ROTATION_0:
                case Surface.ROTATION_90:
                    return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                default:
                    return ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
            }
        } else {
            switch (rotation) {
                case Surface.ROTATION_0:
                case Surface.ROTATION_270:
                    return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                default:
                    return ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
            }
        }
    }

    private void initCamera(SurfaceHolder surfaceHolder) {
        if (surfaceHolder == null) {
            throw new IllegalStateException("No SurfaceHolder provided");
        }
        if (cameraManager.isOpen()) {
            Log.w(TAG, "initCamera() while already open -- late SurfaceView callback?");
            return;
        }
        try {
            cameraManager.openDriver(surfaceHolder);
            // Creating the handler starts the preview, which can also throw a RuntimeException.
            if (handler == null) {
                handler = new CaptureActivityHandler(this, decodeFormats, decodeHints, characterSet, cameraManager);
            }
            decodeOrStoreSavedBitmap(null, null);
        } catch (IOException ioe) {
            Log.w(TAG, ioe);
//            displayFrameworkBugMessageAndExit();
            //错误信息
        } catch (RuntimeException e) {
            // Barcode Scanner has seen crashes in the wild of this variety:
            // java.?lang.?RuntimeException: Fail to connect to camera service
            Log.w(TAG, "Unexpected error initializing camera", e);
//            displayFrameworkBugMessageAndExit();
            //错误信息
        }
    }

    private void decodeOrStoreSavedBitmap(Bitmap bitmap, Result result) {
        // Bitmap isn't used yet -- will be used soon
        if (handler == null) {
            savedResultToShow = result;
        } else {
            if (result != null) {
                savedResultToShow = result;
            }
            if (savedResultToShow != null) {
                Message message = Message.obtain(handler, R.id.decode_succeeded, savedResultToShow);
                handler.sendMessage(message);
            }
            savedResultToShow = null;
        }
    }




    /**
     * A valid barcode has been found, so give an indication of success and show the results.
     *
     * @param rawResult The contents of the barcode.
     * @param scaleFactor amount by which thumbnail was scaled
     * @param barcode   A greyscale bitmap of the camera data which was decoded.
     */
    public void handleDecode(Result rawResult, Bitmap barcode, float scaleFactor) {
        System.out.println("_____________"+rawResult.getText().toString());
        inactivityTimer.onActivity();

        beepManager.playBeepSoundAndVibrate();

        lastResult = rawResult;
//        ResultHandler resultHandler = ResultHandlerFactory.makeResultHandler(this, rawResult);
//
        boolean fromLiveScan = barcode != null;
        if (fromLiveScan) {
//            historyManager.addHistoryItem(rawResult, resultHandler);
            // Then not from history, so beep/vibrate and we have an image to draw on
//            beepManager.playBeepSoundAndVibrate();
//            drawResultPoints(barcode, scaleFactor, rawResult);
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            // Wait a moment or else it will scan the same barcode continuously about 3 times
            restartPreviewAfterDelay(BULK_MODE_SCAN_DELAY_MS);
//        switch (source) {
//            case NATIVE_APP_INTENT:
//            case PRODUCT_SEARCH_LINK:
//                handleDecodeExternally(rawResult, resultHandler, barcode);
//                break;
//            case ZXING_LINK:
//                if (scanFromWebPageManager == null || !scanFromWebPageManager.isScanFromWebPage()) {
//                    handleDecodeInternally(rawResult, resultHandler, barcode);
//                } else {
//                    handleDecodeExternally(rawResult, resultHandler, barcode);
//                }
//                break;
//            case NONE:
//                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
//                if (fromLiveScan && prefs.getBoolean(PreferencesActivity.KEY_BULK_MODE, false)) {
//                    Toast.makeText(getApplicationContext(),
//                            getResources().getString(R.string.msg_bulk_mode_scanned) + " (" + rawResult.getText() + ')',
//                            Toast.LENGTH_SHORT).show();
//                    // Wait a moment or else it will scan the same barcode continuously about 3 times
//                    restartPreviewAfterDelay(BULK_MODE_SCAN_DELAY_MS);
//                } else {
//                    handleDecodeInternally(rawResult, resultHandler, barcode);
//                }
//                break;
//        }
    }

    public void restartPreviewAfterDelay(long delayMS) {
        if (handler != null) {
            handler.sendEmptyMessageDelayed(R.id.restart_preview, delayMS);
        }
//        resetStatusView();
    }

    public void drawViewfinder() {
        viewfinderView.drawViewfinder();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (holder == null) {
            Log.e(TAG, "*** WARNING *** surfaceCreated() gave us a null surface!");
        }

        if (!hasSurface) {
            hasSurface = true;
            initCamera(holder);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        hasSurface = false;
    }
}
