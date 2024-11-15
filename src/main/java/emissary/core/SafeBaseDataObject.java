package emissary.core;

public class SafeBaseDataObject extends BaseDataObject {


    /**
     * {@inheritDoc}
     */
    @Override
    public void checkForUnsafeDataChanges() {
        safeUsageChecker.checkForUnsafeDataChanges();

        if (theData != null) {
            safeUsageChecker.recordSnapshot(theData);
        }
    }
}
