package com.bookinghealthy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "vietqr")
public class VietQrProperties {

    private String bankId;

    private String accountNo;

    private String accountName;

    private String memoPrefix;

    public String getBankId() { return bankId; }
    public void setBankId(String bankId) { this.bankId = bankId; }

    public String getAccountNo() { return accountNo; }
    public void setAccountNo(String accountNo) { this.accountNo = accountNo; }

    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }

    public String getMemoPrefix() { return memoPrefix; }
    public void setMemoPrefix(String memoPrefix) { this.memoPrefix = memoPrefix; }
}
