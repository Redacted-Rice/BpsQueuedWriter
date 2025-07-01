package redactedrice.bpsqueuedwriter;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumMap;

import redactedrice.bpsqueuedwriter.BpsWriter.BpsHunkCopyType;
import redactedrice.gbcframework.utils.ByteUtils;

public class BpsHunkCopy extends BpsHunk {
    public static final String DEFAULT_NAME = "UNNAMED_COPY_HUNK";

    // Do this as an enum map for future proofing
    private static EnumMap<BpsHunkType, Integer> prevVals = new EnumMap<>(BpsHunkType.class);

    private int copyFromIndex;

    public BpsHunkCopy(int destinationIndex, BpsHunkCopyType type, int length, int copyFromIndex) {
        this(DEFAULT_NAME, destinationIndex, type, length, copyFromIndex);
    }

    public BpsHunkCopy(String name, int destinationIndex, BpsHunkCopyType type, int length,
            int copyFromIndex) {
        super(name, destinationIndex, type.asBpsHunkType(), length);

        // Check if it is a target copy that it doesn't try to read from a
        // future, unwritten location
        if (type == BpsHunkCopyType.TARGET_COPY && copyFromIndex >= destinationIndex) {
            throw new IllegalArgumentException("BPS hunk target copy has a copy from index of "
                    + copyFromIndex + " which is after or equal to the destination index of "
                    + destinationIndex + ". Target hunks cannot target unwritten data");
        }
        this.copyFromIndex = copyFromIndex;
    }

    @Override
    public boolean tryExtend(BpsHunk nextHunk) {
        // TODO: More logic for target to make sure we don't make it too large or anything
        if (nextHunk instanceof BpsHunkCopy && doesHunkAlign(nextHunk)) {
            if (copyFromIndex + getLength() == ((BpsHunkCopy) nextHunk).copyFromIndex) {
                extendCommonData(nextHunk);
                // Nothing else to do
                return true;
            }
        }
        return false;
    }

    @Override
    public void apply(byte[] targetBytes, byte[] originalBytes) {
        switch (getType()) {
        case SOURCE_COPY:
            ByteUtils.copyBytes(targetBytes, getDestinationIndex(), originalBytes, copyFromIndex,
                    getLength());
            break;
        case TARGET_COPY:
            ByteUtils.copyBytes(targetBytes, getDestinationIndex(), targetBytes, copyFromIndex,
                    getLength());
            break;
        default:
            throw new IllegalArgumentException(
                    "Internal error: Invalid type for copy BPS Hunk was found:" + getType());
        }
    }

    @Override
    public void write(ByteArrayOutputStream bpsOs) throws IOException {
        checkDestinationIndex(bpsOs);
        writeHunkHeader(bpsOs);

        // These are stored as offsets from the last used value
        // instead of absolute values. Convert the absolute value
        // to the offset then update the last used value
        int offset = copyFromIndex - prevVals.get(getType());
        prevVals.put(getType(), copyFromIndex + getLength()); // Increments offset while it reads

        bpsOs.write(ByteUtils.sevenBitEncodeSigned(offset));
    }

    public static void setOffsetsForWriting() {
        // Set all values to 0
        for (BpsHunkCopyType type : BpsHunkCopyType.values()) {
            prevVals.put(type.asBpsHunkType(), 0);
        }
    }

    public int getCopyFromIndex() {
        return copyFromIndex;
    }
}
