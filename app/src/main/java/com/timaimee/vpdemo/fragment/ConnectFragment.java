package com.timaimee.vpdemo.fragment;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.inuker.bluetooth.library.Code;
import com.inuker.bluetooth.library.Constants;
import com.inuker.bluetooth.library.model.BleGattProfile;
import com.inuker.bluetooth.library.search.SearchResult;
import com.inuker.bluetooth.library.search.response.SearchResponse;
import com.inuker.bluetooth.library.utils.BluetoothUtils;
import com.orhanobut.logger.Logger;
import com.timaimee.vpdemo.DeviceCompare;
import com.timaimee.vpdemo.R;
import com.timaimee.vpdemo.activity.OperaterActivity;
import com.timaimee.vpdemo.adapter.BleScanViewAdapter;
import com.timaimee.vpdemo.adapter.DividerItemDecoration;
import com.timaimee.vpdemo.adapter.OnRecycleViewClickCallback;
import com.veepoo.protocol.VPOperateManager;
import com.veepoo.protocol.listener.base.IABleConnectStatusListener;
import com.veepoo.protocol.listener.base.IBleWriteResponse;
import com.veepoo.protocol.listener.base.IConnectResponse;
import com.veepoo.protocol.listener.base.INotifyResponse;
import com.veepoo.protocol.listener.data.IDeviceFuctionDataListener;
import com.veepoo.protocol.listener.data.IPersonInfoDataListener;
import com.veepoo.protocol.listener.data.IPwdDataListener;
import com.veepoo.protocol.model.datas.DeviceFunctionPackage1;
import com.veepoo.protocol.model.datas.DeviceFunctionPackage2;
import com.veepoo.protocol.model.datas.DeviceFunctionPackage3;
import com.veepoo.protocol.model.datas.DeviceFunctionPackage4;
import com.veepoo.protocol.model.datas.DeviceFunctionPackage5;
import com.veepoo.protocol.model.datas.FunctionDeviceSupportData;
import com.veepoo.protocol.listener.data.ICustomSettingDataListener;
import com.veepoo.protocol.listener.data.ISocialMsgDataListener;
import com.veepoo.protocol.model.settings.CustomSettingData;
import com.veepoo.protocol.model.datas.FunctionSocailMsgData;
import com.veepoo.protocol.model.datas.PersonInfoData;
import com.veepoo.protocol.model.datas.PwdData;
import com.veepoo.protocol.model.enums.EOprateStauts;
import com.veepoo.protocol.model.enums.EPwdStatus;
import com.veepoo.protocol.model.enums.ESex;

import java.util.ArrayList;
import java.util.List;

