package redactedrice.bpsqueuedwriter;

@FunctionalInterface
interface ChunkedHunkBuilder {
    BpsHunk build(String chunkName, int destinationIndex, int chunkLength, int chunkOffset);
}
