package fd;

import fd.persistence.Domain;

import java.util.Collections;
import java.util.stream.Collectors;

public class Mappers {

    public static Domain.Customer.Builder fromApi(Service.CreateCustomerCommand cmd){
        return Domain.Customer.newBuilder()
                .setRule(fromApi(cmd.getRule()))
                .addAllTrans(cmd.getTransHistoryList().stream().map(Mappers::fromApi).map(Domain.Transaction.Builder::build).collect(Collectors.toList()));
    }
    public static Service.CustomerState.Builder toApi(Domain.Customer customer){
        return Service.CustomerState.newBuilder()
                .setRule(toApi(customer.getRule()))
                .addAllTrans(customer.getTransList().stream().map(Mappers::toApi).map(Service.TransactionState.Builder::build).collect(Collectors.toList()));
    }

    public static Domain.FraudDetectionRule.Builder fromApi(Service.FraudDetectionRule rule){
        return Domain.FraudDetectionRule.newBuilder()
                .setRuleId(rule.getRuleId())
                .setMaxAmountCents(rule.getMaxAmountCents())
                .setTransBacktrackHours(rule.getTransBacktrackHours())
                .setTransBacktrackMaxCount(rule.getTransBacktrackMaxCount());

    }
    public static Service.FraudDetectionRule.Builder toApi(Domain.FraudDetectionRule rule){
        return Service.FraudDetectionRule.newBuilder()
                .setRuleId(rule.getRuleId())
                .setMaxAmountCents(rule.getMaxAmountCents())
                .setTransBacktrackHours(rule.getTransBacktrackHours())
                .setTransBacktrackMaxCount(rule.getTransBacktrackMaxCount());

    }
    public static Domain.Transaction.Builder fromApi(Service.Transaction trans){
        return Domain.Transaction.newBuilder()
                .setTransId(trans.getTransId())
                .setAmountCents(trans.getAmountCents())
                .setTimestamp(trans.getTimestamp());
    }
    public static Service.Transaction.Builder toApiTrans(Domain.Transaction trans){
        return Service.Transaction.newBuilder()
                .setTransId(trans.getTransId())
                .setAmountCents(trans.getAmountCents())
                .setTimestamp(trans.getTimestamp());
    }
    public static Service.TransactionState.Builder toApi(Domain.Transaction trans){
        return Service.TransactionState.newBuilder()
                .setTrans(toApiTrans(trans))
                .setFraudReport(toApi(trans.getFraudReport()));
    }
    public static Service.FraudDetectionReport.Builder toApi(Domain.FraudDetectionReport report){
        if(report==null || !report.isInitialized())
            return null;
        return Service.FraudDetectionReport.newBuilder()
                .setRiskScore(report.getRiskScore())
                .setPotentialFraud(report.getPotentialFraud());
    }

}
