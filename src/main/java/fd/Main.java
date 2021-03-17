package fd;

import fd.domain.CustomerEntity;
import fd.persistence.Domain;
import io.cloudstate.javasupport.*;

public final class Main {
    public static final void main(String[] args) throws Exception {
        new CloudState()
                .registerEventSourcedEntity(
                        CustomerEntity.class,
                        Service.getDescriptor().findServiceByName("CustomerFraudDetection"),
                        Domain.getDescriptor())
                .start()
                .toCompletableFuture()
                .get();
    }
}