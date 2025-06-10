package redactedrice.bpsqueuedwriter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.TreeSet;

import redactedrice.bpsqueuedwriter.BpsHunk.BpsHunkType;
import redactedrice.gbcframework.QueuedWriter;
import redactedrice.gbcframework.addressing.AddressRange;
import redactedrice.gbcframework.utils.ByteUtils;

public class BpsWriter implements QueuedWriter
{	
	public enum BpsHunkCopyType
	{
		SOURCE_COPY(BpsHunkType.SOURCE_COPY),
		TARGET_COPY(BpsHunkType.TARGET_COPY);

        private BpsHunkType type;

        private BpsHunkCopyType(BpsHunkType type) 
        {
			this.type = type;
		}
        
        public BpsHunkType asBpsHunkType()
        {
        	return type;
        }
	}
	
	// The target address and the hunk that starts at the target address
	byte[] sourceBytes;
	TreeSet<BpsHunk> hunks;
	TreeMap<Integer, Integer> spacesToBlank;
	
	int selfReadBeingCreatedDestIndex;
	String selfReadBeingCreatedName;
	ByteArrayOutputStream selfReadBeingCreated;
	List<AddressRange> selfReadBeingCreatedReuse;
	
	public BpsWriter(byte[] originalBytes) 
	{
		sourceBytes = originalBytes;
		hunks = new TreeSet<>();
		spacesToBlank = new TreeMap<>();
		
		selfReadBeingCreatedDestIndex = -1;
		selfReadBeingCreated = new ByteArrayOutputStream();
	}

	@Override
	public void append(byte... bytes) throws IOException
	{
		selfReadBeingCreated.write(bytes);
	}
	
	@Override
	public String getCurrentBlockName()
	{
		return selfReadBeingCreatedName;
	}
	
	@Override
	public void startNewBlock(int segmentStartAddress)
	{
		startNewBlock(segmentStartAddress, BpsHunkSelfRead.DEFAULT_NAME);
	}
	
	@Override
	public void startNewBlock(int segmentStartAddress, String segmentName)
	{
		// Check if we are overwriting the previous hunk	
		finalizeSelfReadBeingCreated();
		selfReadBeingCreatedDestIndex = segmentStartAddress;
		selfReadBeingCreatedName = segmentName;
	}

	@Override
	public void startNewBlock(int segmentStartAddress, List<AddressRange> reuseHints)
	{		
		startNewBlock(segmentStartAddress, BpsHunkSelfRead.DEFAULT_NAME, reuseHints);
	}
	
	@Override
	public void startNewBlock(int segmentStartAddress, String segmentName, List<AddressRange> reuseHints)
	{	
		startNewBlock(segmentStartAddress, segmentName);
		selfReadBeingCreatedReuse = new ArrayList<>();
		if (reuseHints != null)
		{
			selfReadBeingCreatedReuse.addAll(reuseHints);
		}
	}

	@Override
	public void queueBlankedBlock(AddressRange range) 
	{
		spacesToBlank.put(range.getStart(), range.getStopExclusive());
	}
	
	private void checkAndAddHunk(BpsHunk hunk)
	{
		checkForPrevHunkOverwrite(hunk);	
		checkForNextHunkOverwrite(hunk);
		hunks.add(hunk);
	}
	
	public void newSourceRead(int readFromIndex, int size)  
	{
		BpsHunkSourceRead sourceReadHunk = new BpsHunkSourceRead(readFromIndex, size);
		newHunkCommon(sourceReadHunk);
	}
	
	public void newSourceRead(String name, int readFromIndex, int size)  
	{
		BpsHunkSourceRead sourceReadHunk = new BpsHunkSourceRead(name, readFromIndex, size);
		newHunkCommon(sourceReadHunk);
	}
	
	public void newCopyHunk(int destinationIndex, BpsHunkCopyType type, int size, int copyFromStartIndex)  
	{
		BpsHunkCopy copyHunk = new BpsHunkCopy(destinationIndex, type, size, copyFromStartIndex);
		newHunkCommon(copyHunk);
	}
	
	public void newCopyHunk(String name, int destinationIndex, BpsHunkCopyType type, int size, int copyFromStartIndex)  
	{
		BpsHunkCopy copyHunk = new BpsHunkCopy(name, destinationIndex, type, size, copyFromStartIndex);
		newHunkCommon(copyHunk);
	}
	
	private void newHunkCommon(BpsHunk hunk)  
	{		
		// Check that this hunk doesn't overwrite any others		
		finalizeSelfReadBeingCreated();
		checkAndAddHunk(hunk);
	}
	
