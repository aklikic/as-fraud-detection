package fd.domain;

import akka.event.slf4j.Logger;
import com.google.protobuf.Empty;
import fd.Mappers;
import fd.Service;
import fd.persistence.Domain;
import io.cloudstate.javasupport.EntityId;
import io.cloudstate.javasupport.eventsourced.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.stream.Stream;

@EventSourcedEntity(persistenceId = "customer", snapshotEvery = 20)
public class CustomerEntity {
    private final String customerId;
    private Domain.Customer customer;

    public CustomerEntity(@EntityId String customerId) {
        this.customerId = customerId;
    }

    @Snapshot
    public Domain.Customer snapshot() {
        return customer;
    }

    @SnapshotHandler
    public void handleSnapshot(Domain.Customer customer) {
        this.customer = customer;
    }

    //commands

    @CommandHandler
    public Empty createCustomer(Service.CreateCustomerCommand cmd, CommandContext ctx) {
        if(customer!=null)
            return Empty.getDefaultInstance();
        ctx.emit(Domain.CustomerCreated.newBuilder()
                        .setCustomer(Mappers.fromApi(cmd))
                        .build());
        return Empty.getDefaultInstance();
    }
    @CommandHandler
    public Service.CustomerState getCustomer(Service.GetCustomerCommand cmd, CommandContext ctx) {
        return Mappers.toApi(customer).build();
    }
    @CommandHandler
    public Empty updateFraudDetectionRule(Service.UpdateFraudDetectionRuleCommand cmd, CommandContext ctx){
        ctx.emit(Domain.FraudDetectionRuleUpdated.newBuilder()
                .setRule(Mappers.fromApi(cmd.getRule()))
                .build());
        return Empty.getDefaultInstance();
    }
    @CommandHandler
    public Service.FraudDetectionReport addTransaction(Service.AddTransactionCommand cmd, CommandContext ctx){
        Optional<Domain.Transaction> existingTrans = checkTransExists(cmd.getTrans().getTransId());
        if(existingTrans.isPresent())
            return Mappers.toApi(existingTrans.get().getFraudReport()).build();

        //fraud detection logic
        Domain.FraudDetectionReport.Builder fraudReport = Domain.FraudDetectionReport.newBuilder();
        fraudReport.setRuleId(customer.getRule().getRuleId());
        fraudReport.setPotentialFraud(cmd.getTrans().getAmountCents()>customer.getRule().getMaxAmountCents());
        if(fraudReport.getPotentialFraud())
            fraudReport.setRiskScore(100);
        else
            fraudReport.setRiskScore(0);

        ctx.emit(Domain.TransactionAdded.newBuilder()
                .setTrans(Mappers.fromApi(cmd.getTrans()).setFraudReport(fraudReport))
                .build());

        getTransactionsToRemove().map(transId->Domain.TransactionRemoved.newBuilder().setTransId(transId).build())
                                 .forEach(ctx::emit);

        return Mappers.toApi(fraudReport.build()).build();
    }

    private Stream<String> getTransactionsToRemove(){
        Instant now = Instant.now();
        return
        customer.getTransList().stream().filter(t->{
            Instant transTimestamp = Instant.ofEpochMilli(t.getTimestamp());
            return transTimestamp.isBefore(now.minus(customer.getRule().getTransBacktrackHours(), ChronoUnit.HOURS));
        }).map(Domain.Transaction::getTransId);
    }

    private Optional<Domain.Transaction> checkTransExists(String transId){
        if(customer.getTransList()==null)
            return Optional.empty();
        return customer.getTransList().stream().filter(t->t.getTransId().equals(transId)).findFirst();
    }

    //events
    @EventHandler
    public void customerCreated(Domain.CustomerCreated event) {
        customer = event.getCustomer();
    }
    @EventHandler
    public void fraudDetectionRuleUpdated(Domain.FraudDetectionRuleUpdated event) {
        customer.toBuilder().setRule(event.getRule()).build();
    }
    @EventHandler
    public void transactionAdded(Domain.TransactionAdded event) {
        Instant transTimestamp = Instant.ofEpochMilli(event.getTrans().getTimestamp());
        if(transTimestamp.isAfter(Instant.now().minus(customer.getRule().getTransBacktrackHours(), ChronoUnit.HOURS)))
            customer=customer.toBuilder().addTrans(event.getTrans()).build();
    }
    @EventHandler
    public void transactionRemoved(Domain.TransactionRemoved event) {
        customer.getTransList().removeIf(t->t.getTransId().equals(event.getTransId()));
    }

}
