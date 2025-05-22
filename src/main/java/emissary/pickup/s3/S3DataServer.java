package emissary.pickup.s3;

import emissary.core.Pausable;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.services.rekognition.model.InvalidS3ObjectException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * Thread to monitor a s3 bucket for files
 */
public class S3DataServer extends Pausable {

    // Logger
    protected static final Logger logger = LoggerFactory.getLogger(S3DataServer.class);

    // How often to check for new messages in seconds
    protected int pollingInterval = 10;
    AmazonS3 s3;

    protected String loadedBucket;
    protected String inputBucket;

    protected S3PickUpPlace myParent;

    // Thread safe termination
    protected boolean timeToShutdown = false;

    @SuppressWarnings("ThreadPriorityCheck")
    public S3DataServer(S3PickUpPlace parent, int pollingInterval, AWSCredentials awsCredentials, String awsRegion, String inputBucket,
            String loadedBucket) {
        super("S3-" + inputBucket);
        myParent = parent;
        this.pollingInterval = pollingInterval;
        this.inputBucket = inputBucket;
        this.loadedBucket = loadedBucket;
        this.s3 = amazonS3(awsCredentials, awsRegion);
        this.setPriority(Thread.NORM_PRIORITY - 2);
        this.setDaemon(true);
    }

    @Override
    public void run() {
        while (!timeToShutdown) {
            if (checkPaused()) {
                continue;
            }

            // Check the input for new files
            ListObjectsV2Result result = s3.listObjectsV2(inputBucket);
            List<S3ObjectSummary> objects = result.getObjectSummaries();
            for (S3ObjectSummary objectSummary : objects) {
                logger.info("Found object: {}", objectSummary.getKey());
            }

            int processedCount = 0;

            for (S3ObjectSummary objectSummary : objects) {
                logger.info("Processing object: {}", objectSummary.getKey());

                byte[] objectPayload = getS3Bytes(inputBucket, objectSummary.getKey());

                if (objectPayload == null) {
                    logger.warn("Empty payload object");
                    // TODO: Do something when empty payload
                }

                boolean processed = myParent.processDataPayload(objectPayload, objectSummary.getKey());
                if (processed) {
                    // Move to loaded S3 Bucket
                    moveObject(inputBucket, loadedBucket, objectSummary.getKey());
                } else {
                    // TODO: Move message to error bucket
                }
                processedCount++;
            }

            // Delay for the polling interval if there was
            // nothing to do for this last round
            if (processedCount == 0) {
                try {
                    Thread.sleep(pollingInterval * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

    }


    private byte[] getS3Bytes(String bucket, String key) {
        try {
            S3Object object = s3.getObject(bucket, key);
            return IOUtils.toByteArray(object.getObjectContent());
        } catch (InvalidS3ObjectException | IOException e) {
            logger.error("Error Fetching S3 Object", e);
        }
        return new byte[0];
    }

    private void moveObject(String sourceBucket, String targetBucket, String objectKey) {
        // Copy to new bucket
        s3.copyObject(sourceBucket, objectKey, targetBucket, objectKey);
        // Delete it from old bucket
        s3.deleteObject(sourceBucket, objectKey);
    }

    private static AmazonS3 amazonS3(AWSCredentials credentials, String region) {
        return AmazonS3ClientBuilder
                .standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion(region)
                .build();
    }

    /**
     * Shutdown the thread
     */
    public void shutdown() {
        timeToShutdown = true;
    }

}
