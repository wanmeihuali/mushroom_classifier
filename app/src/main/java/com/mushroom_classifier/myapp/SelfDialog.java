
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

    private Button return_button;
    private TextView titleTv;
    private TextView messageTv;
    private String titleStr;
    private String messageStr;
    private String returnStr;

    private onReturnOnclickListener returnOnclickListener;


    public void setReturnOnclickListener(String str, onReturnOnclickListener onReturnOnclickListener) {
        if (str != null) {
            returnStr = str;
        }
        this.returnOnclickListener = onReturnOnclickListener;
    }

    SelfDialog(Context context) {
        super(context, R.style.Theme_AppCompat_Light_Dialog);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_layout);
        setCanceledOnTouchOutside(false);

        initView();
        initData();
        initEvent();

    }

    private void initEvent() {
        return_button.setOnClickListener(
            (View v)->{
                if (returnOnclickListener != null) {
                    returnOnclickListener.onYesClick();
                }
            }
        );

    }

    private void initData() {
        //如果用户自定了title和message
        if (titleStr != null) {
            titleTv.setText(titleStr);
        }
        if (messageStr != null) {
            messageTv.setText(messageStr);
        }
        if (returnStr != null) {
            return_button.setText(returnStr);
        }
    }

    private void initView() {
        return_button = findViewById(R.id.return_button);
        titleTv = findViewById(R.id.title);
        messageTv = findViewById(R.id.message);

    }

    /**
     * set dialog title
     *
     * @param title the title of the dialog
     */
    void setTitle(String title) {
        titleStr = title;
    }

    /**
     * set dialog message
     *
     * @param message the message to show in the dialog
     */
    void setMessage(String message) {
        messageStr = message;
    }


    /**
     * set dialog iamge
     *
     * @param imageBytes the image to show in the dialog
     */
    void setImage(byte[] imageBytes) {
        ImageView imageV = findViewById(R.id.exampleimg);
        if (imageBytes != null)
        {
            Bitmap bmp =   convertByteArrayToBitmap(imageBytes);
            imageV.setImageBitmap(bmp);
        }

    }

    private Bitmap convertByteArrayToBitmap(byte[] bytes) {
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }


    public interface onReturnOnclickListener {
        void onYesClick();
    }

}