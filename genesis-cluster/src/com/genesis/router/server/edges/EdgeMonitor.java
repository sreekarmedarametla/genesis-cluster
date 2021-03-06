/**
 * Copyright 2016 Gash.
 *
 * This file and intellectual content is protected under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.genesis.router.server.edges;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.genesis.monitors.ElectionMonitor;
import com.genesis.monitors.NetworkMonitor;
import com.genesis.monitors.QueueMonitor;
import com.genesis.queues.Queue;
import com.genesis.resource.ResourceUtil;
import com.genesis.router.container.RoutingConf.RoutingEntry;
import com.genesis.router.server.CommandInit;
import com.genesis.router.server.STATE;
import com.genesis.router.server.ServerState;
import com.genesis.router.server.WorkInit;
import com.google.protobuf.ByteString;
import com.message.ClientMessage.RequestMessage;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import pipe.common.Common.Header;
import pipe.common.Common.Node;
import pipe.election.Election.LeaderStatus;
import pipe.election.Election.LeaderStatus.LeaderQuery;
import pipe.work.Work.NodeLinks;
import pipe.work.Work.Task;
import pipe.work.Work.TaskType;
import pipe.work.Work.Vote.Verdict;
import routing.Pipe.CommandMessage;
import pipe.work.Work.WorkMessage;
import pipe.work.Work.WorkState;

public class EdgeMonitor implements EdgeListener, Runnable {
	protected static Logger logger = LoggerFactory.getLogger("edge monitor");

	private NetworkMonitor nmon = NetworkMonitor.getInstance();
	private int hackCounter = 0;
	private EdgeList outboundEdges;
	private EdgeList inboundEdges;
	private EdgeList failedInNodes = new EdgeList();
	private EdgeList failedOutNodes = new EdgeList();
	private EdgeInfo thisNode;
	private EdgeInfo leader;
	private QueueMonitor qMon ;
	private Queue lazyQ = null;
	private ElectionMonitor eMonitor = new ElectionMonitor();
	private long dt = 2000;
	private ServerState state;
	private int candidateRetry = 0;
	private int leaderStatus = 0;
	private boolean forever = true;

	private int term = 0;

	public EdgeMonitor(ServerState state) {
		if (state == null)
			throw new RuntimeException("state is null");
		this.thisNode = new EdgeInfo(state.getConf().getNodeId(),
				state.getConf().getMyHost(),state.getConf().getWorkPort());
		this.outboundEdges = new EdgeList();
		this.inboundEdges = new EdgeList();
		this.state = state;
		this.state.setEmon(this);
		this.qMon = state.getQueueMonitor();
		lazyQ = qMon.getLazyQueue();

		if (state.getConf().getRouting() != null) {
			for (RoutingEntry e : state.getConf().getRouting()) {
				outboundEdges.addNode(e.getId(), e.getHost(), e.getPort());
			}
		}

		if (state.getConf().getHeartbeatDt() > this.dt)
			this.dt = state.getConf().getHeartbeatDt();
	}

	public EdgeInfo createInboundIfNew(int ref, String host, int port) {
		if(ref != 0)
		return inboundEdges.createIfNew(ref, host, port);
		return null;
	}

	public void updateHeartBeat(Node node,long heartBeat,WorkState wstate){
		EdgeInfo edge = createInboundIfNew(node.getId(), node.getHost(), node.getPort());
		edge.setLastHeartbeat(heartBeat);
		edge.setEnqueue(wstate.getEnqueued());
		edge.setProcessed(wstate.getProcessed());
		if(wstate.getTerm() > this.term ){
			if(state.state == STATE.LEADER){
				state.state = STATE.FOLLOWER;
			}
			this.term = wstate.getTerm();
		}
	}
	

	public void shutdown() {
		forever = false;
	}

	@Override
	public void run() {
		while (forever) {
			try {
				switch(state.state){
					case ORPHAN :{
						//askWhoIsLeader();
						checkLeaderStatus();
						if(this.inboundEdges.map != null && this.inboundEdges.map.size() > 0) {
							state.state = STATE.FOLLOWER;
						}
						else{
							registerNode();
						}
						break;
					}
					case FOLLOWER :{
						checkLeaderStatus();
						candidateRetry = 0;
						checkInbound();
						pushHeartBeat();
						if(failedOutNodes.map != null && failedOutNodes.map.size() > 0)
							reportOutNodeFailure();						
						if(leader != null && "DEAD".equalsIgnoreCase(leader.status)){
							candidateRetry = 0;
							LeaderStatus lStatus= eMonitor.init(thisNode);
							term++;
							prepareAndPassElection(lStatus);
							state.state = STATE.CANDIDATE;					
						}
						if(failedInNodes.map != null && failedInNodes.map.size() > 0)
							reportInNodeFailure();
						currentStats();
						break;
					}
					case LEADER :{
						candidateRetry = 0;
						checkInbound();
						pushHeartBeat();
						if(failedOutNodes.map != null && failedOutNodes.map.size() > 0)
							reportOutNodeFailure();						
						if(failedInNodes.map != null && failedInNodes.map.size() > 0)
							reportInNodeFailure();
						currentStats();
						if(inboundEdges.map.isEmpty() && outboundEdges.map.isEmpty()){
							logger.info("rogue node");
							state.state = STATE.FOLLOWER;
						}
						break;
					}
					case VOTED:{
						candidateRetry = 0;
						checkInbound();
						pushHeartBeat();
						if(failedOutNodes.map != null && failedOutNodes.map.size() > 0)
							reportOutNodeFailure();						
						if(failedInNodes.map != null && failedInNodes.map.size() > 0)
							reportInNodeFailure();
						currentStats();
						break;
					}
					case CANDIDATE :{
						candidateRetry ++;
						int totalNodes = nmon.nmap.size() > nmon.nmap.size() ?
								nmon.nmap.size() : nmon.nmap.size();
							
						checkInbound();
						pushHeartBeat();
						if(failedOutNodes.map != null && failedOutNodes.map.size() > 0)
							reportOutNodeFailure();						
						if(failedInNodes.map != null && failedInNodes.map.size() > 0)
							reportInNodeFailure();
						logger.info("candidate retry count and total nodes "
								+ ": "+candidateRetry +" : "+totalNodes);
						if( eMonitor.getVoted() > (totalNodes - 2 ) / 2){
							candidateRetry = 0;
							state.state = STATE.LEADER;
							leader = thisNode;
							claimLeadership();
							
						}
						if(candidateRetry > 3){
							candidateRetry = 0;
							LeaderStatus lStatus= eMonitor.init(thisNode);
							term++;
							prepareAndPassElection(lStatus);
							
						}
						break;
					}
					
				
				}
				Thread.sleep(dt);
				
			} catch (Exception e) {
				e.printStackTrace();
			}

		}
	}
	
	public void claimLeadership() {
		WorkMessage.Builder leaderB = WorkMessage.newBuilder();
		LeaderStatus.Builder status = LeaderStatus.newBuilder();
		status.setAction(LeaderQuery.THELEADERIS);
		leaderB.setLeader(status.build());
		Header.Builder header = Header.newBuilder();
		Node.Builder origin = Node.newBuilder();
		origin.setId(state.getConf().getNodeId());
		origin.setHost(state.getConf().getMyHost());
		origin.setPort(state.getConf().getWorkPort());
		header.setOrigin(origin.build());
		header.setTime(System.currentTimeMillis());
		leaderB.setHeader(header);
		leaderB.setSecret(1002);
		passMsg(leaderB.build());
		initDragonBeat(1);
		
	}

	private void checkLeaderStatus() {
		if(leader != null && "ALIVE".equalsIgnoreCase(leader.status)){
			leaderStatus = 0;
			return;
		}
		if(leader != null){
			if("UNKNOWN".equalsIgnoreCase(leader.status)){
				logger.info("leader status is unknown , i'm going to wait for 4 more ticks");
				leaderStatus++;
			}
		}
		else if(leader == null ){
			logger.info("leader is not found , i'm going to wait for 4 more ticks");
			leaderStatus++;
		}
		if(leaderStatus > 4){
			leaderStatus = 0;
			candidateRetry = 0;
			LeaderStatus lStatus= eMonitor.init(thisNode);
			prepareAndPassElection(lStatus);
			state.state = STATE.CANDIDATE;		
		}
	}
	
	private void askWhoIsLeader() {
		for (EdgeInfo ei : this.outboundEdges.map.values()) {
			if (ei.getChannel() != null && ei.isActive()) {
				ei.retry = 0;
				WorkMessage wm = ResourceUtil.enquireLeader(thisNode,ei.getRef());
				ei.getChannel().writeAndFlush(wm);
			} else {
				try{
					logger.info("trying to connect to node " + ei.getRef());
					EventLoopGroup group = new NioEventLoopGroup();
					WorkInit si = new WorkInit(state, false);
					Bootstrap b = new Bootstrap();
					b.group(group).channel(NioSocketChannel.class).handler(si);
					b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000);
					b.option(ChannelOption.TCP_NODELAY, true);
					b.option(ChannelOption.SO_KEEPALIVE, true);
					
					ChannelFuture channel = b.connect(ei.getHost(), ei.getPort()).syncUninterruptibly();
					
					ei.setChannel(channel.channel());
					ei.setActive(channel.channel().isActive());
				}
				catch(Exception e){
					logger.error("Failed outbound node, i'm clueless");
				}
			}
		}
		
		
	}

	private void currentStats() {
		logger.info("current state "+state.state);
		logger.info("current inbounds "+this.inboundEdges.map.keySet());
		logger.info("current outbounds "+this.outboundEdges.map.keySet());
		logger.info("inbound queue size [" +qMon.getInboundQueue().getSize()+"]");
		logger.info("Outbouond queue size ["+qMon.getOutboundQueue().getSize()+"]");
		logger.info("Lazy queue size ["+qMon.getLazyQueue().getSize()+"]");
		logger.info("Global outbound worker queue ["+state.getGlobalOutboundQueue().size()+"]");

	}

	private void prepareAndPassElection(LeaderStatus lStatus) {
		for (EdgeInfo ei : this.outboundEdges.map.values()) {
			if (ei.getChannel() != null && ei.isActive()) {
				WorkMessage wm = ResourceUtil.createElectionMsg(thisNode,ei.getRef(),lStatus);
				ei.getChannel().writeAndFlush(wm);
			}
		}
	}

	private void registerNode() {
		
		for (EdgeInfo ei : this.outboundEdges.map.values()) {
			if (ei.getChannel() != null && ei.isActive()) {
				WorkMessage wm = ResourceUtil.createRegisterMsg(thisNode,ei.getRef());
				ei.getChannel().writeAndFlush(wm);
			} else {
				try{
					EventLoopGroup group = new NioEventLoopGroup();
					WorkInit si = new WorkInit(state, false);
					Bootstrap b = new Bootstrap();
					b.group(group).channel(NioSocketChannel.class).handler(si);
					b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000);
					b.option(ChannelOption.TCP_NODELAY, true);
					b.option(ChannelOption.SO_KEEPALIVE, true);
					
					ChannelFuture channel = b.connect(ei.getHost(), ei.getPort()).syncUninterruptibly();
					
					ei.setChannel(channel.channel());
					ei.setActive(channel.channel().isActive());
				}
				catch(Exception e){
					logger.error("error in registering with node "+ei.getRef());
				}
			}
		}
		
	}

	private void reportOutNodeFailure() {
		
		List<NodeLinks> links = nmon.nmap;
		for(EdgeInfo ei : failedOutNodes.map.values()) {
			for(NodeLinks node : links){
				if(ei.getRef() == node.getMe().getId()){
					List<Node> lists = checkCircularNodes(node.getOutboundList());
					if(lists.size() > 0){
						registerNewOutbound(lists);
					}
					else{
						logger.info("circular loop might have encountered, removing node "+ei.getRef()
						+" from failedInNodes");
						failedOutNodes.map.remove(ei.getRef());
					}
				}
			}
			this.outboundEdges.removeNode(ei.getRef());

		}
	}

	private void registerNewOutbound(List<Node> outboundList) {
		for(Node node : outboundList)
			if(node.getId() != thisNode.getRef() && node.getId() != 0)
				this.outboundEdges.createIfNew(node.getId(), node.getHost() , node.getPort());
	}
	
	private void registerNewInbound(List<Node> inboundList) {
		for(Node node : inboundList)
			if(this.inboundEdges.map.containsKey(node.getId())){
				logger.info("Node node "+node.getId()+" connected to node "+thisNode.getRef());
				failedInNodes.map.remove(node.getId());
			}
			else {
				logger.info(" --- waiting for the other out bound edge "+node.getId() 
				+"to connect --- ");
			}
	}


	private void reportInNodeFailure() {
		
		List<NodeLinks> links = nmon.nmap;
		for(EdgeInfo ei : failedInNodes.map.values()) {
			for(NodeLinks node : links){
				if(ei.getRef() == node.getMe().getId()){
					List<Node> lists = checkCircularNodes(node.getInboundList());
					if(lists.size() > 0){
						registerNewInbound(lists);
					}
					else{
						logger.info("circular loop might have encountered, removing node "+ei.getRef()
						+" from failedInNodes");
						failedInNodes.map.remove(ei.getRef());
					}
				
				}
			}
			this.inboundEdges.removeNode(ei.getRef());	
		}
		
	}

	private List<Node> checkCircularNodes(List<Node> badList) {
		List<Node> goodList = new ArrayList<Node>();
		for(Node n: badList){
			/*
			 * uncomment to enforce atleast three nodes in the ring
			 * circular nodes checks if the failed node is connected to any of
			 * the inbound or outbound nodes associated with the current node
			 */
			/*if(!this.outboundEdges.map.containsKey(n.getId()) &&
					!this.inboundEdges.map.containsKey(n.getId()) &&
					n.getId() != thisNode.getRef())*/
			/*
			 * it's okay to form a cluster with just 2 nodes as long as it is 
			 * not connected to itself
			 */
			if(n.getId() != thisNode.getRef())
				goodList.add(n);
		}
		return goodList;
	}

	private void checkInbound() {
		for(EdgeInfo ei: this.inboundEdges.map.values()){
			if(ei.getLastHeartbeat() != -1 ) {
				if( System.currentTimeMillis() - ei.getLastHeartbeat() < 2*dt )
					logger.info("inbound edge health good!");
				else{
					if(leader != null){
						if(ei.getRef() == leader.getRef()){
							// initiate election
							logger.info("leader is dead initiate leader election");
							leader.status = "DEAD";
						}
					}
					logger.info("node "+ei.getRef()+" failed, adding it to failed-in list");
					failedInNodes.map.put(ei.getRef(), ei);
				}
			}
			else
				logger.info("still waiting for server "+ei.getRef());
		}
	}

	private void pushHeartBeat(){

		WorkState.Builder sb = WorkState.newBuilder();
		sb.setEnqueued(qMon.getInboundQueue().numEnqueued());
		sb.setProcessed(qMon.getInboundQueue().numProcessed());
		sb.setTerm(this.term);

		for (EdgeInfo ei : this.outboundEdges.map.values()) {
			if (ei.getChannel() != null && ei.isActive()) {
				ei.retry = 0;
				WorkMessage wm = ResourceUtil.createHB(thisNode,sb.build(),ei.getRef());
				ei.getChannel().writeAndFlush(wm);
			} else {
				try{
					EventLoopGroup group = new NioEventLoopGroup();
					WorkInit si = new WorkInit(state, false);
					Bootstrap b = new Bootstrap();
					b.group(group).channel(NioSocketChannel.class).handler(si);
					b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000);
					b.option(ChannelOption.TCP_NODELAY, true);
					b.option(ChannelOption.SO_KEEPALIVE, true);
					
					ChannelFuture channel = b.connect(ei.getHost(), ei.getPort()).syncUninterruptibly();
					
					ei.setChannel(channel.channel());
					ei.setActive(channel.channel().isActive());
				}
				catch(Exception e){
					logger.error("error in conecting to node "+ei.getRef()+" exception "+e.getMessage());
					if(++ei.retry > 2){
						if(leader != null){
							
							if(ei.getRef() == leader.getRef()){
							// initiate election
								logger.info("leader is dead initiate leader election");
								leader.status = "DEAD";
							}
							
						}
						logger.info("node "+ei.getRef()+" failed, adding it to failed-out list");
						failedOutNodes.map.put(ei.getRef(), ei);
					}
					else{
						logger.info("retrying connection to "+ei.getRef()+" count : "+ei.retry);
					}
				}
			}
		}

	
	}

	public void setLeader(EdgeInfo e){
		this.leader = e;
		this.leader.status = "ALIVE";
		state.state = STATE.FOLLOWER;
	}
	
	public EdgeInfo getLeader(){
		return leader != null ? leader: null;
	}
	
	@Override
	public synchronized void onAdd(EdgeInfo ei) {
		// TODO check connection
	}

	@Override
	public synchronized void onRemove(EdgeInfo ei) {
		// TODO ?
	}

	public void initDragonBeat(int outCheckSum) {
		NodeLinks links = prepareDragonBeatMsg();
		for (EdgeInfo ei : this.outboundEdges.map.values()) {
			if (ei.getChannel() != null && ei.isActive()) {
				WorkMessage wm = ResourceUtil.createDB("L1",thisNode, ei.getRef() ,links , outCheckSum);
				ei.getChannel().writeAndFlush(wm);
			}
		}
	}

	public NodeLinks prepareDragonBeatMsg() {
		NodeLinks.Builder links = NodeLinks.newBuilder();
		for(EdgeInfo in : this.inboundEdges.map.values()){
			Node.Builder node = Node.newBuilder();
			node.setId(in.getRef());
			node.setHost(in.getHost());
			node.setPort(in.getPort());
			links.addInbound(node);
		}
		Node.Builder me = Node.newBuilder();
		me.setId(thisNode.getRef());
		me.setHost(thisNode.getHost());
		me.setPort(thisNode.getPort());
		links.setMe(me);
		for(EdgeInfo out : this.outboundEdges.map.values()){
			Node.Builder node = Node.newBuilder();
			node.setId(out.getRef());
			node.setHost(out.getHost());
			node.setPort(out.getPort());
			links.addOutbound(node);
		}
		return links.build();
	}

	public void passOnDragon(String mode,List<NodeLinks> links, int outCheckSum) {
		for (EdgeInfo ei : this.outboundEdges.map.values()) {
			if (ei.getChannel() != null && ei.isActive()) {
				WorkMessage wm = ResourceUtil.createDBList(mode,thisNode, ei.getRef() ,links , outCheckSum);
				ei.getChannel().writeAndFlush(wm);
			}
		}
		
	}

	public void passMsg(WorkMessage leader2) {
		for (EdgeInfo ei : this.outboundEdges.map.values()) {
			if (ei.getChannel() != null && ei.isActive()) {
				logger.info("Edge monitor sending leader msg "+leader2);
				ei.getChannel().writeAndFlush(leader2);
			}
		}
	}

	public void registerNewbie(Node newbie) {
		boolean isSuccess = false;
		for (EdgeInfo ei : this.inboundEdges.map.values()) {
			WorkMessage wm = ResourceUtil.createNewbieMessage(thisNode,ei.getRef(),newbie);
			if(ei.getChannel() != null && ei.isActive()){
				logger.info("newbie msg "+wm);
				ei.getChannel().writeAndFlush(wm);
				isSuccess = true;
			}
			else{
				createPermanentChannel(ei);
				if(ei.getChannel() != null && ei.isActive()){
					logger.info("newbie msg "+wm);
					ei.getChannel().writeAndFlush(wm);
					isSuccess = true;
				}
			}
		}
		if(isSuccess)
			this.inboundEdges = new EdgeList();
	}

	
	public void replaceOutNode(EdgeInfo oldEdge, EdgeInfo newEdge) {
		
			logger.error("-----------------------------------");
			logger.error("replacing out node and in nodes");
			logger.error("-----------------------------------");			
			
			this.outboundEdges.removeNode(oldEdge.getRef());
			//onRemove(oldEdge);
			this.outboundEdges.addNode(newEdge.getRef(), newEdge.getHost(), newEdge.getPort());
		//onAdd(newEdge);
		
	}

	public void handleElectionMessage(WorkMessage msg) {
		
		String host =  msg.getLeader().getLeaderHost();
		int port = msg.getLeader().getLeaderPort();
		int id = msg.getLeader().getLeaderId();
		
		EdgeInfo candidate = new EdgeInfo(id,host,port);
		WorkMessage accept = ResourceUtil.createVoteMessage(thisNode,id,Verdict.VOTE);

		createPermanentChannel(candidate);
		if (candidate.getChannel() != null && candidate.isActive()) {
			candidate.getChannel().writeAndFlush(accept);
		}
		prepareAndPassElection(msg.getLeader());
		
	}

	private Channel createPermanentChannel(EdgeInfo ei) {
		if(!ei.isActive()) {
			try{
				logger.info("trying to connect to node " + ei.getRef());
				EventLoopGroup group = new NioEventLoopGroup();
				WorkInit si = new WorkInit(state, false);
				Bootstrap b = new Bootstrap();
				b.group(group).channel(NioSocketChannel.class).handler(si);
				b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000);
				b.option(ChannelOption.TCP_NODELAY, true);
				b.option(ChannelOption.SO_KEEPALIVE, true);
				
				ChannelFuture channel = b.connect(ei.getHost(), ei.getPort()).syncUninterruptibly();
				
				ei.setChannel(channel.channel());
				ei.setActive(channel.channel().isActive());
				return ei.getChannel();
			}catch(Exception e){
				e.printStackTrace();
			}
		}
		return null;
	}
	
	private Channel createCommandChannel(EdgeInfo ei) {
		if(!ei.isActive()) {
			try{
				logger.info("trying to connect to node " + ei.getRef());
				EventLoopGroup group = new NioEventLoopGroup();
				CommandInit si = new CommandInit(state, false);
				Bootstrap b = new Bootstrap();
				b.group(group).channel(NioSocketChannel.class).handler(si);
				b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000);
				b.option(ChannelOption.TCP_NODELAY, true);
				b.option(ChannelOption.SO_KEEPALIVE, true);
				
				ChannelFuture channel = b.connect(ei.getHost(), ei.getPort()).syncUninterruptibly();
				
				ei.setChannel(channel.channel());
				ei.setActive(channel.channel().isActive());
				return ei.getChannel();
			}catch(Exception e){
				e.printStackTrace();
			}
		}
		return null;
	}

	
	public void handleVote(WorkMessage msg) {
		// TODO Auto-generated method stub
		eMonitor.setVotedNum(1);
	}

	public void handleStealer() {
		if(state.state == STATE.FOLLOWER)
		if(shouldStealTask()){
			for (EdgeInfo ei : this.outboundEdges.map.values()) {
				
				if (ei.getChannel() != null && ei.isActive()) {
		
					Header.Builder hb = Header.newBuilder();
					hb.setNodeId(state.getConf().getNodeId());
					hb.setDestination(ei.getRef());
					hb.setTime(System.currentTimeMillis());
					WorkMessage.Builder wb = WorkMessage.newBuilder();
					wb.setHeader(hb);
					wb.setSteal(true);
					wb.setSecret(1);
					ei.getChannel().writeAndFlush(wb.build());
				}
			}
			
		}
	}
	
	private boolean shouldStealTask() {
		
		if(qMon.getInboundQueue().getSize() < 2)
			return true;
		return false;
	}
	
	public void sendToLazyQueue(Task oldTask, ByteString data) {
		
		Node me = ResourceUtil.edgeToNode(thisNode);
		Task.Builder newTask = oldTask.newBuilder();
		newTask.setType(TaskType.LAZYTASK);
		newTask.setSeqId(oldTask.getSeqId());
		newTask.setSeriesId(oldTask.getSeriesId());
		CommandMessage.Builder cmd = CommandMessage.newBuilder();
		//newTask.setCommandMessage(t.getCommandMessage());
		RequestMessage.Builder reqMsg = RequestMessage.newBuilder();
		reqMsg.setData(data);
		reqMsg.setKey(oldTask.getCommandMessage().getReqMsg().getKey());
		reqMsg.setOperation(oldTask.getCommandMessage().getReqMsg().getOperation());
		reqMsg.setSeqNo(oldTask.getCommandMessage().getReqMsg().getSeqNo());
		cmd.setReqMsg(reqMsg.build());
		cmd.setHeader(oldTask.getCommandMessage().getHeader());
		newTask.addProcessed(me);
		newTask.setCommandMessage(cmd.build());
		
		for(EdgeInfo ei : this.outboundEdges.map.values()) {
			if(ei.getChannel() != null && ei.isActive()) {
				ei.getChannel().writeAndFlush(ResourceUtil.wrapIntoWorkMessage(thisNode,ei.getRef(),newTask));
			}
			else {
				logger.error("lazying delayed because of inactive channel to node "+ei.getRef());
			}
		}
	}

	

	public WorkMessage helpFindLeaderNode(WorkMessage msg) {
		if(leader != null){
			return ResourceUtil.buildRegisterNewbieResponse(leader, msg.getHeader().getOrigin().getId());
		}
		return null;
	}

	public void updateAndBoradCast(Task oldTask,ByteString data) {
		
		Node me = ResourceUtil.edgeToNode(thisNode);
		Task.Builder newTask = oldTask.newBuilder();
		newTask.setType(TaskType.LAZYTASK);
		newTask.setSeqId(oldTask.getSeqId());
		newTask.setSeriesId(oldTask.getSeriesId());
		CommandMessage.Builder cmd = CommandMessage.newBuilder();
		//newTask.setCommandMessage(t.getCommandMessage());
		RequestMessage.Builder reqMsg = RequestMessage.newBuilder();
		reqMsg.setData(data);
		reqMsg.setKey(oldTask.getCommandMessage().getReqMsg().getKey());
		reqMsg.setOperation(oldTask.getCommandMessage().getReqMsg().getOperation());
		reqMsg.setSeqNo(oldTask.getCommandMessage().getReqMsg().getSeqNo());
		cmd.setReqMsg(reqMsg.build());
		cmd.setHeader(oldTask.getCommandMessage().getHeader());
		newTask.addProcessed(me);
		newTask.setCommandMessage(cmd.build());
		
		
		/*
		Node me = ResourceUtil.edgeToNode(thisNode);
		Task.Builder newTask = task.newBuilder();
		*/
		List<Node> nodeList = oldTask.getProcessedList();
		Set<Integer> nodeIds = new TreeSet<Integer>();
		for(Node search : nodeList) {
			nodeIds.add(search.getId());
			if(search.getId() == me.getId())
				return;
		}
		/*nodeList.add(me);
		newTask.addAllProcessed(nodeList);
		*/
		List<Node> mutableCollection = new ArrayList<Node>();
		mutableCollection.addAll(nodeList);
		mutableCollection.add(me);
		newTask.addAllProcessed(mutableCollection);
		
		for(EdgeInfo ei : this.outboundEdges.map.values()) {
			if(!nodeIds.contains(ei.getRef()) && 
					ei.getChannel() != null && ei.isActive()) {
				
				ei.getChannel().writeAndFlush(ResourceUtil.wrapIntoWorkMessage(thisNode,ei.getRef(),newTask));
			}
			else {
				logger.error("lazying delayed because of inactive channel to node "+ei.getRef());
			}
		}
		
	}

	public void updateMooderator(String id, Node origin) {
		
		for(EdgeInfo ei : this.outboundEdges.map.values()) {
			if(ei.getChannel() != null && ei.isActive()){
				WorkMessage wm = ResourceUtil.buildMooderatorMessage(ei,id,origin,thisNode);
				ei.getChannel().writeAndFlush(wm);
			}
		}
	}

	public void handleModerator(WorkMessage wm) {
		if(thisNode.getRef() != wm.getHeader().getOrigin().getId()){
			Channel channel = 
					createCommandChannel(ResourceUtil.nodeToEdge(wm.getModerator().getOrigin()));
			if(channel != null) {
				state.moderator.put(wm.getModerator().getId(), channel);
			}
			else {
				logger.info("error in creating a moderator channel to client");
			}
			for(EdgeInfo ei : this.outboundEdges.map.values()) {
				if(ei.getChannel() != null && ei.isActive()){
					logger.info("passing on the moderator message");
					ei.getChannel().writeAndFlush(wm);
				}
			}
		}
		
	}
}
