package redactedrice.bpsqueuedwriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import redactedrice.gbcframework.utils.ByteUtils;

public abstract class BpsHunk implements Comparable<BpsHunk>, Cloneable
{
	public static final String DEFAULT_NAME = "UNNAMED_HUNK";
	
	public enum BpsHunkType
	{
		SOURCE_READ(0), SELF_READ(1), SOURCE_COPY(2), TARGET_COPY(3);
		
		private byte value;
		BpsHunkType(int inValue)
		{
			if (inValue > ByteUtils.MAX_BYTE_VALUE || inValue < 0)
			{
				throw new IllegalArgumentException("Invalid constant input for "
						+ "BpsHunkType enum: " + inValue);
			}
			value = (byte) inValue;
		}
		
		byte getValue()
		{
			return value;
		}
	}
	
    public int compareTo(BpsHunk other)
    {
    	return this.destinationIndex - other.destinationIndex;
    }

	private String name;
	private int destinationIndex;
	private BpsHunkType type;
	private int length;
	
	protected BpsHunk(String name, int destinationIndex, BpsHunkType type, int length)
	{
		this.name = name;
		this.destinationIndex = destinationIndex;
		this.type = type;
		this.length = length;
	}
	
	protected BpsHunk(int destinationIndex, BpsHunkType type, int length)
	{
		this.name = DEFAULT_NAME;
		this.destinationIndex = destinationIndex;
		this.type = type;
		this.length = length;
	}

	public abstract boolean tryExtend(BpsHunk nextHunk);
	
	protected boolean doesHunkAlign(BpsHunk nextHunk)
	{
		return this.getDestinationIndex() + this.getLength() == nextHunk.getDestinationIndex();
	}
	
	protected void extendCommonData(BpsHunk nextHunk)
	{
		this.length += nextHunk.length;
	}

	public abstract void apply(byte[] targetBytes, byte[] originalBytes);	
	public abstract void write(ByteArrayOutputStream bpsOs) throws IOException;
	
	protected void checkDestinationIndex(ByteArrayOutputStream bpsOs)
	{
		// TODO: Add a curr index if we want to do this check
//		if (bpsOs.size() != destinationIndex)
//		{
//			throw new IllegalArgumentException("Internal error: Destination Index "
//					+ "mismatch in byte array output stream while writting BPS. Expected "
//					+ "index " + destinationIndex + " but output stream is at " +
//					bpsOs.size());
//		}
	}
	
	protected void writeHunkHeader(ByteArrayOutputStream bpsOs) throws IOException
	{
	    // We know the length is at least 1
		long hunkLength = (((long)getLength() & 0xFFFF) - 1) << 2;
		long hunkValue = ((long) getType().getValue()) & 0xFF;
		bpsOs.write(ByteUtils.sevenBitEncode(hunkLength + hunkValue));
	}

	public String getName()
	{
		return name;
	}
	
	public int getDestinationIndex() 
	{
		return destinationIndex;
	}
	
	public int getLength() 
	{
		return length;
	}

	public BpsHunkType getType() 
	{
		return type;
	}
}
