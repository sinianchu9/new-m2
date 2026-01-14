package com.timaimee.vpdemo.activity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;
import com.orhanobut.logger.LogLevel;
import com.orhanobut.logger.Logger;
import com.timaimee.vpdemo.R;
import com.timaimee.vpdemo.adapter.CustomLogAdapter;
import com.timaimee.vpdemo.fragment.ConnectFragment;
import com.timaimee.vpdemo.fragment.HealthDataFragment;
import com.timaimee.vpdemo.fragment.ReminderFragment;
import com.timaimee.vpdemo.fragment.SettingsFragment;
import com.veepoo.protocol.VPOperateManager;
import com.veepoo.protocol.util.VPLogger;

import java.util.ArrayList;
import java.util.List;

import tech.gujin.toast.ToastUtil;

public class MainActivity extends AppCompatActivity {
    private final static String TAG = MainActivity.class.getSimpleName();
    private BottomNavigationView mBottomNavigation;
    private Fragment mCurrentFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ToastUtil.initialize(this);
        VPOperateManager.getInstance().init(this);
        VPLogger.setDebug(true);
        initLog();

        initViews();
        checkPermission();

        // 默认显示连接页面
        if (savedInstanceState == null) {
            switchFragment(new ConnectFragment());
        }
    }

    private void initViews() {
        mBottomNavigation = findViewById(R.id.bottom_navigation);
        mBottomNavigation.setOnNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_connect) {
                switchFragment(new ConnectFragment());
                return true;
            } else if (itemId == R.id.nav_health_data) {
                switchFragment(new HealthDataFragment());
                return true;
            } else if (itemId == R.id.nav_reminder) {
                switchFragment(new ReminderFragment());
                return true;
            } else if (itemId == R.id.nav_settings) {
                switchFragment(new SettingsFragment());
                return true;
            }
            return false;
        });
    }

    private void switchFragment(Fragment fragment) {
        if (mCurrentFragment != null && mCurrentFragment.getClass().equals(fragment.getClass())) {
            return;
        }
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, fragment);
        transaction.commit();
        mCurrentFragment = fragment;
    }

    private void initLog() {
        Logger.init("timaimee")
                .methodCount(0)
                .methodOffset(0)
                .hideThreadInfo()
                .logLevel(LogLevel.FULL)
                .logAdapter(new CustomLogAdapter());
    }

    private void checkPermission() {
        if (Build.VERSION.SDK_INT < 23) {
            return;
        }

        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        Dexter.withContext(this)
                .withPermissions(permissions)
                .withListener(new MultiplePermissionsListener() {
                    @Override
                    public void onPermissionsChecked(MultiplePermissionsReport report) {
                        if (!report.areAllPermissionsGranted()) {
                            showPermissionDialog();
                        }
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(List<PermissionRequest> permissions,
                            PermissionToken token) {
                        token.continuePermissionRequest();
                    }
                }).check();
    }

    private void showPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("权限申请")
                .setMessage("应用需要蓝牙和位置权限才能正常工作，请在设置中开启。")
                .setPositiveButton("去设置", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.fromParts("package", getPackageName(), null));
                    startActivity(intent);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
