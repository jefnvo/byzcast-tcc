/**
 * 
 */
package ch.usi.inf.dslab.bftamcast.server;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import bftsmart.communication.client.CommunicationSystemServerSide;
import bftsmart.communication.client.CommunicationSystemServerSideFactory;
import bftsmart.communication.client.ReplyListener;
import bftsmart.tom.AsynchServiceProxy;
import bftsmart.tom.MessageContext;
import bftsmart.tom.ReplicaContext;
import bftsmart.tom.RequestContext;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.server.FIFOExecutable;
import bftsmart.tom.server.Replier;
import ch.usi.inf.dslab.bftamcast.graph.Tree;
import ch.usi.inf.dslab.bftamcast.graph.Vertex;
import ch.usi.inf.dslab.bftamcast.kvs.Request;
import ch.usi.inf.dslab.bftamcast.kvs.RequestType;
import ch.usi.inf.dslab.bftamcast.util.RequestTracker;

/**
 * @author Christian Vuerich - christian.vuerich@usi.ch
 *
 *         Temporary class to start experimenting and understanding byzcast
 * 
 *         - understand byzcast structure - make server and clients non blocking
 *         (asynchronous) - remove auxiliary groups and use target groups to
 *         build the overlay tree
 */
public class UniversalReplier implements Replier, FIFOExecutable, Serializable, ReplyListener {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private Tree overlayTree;
	private int groupId;
	protected transient Lock replyLock;
	protected transient Condition contextSet;
	protected transient ReplicaContext rc;
	protected Request req;
	private Map<Integer, byte[]> table;
	private Map<Integer, RequestTracker> repliesTracker;
	private SortedMap<Integer, Vector<TOMMessage>> globalReplies;
	private Vertex me;

	public UniversalReplier(int RepID, int groupID, String treeConfig) {

		this.overlayTree = new Tree(treeConfig, UUID.randomUUID().hashCode());
		this.groupId = groupID;
		me = overlayTree.findVertexById(groupID);

		replyLock = new ReentrantLock();
		contextSet = replyLock.newCondition();
		globalReplies = new TreeMap<>();
		repliesTracker = new HashMap<>();
		table = new TreeMap<>();
		req = new Request();
	}

	@Override
	public void manageReply(TOMMessage request, MessageContext msgCtx) {
		// http://www.javapractices.com/topic/TopicAction.do?Id=56
		// UUID.fromString("server"+group+serverid)
		// call second
		while (rc == null) {
			try {
				this.replyLock.lock();
				this.contextSet.await();
				this.replyLock.unlock();
			} catch (InterruptedException ex) {
				Logger.getLogger(AmcastLocalReplier.class.getName()).log(Level.SEVERE, null, ex);
			}
		}

		req.fromBytes(request.getContent());
		System.out.println("seq #" + req.getSeqNumber());
		System.out.println("sender " + request.getSender());
		System.out.println("called manageReply");

		// for
		// if me execute
		// if in child send
		// else for child, if child reach send, if not already sent (child also a
		// destination)
		boolean sent = false;
		int[] destinations = req.getDestination();
		int majNeeded = 0;
		List<Vertex> toSend = new ArrayList<>();
		// List<Vertex>
		for (int i = 0; i < destinations.length; i++) {
			if (destinations[i] == groupId) {
				// execute
				// Request ans = execute(req);
				// reply
				request.reply.setContent(req.toBytes());
				rc.getServerCommunicationSystem().send(new int[] { request.getSender() }, request.reply);
				// CommunicationSystemServerSide clientsConn =
				// CommunicationSystemServerSideFactory.getCommunicationSystemServerSide(rc.getSVController().);
				// rc.getServerCommunicationSystem().send(new int[] { 5 }, request.reply);
			}
			// my child in tree is a destination, forward it
			else if (me.childernIDs.contains(destinations[i])) {
				Vertex v = overlayTree.findVertexById(destinations[i]);

				majNeeded += (int) Math.ceil((double) (v.proxy.getViewManager().getCurrentViewN()
						+ v.proxy.getViewManager().getCurrentViewF() + 1) / 2.0);
				toSend.add(v);
			}
			// destination must be in the path of only one of my childrens
			else {

				for (Vertex v : me.children) {
					if (v.inReach(destinations[i])) {
						if (!toSend.contains(v)) {
							majNeeded += (int) Math.ceil((double) (v.proxy.getViewManager().getCurrentViewN()
									+ v.proxy.getViewManager().getCurrentViewF() + 1) / 2.0);
							toSend.add(v);
						}
						break;// only one path
					}
				}
			}

		}

		repliesTracker.put(req.getSeqNumber(), new RequestTracker(majNeeded, request.getSender(), request));
		System.out.println("neeeeeded     " + majNeeded);
		for (Vertex v : toSend) {
			v.asyncAtomicMulticast(req, this);
		}

	}

	protected Request execute(Request req) {
		byte[] resultBytes;
		boolean toMe = false;

		for (int i = 0; i < req.getDestination().length; i++) {
			if (req.getDestination()[i] == groupId) {
				toMe = true;
				break;
			}
		}

		if (!toMe) {
			System.out.println("Message not addressed to my group.");
			req.setType(RequestType.NOP);
			req.setValue(null);
		} else {
			switch (req.getType()) {
			case PUT:
				resultBytes = table.put(req.getKey(), req.getValue());
				break;
			case GET:
				resultBytes = table.get(req.getKey());
				break;
			case REMOVE:
				resultBytes = table.remove(req.getKey());
				break;
			case SIZE:
				resultBytes = ByteBuffer.allocate(4).putInt(table.size()).array();
				break;
			default:
				resultBytes = null;
				System.err.println("Unknown request type: " + req.getType());
			}

			req.setValue(resultBytes);
		}
		return req;
	}

	@Override
	public void setReplicaContext(ReplicaContext rc) {
		this.replyLock.lock();
		this.rc = rc;
		this.contextSet.signalAll();
		this.replyLock.unlock();
	}

	@Override
	public byte[] executeOrderedFIFO(byte[] bytes, MessageContext messageContext, int i, int i1) {
		System.out.println("called executeOrderedFIFO");
		// call first
		return bytes;
	}

	@Override
	public byte[] executeUnorderedFIFO(byte[] bytes, MessageContext messageContext, int i, int i1) {
		throw new UnsupportedOperationException("Universal replier only accepts ordered messages");
	}

	@Override
	public byte[] executeOrdered(byte[] bytes, MessageContext messageContext) {
		throw new UnsupportedOperationException("All ordered messages should be FIFO");
	}

	@Override
	public byte[] executeUnordered(byte[] bytes, MessageContext messageContext) {
		throw new UnsupportedOperationException("All ordered messages should be FIFO");
	}

	@Override
	public void reset() {
		// TODO Auto-generated method stub

	}

	@Override
	public void replyReceived(RequestContext context, TOMMessage reply) {

		Request replyReq = new Request();
		replyReq.fromBytes(reply.getContent());
		RequestTracker tracker = repliesTracker.get(replyReq.getSeqNumber());
		if (tracker != null && tracker.addReply(replyReq)) {
			System.out.println("finish, sent up req # " + replyReq.getSeqNumber());
			tracker.getRecivedRequest().reply.setContent(replyReq.toBytes());
			rc.getServerCommunicationSystem().send(new int[] { tracker.getReplier() },
					tracker.getRecivedRequest().reply);

			// repliesTracker.remove(replyReq.getSeqNumber());

		}
	}
}
