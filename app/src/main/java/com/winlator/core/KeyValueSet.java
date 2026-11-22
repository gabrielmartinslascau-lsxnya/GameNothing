package com.winlator.core;

import androidx.annotation.NonNull;

import java.util.Iterator;

public class KeyValueSet implements Iterable<String[]> {
    private String data;

    public KeyValueSet() {
        this.data = "";
    }

    public KeyValueSet(Object data) {
        this(data != null ? data.toString() : null);
    }

    public KeyValueSet(String data) {
        String str = "";
        this.data = "";
        if (data != null && !data.isEmpty()) {
            str = data;
        }
        this.data = str;
    }

    public String getData(){
        return data;
    }

    private int[] indexOfKey(String key) {
        int start = 0;
        while (start < data.length()) {
            int end = data.indexOf(",", start);
            if (end == -1) end = data.length();
            if (start < end) {
                int index = data.indexOf("=", start);
                if (index > start && index < end) {
                    String currKey = data.substring(start, index);
                    if (currKey.equals(key)) return new int[]{start, end};
                }
            }
            start = end + 1;
        }

        return null;
    }

    public String get(String key) {
        return get(key, "");
    }

    public String get(String key, String fallback) {
        if (this.data.isEmpty()) {
            return fallback;
        }
        Iterator<String[]> it = iterator();
        while (it.hasNext()) {
            String[] keyValue = it.next();
            if (keyValue[0].equals(key)) {
                return keyValue[1];
            }
        }
        return fallback;
    }

    public float getFloat(String key, float fallback) {
        try {
            String value = get(key);
            return !value.isEmpty() ? Float.parseFloat(value) : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public int getInt(String key) {
        return getInt(key, 0);
    }

    public int getInt(String key, int fallback) {
        try {
            String value = get(key);
            return !value.isEmpty() ? Integer.parseInt(value) : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public String getHexString(String key, int fallback) {
        int result;
        try {
            String value = get(key);
            result = !value.isEmpty() ? Integer.parseInt(value) : fallback;
        } catch (NumberFormatException e) {
            result = fallback;
        }
        return "0x" + String.format("%08x", Integer.valueOf(result));
    }

    public boolean getBoolean(String key) {
        return getBoolean(key, false);
    }

    public boolean getBoolean(String key, boolean fallback) {
        String value = get(key);
        return !value.isEmpty() ? value.equals("1") || value.equals("t") || value.equals("true") : fallback;
    }

    public KeyValueSet put(String key, Object value) {
        String str;
        int[] range = indexOfKey(key);
        if (range != null) {
            this.data = StringUtils.replace(this.data, range[0], range[1], key + "=" + value);
        } else {
            StringBuilder sb = new StringBuilder();
            if (this.data.isEmpty()) {
                str = "";
            } else {
                str = this.data + ",";
            }
            sb.append(str);
            sb.append(key);
            sb.append("=");
            sb.append(value);
            this.data = sb.toString();
        }
        return this;
    }

    @Override // java.lang.Iterable
    public Iterator<String[]> iterator() {
        return new Iterator<String[]>() { // from class: com.winlator.core.KeyValueSet.1
            private int pos = 0;
            private String[] nextItem = advance();

            private String[] advance() {
                while (pos < KeyValueSet.this.data.length()) {
                    int end = KeyValueSet.this.data.indexOf(",", pos);
                    if (end == -1) end = KeyValueSet.this.data.length();
                    int start = pos;
                    pos = end + 1;
                    if (start < end) {
                        int eq = KeyValueSet.this.data.indexOf("=", start);
                        if (eq > start && eq < end) {
                            return new String[]{
                                KeyValueSet.this.data.substring(start, eq),
                                KeyValueSet.this.data.substring(eq + 1, end)
                            };
                        }
                    }
                }
                return null;
            }

            @Override // java.util.Iterator
            public boolean hasNext() {
                return nextItem != null;
            }

            @Override // java.util.Iterator
            public String[] next() {
                if (nextItem == null) throw new java.util.NoSuchElementException();
                String[] current = nextItem;
                nextItem = advance();
                return current;
            }
        };
    }

    @NonNull
    @Override
    public String toString() {
        return data;
    }

    public boolean isEmpty() {
        return this.data.isEmpty();
    }
}
