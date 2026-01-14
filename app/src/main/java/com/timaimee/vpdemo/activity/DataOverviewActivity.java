package com.timaimee.vpdemo.activity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.inuker.bluetooth.library.Constants;
import com.timaimee.vpdemo.R;
import com.veepoo.protocol.VPOperateManager;
import com.veepoo.protocol.listener.data.IOriginData3Listener;
import com.veepoo.protocol.listener.data.IOriginDataListener;
import com.veepoo.protocol.listener.data.IOriginProgressListener;
import com.veepoo.protocol.listener.data.ISleepDataListener;
import com.veepoo.protocol.listener.data.ISportDataListener;
import com.veepoo.protocol.listener.data.ITemptureDataListener;
import com.veepoo.protocol.listener.base.IABleConnectStatusListener;
import com.veepoo.protocol.listener.base.IBleWriteResponse;
import com.veepoo.protocol.model.datas.HRVOriginData;
import com.veepoo.protocol.model.datas.OriginData;
import com.veepoo.protocol.model.datas.OriginData3;
import com.veepoo.protocol.model.datas.OriginHalfHourData;
import com.veepoo.protocol.model.datas.SleepData;
import com.veepoo.protocol.model.datas.Spo2hOriginData;
import com.veepoo.protocol.model.datas.SportData;
import com.veepoo.protocol.model.datas.TemptureData;
import com.veepoo.protocol.model.settings.ReadOriginSetting;
import com.veepoo.protocol.shareprence.VpSpGetUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DataOverviewActivity extends AppCompatActivity {
    private Button mSyncButton;
    private TextView mSyncStatus;
    private TextView mStepValue;
    private TextView mKcalValue;
    private TextView mDistanceValue;
    private TextView mSleepTotalValue;
    private TextView mSleepDeepValue;
    private TextView mSleepLightValue;
    private LinearLayout mPhysioContainer;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mIsSyncing = false;
    private boolean mIsConnected = false;
    private boolean mIsConnectListenerRegistered = false;
    private String mDeviceAddress;
    private SyncState mSyncState = SyncState.IDLE;
    private int mPendingSyncCount = 0;
    private final List<String> mHeartRateValues = new ArrayList<>();
    private final List<String> mBloodPressureValues = new ArrayList<>();
    private final List<String> mSpo2Values = new ArrayList<>();
    private final List<String> mHrvValues = new ArrayList<>();
    private final List<String> mTempValues = new ArrayList<>();
    private final int mWatchDay = 3;
    private Runnable mSyncTimeout;

    private enum SyncState {
        IDLE,
        SYNCING,
        FAILED
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_overview);
        mDeviceAddress = getIntent().getStringExtra("deviceaddress");
        initViews();
        initDefaultCards();
        updateSyncUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshConnectionState();
    }

    @Override
    protected void onDestroy() {
        if (mDeviceAddress != null && mIsConnectListenerRegistered) {
            VPOperateManager.getInstance().unregisterConnectStatusListener(mDeviceAddress, mBleConnectStatusListener);
            mIsConnectListenerRegistered = false;
        }
        super.onDestroy();
    }

    private void initViews() {
        mSyncButton = findViewById(R.id.btn_sync);
        mSyncStatus = findViewById(R.id.tv_sync_status);
        mStepValue = findViewById(R.id.tv_step_value);
        mKcalValue = findViewById(R.id.tv_kcal_value);
        mDistanceValue = findViewById(R.id.tv_distance_value);
        mSleepTotalValue = findViewById(R.id.tv_sleep_total_value);
        mSleepDeepValue = findViewById(R.id.tv_sleep_deep_value);
        mSleepLightValue = findViewById(R.id.tv_sleep_light_value);
        mPhysioContainer = findViewById(R.id.physio_container);

        mStepValue.setText("暂无数据");
        mKcalValue.setText("暂无数据");
        mDistanceValue.setText("暂无数据");
        mSleepTotalValue.setText("暂无数据");
        mSleepDeepValue.setText("暂无数据");
        mSleepLightValue.setText("暂无数据");

        mSyncButton.setOnClickListener(v -> onSyncClicked());
    }

    private void initDefaultCards() {
        List<PhysioItem> items = new ArrayList<>();
        items.add(new PhysioItem("心率", "bpm", null));
        items.add(new PhysioItem("血氧饱和度", "%", null));
        items.add(new PhysioItem("血压", "mmHg", null));
        items.add(new PhysioItem("血糖", "mmol/L", null));
        setPhysioItems(items);
    }

    private void onSyncClicked() {
        if (!mIsConnected) {
            showMsg("未连接设备");
            return;
        }
        if (mIsSyncing) {
            return;
        }
        resetDataState();
        mIsSyncing = true;
        mSyncState = SyncState.SYNCING;
        updateSyncUI();
        startSportSync();
        startSleepSync();
        startOriginSync();
        startTemperatureSync();
        scheduleSyncTimeout();
    }

    private void setPhysioItems(List<PhysioItem> items) {
        mPhysioContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (PhysioItem item : items) {
            View card = inflater.inflate(R.layout.item_data_card, mPhysioContainer, false);
            TextView name = card.findViewById(R.id.tv_card_name);
            TextView value = card.findViewById(R.id.tv_card_value);
            TextView unit = card.findViewById(R.id.tv_card_unit);
            name.setText(item.name);
            unit.setText(item.unit);
            value.setText(item.value == null || item.value.trim().isEmpty() ? "暂无数据" : item.value);
            mPhysioContainer.addView(card);
        }
    }

    private void refreshConnectionState() {
        if (mDeviceAddress == null || mDeviceAddress.trim().isEmpty()) {
            mIsConnected = false;
            updateSyncUI();
            return;
        }
        int status = VPOperateManager.getInstance().getConnectStatus(mDeviceAddress);
        mIsConnected = status == Constants.STATUS_CONNECTED || status == Constants.STATUS_DEVICE_CONNECTED;
        if (!mIsConnectListenerRegistered) {
            VPOperateManager.getInstance().registerConnectStatusListener(mDeviceAddress, mBleConnectStatusListener);
            mIsConnectListenerRegistered = true;
        }
        updateSyncUI();
    }

    private void updateSyncUI() {
        if (!mIsConnected) {
            mSyncStatus.setText("未连接设备");
            mSyncButton.setEnabled(false);
            return;
        }
        if (mSyncState == SyncState.SYNCING) {
            mSyncStatus.setText("同步中...");
        } else if (mSyncState == SyncState.FAILED) {
            mSyncStatus.setText("同步失败，请重试");
        } else {
            mSyncStatus.setText("等待同步");
        }
        mSyncButton.setEnabled(!mIsSyncing);
    }

    private final IABleConnectStatusListener mBleConnectStatusListener = new IABleConnectStatusListener() {
        @Override
        public void onConnectStatusChanged(String mac, int status) {
            mIsConnected = status == Constants.STATUS_CONNECTED || status == Constants.STATUS_DEVICE_CONNECTED;
            updateSyncUI();
        }
    };

    private final IBleWriteResponse mWriteResponse = code -> {
    };

    private void startSportSync() {
        mPendingSyncCount++;
        VPOperateManager.getInstance().readSportStep(mWriteResponse, new ISportDataListener() {
            @Override
            public void onSportDataChange(SportData sportData) {
                if (sportData != null) {
                    updateSportData(sportData);
                }
                markSyncPartDone();
            }
        });
    }

    private void startSleepSync() {
        mPendingSyncCount++;
        VPOperateManager.getInstance().readSleepDataSingleDay(mWriteResponse, new ISleepDataListener() {
            @Override
            public void onSleepDataChange(String day, SleepData sleepData) {
                if (sleepData != null) {
                    updateSleepData(sleepData);
                }
            }

            @Override
            public void onSleepProgress(float progress) {
            }

            @Override
            public void onSleepProgressDetail(String day, int packagenumber) {
            }

            @Override
            public void onReadSleepComplete() {
                markSyncPartDone();
            }
        }, 0, mWatchDay);
    }

    private void startOriginSync() {
        mPendingSyncCount++;
        int protocolVersion = VpSpGetUtil.getVpSpVariInstance(this).getOriginProtocolVersion();
        IOriginProgressListener listener;
        if (protocolVersion == 3 || protocolVersion == 5) {
            listener = new IOriginData3Listener() {
                @Override
                public void onOriginFiveMinuteListDataChange(List<OriginData3> originDataList) {
                    if (originDataList != null) {
                        for (OriginData3 data : originDataList) {
                            appendOriginData(data);
                        }
                        updatePhysioCards();
                    }
                }

                @Override
                public void onOriginHalfHourDataChange(OriginHalfHourData originHalfHourDataList) {
                }

                @Override
                public void onOriginHRVOriginListDataChange(List<HRVOriginData> originHrvDataList) {
                    if (originHrvDataList != null) {
                        for (HRVOriginData data : originHrvDataList) {
                            if (data != null && data.getRate() != null) {
                                mHrvValues.add(data.getRate());
                            }
                        }
                        updatePhysioCards();
                    }
                }

                @Override
                public void onOriginSpo2OriginListDataChange(List<Spo2hOriginData> originSpo2hDataList) {
                    if (originSpo2hDataList != null) {
                        for (Spo2hOriginData data : originSpo2hDataList) {
                            int value = data.getOxygenValue();
                            if (value > 0) {
                                mSpo2Values.add(String.valueOf(value));
                            }
                        }
                        updatePhysioCards();
                    }
                }

                @Override
                public void onReadOriginProgress(float progress) {
                }

                @Override
                public void onReadOriginProgressDetail(int day, String date, int allPackage, int currentPackage) {
                }

                @Override
                public void onReadOriginComplete() {
                    markSyncPartDone();
                }
            };
        } else {
            listener = new IOriginDataListener() {
                @Override
                public void onOringinFiveMinuteDataChange(OriginData originData) {
                    if (originData != null) {
                        appendOriginData(originData);
                        updatePhysioCards();
                    }
                }

                @Override
                public void onOringinHalfHourDataChange(OriginHalfHourData originHalfHourData) {
                }

                @Override
                public void onReadOriginProgress(float progress) {
                }

                @Override
                public void onReadOriginProgressDetail(int day, String date, int allPackage, int currentPackage) {
                }

                @Override
                public void onReadOriginComplete() {
                    markSyncPartDone();
                }
            };
        }
        VPOperateManager.getInstance().readOriginDataSingleDay(mWriteResponse, listener, 0, 1, mWatchDay);
    }

    private void startTemperatureSync() {
        boolean canReadTemperature = VpSpGetUtil.getVpSpVariInstance(this).isSupportReadTempture();
        int temperatureType = VpSpGetUtil.getVpSpVariInstance(this).getTemperatureType();
        if (!canReadTemperature || temperatureType == 5) {
            return;
        }
        mPendingSyncCount++;
        ReadOriginSetting setting = new ReadOriginSetting(0, 1, false, mWatchDay);
        VPOperateManager.getInstance().readTemptureDataBySetting(mWriteResponse, new ITemptureDataListener() {
            @Override
            public void onTemptureDataListDataChange(List<TemptureData> temptureDataList) {
                if (temptureDataList != null) {
                    for (TemptureData data : temptureDataList) {
                        mTempValues.add(formatDouble(data.getTempture()));
                    }
                    updatePhysioCards();
                }
            }

            @Override
            public void onReadOriginProgressDetail(int day, String date, int allPackage, int currentPackage) {
            }

            @Override
            public void onReadOriginProgress(float progress) {
            }

            @Override
            public void onReadOriginComplete() {
                markSyncPartDone();
            }
        }, setting);
    }

    private void appendOriginData(OriginData data) {
        if (data == null) {
            return;
        }
        if (data.getRateValue() > 0) {
            mHeartRateValues.add(String.valueOf(data.getRateValue()));
        }
        if (data.getHighValue() > 0 && data.getLowValue() > 0) {
            mBloodPressureValues.add(data.getHighValue() + "/" + data.getLowValue());
        }
        if (data.getTemperature() > 0) {
            mTempValues.add(formatDouble(data.getTemperature()));
        }
        if (data instanceof OriginData3) {
            OriginData3 data3 = (OriginData3) data;
            if (data3.getPpgs() != null) {
                for (int rate : data3.getPpgs()) {
                    if (rate > 0) {
                        mHeartRateValues.add(String.valueOf(rate));
                    }
                }
            }
            if (data3.getOxygens() != null) {
                for (int oxygen : data3.getOxygens()) {
                    if (oxygen > 0) {
                        mSpo2Values.add(String.valueOf(oxygen));
                    }
                }
            }
        }
    }

    private void updateSportData(SportData sportData) {
        int steps = sportData.getStep();
        double kcal = sportData.getKcal();
        double distance = sportData.getDis();
        mStepValue.setText(steps > 0 ? String.valueOf(steps) : "暂无数据");
        mKcalValue.setText(kcal > 0 ? formatDouble(kcal) : "暂无数据");
        mDistanceValue.setText(distance > 0 ? formatDouble(distance) : "暂无数据");
    }

    private void updateSleepData(SleepData sleepData) {
        int total = sleepData.getAllSleepTime();
        int deep = sleepData.getDeepSleepTime();
        int light = sleepData.getLowSleepTime();
        mSleepTotalValue.setText(total > 0 ? total + " 分钟" : "暂无数据");
        mSleepDeepValue.setText(deep > 0 ? deep + " 分钟" : "暂无数据");
        mSleepLightValue.setText(light > 0 ? light + " 分钟" : "暂无数据");
    }

    private void updatePhysioCards() {
        List<PhysioItem> items = new ArrayList<>();
        items.add(new PhysioItem("心率", "bpm", joinValues(mHeartRateValues)));
        items.add(new PhysioItem("血压", "mmHg", joinValues(mBloodPressureValues)));
        items.add(new PhysioItem("血氧", "%", joinValues(mSpo2Values)));
        items.add(new PhysioItem("HRV", "", joinValues(mHrvValues)));
        items.add(new PhysioItem("体温", "℃", joinValues(mTempValues)));
        setPhysioItems(items);
    }

    private void resetDataState() {
        mHeartRateValues.clear();
        mBloodPressureValues.clear();
        mSpo2Values.clear();
        mHrvValues.clear();
        mTempValues.clear();
        mStepValue.setText("暂无数据");
        mKcalValue.setText("暂无数据");
        mDistanceValue.setText("暂无数据");
        mSleepTotalValue.setText("暂无数据");
        mSleepDeepValue.setText("暂无数据");
        mSleepLightValue.setText("暂无数据");
        updatePhysioCards();
    }

    private void markSyncPartDone() {
        if (mPendingSyncCount > 0) {
            mPendingSyncCount--;
        }
        if (mPendingSyncCount == 0) {
            finishSyncSuccess();
        }
    }

    private void finishSyncSuccess() {
        mIsSyncing = false;
        mSyncState = SyncState.IDLE;
        cancelSyncTimeout();
        updateSyncUI();
    }

    private void scheduleSyncTimeout() {
        cancelSyncTimeout();
        mSyncTimeout = () -> {
            if (!mIsSyncing) {
                return;
            }
            mIsSyncing = false;
            mSyncState = SyncState.FAILED;
            updateSyncUI();
            showMsg("同步失败，请重试");
        };
        mHandler.postDelayed(mSyncTimeout, 12000);
    }

    private void cancelSyncTimeout() {
        if (mSyncTimeout != null) {
            mHandler.removeCallbacks(mSyncTimeout);
        }
    }

    private String joinValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "暂无数据";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append("、");
            }
            builder.append(values.get(i));
        }
        return builder.toString();
    }

    private String formatDouble(double value) {
        return String.format(Locale.US, "%.1f", value);
    }

    private void showMsg(String msg) {
        Toast.makeText(DataOverviewActivity.this, msg, Toast.LENGTH_SHORT).show();
    }

    private static class PhysioItem {
        final String name;
        final String unit;
        final String value;

        PhysioItem(String name, String unit, String value) {
            this.name = name;
            this.unit = unit;
            this.value = value;
        }
    }
}
