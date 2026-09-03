package redactedrice.bpsqueuedwriter;


import redactedrice.gbcframework.utils.ByteUtils;

public enum BpsHunkType {
    SOURCE_READ(0), SELF_READ(1), SOURCE_COPY(2), TARGET_COPY(3);

    private byte value;

    BpsHunkType(int inValue) {
        if (inValue > ByteUtils.MAX_BYTE_VALUE || inValue < 0) {
            throw new IllegalArgumentException(
                    "Invalid constant input for " + "BpsHunkType enum: " + inValue);
        }
        value = (byte) inValue;
    }

    byte getValue() {
        return value;
    }
}
