package com.ezeebit.wallet.application.port.in;

import java.util.List;

public interface GetBalancesUseCase {
    List<BalanceView> balancesOf(long merchantId);
}
