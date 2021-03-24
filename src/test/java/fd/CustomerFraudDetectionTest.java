package fd;

import fd.domain.FraudDetectionEntity;
import frauddetection.FraudDetectionCommon;
import frauddetection.Service;
import frauddetection.domain.FraudDetectionDomain;
import io.cloudstate.javasupport.eventsourced.CommandContext;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.Assert.*;

public class CustomerFraudDetectionTest {


    @Test
    public void addTransaction(){
        CommandContext context = Mockito.mock(CommandContext.class);
        String customerId = "1";
        int transAmountCents = 1001;
        int ruleMaxAmountCents = 1000;
        int configMaxTransactionsCount = 2;
        String ruleId = "1";
        FraudDetectionEntity entity = createAndCreateFraudDetection(customerId,configMaxTransactionsCount,ruleId,ruleMaxAmountCents);

        Service.AddTransactionCommand addTrans = createAddCommand(transAmountCents);
        entity.addTransaction(addTrans,context);

        FraudDetectionDomain.ScoredTransactionAdded addedTrans = createAddedEvent(addTrans,ruleId,ruleMaxAmountCents);
        entity.transactionAdded(addedTrans);
        Mockito.verify(context).emit(addedTrans);


        addTrans = createAddCommand(transAmountCents);
        entity.addTransaction(addTrans,context);

        addedTrans = createAddedEvent(addTrans,ruleId,ruleMaxAmountCents);
        entity.transactionAdded(addedTrans);
        Mockito.verify(context).emit(addedTrans);

        addTrans = createAddCommand(transAmountCents);
        entity.addTransaction(addTrans,context);

        addedTrans = createAddedEvent(addTrans,ruleId,ruleMaxAmountCents);
        entity.transactionAdded(addedTrans);
        Mockito.verify(context).emit(addedTrans);

        addTrans = createAddCommand(transAmountCents);
        entity.addTransaction(addTrans,context);

        addedTrans = createAddedEvent(addTrans,ruleId,ruleMaxAmountCents);
        entity.transactionAdded(addedTrans);
        Mockito.verify(context).emit(addedTrans);

        FraudDetectionCommon.FraudDetectionState cs = entity.getFraudDetection(Service.GetFraudDetectionCommand.newBuilder()
                                                                .setCustomerId(customerId).build(),context);
        assertEquals(4,cs.getTransactionsCount());
        //TODO test transactionRemoved??
    }

    private Service.AddTransactionCommand createAddCommand(int transAmountCents){
        return Service.AddTransactionCommand.newBuilder()
                .setTransactionId(UUID.randomUUID().toString())
                .setAmountCents(transAmountCents)
                .setTimestamp(System.currentTimeMillis())
                .build();
    }
    private FraudDetectionDomain.ScoredTransactionAdded createAddedEvent(Service.AddTransactionCommand addTrans, String ruleId, int ruleMaxAmountCents){
        return FraudDetectionDomain.ScoredTransactionAdded.newBuilder()
                .setCustomerId(addTrans.getCustomerId())
                .setTransactionId(addTrans.getTransactionId())
                .setAmountCents(addTrans.getAmountCents())
                .setTimestamp(addTrans.getTimestamp())
                .setRuleId(ruleId)
                .setPotentialFraud(addTrans.getAmountCents()>ruleMaxAmountCents)
                .setRiskScore(addTrans.getAmountCents()>ruleMaxAmountCents?100:0)
                .build();
    }

    private FraudDetectionEntity createAndCreateFraudDetection(String customerId, int configMaxTransactionsCount, String ruleId, int ruleMaxAmountCents){
        CommandContext context = Mockito.mock(CommandContext.class);
        FraudDetectionEntity entity = new FraudDetectionEntity(customerId);
        entity.setConfigMaxTransactionsCount(configMaxTransactionsCount);

        Service.CreateFraudDetectionCommand create = Service.CreateFraudDetectionCommand.newBuilder()
                .setCustomerId(customerId)
                .setRuleId(ruleId)
                .setMaxAmountCents(ruleMaxAmountCents)
                .build();
        entity.createFraudDetection(create,context);
        FraudDetectionDomain.FraudDetectionCreated created = FraudDetectionDomain.FraudDetectionCreated.newBuilder()
                .setCustomerId(customerId)
                .setRuleId(ruleId)
                .setMaxAmountCents(ruleMaxAmountCents)
                .build();
        entity.fraudDetectionCreated(created);
        Mockito.verify(context).emit(created);

        return entity;
    }
}
