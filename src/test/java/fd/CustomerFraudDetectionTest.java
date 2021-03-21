package fd;

import fd.domain.CustomerEntity;
import fd.persistence.Domain;
import io.cloudstate.javasupport.eventsourced.CommandContext;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.Assert.*;

public class CustomerFraudDetectionTest {

    private final static String ruleId = "1";
    @Test
    public void addTransaction(){
        CommandContext context = Mockito.mock(CommandContext.class);
        String customerId = "1";
        int transAmountCents = 1001;
        int ruleMaxAmountCents = 1000;
        int ruleMaxCount = 2;
        CustomerEntity entity = createAndCreateCustomer(customerId,ruleMaxAmountCents,ruleMaxCount);

        Service.AddTransactionCommand addTrans = createAddCommand(transAmountCents);
        entity.addTransaction(addTrans,context);

        Domain.TransactionAdded addedTrans = createAddedEvent(addTrans,ruleMaxAmountCents);
        entity.transactionAdded(addedTrans);
        Mockito.verify(context).emit(addedTrans);


        addTrans = createAddCommand(transAmountCents);
        entity.addTransaction(addTrans,context);

        addedTrans = createAddedEvent(addTrans,ruleMaxAmountCents);
        entity.transactionAdded(addedTrans);
        Mockito.verify(context).emit(addedTrans);

        addTrans = createAddCommand(transAmountCents);
        entity.addTransaction(addTrans,context);

        addedTrans = createAddedEvent(addTrans,ruleMaxAmountCents);
        entity.transactionAdded(addedTrans);
        Mockito.verify(context).emit(addedTrans);

        addTrans = createAddCommand(transAmountCents);
        entity.addTransaction(addTrans,context);

        addedTrans = createAddedEvent(addTrans,ruleMaxAmountCents);
        entity.transactionAdded(addedTrans);
        Mockito.verify(context).emit(addedTrans);

//        Service.CustomerState cs = entity.getCustomer(Service.GetCustomerCommand.newBuilder().setCustomerId(customerId).build(),context);
//        assertEquals(2,cs.getTransCount());
    }

    private Service.AddTransactionCommand createAddCommand(int transAmountCents){
        return Service.AddTransactionCommand.newBuilder()
                .setTrans(Service.Transaction.newBuilder()
                        .setAmountCents(transAmountCents)
                        .setTimestamp(System.currentTimeMillis())
                        .setTransId(UUID.randomUUID().toString())
                        .build())
                .build();
    }
    private Domain.TransactionAdded createAddedEvent(Service.AddTransactionCommand addTrans,int ruleMaxAmountCents){
        return Domain.TransactionAdded.newBuilder()
                .setTrans(Mappers.fromApi(addTrans.getTrans())
                        .setFraudReport(Domain.FraudDetectionReport.newBuilder()
                                .setRiskScore(addTrans.getTrans().getAmountCents()>ruleMaxAmountCents?100:0)
                                .setRuleId(ruleId)
                                .setPotentialFraud(addTrans.getTrans().getAmountCents()>ruleMaxAmountCents)
                                .build()))
                .build();
    }

    private CustomerEntity createAndCreateCustomer(String customerId, int ruleMaxAmountCents, int ruleMaxCount){
        CommandContext context = Mockito.mock(CommandContext.class);
        CustomerEntity entity = new CustomerEntity(customerId);
        Domain.Customer dCustomer = Domain.Customer.newBuilder()
                .setRule(Domain.FraudDetectionRule.newBuilder()
                        .setRuleId(ruleId)
                        .setTransBacktrackHours(1)
                        .setMaxAmountCents(ruleMaxAmountCents)
                        .setTransBacktrackMaxCount(ruleMaxCount)
                        .build())
                .build();
        Service.CreateCustomerCommand create = Service.CreateCustomerCommand.newBuilder()
                .setCustomerId(customerId)
                .setRule(Mappers.toApi(dCustomer.getRule()))
                .build();
        entity.createCustomer(create,context);
        Domain.CustomerCreated created = Domain.CustomerCreated.newBuilder().setCustomer(dCustomer).build();
        entity.customerCreated(created);
        Mockito.verify(context).emit(created);

        return entity;
    }
}
