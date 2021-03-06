package com.genesis.resource;

import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.genesis.router.client.CommInit;
import com.genesis.router.server.ServerState;
import com.genesis.router.server.edges.EdgeInfo;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.message.ClientMessage.ChunkInfo;
import com.message.ClientMessage.Operation;
import com.message.ClientMessage.RequestMessage;
import com.message.ClientMessage.ResponseMessage;

import global.Global.File;
import global.Global.GlobalHeader;
import global.Global.GlobalMessage;
import global.Global.RequestType;
import global.Global.Response;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import pipe.common.Common.Failure;
import pipe.common.Common.Header;
import pipe.common.Common.Node;
import pipe.election.Election.LeaderStatus;
import pipe.election.Election.LeaderStatus.LeaderQuery;
import pipe.election.Election.LeaderStatus.LeaderState;
import pipe.work.Work.DragonBeat;
import pipe.work.Work.Heartbeat;
import pipe.work.Work.Moderator;
import pipe.work.Work.NodeLinks;
import pipe.work.Work.Register;
import pipe.work.Work.Task;
import pipe.work.Work.Vote;
import pipe.work.Work.Vote.Verdict;
import pipe.work.Work.WorkMessage;
import pipe.work.Work.WorkState;
import routing.Pipe.CommandMessage;

public class ResourceUtil {

	private static Logger logger = LoggerFactory.getLogger("resource util");
	
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
	