public class ConnectFragment extends Fragment
        implements SwipeRefreshLayout.OnRefreshListener, OnRecycleViewClickCallback {
    private final static String TAG = ConnectFragment.class.getSimpleName();
    private final int REQUEST_CODE = 1;
    List<SearchResult> mListData = new ArrayList<>();
    List<String> mListAddress = new ArrayList<>();
    SwipeRefreshLayout mSwipeRefreshLayout;
    BleScanViewAdapter bleConnectAdatpter;
    Handler mHandler = new Handler();
    RecyclerView mRecyclerView;
    TextView mStatusTitleText;
    TextView mStatusSubtitleText;
    Button mBtnScanStart;
    Button mBtnScanStop;
    Button mBtnDisconnect;
    private boolean mIsOadModel;
    private UiState mUiState = UiState.IDLE;
    private boolean mIsConnecting = false;
    private String mConnectedMac;
    private String mConnectedName;

    private enum UiState {
        IDLE,
        SCANNING,
        CONNECTED
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_connect, container, false);
        initViews(view);
        checkPermission();
        refreshConnectionState();
        return view;
    }

    private void initViews(View view) {
        mSwipeRefreshLayout = view.findViewById(R.id.mian_swipeRefreshLayout);
        mRecyclerView = view.findViewById(R.id.main_recylerlist);
        mStatusTitleText = view.findViewById(R.id.status_title);
        mStatusSubtitleText = view.findViewById(R.id.status_subtitle);
        mBtnScanStart = view.findViewById(R.id.btn_scan_start);
        mBtnScanStop = view.findViewById(R.id.btn_scan_stop);
        mBtnDisconnect = view.findViewById(R.id.btn_disconnect);

        mRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        bleConnectAdatpter = new BleScanViewAdapter(getContext(), mListData);
        mRecyclerView.setAdapter(bleConnectAdatpter);
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());
        mRecyclerView.addItemDecoration(new DividerItemDecoration(getContext(), DividerItemDecoration.VERTICAL_LIST));
        bleConnectAdatpter.setBleItemOnclick(this);
        mSwipeRefreshLayout.setOnRefreshListener(this);

        mBtnScanStart.setOnClickListener(v -> startScanWithState());
        mBtnScanStop.setOnClickListener(v -> stopScanWithState());
        mBtnDisconnect.setOnClickListener(v -> disconnectDevice());

        updateUiState(UiState.IDLE);
    }

    private void updateUiState(UiState state) {
        mUiState = state;
        if (getActivity() == null)
            return;
        getActivity().runOnUiThread(() -> {
            switch (state) {
                case IDLE:
                    mStatusTitleText.setText("未连接");
                    mStatusSubtitleText.setText("未连接任何设备");
                    mBtnScanStart.setEnabled(true);
                    mBtnScanStop.setEnabled(false);
                    mBtnDisconnect.setVisibility(View.GONE);
                    break;
                case SCANNING:
                    mStatusTitleText.setText("正在扫描");
                    mStatusSubtitleText.setText("正在搜索周围的蓝牙设备...");
                    mBtnScanStart.setEnabled(false);
                    mBtnScanStop.setEnabled(true);
                    mBtnDisconnect.setVisibility(View.GONE);
                    break;
                case CONNECTED:
                    mStatusTitleText.setText("已连接");
                    mStatusSubtitleText
                            .setText((mConnectedName != null ? mConnectedName : "未知设备") + " [" + mConnectedMac + "]");
                    mBtnScanStart.setEnabled(false);
                    mBtnScanStop.setEnabled(false);
                    mBtnDisconnect.setVisibility(View.VISIBLE);
                    break;
            }
            if (mIsConnecting) {
                mStatusTitleText.setText("正在连接");
                mStatusSubtitleText.setText("请稍候...");
            }
        });
    }

    private void checkPermission() {
        if (Build.VERSION.SDK_INT <= 22) {
            return;
        }
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }

        boolean allGranted = true;
        for (String p : permissions) {
            if (ContextCompat.checkSelfPermission(getContext(), p) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (!allGranted) {
            mStatusSubtitleText.setText("权限未授予，请在设置中开启定位/蓝牙权限");
            mStatusSubtitleText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        } else {
            mStatusSubtitleText.setTextColor(getResources().getColor(R.color.text_secondary));
        }
    }

    private void startScanWithState() {
        if (mIsConnecting || mUiState == UiState.CONNECTED) {
            return;
        }
        scanDevice();
    }

    private void scanDevice() {
        if (!mListAddress.isEmpty()) {
            mListAddress.clear();
        }
        if (!mListData.isEmpty()) {
            mListData.clear();
            bleConnectAdatpter.notifyDataSetChanged();
        }

        if (!BluetoothUtils.isBluetoothEnabled()) {
            Toast.makeText(getContext(), "蓝牙没有开启", Toast.LENGTH_SHORT).show();
            return;
        }
        updateUiState(UiState.SCANNING);
        Logger.t(TAG).i("startScanDevice called");
        VPOperateManager.getInstance().startScanDevice(mSearchResponse);
    }

    private void stopScanWithState() {
        if (mUiState != UiState.SCANNING) {
            return;
        }
        VPOperateManager.getInstance().stopScanDevice();
        refreshStop();
        if (mConnectedMac != null) {
            updateUiState(UiState.CONNECTED);
        } else {
            updateUiState(UiState.IDLE);
        }
    }

    private void disconnectDevice() {
        if (mConnectedMac == null || mIsConnecting) {
            return;
        }
        VPOperateManager.getInstance().disconnectWatch(new IBleWriteResponse() {
            @Override
            public void onResponse(int code) {
                mConnectedMac = null;
                mConnectedName = null;
                updateUiState(UiState.IDLE);
            }
        });
    }

    @Override
    public void onRefresh() {
        if (mUiState != UiState.CONNECTED && !mIsConnecting) {
            mHandler.postDelayed(this::scanDevice, 1000);
        } else {
            refreshStop();
        }
    }

    void refreshStop() {
        if (mSwipeRefreshLayout.isRefreshing()) {
            mSwipeRefreshLayout.setRefreshing(false);
        }
    }

    @Override
    public void OnRecycleViewClick(int position) {
        if (mIsConnecting) {
            return;
        }
        SearchResult searchResult = mListData.get(position);
        if (mConnectedMac != null && mConnectedMac.equals(searchResult.getAddress())) {
            return;
        }
        if (mUiState == UiState.SCANNING) {
            stopScanWithState();
        }
        mIsConnecting = true;
        mConnectedName = searchResult.getName();
        updateUiState(mUiState);
        connectDevice(searchResult.getAddress(), searchResult.getName());
    }

    private void connectDevice(final String mac, final String deviceName) {
        VPOperateManager.getInstance().registerConnectStatusListener(mac, mBleConnectStatusListener);
        VPOperateManager.getInstance().connectDevice(mac, deviceName, new IConnectResponse() {
            @Override
            public void connectState(int code, BleGattProfile profile, boolean isoadModel) {
                if (code == Code.REQUEST_SUCCESS) {
                    mIsOadModel = isoadModel;
                    mIsConnecting = false;
                    updateUiState(mUiState);
                } else {
                    mIsConnecting = false;
                    updateUiState(mUiState);
                    Toast.makeText(getContext(), "连接失败 (Code: " + code + ")，请尝试重新扫描", Toast.LENGTH_LONG).show();
                }
            }
        }, new INotifyResponse() {
            @Override
            public void notifyState(int state) {
                if (state == Code.REQUEST_SUCCESS) {
                    performAutoSetup();
                } else {
                    mIsConnecting = false;
                    updateUiState(mUiState);
                    Toast.makeText(getContext(), "服务通知开启失败 (Code: " + state + ")，请重试", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private final IABleConnectStatusListener mBleConnectStatusListener = new IABleConnectStatusListener() {
        @Override
        public void onConnectStatusChanged(String mac, int status) {
            if (status == Constants.STATUS_CONNECTED) {
                mConnectedMac = mac;
                mIsConnecting = false;
                if (getActivity() != null) {
                    getActivity().getSharedPreferences("ble_prefs", Context.MODE_PRIVATE)
                            .edit()
                            .putString("connected_mac", mac)
                            .putString("connected_name", mConnectedName)
                            .apply();
                }
                updateUiState(UiState.CONNECTED);
                if (bleConnectAdatpter != null) {
                    bleConnectAdatpter.setConnectedMac(mac);
                }
            } else if (status == Constants.STATUS_DISCONNECTED) {
                mConnectedMac = null;
                if (getActivity() != null) {
                    getActivity().getSharedPreferences("ble_prefs", Context.MODE_PRIVATE)
                            .edit()
                            .remove("connected_mac")
                            .remove("connected_name")
                            .apply();
                }
                mConnectedName = null;
                mIsConnecting = false;
                updateUiState(UiState.IDLE);
                if (bleConnectAdatpter != null) {
                    bleConnectAdatpter.setConnectedMac(null);
                }
            }
        }
    };

    private void refreshConnectionState() {
        if (getActivity() == null)
            return;
        mConnectedMac = getActivity().getSharedPreferences("ble_prefs", Context.MODE_PRIVATE)
                .getString("connected_mac", null);
        mConnectedName = getActivity().getSharedPreferences("ble_prefs", Context.MODE_PRIVATE)
                .getString("connected_name", null);
        if (mConnectedMac != null) {
            int status = VPOperateManager.getInstance().getConnectStatus(mConnectedMac);
            if (status == Constants.STATUS_CONNECTED || status == Constants.STATUS_DEVICE_CONNECTED) {
                updateUiState(UiState.CONNECTED);
                if (bleConnectAdatpter != null) {
                    bleConnectAdatpter.setConnectedMac(mConnectedMac);
                }
                // Add connected device to list if not present
                if (!mListAddress.contains(mConnectedMac)) {
                    android.util.Log.i("ConnectFragment", "Adding connected device to list: " + mConnectedMac);
                    BluetoothDevice device = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                            .getRemoteDevice(mConnectedMac);
                    SearchResult result = new SearchResult(device, -50, new byte[0]);
                    mListData.add(result);
                    mListAddress.add(mConnectedMac);
                    mListData.sort(new DeviceCompare());
                    if (bleConnectAdatpter != null) {
                        bleConnectAdatpter.notifyDataSetChanged();
                    }
                }
            } else {
                mConnectedMac = null;
                getActivity().getSharedPreferences("ble_prefs", Context.MODE_PRIVATE)
                        .edit().remove("connected_mac").remove("connected_name").apply();
            }
        }
    }

    private final SearchResponse mSearchResponse = new SearchResponse() {
        @Override
        public void onSearchStarted() {
            updateUiState(UiState.SCANNING);
        }

        @Override
        public void onDeviceFounded(final SearchResult device) {
            if (getActivity() == null)
                return;
            getActivity().runOnUiThread(() -> {
                if (!mListAddress.contains(device.getAddress())) {
                    mListData.add(device);
                    mListAddress.add(device.getAddress());
                    Logger.t(TAG).i("Device added: " + device.getName() + " [" + device.getAddress() + "]");
                } else {
                    Logger.t(TAG).d("Device already in list: " + device.getAddress());
                }
                mListData.sort(new DeviceCompare());
                bleConnectAdatpter.notifyDataSetChanged();
            });
        }

        @Override
        public void onSearchStopped() {
            refreshStop();
            updateUiState(mConnectedMac != null ? UiState.CONNECTED : UiState.IDLE);
        }

        @Override
        public void onSearchCanceled() {
            refreshStop();
            updateUiState(mConnectedMac != null ? UiState.CONNECTED : UiState.IDLE);
        }
    };

    private void performAutoSetup() {
        VPOperateManager.getInstance().confirmDevicePwd(new IBleWriteResponse() {
            @Override
            public void onResponse(int code) {
            }
        }, new IPwdDataListener() {
            @Override
            public void onPwdDataChange(PwdData pwdData) {
                if (pwdData == null)
                    return;
                if (pwdData.getmStatus() == EPwdStatus.CHECK_SUCCESS
                        || pwdData.getmStatus() == EPwdStatus.CHECK_AND_TIME_SUCCESS) {
                    syncPersonInfo();
                }
            }
        }, new IDeviceFuctionDataListener() {
            @Override
            public void onFunctionSupportDataChange(FunctionDeviceSupportData functionSupport) {
                if (getActivity() != null && functionSupport != null) {
                    getActivity().getSharedPreferences("ble_prefs", Context.MODE_PRIVATE)
                            .edit()
                            .putInt("watch_data_day", functionSupport.getWathcDay())
                            .apply();
                }
            }

            @Override
            public void onDeviceFunctionPackage1Report(DeviceFunctionPackage1 deviceFunctionPackage1) {
            }

            @Override
            public void onDeviceFunctionPackage2Report(DeviceFunctionPackage2 deviceFunctionPackage2) {
            }

            @Override
            public void onDeviceFunctionPackage3Report(DeviceFunctionPackage3 deviceFunctionPackage3) {
            }

            @Override
            public void onDeviceFunctionPackage4Report(DeviceFunctionPackage4 deviceFunctionPackage4) {
            }

            @Override
            public void onDeviceFunctionPackage5Report(DeviceFunctionPackage5 deviceFunctionPackage5) {
            }
        }, new ISocialMsgDataListener() {
            @Override
            public void onSocialMsgSupportDataChange(FunctionSocailMsgData socailMsgData) {
            }

            @Override
            public void onSocialMsgSupportDataChange2(FunctionSocailMsgData socailMsgData) {
            }
        }, new ICustomSettingDataListener() {
            @Override
            public void OnSettingDataChange(CustomSettingData customSettingData) {
            }
        }, "0000", false);
    }

    private void syncPersonInfo() {
        PersonInfoData personInfoData = new PersonInfoData(ESex.MAN, 175, 75, 25, 8000);
        VPOperateManager.getInstance().syncPersonInfo(new IBleWriteResponse() {
            @Override
            public void onResponse(int code) {
            }
        }, new IPersonInfoDataListener() {
            @Override
            public void OnPersoninfoDataChange(EOprateStauts oprateStauts) {
            }
        }, personInfoData);
    }
}
