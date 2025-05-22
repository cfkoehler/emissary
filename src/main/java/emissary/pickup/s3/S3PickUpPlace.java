package emissary.pickup.s3;

import emissary.core.DataObjectFactory;
import emissary.core.EmissaryException;
import emissary.core.IBaseDataObject;
import emissary.pickup.IPickUpPlace;
import emissary.pickup.PickUpPlace;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;

import java.io.IOException;
import java.time.Instant;

import static emissary.util.TimeUtil.getDateAsISO8601;

public class S3PickUpPlace extends PickUpPlace implements IPickUpPlace {

    private String awsAccessKey;
    private String awsSecretKey;
    private String awsRegion;
    private AWSCredentials awsCredentials;
    private String inputBucket;
    private String loadedBucket;

    // How often to check bucket in millis for new data
    protected int pollingInterval = 30000;


    public S3PickUpPlace() throws IOException {
        super();
        configurePlace();
    }

    protected void configurePlace() {
        pollingInterval = configG.findIntEntry("POLLING_INTERVAL", pollingInterval);
        awsAccessKey = configG.findStringEntry("AWS_ACCESS_KEY");
        awsSecretKey = configG.findStringEntry("AWS_SECRET_KEY");
        awsRegion = configG.findStringEntry("AWS_REGION");
        inputBucket = configG.findStringEntry("S3_INPUT_BUCKET");
        loadedBucket = configG.findStringEntry("s3_LOADED_BUCKET");
        awsCredentials = credentials();
        logger.info("S3 pickup configured for bucket: {} in region {}", inputBucket, awsRegion);
        startDataServer();
    }

    public boolean processDataPayload(byte[] payload, String objectKey) {
        IBaseDataObject dataObject = DataObjectFactory.getInstance(payload, objectKey);
        dataObject.setParameter("originalFilename", objectKey);
        dataObject.putParameter("processingDateTime", getDateAsISO8601(Instant.now()));

        dataObject.setCurrentForm("UNKNOWN"); // TODO: use config option
        dataObject.setFileType("UNKNOWN");

        try {
            logger.info("**Deploying an agent for id: {}, object key: {}, forms: {}", dataObject.getInternalId(), objectKey,
                    dataObject.getAllCurrentForms());
            assignToPooledAgent(dataObject, -1L);
        } catch (EmissaryException e) {
            logger.error("Error deploying agent", e);
            return false;
        }
        return true;
    }

    public AWSCredentials credentials() {
        return new BasicAWSCredentials(awsAccessKey, awsSecretKey);
    }


    public void startDataServer() {
        S3DataServer s3DataServer = new S3DataServer(this, pollingInterval, awsCredentials, awsRegion, inputBucket, loadedBucket);
        s3DataServer.start();
    }
}
