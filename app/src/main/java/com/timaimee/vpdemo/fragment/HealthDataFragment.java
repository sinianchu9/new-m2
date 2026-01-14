package com.timaimee.vpdemo.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.inuker.bluetooth.library.Code;
import com.inuker.bluetooth.library.utils.BluetoothUtils;
import com.timaimee.vpdemo.R;
import com.veepoo.protocol.VPOperateManager;
import com.veepoo.protocol.listener.base.IBleWriteResponse;
import com.veepoo.protocol.listener.data.IAllHealthDataListener;
import com.veepoo.protocol.model.datas.OriginData;
import com.veepoo.protocol.model.datas.OriginData3;
import com.veepoo.protocol.model.datas.OriginHalfHourData;
import com.veepoo.protocol.model.datas.SleepData;
import com.veepoo.protocol.model.datas.SportData;
import com.veepoo.protocol.listener.data.ISportDataListener;

import java.util.List;

public class HealthDataFragment extends Fragment {
    private TextView tvStepCount, tvCalories, tvDistance;
    private TextView tvSleepDuration, tvDeepSleep, tvLightSleep;
    private TextView tvHeartRate, tvSpo2, tvBloodPressure, tvBloodGlucose;
    private TextView tvSyncStatus;
    private Button btnSync;
    private String mConnectedMac;
    private boolean mIsConnected = false;
    private boolean mIsConnectListenerRegistered = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_health_data, container, false);
        initViews(view);
        refreshConnectionState();
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
        tvStepCount = view.findViewById(R.id.tv_step_count);
        tvCalories = view.findViewById(R.id.tv_calories);
        tvDistance = view.findViewById(R.id.tv_distance);
        tvSleepDuration = view.findViewById(R.id.tv_sleep_duration);
        tvDeepSleep = view.findViewById(R.id.tv_deep_sleep);
        tvLightSleep = view.findViewById(R.id.tv_light_sleep);
        tvHeartRate = view.findViewById(R.id.tv_heart_rate);
        tvSpo2 = view.findViewById(R.id.tv_spo2);
        tvBloodPressure = view.findViewById(R.id.tv_blood_pressure);
        tvBloodGlucose = view.findViewById(R.id.tv_blood_glucose);
        tvSyncStatus = view.findViewById(R.id.tv_sync_status);
        btnSync = view.findViewById(R.id.btn_sync);

        // 初始化显示为 "—"
        tvStepCount.setText("—");
        tvCalories.setText("—");
        tvDistance.setText("—");
        tvSleepDuration.setText("—");
        tvDeepSleep.setText("—");
        tvLightSleep.setText("—");
        tvHeartRate.setText("—");
        tvSpo2.setText("—");
        tvBloodPressure.setText("—");
        tvBloodGlucose.setText("—");

        btnSync.setOnClickListener(v -> startSync());
    }

    private void startSync() {
        if (!mIsConnected) {
            Toast.makeText(getContext(), "设备未连接", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!BluetoothUtils.isBluetoothEnabled()) {
            Toast.makeText(getContext(), "蓝牙未开启", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSync.setEnabled(false);
        btnSync.setText("同步中...");
        tvSyncStatus.setText("正在同步当前计步...");

        // 1. 先读取当前计步
        VPOperateManager.getInstance().readSportStep(new IBleWriteResponse() {
            @Override
            public void onResponse(int code) {
            }
        }, new ISportDataListener() {
            @Override
            public void onSportDataChange(SportData sportData) {
                if (getActivity() != null && sportData != null) {
                    getActivity().runOnUiThread(() -> {
                        tvStepCount.setText(String.valueOf(sportData.getStep()));
                        tvCalories.setText(String.format("%.1f", sportData.getKcal()));
                        tvDistance.setText(String.format("%.2f", sportData.getDis()));
                    });
                }
                // 2. 读取当前计步成功后，再读取历史健康数据
                readHistoryHealthData();
            }
        });
    }

    private void readHistoryHealthData() {
        int watchDataDay = getActivity().getSharedPreferences("ble_prefs", android.content.Context.MODE_PRIVATE)
                .getInt("watch_data_day", 3);

        tvSyncStatus.setText("正在同步历史健康数据...");

        VPOperateManager.getInstance().readAllHealthData(new IAllHealthDataListener() {
            @Override
            public void onProgress(float progress) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        tvSyncStatus.setText(String.format("正在同步历史数据...%.0f%%", progress * 100));
                    });
                }
            }

            @Override
            public void onOringinFiveMinuteDataChange(OriginData originData) {
                if (getActivity() != null && originData != null) {
                    getActivity().runOnUiThread(() -> {
                        if (originData.getRateValue() > 0) {
                            tvHeartRate.setText(String.valueOf(originData.getRateValue()));
                        }

                        // 处理血氧数据 (OriginData3 才有血氧数组)
                        if (originData instanceof OriginData3) {
                            OriginData3 data3 = (OriginData3) originData;
                            if (data3.getOxygens() != null && data3.getOxygens().length > 0) {
                                // 取数组中最后一个非零值作为当前显示
                                int lastOxygen = 0;
                                for (int oxygen : data3.getOxygens()) {
                                    if (oxygen > 0) {
                                        lastOxygen = oxygen;
                                    }
                                }
                                if (lastOxygen > 0) {
                                    tvSpo2.setText(lastOxygen + "%");
                                }
                            }
                        }

                        if (originData.getHighValue() > 0 && originData.getLowValue() > 0) {
                            tvBloodPressure.setText(originData.getHighValue() + "/" + originData.getLowValue());
                        }
                    });
                }
            }

            @Override
            public void onOringinHalfHourDataChange(OriginHalfHourData originHalfHourData) {
                // 历史步数汇总，如果当前步数为0，可以用这个兜底
                if (getActivity() != null && originHalfHourData != null) {
                    getActivity().runOnUiThread(() -> {
                        if (tvStepCount.getText().toString().equals("0")
                                || tvStepCount.getText().toString().isEmpty()) {
                            tvStepCount.setText(String.valueOf(originHalfHourData.getAllStep()));
                        }
                    });
                }
            }

            @Override
            public void onReadOriginComplete() {
                // 原始数据读取完成
            }

            @Override
            public void onSleepDataChange(String day, SleepData sleepData) {
                if (getActivity() != null && sleepData != null) {
                    getActivity().runOnUiThread(() -> {
                        if (sleepData.getAllSleepTime() > 0) {
                            int hours = sleepData.getAllSleepTime() / 60;
                            int mins = sleepData.getAllSleepTime() % 60;
                            tvSleepDuration.setText(hours + "h " + mins + "m");
                            tvDeepSleep.setText(
                                    (sleepData.getDeepSleepTime() / 60) + "h " + (sleepData.getDeepSleepTime() % 60)
                                            + "m");
                            tvLightSleep.setText(
                                    (sleepData.getLowSleepTime() / 60) + "h " + (sleepData.getLowSleepTime() % 60)
                                            + "m");
                        } else {
                            tvSleepDuration.setText("—");
                            tvDeepSleep.setText("—");
                            tvLightSleep.setText("—");
                        }
                    });
                }
            }

            @Override
            public void onReadSleepComplete() {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        tvSyncStatus.setText("健康数据同步完成");
                        resetSyncButton();
                    });
                }
            }
        }, watchDataDay);
    }

    private void resetSyncButton() {
        btnSync.setEnabled(mIsConnected);
        btnSync.setText("同步数据");
    }

    private void refreshConnectionState() {
        if (getActivity() == null)
            return;
        mConnectedMac = getActivity().getSharedPreferences("ble_prefs", android.content.Context.MODE_PRIVATE)
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
            btnSync.setEnabled(mIsConnected);
            if (!mIsConnected) {
                tvSyncStatus.setText("未连接设备，无法同步");
            } else {
                if (tvSyncStatus.getText().toString().equals("未连接设备，无法同步")) {
                    tvSyncStatus.setText("设备已连接，可以同步");
                }
            }
        });
    }

    private final com.veepoo.protocol.listener.base.IABleConnectStatusListener mBleConnectStatusListener = new com.veepoo.protocol.listener.base.IABleConnectStatusListener() {
        @Override
        public void onConnectStatusChanged(String mac, int status) {
            mIsConnected = status == com.inuker.bluetooth.library.Constants.STATUS_CONNECTED
                    || status == com.inuker.bluetooth.library.Constants.STATUS_DEVICE_CONNECTED;
            if (!mIsConnected && btnSync != null && "同步中...".equals(btnSync.getText().toString())) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        resetSyncButton();
                        tvSyncStatus.setText("同步中断：设备已断开");
                        Toast.makeText(getContext(), "同步中断，设备已断开", Toast.LENGTH_SHORT).show();
                    });
                }
            }
            updateConnectionUI();
        }
    };
}
