package redactedrice.bpsqueuedwriter;



import java.io.ByteArrayOutputStream;
import java.io.IOException;

import redactedrice.gbcframework.utils.ByteUtils;

public abstract class BpsHunk implements Comparable<BpsHunk>, Cloneable {
    public static final String DEFAULT_NAME = "UNNAMED_HUNK";

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
        // BPS action header: ((length - 1) << 2) + action type, variable-length encoded
        long hunkLength = ((long) getLength() - 1) << 2;
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
