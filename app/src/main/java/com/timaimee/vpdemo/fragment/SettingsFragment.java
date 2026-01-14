package com.timaimee.vpdemo.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.inuker.bluetooth.library.Code;
import com.timaimee.vpdemo.R;
import com.timaimee.vpdemo.activity.CustomTextAlarmActivity;
import com.timaimee.vpdemo.activity.ScreenLightTimeActivity;
import com.timaimee.vpdemo.activity.SwitchView;
import com.timaimee.vpdemo.activity.HealthSettingsActivity;
import com.timaimee.vpdemo.activity.FindDeviceActivity;
import com.timaimee.vpdemo.activity.EventRemindActivity;
import com.veepoo.protocol.VPOperateManager;
import com.veepoo.protocol.listener.base.IBleWriteResponse;
import com.veepoo.protocol.listener.data.IScreenStyleListener;
import com.veepoo.protocol.listener.data.IZT163DeviceAlwaysOffScreenOptListener;
import com.veepoo.protocol.model.datas.ScreenStyleData;
import com.veepoo.protocol.model.enums.EUIFromType;

public class SettingsFragment extends Fragment implements IZT163DeviceAlwaysOffScreenOptListener {
    private SwitchView mAlwaysOffSwitch;
    private TextView mDirectStatus, mConnectionHint;
    private LinearLayout mItemScreenTime, mItemAlarm, mItemWatchFace, mItemFactoryReset, mItemReset;
    private LinearLayout mItemHealthSettings, mItemFindDevice, mItemEventRemind;
    private String mConnectedMac;
    private boolean mIsConnected = false;
    private boolean mIsConnectListenerRegistered = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);
        initViews(view);
        refreshConnectionState();
        readAlwaysOffState();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshConnectionState();
    }

    @Override
    public void onDestroy() {
        if (mConnectedMac != null && mIsConnectListenerRegistered) {
            VPOperateManager.getInstance().unregisterConnectStatusListener(mConnectedMac, mBleConnectStatusListener);
            mIsConnectListenerRegistered = false;
        }
        super.onDestroy();
    }

    private void initViews(View view) {
        mAlwaysOffSwitch = view.findViewById(R.id.switch_always_off);
        mDirectStatus = view.findViewById(R.id.tv_direct_status);
        mConnectionHint = view.findViewById(R.id.tv_connection_hint);
        mItemScreenTime = view.findViewById(R.id.item_screen_time);
        mItemAlarm = view.findViewById(R.id.item_alarm);
        mItemWatchFace = view.findViewById(R.id.item_watch_face);
        mItemFactoryReset = view.findViewById(R.id.item_factory_reset);
        mItemReset = view.findViewById(R.id.item_reset);
        mItemHealthSettings = view.findViewById(R.id.item_health_settings);
        mItemFindDevice = view.findViewById(R.id.item_find_device);
        mItemEventRemind = view.findViewById(R.id.item_event_remind);

        mAlwaysOffSwitch.setOnStateChangedListener(new SwitchView.OnStateChangedListener() {
            @Override
            public void toggleToOn(SwitchView view) {
                setAlwaysOff(true);
            }

            @Override
            public void toggleToOff(SwitchView view) {
                setAlwaysOff(false);
            }
        });

        mItemWatchFace.setOnClickListener(v -> readWatchFace());
        mItemScreenTime.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(getActivity(), ScreenLightTimeActivity.class);
            intent.putExtra("deviceaddress", mConnectedMac);
            startActivity(intent);
        });
        mItemAlarm.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(getActivity(), CustomTextAlarmActivity.class);
            intent.putExtra("deviceaddress", mConnectedMac);
            startActivity(intent);
        });

        mItemHealthSettings.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(getActivity(), HealthSettingsActivity.class);
            intent.putExtra("deviceaddress", mConnectedMac);
            startActivity(intent);
        });
        mItemFindDevice.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(getActivity(), FindDeviceActivity.class);
            intent.putExtra("deviceaddress", mConnectedMac);
            startActivity(intent);
        });
        mItemEventRemind.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(getActivity(), EventRemindActivity.class);
            intent.putExtra("deviceaddress", mConnectedMac);
            startActivity(intent);
        });

        mItemFactoryReset.setOnClickListener(v -> showConfirmDialog("恢复出厂设置", "确定要恢复出厂设置吗？", () -> {
            VPOperateManager.getInstance().clearDeviceData(code -> {
            });
            Toast.makeText(getContext(), "已发送指令", Toast.LENGTH_SHORT).show();
        }));
        mItemReset.setOnClickListener(v -> showConfirmDialog("复位", "确定要复位设备吗？", () -> {
            VPOperateManager.getInstance().resetDeviceData(code -> {
            });
            Toast.makeText(getContext(), "已发送指令", Toast.LENGTH_SHORT).show();
        }));
    }

    private void refreshConnectionState() {
        if (getActivity() == null)
            return;
        mConnectedMac = getActivity().getSharedPreferences("ble_prefs", Context.MODE_PRIVATE)
                .getString("connected_mac", null);

        if (mConnectedMac != null) {
            int status = VPOperateManager.getInstance().getConnectStatus(mConnectedMac);
            mIsConnected = status == com.inuker.bluetooth.library.Constants.STATUS_CONNECTED
                    || status == com.inuker.bluetooth.library.Constants.STATUS_DEVICE_CONNECTED;
            if (!mIsConnectListenerRegistered) {
                VPOperateManager.getInstance().registerConnectStatusListener(mConnectedMac, mBleConnectStatusListener);
                mIsConnectListenerRegistered = true;
            }
        } else {
            mIsConnected = false;
        }
        updateConnectionUI();
    }

    private void updateConnectionUI() {
        if (getActivity() == null)
            return;
        getActivity().runOnUiThread(() -> {
            mConnectionHint.setText(mIsConnected ? "已连接设备" : "未连接设备");
            mAlwaysOffSwitch.setEnabled(mIsConnected);
            mItemScreenTime.setEnabled(mIsConnected);
            mItemAlarm.setEnabled(mIsConnected);
            mItemWatchFace.setEnabled(mIsConnected);
            mItemFactoryReset.setEnabled(mIsConnected);
            mItemReset.setEnabled(mIsConnected);
            mItemHealthSettings.setEnabled(mIsConnected);
            mItemFindDevice.setEnabled(mIsConnected);
            mItemEventRemind.setEnabled(mIsConnected);

            float alpha = mIsConnected ? 1.0f : 0.5f;
            mItemScreenTime.setAlpha(alpha);
            mItemAlarm.setAlpha(alpha);
            mItemWatchFace.setAlpha(alpha);
            mItemFactoryReset.setAlpha(alpha);
            mItemReset.setAlpha(alpha);
            mItemHealthSettings.setAlpha(alpha);
            mItemFindDevice.setAlpha(alpha);
            mItemEventRemind.setAlpha(alpha);
        });
    }

    private final com.veepoo.protocol.listener.base.IABleConnectStatusListener mBleConnectStatusListener = new com.veepoo.protocol.listener.base.IABleConnectStatusListener() {
        @Override
        public void onConnectStatusChanged(String mac, int status) {
            mIsConnected = status == com.inuker.bluetooth.library.Constants.STATUS_CONNECTED
                    || status == com.inuker.bluetooth.library.Constants.STATUS_DEVICE_CONNECTED;
            updateConnectionUI();
        }
    };

    private void setAlwaysOff(boolean open) {
        VPOperateManager.getInstance().setZT163DeviceAlwaysOffScreen(open, code -> {
            if (code != Code.REQUEST_SUCCESS) {
                getActivity().runOnUiThread(
                        () -> Toast.makeText(getContext(), "指令发送失败 (Code: " + code + ")", Toast.LENGTH_SHORT).show());
            }
        }, this);
    }

    private void readAlwaysOffState() {
        VPOperateManager.getInstance().readZT163DeviceAlwaysOffScreen(code -> {
            if (code != Code.REQUEST_SUCCESS) {
                // 读取失败通常不弹窗，避免干扰，但在日志中记录或在状态栏显示
            }
        }, this);
    }

    private void readWatchFace() {
        VPOperateManager.getInstance().readScreenStyle(code -> {
            if (code != Code.REQUEST_SUCCESS) {
                getActivity().runOnUiThread(
                        () -> Toast.makeText(getContext(), "读取表盘失败 (Code: " + code + ")", Toast.LENGTH_SHORT).show());
            }
        }, screenStyleData -> {
            showWatchFaceDialog(screenStyleData);
        });
    }

    private void showWatchFaceDialog(ScreenStyleData currentData) {
        String[] items = { "表盘 0", "表盘 1", "表盘 2", "表盘 3", "表盘 4", "表盘 5", "表盘 6" };
        new AlertDialog.Builder(getContext())
                .setTitle("选择表盘 (当前: " + currentData.getScreenIndex() + ")")
                .setItems(items, (dialog, which) -> {
                    VPOperateManager.getInstance().settingScreenStyle(code -> {
                    }, screenStyleData -> {
                        Toast.makeText(getContext(), "设置成功", Toast.LENGTH_SHORT).show();
                    }, which, EUIFromType.DEFAULT);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showConfirmDialog(String title, String message, Runnable onConfirm) {
        new AlertDialog.Builder(getContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("确定", (dialog, which) -> onConfirm.run())
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    public void onZT163DeviceAlwaysOffScreenSettingSuccess(boolean isOpen) {
        if (getActivity() == null)
            return;
        getActivity().runOnUiThread(() -> {
            mAlwaysOffSwitch.setOpened(isOpen);
            mDirectStatus.setText("常灭屏：" + (isOpen ? "已开启" : "已关闭"));
        });
    }

    @Override
    public void onZT163DeviceAlwaysOffScreenSettingFailed() {
        if (getActivity() == null)
            return;
        getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "设置失败", Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onZT163DeviceAlwaysOffScreenReport(boolean isOpen) {
        if (getActivity() == null)
            return;
        getActivity().runOnUiThread(() -> {
            mAlwaysOffSwitch.setOpened(isOpen);
            mDirectStatus.setText("常灭屏：" + (isOpen ? "已开启" : "已关闭"));
        });
    }

    @Override
    public void onFunctionNotSupport() {
        if (getActivity() == null)
            return;
        getActivity().runOnUiThread(() -> mDirectStatus.setText("常灭屏：设备不支持"));
    }
}