	private void finalizeSelfReadBeingCreated()
	{
		if (selfReadBeingCreatedDestIndex > 0 && selfReadBeingCreated.size() > 0)
		{
			// If there are reuse hints, check now to see if we can reuse the source
			// Possibly in the future we could add target/inter-patch reuse but for now just
			// worry about source reuse as its the more problematic one
			if (!selfReadBeingCreatedReuse.isEmpty())
			{
				createHunksBasedOnHints();
			}
			else
			{				checkAndAddHunk(new BpsHunkSelfRead(selfReadBeingCreatedName,
						selfReadBeingCreatedDestIndex, selfReadBeingCreated.toByteArray()));
			}
			
			selfReadBeingCreated.reset();
			selfReadBeingCreatedDestIndex = -1;
			selfReadBeingCreatedName = "INTERNAL_NAME_ERROR";
			selfReadBeingCreatedReuse.clear();
		}
	}
	
	private void createHunksBasedOnHints()
	{		
		byte[] hunkDesiredBytes = selfReadBeingCreated.toByteArray();
		// Until we have processed the entire hunk
		int hunksCreated = 0;
		int hunkSpot = 0;
		int lastMatchSpot = 0;
		
		// Could be a for loop but using a while since we are potentially
		// jumping iterator forward outside the end of loop iterator
		while (hunkSpot < hunkDesiredBytes.length)
		{
			// Look for a segment match starting with this byte in the hunk
			AddressRange bestMatch = getBestMatch(hunkDesiredBytes, hunkSpot);
			
			// If its worth copying (right now at least 4 length)
			if (bestMatch.size() > 3 || // TODO: Make option
					hunkSpot + bestMatch.size() == hunkDesiredBytes.length) // Matches to the end
			{
				// Write the self copy if needed
				if (lastMatchSpot != hunkSpot)
				{
					// Write from the last match spot to the current spot
					checkAndAddHunk(new BpsHunkSelfRead(selfReadBeingCreatedName + hunksCreated++ + "_selfRead", 
									selfReadBeingCreatedDestIndex + lastMatchSpot, 
									Arrays.copyOfRange(
											hunkDesiredBytes, lastMatchSpot, hunkSpot)));
				}
				
				// Now update the last match spot and write from the current spot to there
				lastMatchSpot = hunkSpot + bestMatch.size();
				checkAndAddHunk(new BpsHunkCopy(selfReadBeingCreatedName + hunksCreated++ + "_copy",
								selfReadBeingCreatedDestIndex + hunkSpot,
								BpsHunkCopyType.SOURCE_COPY, bestMatch.size(), bestMatch.getStart()));
				
				// + 1 to move to the next unmatched spot
				hunkSpot = lastMatchSpot;
			}
			hunkSpot++;
		}
		
		// Write the trailing self read if we didn't match to the end
		if (hunkDesiredBytes.length > lastMatchSpot)
		{
			// Write from the last match spot to the current spot
			checkAndAddHunk(new BpsHunkSelfRead(selfReadBeingCreatedName + hunksCreated++ + "_selfRead", 
					selfReadBeingCreatedDestIndex + lastMatchSpot,
					Arrays.copyOfRange(
							hunkDesiredBytes, lastMatchSpot, hunkSpot)));
		}
	}
	
	// TODO: Move to a utility?
	private AddressRange getBestMatch(byte[] hunkDesiredBytes, int hunkSpot)
	{
		// For each reuse hint, we will search for matching strings
		int bestAddress = 0;
		int bestCount = 0;
		for (AddressRange range : selfReadBeingCreatedReuse)
		{
			// While we haven't checked each option that could be
			// larger for this spot (- best length since if we have less than
			// that left, we can't find a longer one)
			for (int startAddress = range.getStart(); startAddress < range.getStopExclusive() - bestCount; startAddress++)
			{
				// If it matches, see for how long it matches
				if (hunkDesiredBytes[hunkSpot] == sourceBytes[startAddress])
				{
					int matchCount = 0;
					while (startAddress + matchCount < range.getStopExclusive() && // Not at the end of the hint
							hunkSpot + matchCount < hunkDesiredBytes.length && // Not the end of the matcher bytes
							hunkDesiredBytes[hunkSpot + matchCount] == sourceBytes[startAddress + matchCount])
					{
						matchCount++;
					}
					
					if (matchCount > bestCount)
					{
						bestCount = matchCount;
						bestAddress = startAddress;
					}
				}
			}
		}
		
		return new AddressRange(bestAddress, bestAddress + bestCount);
	}
	
