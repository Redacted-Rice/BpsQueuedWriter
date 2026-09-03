package redactedrice.bpsqueuedwriter;

public enum BpsHunkCopyType {
    SOURCE_COPY(BpsHunkType.SOURCE_COPY), TARGET_COPY(BpsHunkType.TARGET_COPY);

    private BpsHunkType type;

    private BpsHunkCopyType(BpsHunkType type) {
        this.type = type;
    }

    public BpsHunkType asBpsHunkType() {
        return type;
    }
}
