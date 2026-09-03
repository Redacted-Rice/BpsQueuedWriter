package redactedrice.bpsqueuedwriter;


import java.io.ByteArrayOutputStream;
import java.io.IOException;

import redactedrice.gbcframework.utils.ByteUtils;

public abstract class BpsHunk implements Comparable<BpsHunk>, Cloneable {
    public static final String DEFAULT_NAME = "UNNAMED_HUNK";
    public static final int MAX_HUNK_LENGTH = 0xFFFF;

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

    public int compareTo(BpsHunk other) {
        return this.destinationIndex - other.destinationIndex;
    }

    private String name;
    private int destinationIndex;
    private BpsHunkType type;
    private int length;

    protected BpsHunk(String name, int destinationIndex, BpsHunkType type, int length) {
        validateHunkLength(length);
        this.name = name;
        this.destinationIndex = destinationIndex;
        this.type = type;
        this.length = length;
    }

    protected BpsHunk(int destinationIndex, BpsHunkType type, int length) {
        validateHunkLength(length);
        this.name = DEFAULT_NAME;
        this.destinationIndex = destinationIndex;
        this.type = type;
        this.length = length;
    }

    protected static void validateHunkLength(int length) {
        if (length < 1) {
            throw new IllegalArgumentException("BPS hunk length must be at least 1: " + length);
        }
        if (length > MAX_HUNK_LENGTH) {
            throw new IllegalArgumentException(
                    "BPS hunk length exceeds max of " + MAX_HUNK_LENGTH + ": " + length);
        }
    }

    protected boolean canExtend(BpsHunk nextHunk) {
        return getLength() + nextHunk.getLength() <= MAX_HUNK_LENGTH;
    }

    public abstract boolean tryExtend(BpsHunk nextHunk);

    protected boolean doesHunkAlign(BpsHunk nextHunk) {
        return this.getDestinationIndex() + this.getLength() == nextHunk.getDestinationIndex();
    }

    protected void extendCommonData(BpsHunk nextHunk) {
        this.length += nextHunk.length;
    }

    public abstract void apply(byte[] targetBytes, byte[] originalBytes);

    public abstract void write(ByteArrayOutputStream bpsOs) throws IOException;

    public void checkRomCursor(int romCursor) {
        if (getDestinationIndex() != romCursor) {
            throw new IllegalStateException("BPS hunk \"" + getName() + "\" starts at ROM index "
                    + getDestinationIndex() + " but expected " + romCursor);
        }
    }

    protected void writeHunkHeader(ByteArrayOutputStream bpsOs) throws IOException {
        if (getLength() > MAX_HUNK_LENGTH) {
            throw new IllegalStateException("BPS hunk \"" + getName() + "\" length " + getLength()
                    + " exceeds max of " + MAX_HUNK_LENGTH);
        }
        // We know the length is at least 1
        long hunkLength = (((long) getLength() & 0xFFFF) - 1) << 2;
        long hunkValue = ((long) getType().getValue()) & 0xFF;
        bpsOs.write(ByteUtils.sevenBitEncode(hunkLength + hunkValue));
    }

    public String getName() {
        return name;
    }

    public int getDestinationIndex() {
        return destinationIndex;
    }

    public int getLength() {
        return length;
    }

    public BpsHunkType getType() {
        return type;
    }
}
