package com.genesis.resource;

import java.util.List;

import com.genesis.router.server.ServerState;
import com.genesis.router.server.edges.EdgeInfo;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.message.ClientMessage.ChunkInfo;
import com.message.ClientMessage.RequestMessage;
import com.message.ClientMessage.ResponseMessage;

import pipe.common.Common.Failure;
import pipe.common.Common.Header;
import pipe.common.Common.Node;
import pipe.election.Election.LeaderStatus;
import pipe.election.Election.LeaderStatus.LeaderQuery;
import pipe.election.Election.LeaderStatus.LeaderState;
import pipe.work.Work.DragonBeat;
import pipe.work.Work.Heartbeat;
import pipe.work.Work.NodeLinks;
import pipe.work.Work.Register;
import pipe.work.Work.Task;
import pipe.work.Work.Vote;
import pipe.work.Work.Vote.Verdict;
import pipe.work.Work.WorkMessage;
import pipe.work.Work.WorkState;
import routing.Pipe.CommandMessage;

public class ResourceUtil {

	
	public static WorkMessage createHB(EdgeInfo ei,WorkState sb,int destID) {
		Node.Builder nb = Node.newBuilder();
		nb.setId(ei.getRef());
		nb.setHost(ei.getHost());
		nb.setPort(ei.getPort());
		
		Heartbeat.Builder bb = Heartbeat.newBuilder();
		bb.setState(sb);

		Header.Builder hb = Header.newBuilder();
		hb.setOrigin(nb);
		hb.setDestination(destID);
		hb.setTime(System.currentTimeMillis());

		WorkMessage.Builder wb = WorkMessage.newBuilder();
		wb.setHeader(hb);
		wb.setSecret(1001);
		wb.setBeat(bb);

		return wb.build();
	}

	public static WorkMessage createDB(String mode,EdgeInfo ei, int destID , NodeLinks links , int outCheckSum) {

		Node.Builder nb = Node.newBuilder();
		nb.setId(ei.getRef());
		nb.setHost(ei.getHost());
		nb.setPort(ei.getPort());
		
		Header.Builder hb = Header.newBuilder();
		hb.setOrigin(nb);
		hb.setDestination(destID);
		hb.setTime(System.currentTimeMillis());
		
		WorkMessage.Builder wb = WorkMessage.newBuilder();
		DragonBeat.Builder dragon = DragonBeat.newBuilder();
		dragon.addNodelinks(links);
		dragon.setChecksum(outCheckSum);
		dragon.setMode(mode);
		wb.setSecret(1001);
		wb.setHeader(hb);
		wb.setDragon(dragon);
		
		return wb.build();
	}

	public static WorkMessage createDBList(String mode,EdgeInfo ei, int destID, 
		
		List<NodeLinks> links, int outCheckSum) {
		Node.Builder nb = Node.newBuilder();
		nb.setId(ei.getRef());
		nb.setHost(ei.getHost());
		nb.setPort(ei.getPort());
		
		Header.Builder hb = Header.newBuilder();
		hb.setOrigin(nb);
		hb.setDestination(destID);
		hb.setTime(System.currentTimeMillis());
		
		WorkMessage.Builder wb = WorkMessage.newBuilder();
		DragonBeat.Builder dragon = DragonBeat.newBuilder();
		dragon.addAllNodelinks(links);
		dragon.setChecksum(outCheckSum);
		dragon.setMode(mode);
		wb.setSecret(1001);
		wb.setHeader(hb);
		wb.setDragon(dragon);
		return wb.build();
	}

	public static WorkMessage createRegisterMsg(EdgeInfo ei, int destID) {
		
		Node.Builder nb = Node.newBuilder();
		nb.setId(ei.getRef());
		nb.setHost(ei.getHost());
		nb.setPort(ei.getPort());
		
		Header.Builder hb = Header.newBuilder();
		hb.setOrigin(nb);
		hb.setDestination(destID);
		hb.setTime(System.currentTimeMillis());
		
		WorkMessage.Builder wb = WorkMessage.newBuilder();
		
		wb.setSecret(1001);
		wb.setHeader(hb);

		Register.Builder rb = Register.newBuilder();
		rb.setMode("NEWBIE");
		wb.setRegister(rb);
		
		return wb.build();
	}

