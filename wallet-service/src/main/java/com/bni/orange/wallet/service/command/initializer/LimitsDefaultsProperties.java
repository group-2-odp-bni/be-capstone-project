package com.bni.orange.wallet.service.command.initializer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Data;

@ConfigurationProperties(prefix = "orange.limits.default")
@Data
public class LimitsDefaultsProperties {
    private long perTxMinRp;
    private long perTxMaxRp;
    private long dailyMaxRp;
    private long weeklyMaxRp;
    private long monthlyMaxRp;
    private boolean enforcePerTx = true;
    private boolean enforceDaily = true;
    private boolean enforceWeekly = true;
    private boolean enforceMonthly = true;
    private String timezone = "Asia/Jakarta";
}
