package com.timaimee.vpdemo.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import com.timaimee.vpdemo.R;
import com.veepoo.protocol.model.settings.TextAlarm2Setting;

import java.util.List;
import java.util.Locale;

public class CustomTextAlarmAdapter extends BaseAdapter {

    private Context mContext;
    private List<TextAlarm2Setting> mAlarms;
    private OnAlarmActionListener mListener;

    public interface OnAlarmActionListener {
        void onToggle(TextAlarm2Setting setting);

        void onDelete(TextAlarm2Setting setting);

        void onItemClick(TextAlarm2Setting setting);
    }

    public CustomTextAlarmAdapter(Context context, List<TextAlarm2Setting> alarms, OnAlarmActionListener listener) {
        this.mContext = context;
        this.mAlarms = alarms;
        this.mListener = listener;
    }

    @Override
    public int getCount() {
        return mAlarms.size();
    }

    @Override
    public Object getItem(int position) {
        return mAlarms.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(mContext).inflate(R.layout.item_custom_text_alarm, parent, false);
            holder = new ViewHolder();
            holder.tvTime = convertView.findViewById(R.id.tv_time);
            holder.tvContent = convertView.findViewById(R.id.tv_content);
            holder.tvRepeat = convertView.findViewById(R.id.tv_repeat);
            holder.switchAlarm = convertView.findViewById(R.id.switch_alarm);
            holder.ivDelete = convertView.findViewById(R.id.iv_delete);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final TextAlarm2Setting setting = mAlarms.get(position);

        holder.tvTime.setText(
                String.format(Locale.getDefault(), "%02d:%02d", setting.getAlarmHour(), setting.getAlarmMinute()));
        holder.tvContent.setText(setting.getContent());
        holder.tvRepeat.setText(getRepeatText(setting.getRepeatStatus()));

        holder.switchAlarm.setOnCheckedChangeListener(null);
        holder.switchAlarm.setChecked(setting.isOpen());

        holder.switchAlarm.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onToggle(setting);
            }
        });

        holder.ivDelete.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onDelete(setting);
            }
        });

        convertView.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onItemClick(setting);
            }
        });

        return convertView;
    }

    private String getRepeatText(String repeatStatus) {
        if (repeatStatus == null || repeatStatus.length() < 7)
            return "不重复";
        if (repeatStatus.equals("1111111"))
            return "每天";
        if (repeatStatus.equals("0000000"))
            return "不重复";
        if (repeatStatus.equals("0111110"))
            return "工作日";

        StringBuilder sb = new StringBuilder();
        String[] weeks = { "周一", "周二", "周三", "周四", "周五", "周六", "周日" };
        for (int i = 0; i < 7; i++) {
            if (repeatStatus.charAt(i) == '1') {
                if (sb.length() > 0)
                    sb.append(" ");
                sb.append(weeks[i]);
            }
        }
        return sb.toString();
    }

    static class ViewHolder {
        TextView tvTime;
        TextView tvContent;
        TextView tvRepeat;
        Switch switchAlarm;
        ImageView ivDelete;
    }
}
