package com.timaimee.vpdemo.adapter;

import android.content.Context;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.inuker.bluetooth.library.search.SearchResult;
import com.timaimee.vpdemo.R;

import java.util.List;


/**
 * Created by timaimee on 2016/7/25.
 */
public class BleScanViewAdapter extends RecyclerView.Adapter<BleScanViewAdapter.NormalTextViewHolder> {
    private final LayoutInflater mLayoutInflater;
    List<SearchResult> itemData;
    OnRecycleViewClickCallback mBleCallback;
    private String connectedMac;

    public BleScanViewAdapter(Context context, List<SearchResult> data) {
        this.itemData = data;
        mLayoutInflater = LayoutInflater.from(context);
    }

    @Override
    public NormalTextViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new NormalTextViewHolder(mLayoutInflater.inflate(R.layout.item_main, parent, false));
    }

    @Override
    public void onBindViewHolder(NormalTextViewHolder holder, int position) {
        SearchResult result = itemData.get(position);
        String name = result.getName() == null || result.getName().trim().isEmpty() ? "未知设备" : result.getName();
        boolean isConnected = result.getAddress() != null && result.getAddress().equals(connectedMac);
        holder.deviceName.setText(name);
        holder.deviceStatus.setText(isConnected ? "已连接" : "未连接");
        holder.deviceStatus.setTextColor(holder.deviceStatus.getResources().getColor(isConnected ? R.color.text_primary : R.color.text_secondary));
        holder.signalIcon.setImageResource(getSignalRes(result.rssi));
        holder.deviceRssi.setText(result.rssi + " dBm");
        holder.deviceMac.setText(result.getAddress() == null ? "--:--:--:--:--:--" : result.getAddress());
        holder.itemView.setBackgroundResource(isConnected ? R.drawable.bg_device_connected : 0);

    }


    @Override
    public int getItemCount() {
        return itemData == null ? 0 : itemData.size();
    }


    public void setBleItemOnclick(OnRecycleViewClickCallback bleCallback) {
        this.mBleCallback = bleCallback;
    }

    public void setConnectedMac(String mac) {
        this.connectedMac = mac;
        notifyDataSetChanged();
    }

    private int getSignalRes(int rssi) {
        if (rssi >= -55) {
            return R.drawable.ic_signal_3;
        } else if (rssi >= -70) {
            return R.drawable.ic_signal_2;
        } else if (rssi >= -85) {
            return R.drawable.ic_signal_1;
        } else {
            return R.drawable.ic_signal_0;
        }
    }

    public class NormalTextViewHolder extends RecyclerView.ViewHolder {

        TextView deviceName;
        TextView deviceStatus;
        TextView deviceMac;
        TextView deviceRssi;
        ImageView signalIcon;


        NormalTextViewHolder(View view) {
            super(view);
            deviceName = (TextView) view.findViewById(R.id.tv_device_name);
            deviceStatus = (TextView) view.findViewById(R.id.tv_device_status);
            deviceMac = (TextView) view.findViewById(R.id.tv_device_mac);
            deviceRssi = (TextView) view.findViewById(R.id.tv_device_rssi);
            signalIcon = (ImageView) view.findViewById(R.id.iv_signal);
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mBleCallback != null) {
                        mBleCallback.OnRecycleViewClick(getPosition());
                        Log.d("NormalTextViewHolder", "onClick--> position = " + getPosition());
                    }
                }
            });
        }
    }
}
