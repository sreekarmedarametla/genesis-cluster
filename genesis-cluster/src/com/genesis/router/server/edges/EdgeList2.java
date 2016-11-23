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

import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EdgeList2 {
	
	private static Logger logger = LoggerFactory.getLogger("edge list");
	
	protected ConcurrentHashMap<String, EdgeInfo> map = new ConcurrentHashMap<String, EdgeInfo>();

	public EdgeList2() {
		
	}

	public EdgeInfo createIfNew(int ref, String host, int port) {
		if (hasNode(ref))
			return getNode(ref);
		else
			return addNode(ref, host, port);
	}

	public EdgeInfo addNode(int ref, String host, int port) {
		if (!verify(ref, host, port)) {
			// TODO log error
			logger.error("errror in verification fo the node with the follwoing details");
			
			logger.error("Node id : "+ref);
			logger.error("host : " + host);
			logger.error("port no : "+port);
			return null;
			//throw new RuntimeException("Invalid node info");
		}

		if (!hasNode(ref)) {
			EdgeInfo ei = new EdgeInfo(ref, host, port);
			map.put(host, ei);
			return ei;
		} else
			return null;
	}

	private boolean verify(int ref, String host, int port) {
		if (ref < 0 || host == null || port < 1024)
			return false;
		else
			return true;
	}

	public boolean hasNode(int ref) {
		return map.containsKey(ref);

	}

	public EdgeInfo getNode(int ref) {
		return map.get(ref);
	}

	public void removeNode(int ref) {
		map.remove(ref);
	}

	public void clear() {
		map.clear();
	}
}