	public static Node edgeToNode (EdgeInfo ei) {
		Node.Builder node = Node.newBuilder();
		node.setId(ei.getRef());
		node.setHost(ei.getHost());
		node.setPort(ei.getPort());
		return node.build();
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

	
	public static WorkMessage wrapIntoWorkMessage(EdgeInfo ei, int destID, Task.Builder newTask) {
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
		
		wb.setTask(newTask);
		return wb.build();
		
	}
	

	public static CommandMessage createResponseCommandMessage(CommandMessage commandMessage, byte[] data, int seqNo, ServerState state, int noOfChunks){
		
		//logger.info("creating command message");
		CommandMessage.Builder resCmdMessage = CommandMessage.newBuilder();
		RequestMessage reqMsg = commandMessage.getReqMsg();
		
		Header.Builder hb = buildHeader( commandMessage, state);
		
		ResponseMessage.Builder resMsg = ResponseMessage.newBuilder();
		
		resMsg.setSuccess(true);
		resMsg.setOperation(reqMsg.getOperation());
		resMsg.setKey(reqMsg.getKey());
		resMsg.setChunkNo(seqNo);
		resMsg.setNoOfChunks(noOfChunks);
		resMsg.setData(ByteString.copyFrom(data));
		
		
		/*
		try {
				if(seqNo == 0){
					logger.info("RU:createResponseCommandMessage Getting chunk seq."+ seqNo);
					resMsg.setChunkInfo(ChunkInfo.parseFrom(data));
				} else {
					logger.info("RU:createResponseCommandMessage Getting data seq." + seqNo);
					resMsg.setData(ByteString.copyFrom(data));
				}
				
			} catch (Exception e) {
				e.printStackTrace();
			}*/
		
		resCmdMessage.setHeader(hb);
		resCmdMessage.setResMsg(resMsg);
		
		
		return resCmdMessage.build();
	}
	
public static CommandMessage createAckMessage(CommandMessage commandMessage, ServerState state, String msg){
		
		//Simple response command message!
		CommandMessage.Builder resCmdMessage = CommandMessage.newBuilder();
		RequestMessage reqMsg = commandMessage.getReqMsg();
		
		Header.Builder hb = buildHeader( commandMessage, state);
		
		ResponseMessage.Builder resMsg = ResponseMessage.newBuilder();
		
		resMsg.setSuccess(true);
		resMsg.setOperation(reqMsg.getOperation());
		resMsg.setStatusMsg(msg);
		resMsg.setKey(reqMsg.getKey());
		resCmdMessage.setHeader(hb);
		resCmdMessage.setResMsg(resMsg);
		
		
		return resCmdMessage.build();
	}
	
	
	
	
	public static CommandMessage createResponseFailureMessage(CommandMessage commandMessage, ServerState state){
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
	
	public static CommandMessage createglobalFailureMessage(CommandMessage commandMessage, ServerState state){
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
	
	private static Header.Builder buildHeader(CommandMessage commandMessage, ServerState state) {
		
		Header.Builder hb = Header.newBuilder();
		hb.setNodeId(state.getConf().getNodeId());
		hb.setOrigin(commandMessage.getHeader().getOrigin());
		hb.setTime(System.currentTimeMillis());
		hb.setDestination(commandMessage.getHeader().getNodeId());
		
		return hb;
	}
	
	/**
	 * Constructs WorkMessage from Task
	 * @param task
	 * @param state
	 * @return
	 */
	
	public static WorkMessage buildWorkMessageFromTask(Task task, ServerState state) {
		
		WorkMessage.Builder wb = WorkMessage.newBuilder();
		wb.setHeader(buildHeader(task.getCommandMessage(), state));
		wb.setSecret(1); //dummy value
		wb.setTask(task);
		return wb.build();
	}
	
	public static WorkMessage buildWorkMessageFromCommandMsg(CommandMessage commandMessage, ServerState state){
		
		Task.Builder myTask = Task.newBuilder();
		myTask.setCommandMessage(commandMessage);
		myTask.setSeqId(myTask.getSeqId());
		myTask.setSeriesId(myTask.getSeriesId());
		
		return buildWorkMessageFromTask(myTask.build(), state);
		
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

	public static WorkMessage buildRegisterNewbieResponse(EdgeInfo leader, int ref) {
		
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
		
		Node.Builder leaderNode = Node.newBuilder();
		leaderNode.setId(leader.getRef());
		leaderNode.setHost(leader.getHost());
		leaderNode.setPort(leader.getPort());
		
		Register.Builder register = Register.newBuilder();
		register.setMode("RegisterRespnse");
		register.setLeader(leaderNode.build());
		
		wb.setRegister(register);
		return wb.build();
	}

	public static Channel getChannel(EdgeInfo ei) {
		if(ei.getChannel() != null && ei.isActive())
			return ei.getChannel();
		return createTemporaryChannel(ei);
	}
	private static Channel createTemporaryChannel( EdgeInfo ei) {
		EventLoopGroup group = new NioEventLoopGroup();
		LinkedBlockingDeque<CommandMessage> outbound = 
				new LinkedBlockingDeque<CommandMessage>();
		try {
//			GlobalCommandChannelInitializer si = new GlobalCommandChannelInitializer(conf,false);
			CommInit si = new CommInit(false);
			Bootstrap b = new Bootstrap();
			b.group(group).channel(NioSocketChannel.class).handler(si);
			b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 100000);
			b.option(ChannelOption.TCP_NODELAY, true);
			b.option(ChannelOption.SO_KEEPALIVE, true);

			// Make the connection attempt.
			ChannelFuture channel = b.connect(ei.getHost(), ei.getPort()).syncUninterruptibly();
			//ClientClosedListener ccl = new ClientClosedListener(this);
			//channel.channel().closeFuture();

			logger.info(channel.channel().remoteAddress() + " -> open: "
					+channel.channel().isActive() + ", isActive"
				+ channel.channel().isOpen() + ", write: "
				+ channel.channel().isWritable() + ", reg: "
				+ channel.channel().isRegistered());
			return channel.channel();
		}
		catch(Exception e) {
			logger.error("got an error in creating a temporary "
					+ "command channel to talk to client");
		}
		return null;
		
	}

	public static WorkMessage buildMooderatorMessage(EdgeInfo ei, String id, Node origin, EdgeInfo thisNode) {
		
		Header.Builder hb = Header.newBuilder();
		hb.setOrigin(edgeToNode(thisNode));
		hb.setDestination(ei.getRef());
		hb.setTime(System.currentTimeMillis());
		
		WorkMessage.Builder wb = WorkMessage.newBuilder();
		
		wb.setSecret(1001);
		wb.setHeader(hb);
		
		Moderator.Builder moderator = Moderator.newBuilder();
		moderator.setId(id);
		moderator.setOrigin(origin);
		
		wb.setModerator(moderator.build());
		
		return wb.build();
	}

	public static CommandMessage convertIntoCommand(GlobalMessage msg) {
		// TODO Auto-generated method stub
		
		CommandMessage.Builder resCmdMessage = CommandMessage.newBuilder();
		
		
		//RequestMessage reqMsg = commandMessage.getReqMsg();
		//TODO update the Header fields 
		Header.Builder hb = Header.newBuilder();
		//hb.setNodeId(state.getConf().getNodeId());
		//hb.setOrigin();
		hb.setTime(System.currentTimeMillis());
		hb.setDestination(msg.getGlobalHeader().getDestinationId());
		
		
		ResponseMessage.Builder resMsg = ResponseMessage.newBuilder();
		
		resMsg.setSuccess(true);
		switch(msg.getResponse().getRequestType()){
		case READ:
			resMsg.setOperation(Operation.GET);
			break;
			
		case WRITE:
			resMsg.setOperation(Operation.POST);
			break;
			
		case UPDATE:
			resMsg.setOperation(Operation.PUT);
			break;
		case DELETE:
			resMsg.setOperation(Operation.DEL);
			break;
		
		default:
			System.out.println("Nothing matching was found");
		
		}
	
		resMsg.setKey(msg.getResponse().getFileName());
		resMsg.setNoOfChunks(msg.getResponse().getFile().getTotalNoOfChunks());
		resMsg.setData(msg.getResponse().getFile().getData());
		
		resCmdMessage.setHeader(hb);
		resCmdMessage.setResMsg(resMsg);
		
		return resCmdMessage.build();
		
	}

	public static GlobalMessage createGlobalResponseMessage(GlobalMessage msg, byte[] value, Integer key,
			ServerState state, int noOfChunks) {
		
		logger.info("RU : creating response global message  ===> "); 
		
				GlobalHeader.Builder gh = GlobalHeader.newBuilder();
				gh.setClusterId(msg.getGlobalHeader().getClusterId());
				gh.setTime(System.currentTimeMillis());
				gh.setDestinationId(msg.getGlobalHeader().getDestinationId());
				
				File.Builder fb = File.newBuilder();
				fb.setFilename(msg.getRequest().getFileName());
				fb.setChunkId(key);
				fb.setTotalNoOfChunks(noOfChunks);
				fb.setData(ByteString.copyFrom(value));
				
				Response.Builder res = Response.newBuilder();
				res.setRequestType(msg.getRequest().getRequestType());
				res.setRequestId(msg.getRequest().getRequestId());
				res.setSuccess(true);
				
			
				GlobalMessage.Builder responseMsg = GlobalMessage.newBuilder();
				responseMsg.setGlobalHeader(gh);
				logger.info("response === " +res.build());
				res.setFile(fb);
				responseMsg.setResponse(res.build());
				//logger.info("response msg "+responseMsg.build());
				return responseMsg.build();
		
	}
	
}