	public static WorkMessage createNewbieMessage(EdgeInfo ei, int destID, Node newbie) {
		
		Node.Builder nb = Node.newBuilder();
		nb.setId(ei.getRef());
		nb.setHost(ei.getHost());
		nb.setPort(ei.getPort());
		
		Header.Builder hb = Header.newBuilder();
		hb.setOrigin(nb);
		hb.setDestination(destID);
		hb.setTime(System.currentTimeMillis());
		
		WorkMessage.Builder wb = WorkMessage.newBuilder();
		
		wb.setSecret(1001);
		wb.setHeader(hb);

		Register.Builder rb = Register.newBuilder();
		rb.setMode("NEWBIE");
		rb.setDestNode(newbie);
		wb.setRegister(rb);
		
		return wb.build();
	}
	
	public static EdgeInfo nodeToEdge (Node node){
		return new EdgeInfo(node.getId(),node.getHost(),node.getPort());
		
	}

	public static LeaderStatus createElectionMessage(EdgeInfo thisNode) {
		
		LeaderStatus.Builder leader = LeaderStatus.newBuilder();
		leader.setAction(LeaderQuery.WHOISTHELEADER);
		leader.setState(LeaderState.LEADERDEAD);
		
		leader.setLeaderHost(thisNode.getHost());
		leader.setLeaderId(thisNode.getRef());
		leader.setLeaderPort(thisNode.getPort());
		
		return leader.build();
	}

	public static WorkMessage createElectionMsg(EdgeInfo ei, int destID, LeaderStatus lStatus) {
		WorkMessage.Builder wm = WorkMessage.newBuilder();
		Header.Builder header = Header.newBuilder();
		
		Node.Builder nb = Node.newBuilder();
		nb.setId(ei.getRef());
		nb.setHost(ei.getHost());
		nb.setPort(ei.getPort());
		
		Header.Builder hb = Header.newBuilder();
		hb.setOrigin(nb);
		hb.setDestination(destID);
		hb.setTime(System.currentTimeMillis());
				
		wm.setSecret(1001);
		wm.setHeader(hb);
		wm.setLeader(lStatus);
		
		return wm.build();		
	}

	public static WorkMessage createVoteMessage(EdgeInfo ei, int destID, Verdict vote) {
		WorkMessage.Builder wm = WorkMessage.newBuilder();
		Header.Builder header = Header.newBuilder();
		
		Node.Builder nb = Node.newBuilder();
		nb.setId(ei.getRef());
		nb.setHost(ei.getHost());
		nb.setPort(ei.getPort());
		
		Header.Builder hb = Header.newBuilder();
		hb.setOrigin(nb);
		hb.setDestination(destID);
		hb.setTime(System.currentTimeMillis());
				
		wm.setSecret(1001);
		wm.setHeader(hb);
		
		Vote.Builder voB = Vote.newBuilder();
		voB.setVerdict(vote);
		wm.setVerdict(voB);
		
		return wm.build();		
	}

	
	public static WorkMessage wrapIntoWorkMessage(EdgeInfo ei, int destID, Task t) {
		Node.Builder nb = Node.newBuilder();
		nb.setId(ei.getRef());
		nb.setHost(ei.getHost());
		nb.setPort(ei.getPort());
		
		Header.Builder hb = Header.newBuilder();
		hb.setOrigin(nb);
		hb.setDestination(destID);
		hb.setTime(System.currentTimeMillis());
		
		WorkMessage.Builder wb = WorkMessage.newBuilder();
		
		wb.setSecret(1001);
		wb.setHeader(hb);
		
		wb.setTask(t);
		return wb.build();
		
	}
	

