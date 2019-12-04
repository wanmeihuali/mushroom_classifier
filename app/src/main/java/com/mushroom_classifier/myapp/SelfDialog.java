
package com.mushroom_classifier.myapp;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class SelfDialog extends Dialog {

    private Button return_button;//确定按钮
    private Button no;//取消按钮
    private TextView titleTv;//消息标题文本
    private TextView messageTv;//消息提示文本
    private ImageView imageV;
    private String titleStr;//从外界设置的title文本
    private String messageStr;//从外界设置的消息文本
    //确定文本和取消文本的显示内容
    private String yesStr, noStr;

    private onYesOnclickListener returnOnclickListener;//确定按钮被点击了的监听器


    public void setReturnOnclickListener(String str, onYesOnclickListener onReturnOnclickListener) {
        if (str != null) {
            yesStr = str;
        }
        this.returnOnclickListener = onReturnOnclickListener;
    }

    public SelfDialog(Context context) {
        super(context, R.style.Theme_AppCompat_Light_Dialog);
        imageV = (ImageView) findViewById(R.id.exampleimg);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_layout);
        //按空白处不能取消动画
        setCanceledOnTouchOutside(false);

        //初始化界面控件
        initView();
        //初始化界面数据
        initData();
        //初始化界面控件的事件
        initEvent();

    }

    /**
     * 初始化界面的确定和取消监听器
     */
    private void initEvent() {
        //设置确定按钮被点击后，向外界提供监听
        return_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (returnOnclickListener != null) {
                    returnOnclickListener.onYesClick();
                }
            }
        });

    }

    /**
     * 初始化界面控件的显示数据
     */
    private void initData() {
        //如果用户自定了title和message
        if (titleStr != null) {
            titleTv.setText(titleStr);
        }
        if (messageStr != null) {
            messageTv.setText(messageStr);
        }
        //如果设置按钮的文字
        if (yesStr != null) {
            return_button.setText(yesStr);
        }
        if (noStr != null) {
            no.setText(noStr);
        }
    }

    /**
     * 初始化界面控件
     */
    private void initView() {
        return_button = (Button) findViewById(R.id.return_button);
        titleTv = (TextView) findViewById(R.id.title);
        messageTv = (TextView) findViewById(R.id.message);

    }

    /**
     * 从外界Activity为Dialog设置标题
     *
     * @param title
     */
    public void setTitle(String title) {
        titleStr = title;
    }

    /**
     * 从外界Activity为Dialog设置dialog的message
     *
     * @param message
     */
    public void setMessage(String message) {
        messageStr = message;
    }

    public void setImage(byte[] imageBytes) {
        imageV = findViewById(R.id.exampleimg);
        if (imageBytes != null)
        {
            Bitmap bmp =   convertByteArrayToBitmap(imageBytes);
            imageV.setImageBitmap(bmp);
        }

    }

    private Bitmap convertByteArrayToBitmap(byte[] bytes) {
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    
    /**
     * 设置确定按钮和取消被点击的接口
     */
    public interface onYesOnclickListener {
        public void onYesClick();
    }

}