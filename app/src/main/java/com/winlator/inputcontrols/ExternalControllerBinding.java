package com.winlator.inputcontrols;

import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ExternalControllerBinding {
    public static final byte AXIS_X_NEGATIVE = -1;
    public static final byte AXIS_X_POSITIVE = -2;
    public static final byte AXIS_Y_NEGATIVE = -3;
    public static final byte AXIS_Y_POSITIVE = -4;
    public static final byte AXIS_Z_NEGATIVE = -5;
    public static final byte AXIS_Z_POSITIVE = -6;
    public static final byte AXIS_RZ_NEGATIVE = -7;
    public static final byte AXIS_RZ_POSITIVE = -8;
    private short keyCode;
    private List<Binding> bindings = new ArrayList<>();

    public ExternalControllerBinding() {
        bindings.add(Binding.NONE);
    }

    public int getKeyCodeForAxis() {
        return this.keyCode;
    }

    public void setKeyCode(int keyCode) {
        this.keyCode = (short)keyCode;
    }

    public List<Binding> getBindings() {
        return this.bindings;
    }

    public void setBindings(List<Binding> bindings) {
        this.bindings = bindings;
    }

    public void addBinding(Binding binding) {
        if (this.bindings.size() == 1 && this.bindings.get(0) == Binding.NONE) {
            this.bindings.set(0, binding);
        } else if (!this.bindings.contains(binding)) {
            this.bindings.add(binding);
        }
    }

    public void clearBindings() {
        this.bindings.clear();
        this.bindings.add(Binding.NONE);
    }

    public Binding getBinding() {
        return !bindings.isEmpty() ? bindings.get(0) : Binding.NONE;
    }

    public void setBinding(Binding binding) {
        this.bindings.clear();
        this.bindings.add(binding);
    }

    public JSONObject toJSONObject() {
        try {
            JSONObject controllerBindingJSONObject = new JSONObject();
            controllerBindingJSONObject.put("keyCode", (int) this.keyCode);

            JSONArray bindingsArray = new JSONArray();
            for (Binding b : bindings) bindingsArray.put(b.name());
            controllerBindingJSONObject.put("bindings", bindingsArray);

            return controllerBindingJSONObject;
        } catch (JSONException e) {
            return null;
        }
    }

    @NonNull
    @Override
    public String toString() {
        short s = this.keyCode;
        String keyStr;
        switch (s) {
            case AXIS_RZ_POSITIVE: keyStr = "AXIS RZ+"; break;
            case AXIS_RZ_NEGATIVE: keyStr = "AXIS RZ-"; break;
            case AXIS_Z_POSITIVE: keyStr = "AXIS Z+"; break;
            case AXIS_Z_NEGATIVE: keyStr = "AXIS Z-"; break;
            case AXIS_Y_POSITIVE: keyStr = "AXIS Y+"; break;
            case AXIS_Y_NEGATIVE: keyStr = "AXIS Y-"; break;
            case AXIS_X_POSITIVE: keyStr = "AXIS X+"; break;
            case AXIS_X_NEGATIVE: keyStr = "AXIS X-"; break;
            default:
                keyStr = KeyEvent.keyCodeToString(s).replace("KEYCODE_", "").replace("_", " ");
        }

        if (bindings.size() > 1) {
            return keyStr + " (Multi)";
        } else {
            return keyStr;
        }
    }

    public static int getKeyCodeForAxis(int axis, byte sign) {
        switch (axis) {
            case MotionEvent.AXIS_X:
                return sign > 0 ? AXIS_X_POSITIVE : AXIS_X_NEGATIVE;
            case MotionEvent.AXIS_Y:
                return sign > 0 ? AXIS_Y_NEGATIVE : AXIS_Y_POSITIVE;
            case MotionEvent.AXIS_Z:
                return sign > 0 ? AXIS_Z_POSITIVE : AXIS_Z_NEGATIVE;
            case MotionEvent.AXIS_RZ:
                return sign > 0 ? AXIS_RZ_NEGATIVE : AXIS_RZ_POSITIVE;
            case MotionEvent.AXIS_HAT_X:
                return sign > 0 ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT;
            case MotionEvent.AXIS_HAT_Y:
                return sign > 0 ? KeyEvent.KEYCODE_DPAD_DOWN : KeyEvent.KEYCODE_DPAD_UP;
            default:
                return KeyEvent.KEYCODE_UNKNOWN;
        }
    }
}
