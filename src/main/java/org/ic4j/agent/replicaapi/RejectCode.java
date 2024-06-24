package org.ic4j.agent.replicaapi;

enum RejectCode{
    /// Fatal system error, retry unlikely to be useful
    SysFatal(1),
    /// Transient system error, retry might be possible.
    SysTransient(2),
    /// Invalid destination (e.g. canister/account does not exist)
    DestinationInvalid(3),
    /// Explicit reject by the canister.
    CanisterReject(4),
    /// Canister error (e.g., trap, no response)
    CanisterError(5);
	
	int value;
    
	RejectCode(int value) {
    	this.value = value;
	}
	
	int getValue()
	{
		return this.value;
	}
	
	static RejectCode create(int value)
	{
		
		switch(value)
		{
		case 1:
			return RejectCode.SysFatal;
		case 2:
			return RejectCode.SysTransient;
		case 3:
			return RejectCode.DestinationInvalid;
		case 4:
			return RejectCode.CanisterReject;
		case 5:
			return RejectCode.CanisterError;
		default:
			return RejectCode.SysFatal;		
		}
		
	}

}
