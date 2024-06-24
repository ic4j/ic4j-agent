package org.ic4j.agent.replicaapi;


import java.util.Optional;

import org.ic4j.agent.AgentError;
import org.ic4j.agent.Serialize;
import org.ic4j.agent.Serializer;
import org.ic4j.agent.requestid.RequestId;

final class QueryResponseSignable implements  Serialize {
	
	Optional<Replied> replied;

	Optional<Rejected> rejected;
	
	String status;

	public QueryResponseSignable(QueryResponse queryResponse, RequestId requestId, long timestamp) {
		if(queryResponse != null)
		{
			if(queryResponse.status.equals(QueryResponse.InnerStatus.REPLIED_STATUS))
			{
				this.rejected = Optional.empty();
				Replied replied = new Replied();
				
				replied.reply = queryResponse.replied.get();
				replied.requestId = requestId;
				replied.timestamp = timestamp;
				
				this.status = QueryResponse.REPLIED_STATUS_VALUE;
				this.replied = Optional.of(replied);
			}
			else
			{
				this.replied = Optional.empty();
				
				Rejected rejected = new Rejected();
				
				rejected.rejectCode = queryResponse.rejected.get().rejectCode;
				rejected.rejectMessage = queryResponse.rejected.get().rejectMessage;	
				rejected.errorCode = queryResponse.rejected.get().errorCode;			
				rejected.requestId = requestId;
				rejected.timestamp = timestamp;
				
				this.status = QueryResponse.REJECTED_STATUS_VALUE;
				this.rejected = Optional.of(rejected);
			}
				
		}
		else
			throw AgentError.create( AgentError.AgentErrorCode.MESSAGE_ERROR);
		
	}

	@Override
	public void serialize(Serializer serializer) {
		serializer.serializeField("status", this.status);
		if(this.replied.isPresent())
		{
			serializer.serializeField("reply", this.replied.get().reply);
			serializer.serializeField("request_id", this.replied.get().requestId.get());
			serializer.serializeField("timestamp", this.replied.get().timestamp);
		}
		if(this.rejected.isPresent())
		{
			serializer.serializeField("reject_code", this.rejected.get().rejectCode.getValue());
			serializer.serializeField("reject_message", this.rejected.get().rejectMessage);
			serializer.serializeField("error_code", this.rejected.get().errorCode.get());
			serializer.serializeField("request_id", this.rejected.get().requestId.get());
			serializer.serializeField("timestamp", this.rejected.get().timestamp);			
		}	
	}
	
	class Replied{
		CallReply reply;
		RequestId requestId;
		long timestamp;
		
	}
	
	class Rejected{
		RejectCode rejectCode;
		String rejectMessage;	
		Optional<String> errorCode;
		RequestId requestId;
		long timestamp;		
	}

}