	public CommandMessage createResponseCommandMessage(CommandMessage commandMessage, byte[] data, int seqNo, ServerState state){
		
		//logger.info("creating command message");
		CommandMessage.Builder resCmdMessage = CommandMessage.newBuilder();
		RequestMessage reqMsg = commandMessage.getReqMsg();
		
		Header.Builder hb = buildHeader( commandMessage, state);
		
		ResponseMessage.Builder resMsg = ResponseMessage.newBuilder();
		
		resMsg.setSuccess(true);
		resMsg.setOperation(reqMsg.getOperation());
		resMsg.setKey(reqMsg.getKey());
		resMsg.setChunkNo(seqNo);
		
		try {
				if(seqNo == 0){
					resMsg.setChunkInfo(ChunkInfo.parseFrom(data));
				} else {
					resMsg.setData(ByteString.copyFrom(data));
				}
				
			} catch (InvalidProtocolBufferException e) {
				e.printStackTrace();
			}
		
		resCmdMessage.setHeader(hb);
		resCmdMessage.setResMsg(resMsg);
		
		
		return resCmdMessage.build();
	}
	
	
	public CommandMessage createResponseFailureMessage(CommandMessage commandMessage, ServerState state){
		//logger.info("Creating failure message ");
		CommandMessage.Builder resCmdMessage = CommandMessage.newBuilder();
		
		Header.Builder hb = buildHeader(commandMessage, state);
		
		ResponseMessage.Builder resMsg = ResponseMessage.newBuilder();
		Failure.Builder fb = Failure.newBuilder();
		fb.setMessage("Key not found in the database");
		fb.setId(11);

		resMsg.setSuccess(false);
		resMsg.setFailure(fb);
		
		
		resCmdMessage.setHeader(hb);
		resCmdMessage.setResMsg(resMsg);
		
		return resCmdMessage.build();
	}
	
	private Header.Builder buildHeader(CommandMessage commandMessage, ServerState state) {
		
		Header.Builder hb = Header.newBuilder();
		hb.setNodeId(state.getConf().getNodeId());
		hb.setTime(System.currentTimeMillis());
		hb.setDestination(commandMessage.getHeader().getNodeId());
		return hb;
	}
	
	
	public static WorkMessage enquireLeader(EdgeInfo ei, int ref) {
		Node.Builder nb = Node.newBuilder();
		nb.setId(ei.getRef());
		nb.setHost(ei.getHost());
		nb.setPort(ei.getPort());
		
		Header.Builder hb = Header.newBuilder();
		hb.setOrigin(nb);
		hb.setDestination(ref);
		hb.setTime(System.currentTimeMillis());
		
		WorkMessage.Builder wb = WorkMessage.newBuilder();
		
		wb.setSecret(1001);
		wb.setHeader(hb);
		
		LeaderStatus.Builder leader = LeaderStatus.newBuilder();
		leader.setAction(LeaderQuery.WHOISTHELEADER);
		
		wb.setLeader(leader);
		return wb.build();
	}

	public static WorkMessage buildWhoIsLeaderResponseNode(EdgeInfo leader, int ref) {
		
		Node.Builder nb = Node.newBuilder();
		nb.setId(leader.getRef());
		nb.setHost(leader.getHost());
		nb.setPort(leader.getPort());
		
		Header.Builder hb = Header.newBuilder();
		hb.setOrigin(nb);
		hb.setDestination(ref);
		hb.setTime(System.currentTimeMillis());
		
		WorkMessage.Builder wb = WorkMessage.newBuilder();
		
		wb.setSecret(1001);
		wb.setHeader(hb);
		
		LeaderStatus.Builder status = LeaderStatus.newBuilder();
		status.setAction(LeaderQuery.THELEADERIS);
		status.setState(LeaderState.LEADERALIVE);
		
		wb.setLeader(status);
		return wb.build();
	}
	
	

}
