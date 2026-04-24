package org.kreotak.grott.protocol;

public final class Crc16Modbus {

    private static final int[] TABLE = new int[256];

    static {
        for (int i = 0; i < 256; i++) {
            int crc = i;
            for (int j = 0; j < 8; j++) {
                if ((crc & 1) != 0) {
                    crc = (crc >>> 1) ^ 0xA001;
                } else {
                    crc >>>= 1;
                }
            }
            TABLE[i] = crc;
        }
    }

    private Crc16Modbus() {}

    public static int calculate(byte[] data, int offset, int length) {
        int crc = 0xFFFF;
        for (int i = offset; i < offset + length; i++) {
            crc = (crc >>> 8) ^ TABLE[(crc ^ (data[i] & 0xFF)) & 0xFF];
        }
        return crc & 0xFFFF;
    }

    public static int calculate(byte[] data) {
        return calculate(data, 0, data.length);
    }

    public static boolean verify(byte[] data) {
        if (data.length < 3) return false;
        int len = data.length;
        int stored = ((data[len - 2] & 0xFF) << 8) | (data[len - 1] & 0xFF);
        int computed = calculate(data, 0, len - 2);
        return stored == computed;
    }
}
