package redactedrice.bpsqueuedwriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import redactedrice.gbcframework.utils.ByteUtils;

public class BpsHunkSourceRead extends BpsHunk
{
	public static final String DEFAULT_NAME = "UNNAMED_SOURCE_READ_HUNK";
	
	public BpsHunkSourceRead(int destinationIndex, int length)
	{
		this(DEFAULT_NAME, destinationIndex, length);
	}
	
	public BpsHunkSourceRead(String name, int destinationIndex, int length)
	{
		super(name, destinationIndex, BpsHunkType.SOURCE_READ, length);
	}

	@Override
	public boolean tryExtend(BpsHunk nextHunk)
	{ 
		if (nextHunk instanceof BpsHunkSourceRead && doesHunkAlign(nextHunk))
		{
			extendCommonData(nextHunk);
			// Nothing else to do!
			return true;
		}
		return false;
	}

	@Override
	public void apply(byte[] targetBytes, byte[] originalBytes) 
	{
		ByteUtils.copyBytes(targetBytes, getDestinationIndex(), originalBytes, getDestinationIndex(), getLength());
	}
	
	@Override
	public void write(ByteArrayOutputStream bpsOs) throws IOException 
	{
		checkDestinationIndex(bpsOs);
		writeHunkHeader(bpsOs);
		// Nothing else to write
	}
}
