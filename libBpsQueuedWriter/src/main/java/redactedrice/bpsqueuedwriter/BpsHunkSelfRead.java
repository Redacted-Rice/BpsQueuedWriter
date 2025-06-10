package redactedrice.bpsqueuedwriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import redactedrice.gbcframework.utils.ByteUtils;

public class BpsHunkSelfRead extends BpsHunk
{
	public static final String DEFAULT_NAME = "UNNAMED_SELF_READ_HUNK";
	
	// A list in case we extend them together so we don't have to 
	// copy the array
	List<byte[]> data;
	
	public BpsHunkSelfRead(int destinationIndex, byte toRepeat, int numRepeats) 
	{
		this(DEFAULT_NAME, destinationIndex, toRepeat, numRepeats);
	}

	public BpsHunkSelfRead(String name, int destinationIndex, byte toRepeat, int numRepeats) 
	{
		this(name, destinationIndex, createRepeatedByteArray(toRepeat, numRepeats));
	}
	
	private static byte[] createRepeatedByteArray(byte toRepeat, int numRepeats) 
	{
		byte[] repeatData = new byte[numRepeats];
		for (int i = 0; i < numRepeats; i++)
		{
			repeatData[i] = toRepeat;
		}
		return repeatData;
	}
	
	public BpsHunkSelfRead(int destinationIndex, byte[] data) 
	{
		this(DEFAULT_NAME, destinationIndex, data);
	}
	
	public BpsHunkSelfRead(String name, int destinationIndex, byte[] data) 
	{
		super(name, destinationIndex, BpsHunkType.SELF_READ, data.length);
		this.data = new LinkedList<byte[]>();
		this.data.add(data.clone());
	}

	@Override
	public boolean tryExtend(BpsHunk nextHunk)
	{ 
		if (nextHunk instanceof BpsHunkSelfRead && doesHunkAlign(nextHunk))
		{
			extendCommonData(nextHunk);
			this.data.addAll(((BpsHunkSelfRead)nextHunk).data);
			return true;
		}
		return false;
	}
	
	@Override
	public void apply(byte[] targetBytes,byte[] originalBytes) 
	{
		int nextIndex = getDestinationIndex();
		for (byte[] entry : data)
		{
			ByteUtils.copyBytes(targetBytes, nextIndex, entry);
			nextIndex += entry.length;
		}
	}
	
	@Override
	public void write(ByteArrayOutputStream bpsOs) throws IOException 
	{
		checkDestinationIndex(bpsOs);
		writeHunkHeader(bpsOs);
		for (byte[] entry : data)
		{
			bpsOs.write(entry);
		}
	}
}