	private void checkForPrevHunkOverwrite(BpsHunk hunk)
	{
		BpsHunk prevHunk = hunks.lower(hunk);
		if (prevHunk != null &&
				prevHunk.getDestinationIndex() + prevHunk.getLength() - 1 >= hunk.getDestinationIndex())
		{
			throw new IllegalArgumentException("Overwrite of the previous hunk \"" + 
					prevHunk.getName() + "\"(starting at " + prevHunk.getDestinationIndex() + 
					" and ending at " + (prevHunk.getDestinationIndex() + 
					prevHunk.getLength() - 1) + ") was detected starting at " +
					hunk.getDestinationIndex() + " while adding hunk \"" + hunk.getName() + "\"");
		}
	}
	
	private void checkForNextHunkOverwrite(BpsHunk hunk)
	{
		BpsHunk nextHunk = hunks.higher(hunk);
		if (nextHunk != null &&
				hunk.getDestinationIndex() + hunk.getLength() - 1 >= nextHunk.getDestinationIndex())
		{
			throw new IllegalArgumentException("Overwrite of the next hunk \"" + 
					nextHunk.getName() + "\"(starting at " + nextHunk.getDestinationIndex() + 
					") was detected while checking hunk \"" + hunk.getName() + "\" starting at "
					+ hunk.getDestinationIndex() + " and ending at " + 
					(hunk.getDestinationIndex() + hunk.getLength() - 1));
		}
	}
	
	private void fillHunkSpacesWithBlanksOrSourceReads(int targetLength, int sourceLength, List<AddressRange> toBlank)
	{
		queueBlankedBlocks(toBlank);
		
		// Ensure any pending ones are finalized prior to filling gaps
		finalizeSelfReadBeingCreated();
		
		// Handle if the target is longer than the source
		if (targetLength > sourceLength)
		{
			spacesToBlank.put(sourceLength, targetLength);
		}
		
		// Go through the existing hunks in order filling in any gaps until we reach
		// the end of the file
		int lastEndAddressExclusive = 0;
		Iterator<Entry<Integer, Integer>> nextBlankItr = spacesToBlank.entrySet().iterator();
		Entry<Integer, Integer> nextBlank = getNextOrNull(nextBlankItr);
		TreeSet<BpsHunk> fillerHunks = new TreeSet<>();
		for (BpsHunk hunk : hunks)
		{			
			// There is a gap we need to fill
			if (hunk.getDestinationIndex() > lastEndAddressExclusive)
			{
				createSourceReadOrBlanksToFillSpace(lastEndAddressExclusive,
						hunk.getDestinationIndex(), nextBlank, nextBlankItr, fillerHunks);
			}
			// We filled too much of a gap or we have overlap between hunks
			else if (hunk.getDestinationIndex() < lastEndAddressExclusive)
			{
				// TODO: error
				throw new IllegalArgumentException("Ovelapping hunks detected! TODO");
			}
			// else the space matches up to the end of the previous hunk - we don't need to do anything
			
			// Now that we are done processing this hunk, set the last address to the end of this hunk
			// and move to the next one
			lastEndAddressExclusive = hunk.getDestinationIndex() + hunk.getLength();
		}
		
		// Ensure the target wasn't too short
		if (targetLength < lastEndAddressExclusive)
		{
			throw new IllegalArgumentException("TODO");
		}
		
		// Add the final reads to the end of the file
		createSourceReadOrBlanksToFillSpace(lastEndAddressExclusive, targetLength, nextBlank, nextBlankItr, fillerHunks);
		
		// Now add in the filler hunks
		// TODO: Check for overlap? CheckAndAddAll?
		hunks.addAll(fillerHunks);
	}
	
	private void createSourceReadOrBlanksToFillSpace(
			int fillFrom, 
			int fillTo, 
			Entry<Integer, Integer> nextBlank,
			Iterator<Entry<Integer, Integer>> nextBlankItr,
			TreeSet<BpsHunk> toAppendTo
	)
	{
		// TODO: Arg?
		final byte fillByte = 0;

		while (fillTo > fillFrom)
		{
			// While the next blank is already passed, get the next one
			while (nextBlank != null && nextBlank.getValue() <= fillFrom)
			{
				nextBlank = getNextOrNull(nextBlankItr);
			}
			
			// See if the blank starts after this hunk or there are no more blanks. If so, we have no
			// blanks in this gap and can finish filling in with source reads
			if (nextBlank == null || nextBlank.getKey() >= fillTo)
			{
				// Fill to the next hunk with source reads
				toAppendTo.add(new BpsHunkSourceRead("FILLER_READ_" + fillFrom, fillFrom, fillTo - fillFrom));
				fillFrom = fillTo;
			}
			// Otherwise the next blank overlaps with the space we are filling and we need to see how
			// to split it up
			else
			{
				// If the blank starts after the last end address, we need to do some source reads to the start of
				// the next blank
				if (nextBlank.getKey() > fillFrom)
				{
					// Fill to the blank with source reads
					toAppendTo.add(new BpsHunkSourceRead("FILLER_READ_" + fillFrom, fillFrom, nextBlank.getKey() - fillFrom));
					fillFrom = nextBlank.getKey(); // Causes the else to be hit in the next loop if not start of next hunk
				}
				// If it starts at or before this fill segment, go ahead and do a blank hunk to the
				// end of the blank/next hunk whichever is first
				else
				{
					int blankEnd = nextBlank.getValue();
					if (blankEnd >= fillTo)
					{
						blankEnd = fillTo;
					}
					toAppendTo.add(new BpsHunkSelfRead("BLANKING_HUNK_" + fillFrom, fillFrom, fillByte, blankEnd - fillFrom));
					fillFrom = blankEnd;
				}
			}
		}
	}
	
