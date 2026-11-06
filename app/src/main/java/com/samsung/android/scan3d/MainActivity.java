package com.samsung.android.scan3d;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.permissionx.guolindev.PermissionX;
import com.samsung.android.scan3d.camera.Camera2API;
import com.samsung.android.scan3d.camera.Camera2APIManager;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private Camera2API camera2APIManager;

    private FloatingActionButton btnCapture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        btnCapture = findViewById(R.id.btnCapture);
        btnCapture.setOnClickListener(view -> takePicture());
        btnCapture.setEnabled(true);

        TextureView textureView = findViewById(R.id.textureView);
        camera2APIManager = new Camera2APIManager(this, textureView);

        // Select save directory intent
        ActivityResultLauncher<Intent> mGetContent = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        Uri uri = data.getData();

                        // Save uri to shared preferences
                        SharedPreferences sharedPreferences = getSharedPreferences("app_prefs", MODE_PRIVATE);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString("picked_dir_uri", uri.toString());
                        editor.apply();
                        getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                        DocumentFile pickedDir = DocumentFile.fromTreeUri(MainActivity.this, uri);
                        camera2APIManager.setSaveDir(pickedDir);
                    }
                });
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);


        // Request permissions
        PermissionX.init(MainActivity.this)
                .permissions(Constants.PERMISSIONS)
                .request((allGranted, grantedList, deniedList) -> {

                    if (allGranted) {

                        // Get save directory from shared preferences
                        SharedPreferences sharedPreferences = getSharedPreferences("app_prefs", MODE_PRIVATE);
                        String uriString = sharedPreferences.getString("picked_dir_uri", null);
                        if (uriString != null) {
                            Uri uri = Uri.parse(uriString);
                            DocumentFile pickedDir = DocumentFile.fromTreeUri(this, uri);
                            if (pickedDir != null && pickedDir.isDirectory() && pickedDir.canWrite())
                                camera2APIManager.setSaveDir(pickedDir);
                            else {
                                mGetContent.launch(intent);
                            }
                        } else {
                            // Start intent to select save directory
                            mGetContent.launch(intent);
                        }
                        camera2APIManager.init();
                    } else {
                        Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
                        System.exit(0);
                    }
                });


    }


    protected void takePicture() {
        MainActivity.this.runOnUiThread(() -> {
            btnCapture.setEnabled(false);
        });
        camera2APIManager.captureBurst((success, message) -> {

            if (!success) {
                MainActivity.this.runOnUiThread(() -> {
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                    camera2APIManager.close();
                    camera2APIManager.init();
                });

            } else {

                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setMessage("Deseja salvar captura?").setCancelable(false)
                        .setPositiveButton("Sim", (dialog, id) -> {
                            camera2APIManager.save();
                        })
                        .setNegativeButton("Não", (dialog, id) -> {
                            camera2APIManager.close();
                            camera2APIManager.init();
                        });
                builder.create().show();
            }
            btnCapture.setEnabled(true);
        });
    }
    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
        if (PermissionX.isGranted(this, Manifest.permission.CAMERA)) {
            camera2APIManager.init();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause");
        camera2APIManager.close();
    }
}