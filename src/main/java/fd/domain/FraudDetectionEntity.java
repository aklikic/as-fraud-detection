package fd.domain;

import frauddetection.FraudDetectionCommon;
import frauddetection.Service;
import frauddetection.domain.FraudDetectionDomain;
import org.slf4j.Logger;
import com.google.protobuf.Empty;

import io.cloudstate.javasupport.EntityId;
import io.cloudstate.javasupport.eventsourced.*;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@EventSourcedEntity(persistenceId = "fraudDetection", snapshotEvery = 20)
public class FraudDetectionEntity {

    private int configMaxTransactionsCount = 100;
    private final String customerId;
    private FraudDetectionCommon.FraudDetectionState state;
    private static Logger log = LoggerFactory.getLogger(FraudDetectionEntity.class);

    public FraudDetectionEntity(@EntityId String customerId) {
        this.customerId = customerId;
    }

    public void setConfigMaxTransactionsCount(int configMaxTransactionsCount){
        this.configMaxTransactionsCount = configMaxTransactionsCount;
    }

    @Snapshot
    public FraudDetectionCommon.FraudDetectionState snapshot() {
        return state;
    }

    @SnapshotHandler
    public void handleSnapshot(FraudDetectionCommon.FraudDetectionState state) {
        this.state = state;
    }

    //commands

    @CommandHandler
    public Empty createFraudDetection(Service.CreateFraudDetectionCommand cmd, CommandContext ctx) {
        if(state!=null)
            return Empty.getDefaultInstance();
        ctx.emit(FraudDetectionDomain.FraudDetectionCreated.newBuilder()
                .setCustomerId(cmd.getCustomerId())
                .setRuleId(cmd.getRuleId())
                .setMaxAmountCents(cmd.getMaxAmountCents())
                .build());
        return Empty.getDefaultInstance();
    }
    @CommandHandler
    public FraudDetectionCommon.FraudDetectionState getFraudDetection(Service.GetFraudDetectionCommand cmd, CommandContext ctx) {
        if(state == null) {
            ctx.fail("Fraud detection does not exist");
            return null;
        }
        return state;
    }
    @CommandHandler
    public Empty updateFraudDetectionRule(Service.UpdateFraudDetectionRuleCommand cmd, CommandContext ctx){
        if(state == null) {
            ctx.fail("Fraud detection does not exist");
            return null;
        }
        ctx.emit(FraudDetectionDomain.FraudDetectionRuleUpdated.newBuilder()
                .setCustomerId(cmd.getCustomerId())
                .setRuleId(cmd.getRuleId())
                .setMaxAmountCents(cmd.getMaxAmountCents())
                .build());
        return Empty.getDefaultInstance();
    }
    @CommandHandler
    public FraudDetectionCommon.ScoredTransactionState addTransaction(Service.AddTransactionCommand cmd, CommandContext ctx){
        log.info("[{}] addTransaction: {}",cmd.getCustomerId(),cmd);
        if(state == null) {
            ctx.fail("Fraud detection does not exist");
            return null;
        }
        Optional<FraudDetectionCommon.ScoredTransactionState> existingTrans = checkTransExists(cmd.getTransactionId());
        if(existingTrans.isPresent())
            return existingTrans.get();

        //fraud detection logic
        FraudDetectionCommon.ScoredTransactionState trans = FraudDetectionCommon.ScoredTransactionState.newBuilder()
                .setCustomerId(cmd.getCustomerId())
                .setTransactionId(cmd.getTransactionId())
                .setAmountCents(cmd.getAmountCents())
                .setTimestamp(cmd.getTimestamp())
                .setRuleId(state.getRuleId())
                .setPotentialFraud(cmd.getAmountCents()>state.getMaxAmountCents())
                .setRiskScore(cmd.getAmountCents()>state.getMaxAmountCents()?100:0)
                .build();

        ctx.emit(FraudDetectionDomain.ScoredTransactionAdded.newBuilder()
                .setCustomerId(trans.getCustomerId())
                .setTransactionId(trans.getTransactionId())
                .setAmountCents(trans.getAmountCents())
                .setTimestamp(trans.getTimestamp())
                .setRuleId(trans.getRuleId())
                .setPotentialFraud(trans.getPotentialFraud())
                .setRiskScore(trans.getRiskScore())
                .build());

        List<FraudDetectionDomain.ScoredTransactionRemoved> transToRemove = getTransactionsToRemove(trans);
        log.info("[{}] Trans to remove: {}/{}",cmd.getCustomerId(),transToRemove.size(),(state.getTransactionsCount()+1));
        transToRemove.forEach(ctx::emit);

        return trans;
    }

    private List<FraudDetectionDomain.ScoredTransactionRemoved> getTransactionsToRemove(FraudDetectionCommon.ScoredTransactionState trans){
        FraudDetectionCommon.FraudDetectionState newState = state.toBuilder().addTransactions(trans).build();
        if(newState.getTransactionsCount()>configMaxTransactionsCount)
            return
            newState.getTransactionsList().subList(0, newState.getTransactionsCount()-configMaxTransactionsCount-1).stream()
                    .map(t -> FraudDetectionDomain.ScoredTransactionRemoved.newBuilder()
                            .setCustomerId(trans.getCustomerId())
                            .setTransactionId(t.getTransactionId())
                            .build()
                    ).collect(Collectors.toList());

        return new ArrayList<>();
    }



    private Optional<FraudDetectionCommon.ScoredTransactionState> checkTransExists(String transactionId){
        if(state.getTransactionsList()==null)
            return Optional.empty();
        return state.getTransactionsList().stream().filter(t->t.getTransactionId().equals(transactionId)).findFirst();
    }

    //events
    @EventHandler
    public void fraudDetectionCreated(FraudDetectionDomain.FraudDetectionCreated event) {
        state = FraudDetectionCommon.FraudDetectionState.newBuilder()
                .setCustomerId(event.getCustomerId())
                .setRuleId(event.getRuleId())
                .setMaxAmountCents(event.getMaxAmountCents())
                .build();
    }
    @EventHandler
    public void fraudDetectionRuleUpdated(FraudDetectionDomain.FraudDetectionRuleUpdated event) {
        state=state.toBuilder()
                .setRuleId(event.getRuleId())
                .setMaxAmountCents(event.getMaxAmountCents())
                .build();

    }
    @EventHandler
    public void transactionAdded(FraudDetectionDomain.ScoredTransactionAdded event) {

        FraudDetectionCommon.ScoredTransactionState.Builder trans = FraudDetectionCommon.ScoredTransactionState.newBuilder();
        trans.setCustomerId(event.getCustomerId());
        trans.setTransactionId(event.getTransactionId());
        trans.setAmountCents(event.getAmountCents());
        trans.setTimestamp(event.getTimestamp());
        trans.setRuleId(event.getRuleId());
        trans.setPotentialFraud(event.getPotentialFraud());
        trans.setRiskScore(event.getRiskScore());

        state = state.toBuilder().addTransactions(trans).build();
    }
    @EventHandler
    public void transactionRemoved(FraudDetectionDomain.ScoredTransactionRemoved event) {
        log.info("[{}] Trans list size before remove: {}",state.getCustomerId(),state.getTransactionsCount());
        List<FraudDetectionCommon.ScoredTransactionState> trans =
                state.getTransactionsList().stream().filter(t->t.getTransactionId().equals(event.getTransactionId())).collect(Collectors.toList());
        log.info("[{}] Trans list size after remove: {}",state.getCustomerId(),trans.size());
        state = state.toBuilder().clearTransactions().addAllTransactions(trans).build();
    }

}