	private void optimizeAndPackHunks()
	{
		TreeSet<BpsHunk> newHunks = new TreeSet<>();
		
		// Convert source copies with the same dest & source to sourceReads
		Iterator<BpsHunk> itr = hunks.iterator();
		while (itr.hasNext())
		{
			BpsHunk hunk = itr.next();
			if (hunk.getType() == BpsHunkType.SOURCE_COPY &&
					((BpsHunkCopy)hunk).getCopyFromIndex() ==
						((BpsHunkCopy)hunk).getDestinationIndex())
			{
				newHunks.add(new BpsHunkSourceRead(hunk.getName() + "_AsSourceCopy", ((BpsHunkCopy)hunk).getCopyFromIndex(), hunk.getLength()));
				itr.remove();
			}
		}
		hunks.addAll(newHunks);
		newHunks.clear();
		
		// Now try to combine any we can
		BpsHunk prevHunk = hunks.pollFirst();
		if (prevHunk != null)
		{
			BpsHunk nextHunk = null;
			while((nextHunk = hunks.pollFirst()) != null)
			{
				if (!prevHunk.tryExtend(nextHunk))
				{
					newHunks.add(prevHunk);
					prevHunk = nextHunk;
				}
			}
		
			// add the last hunk being extended
			newHunks.add(prevHunk);
		}
		hunks = newHunks;
	}
	
	private <T> T getNextOrNull(Iterator<T> itr)
	{
		if (itr.hasNext())
		{
			return itr.next();
		}
		else
		{
			return null;
		}
	}
	
	// TODO: Minor Take metadata?	
	public void writeBps(File file, List<AddressRange> toBlank)
	{
		// Ensure any pending ones are finalized prior to writing
		finalizeSelfReadBeingCreated();
		optimizeAndPackHunks();
		
		// We aren't making the rom longer so we pass the same length twice
		fillHunkSpacesWithBlanksOrSourceReads(sourceBytes.length, sourceBytes.length, toBlank);
		
		// TODO: Overlap & gap (target final length) checking?
		
		// Set the offsets for writing
		BpsHunkCopy.setOffsetsForWriting();

		// TODO: Support differing sizes
		byte[] targetBytes = sourceBytes.clone();
		
		// Start writing the bytes for the BPS and the header
		try (ByteArrayOutputStream bpsOs = new ByteArrayOutputStream(); 
				FileOutputStream fos = new FileOutputStream(file))
		{
			bpsOs.write('B');
			bpsOs.write('P');
			bpsOs.write('S');
			bpsOs.write('1');
			
			// Write the sizes in four byte sizes
			bpsOs.write(ByteUtils.sevenBitEncode(sourceBytes.length));
			bpsOs.write(ByteUtils.sevenBitEncode(targetBytes.length));
			bpsOs.write(ByteUtils.sevenBitEncode(0)); // TODO: Minor For now no metadata
			
			// Write the hunks to the patch output stream
			for (BpsHunk hunk : hunks)
			{
				hunk.write(bpsOs);
			}
			
			// Write the source CRC
			bpsOs.write(ByteUtils.toLittleEndianBytes(ByteUtils.computeCrc32(sourceBytes), 4));
			
			// Next we need to determine the target CRC by applying the patch and computing
			// the CRC on the patch bytes and then write that
			for (BpsHunk hunk : hunks)
			{
				hunk.apply(targetBytes, sourceBytes);
			}
			bpsOs.write(ByteUtils.toLittleEndianBytes(ByteUtils.computeCrc32(targetBytes), 4));
			

			// Finally we need to put the CRC of the patch itself
			// So we get the BPS bytes written, write them, calculate the CRC
			// then write that
			byte[] bpsBytes = bpsOs.toByteArray();
			// TODO: BPS temp
//			fos.write(targetBytes);
			fos.write(bpsBytes);
			fos.write(ByteUtils.toLittleEndianBytes(ByteUtils.computeCrc32(bpsBytes), 4));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
