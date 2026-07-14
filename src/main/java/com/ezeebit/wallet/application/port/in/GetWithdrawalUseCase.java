package com.ezeebit.wallet.application.port.in;

import com.ezeebit.wallet.application.port.in.RequestWithdrawalUseCase.WithdrawalView;

public interface GetWithdrawalUseCase {
    WithdrawalView get(long merchantId, String withdrawalId);
}
