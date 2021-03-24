package fd;

import fd.domain.FraudDetectionEntity;

import frauddetection.Service;
import frauddetection.domain.FraudDetectionDomain;
import io.cloudstate.javasupport.*;

public final class Main {
    public static final void main(String[] args) throws Exception {
          new CloudState()
                .registerEventSourcedEntity(
                        FraudDetectionEntity.class,
                        Service.getDescriptor().findServiceByName("FraudDetectionService"),
                        FraudDetectionDomain.getDescriptor())
                .start()
                .toCompletableFuture()
                .get();
    }
}