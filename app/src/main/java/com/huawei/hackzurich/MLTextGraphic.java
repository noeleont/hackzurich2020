/*
 * Copyright 2020. Huawei Technologies Co., Ltd. All rights reserved.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.huawei.hackzurich;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.view.View;

import com.huawei.hms.mlsdk.objects.MLObject;
import com.huawei.hms.mlsdk.text.MLText;

/**
 * Draw the detected object information and overlay it on the preview frame.
 */
public class MLTextGraphic extends GraphicOverlay.Graphic {
    private static final float TEXT_SIZE = 54.0f;

    private static final float STROKE_WIDTH = 4.0f;

    private final MLText.Block object;

    private final Paint boxPaint;

    private final Paint textPaint;

    MLTextGraphic(GraphicOverlay overlay, MLText.Block object) {
        super(overlay);

        this.object = object;

        this.boxPaint = new Paint();
        System.out.println(this.object.getBorder());
        if (overlay.lastClick != null && this.object.getBorder().contains(overlay.lastClick.x, overlay.lastClick.y)) {
            this.boxPaint.setColor(Color.GREEN);
        } else {
            this.boxPaint.setColor(Color.WHITE);
        }
        this.boxPaint.setStyle(Style.STROKE);
        this.boxPaint.setStrokeWidth(MLTextGraphic.STROKE_WIDTH);

        this.textPaint = new Paint();
        this.textPaint.setColor(Color.WHITE);
        this.textPaint.setTextSize(MLTextGraphic.TEXT_SIZE);


    }

    @Override
    public void draw(Canvas canvas) {
        // draw the object border.
        RectF rect = new RectF(this.object.getBorder());
        rect.left = this.translateX(rect.left);
        rect.top = this.translateY(rect.top);
        rect.right = this.translateX(rect.right);
        rect.bottom = this.translateY(rect.bottom);
        canvas.drawRect(rect, this.boxPaint);


        // draw other object info.
        //canvas.drawText(MLTextGraphic.getCategoryName(this.object.getTypeIdentity()), rect.left, rect.bottom, this.textPaint);
        //canvas.drawText("trackingId: " + this.object.getTracingIdentity(), rect.left, rect.top, this.textPaint);
        //if (this.object.getTypePossibility() != null) {
        //    canvas.drawText("confidence: " + this.object.getTypePossibility(), rect.right, rect.bottom, this.textPaint);
        //}
    }

}
