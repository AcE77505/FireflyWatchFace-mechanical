package com.ace77505.watchface.firefly;

import android.view.SurfaceHolder;

import androidx.annotation.NonNull;
import androidx.wear.watchface.WatchFace;
import androidx.wear.watchface.WatchFaceType;
import androidx.wear.watchface.WatchState;
import androidx.wear.watchface.WatchFaceService;
import androidx.wear.watchface.ComplicationSlotsManager;
import androidx.wear.watchface.style.CurrentUserStyleRepository;

import kotlin.coroutines.Continuation;

public class MainService extends WatchFaceService {

    // Kotlin suspend 函数在 Java 层编译为接受 Continuation 的方法，返回 Object。
    @Override
    public Object createWatchFace(
            @NonNull SurfaceHolder surfaceHolder,
            @NonNull WatchState watchState,
            @NonNull ComplicationSlotsManager complicationSlotsManager,
            @NonNull CurrentUserStyleRepository currentUserStyleRepository,
            @NonNull Continuation<? super WatchFace> continuation
    ) {
        // frame delay 每秒 15 帧
        AnalogRenderer renderer = new AnalogRenderer(
                surfaceHolder,
                currentUserStyleRepository,
                watchState,
                1000L / 15,
                getApplicationContext()
        );

        return new WatchFace(
                WatchFaceType.ANALOG,
                renderer
        );
    }
}