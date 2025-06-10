package redactedrice.bpsqueuedwriter.compiler;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

import redactedrice.bpsqueuedwriter.BpsWriter;
import redactedrice.bpsqueuedwriter.BpsWriter.BpsHunkCopyType;
import redactedrice.compiler.instructions.FixedLengthInstruction;
import redactedrice.gbcframework.QueuedWriter;
import redactedrice.gbcframework.addressing.AssignedAddresses;
import redactedrice.gbcframework.addressing.BankAddress;
import redactedrice.gbcframework.utils.RomUtils;


public class SourceTargetCopyInstruct extends FixedLengthInstruction
{
	private int copyFromStartIndex;
	private BpsHunkCopyType type;
	
	// No version that takes an address because thats only use in the case where we
	// use an address from the rom which will always use the default logic
	public SourceTargetCopyInstruct(int copyFromStartIndex, int copyLength, BpsHunkCopyType type) 
	{
		super(copyLength);
		this.copyFromStartIndex = copyFromStartIndex;
		this.type = type;
	}
	
	public static SourceTargetCopyInstruct create(String key, String[] args)
	{	
		final String supportedArgs = "bps_sc and bps_tc only supports (int Offset, int copyLength): ";	

		if (args.length != 2)
		{
			throw new IllegalArgumentException(supportedArgs + "given: " + Arrays.toString(args));
		}
		
		// Determine the type
		BpsHunkCopyType type;
		if (key.endsWith("sc"))
		{
			type = BpsHunkCopyType.SOURCE_COPY;
		}
		else if (key.endsWith("tc"))
		{
			type = BpsHunkCopyType.TARGET_COPY;
		}
		else
		{
			throw new IllegalArgumentException(supportedArgs + "given: " + Arrays.toString(args));
		}
		
		try
		{
			return new SourceTargetCopyInstruct(Integer.parseInt(args[0]), Integer.parseInt(args[1]), type);
		}
		catch (IllegalArgumentException iae)
		{
			throw new IllegalArgumentException(supportedArgs + "given: " + Arrays.toString(args) + " and encountered error: "+ iae.getMessage());
		}
	}

	@Override
	public boolean containsPlaceholder() 
	{
		return false;
	}

	@Override
	public void replacePlaceholderIfPresent(Map<String, String> placeholderToArgs) 
	{
		// Nothing to do
	}
	
	@Override
	public void writeFixedSizeBytes(QueuedWriter writer, BankAddress instructionAddress, AssignedAddresses assignedAddresses) throws IOException 
	{
		if (writer instanceof BpsWriter)
		{
			// We need to add a new hunk for the copy and then another new one for the next read segment
			int instructAddr = RomUtils.convertToGlobalAddress(instructionAddress);
			String currBlockName = writer.getCurrentBlockName();
			((BpsWriter)writer).newCopyHunk(currBlockName + "SourceTargetCopyInstructHunk", instructAddr, type, copyFromStartIndex, getSize());
			((BpsWriter)writer).startNewBlock(instructAddr + getSize(), currBlockName + "_continued");
		}
		else
		{
			throw new IllegalArgumentException("SourceTargetCopyInstruct is only intended to be used with BpsWriter");
		}
	}
}

