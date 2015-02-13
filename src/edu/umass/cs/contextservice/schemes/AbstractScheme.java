package edu.umass.cs.contextservice.schemes;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;

import edu.umass.cs.contextservice.AttributeTypes;
import edu.umass.cs.contextservice.ContextServiceProtocolTask;
import edu.umass.cs.contextservice.config.ContextServiceConfig;
import edu.umass.cs.contextservice.database.AbstractContextServiceDB;
import edu.umass.cs.contextservice.database.InMemoryContextServiceDB;
import edu.umass.cs.contextservice.database.MongoContextServiceDB;
import edu.umass.cs.contextservice.messages.BasicContextServicePacket;
import edu.umass.cs.contextservice.messages.ContextServicePacket;
import edu.umass.cs.contextservice.messages.QueryMsgFromUserReply;
import edu.umass.cs.contextservice.messages.QueryMsgToValuenodeReply;
import edu.umass.cs.contextservice.messages.RefreshTrigger;
import edu.umass.cs.contextservice.messages.ValueUpdateFromGNSReply;
import edu.umass.cs.contextservice.processing.QueryInfo;
import edu.umass.cs.contextservice.processing.UpdateInfo;
import edu.umass.cs.gns.nio.GenericMessagingTask;
import edu.umass.cs.gns.nio.InterfaceNodeConfig;
import edu.umass.cs.gns.nio.InterfacePacketDemultiplexer;
import edu.umass.cs.gns.nio.JSONMessenger;
import edu.umass.cs.gns.protocoltask.ProtocolEvent;
import edu.umass.cs.gns.protocoltask.ProtocolExecutor;
import edu.umass.cs.gns.protocoltask.ProtocolTask;

public abstract class AbstractScheme<NodeIDType> implements InterfacePacketDemultiplexer
{
	protected final JSONMessenger<NodeIDType> messenger;
	protected final ProtocolExecutor<NodeIDType, ContextServicePacket.PacketType, String> protocolExecutor;
	protected final ContextServiceProtocolTask<NodeIDType> protocolTask;
	
	protected final AbstractContextServiceDB<NodeIDType> contextserviceDB;
	
	protected final Object numMesgLock	;
	
	//private final List<AttributeMetadataInformation<NodeIDType>> attrMetaList;
	//private final List<AttributeValueInformation<NodeIDType>> attrValueList;
	protected final Set<NodeIDType> allNodeIDs;
	
	// stores the pending queries
	protected HashMap<Long, QueryInfo<NodeIDType>> pendingQueryRequests				= null;
	
	protected long queryIdCounter														= 0;
	
	protected final Object pendingQueryLock											= new Object();
	
	protected HashMap<Long, UpdateInfo<NodeIDType>> pendingUpdateRequests				= null;
	
	protected long updateIdCounter														= 0;
	
	protected final Object pendingUpdateLock											= new Object();
	
	// lock for synchronizing number of msg update
	protected long numMessagesInSystem													= 0;
	
	protected  DatagramSocket client_socket;
	
	
	public AbstractScheme(InterfaceNodeConfig<NodeIDType> nc, JSONMessenger<NodeIDType> m)
	{
		this.numMesgLock = new Object();
		
		this.allNodeIDs = nc.getNodeIDs();
		
		pendingQueryRequests  = new HashMap<Long, QueryInfo<NodeIDType>>();
		
		pendingUpdateRequests = new HashMap<Long, UpdateInfo<NodeIDType>>();
		
		
		switch(ContextServiceConfig.DATABASE_TYPE)
		{
			case INMEMORY:
			{
				this.contextserviceDB = new InMemoryContextServiceDB<NodeIDType>(m.getMyID());
				break;
			}
			case MONGODB:
			{
				this.contextserviceDB = new MongoContextServiceDB<NodeIDType>(m.getMyID());
				break;
			}
			default:
				this.contextserviceDB = null;
		}
		
		// initialize attribute types
		AttributeTypes.initialize();
		
		this.messenger = m;
		this.protocolExecutor = new ProtocolExecutor<NodeIDType, ContextServicePacket.PacketType, String>(messenger);
		this.protocolTask = new ContextServiceProtocolTask<NodeIDType>(getMyID(), this);
		this.protocolExecutor.register(this.protocolTask.getEventTypes(), this.protocolTask);
		
		try
		{
			client_socket = new DatagramSocket();
		} catch (SocketException e)
		{
			e.printStackTrace();
		}
	}
	
	// public methods
	
	public Set<ContextServicePacket.PacketType> getPacketTypes() 
	{
		return this.protocolTask.getEventTypes();
	}
	
	public NodeIDType getMyID() 
	{
		return this.messenger.getMyID();
	}
	
	/**
	 * returns all nodeIDs
	 * @return
	 */
	public Set<NodeIDType> getAllNodeIDs()
	{
		return this.allNodeIDs;
	}
	
	public void printTheStateAtNode()
	{
		this.contextserviceDB.printDatabase();
	}
	
	public JSONMessenger<NodeIDType> getJSONMessenger()
	{
		return messenger;
	}
	
	/**
	 * java has issues converting LisnkedList.toArray(), that's why this function
	 * @return
	 */
	public GenericMessagingTask<NodeIDType, ?>[] convertLinkedListToArray(LinkedList<?> givenList)
	{
		GenericMessagingTask<NodeIDType, ?>[] array = new GenericMessagingTask[givenList.size()];
		for(int i=0;i<givenList.size();i++)
		{
			array[i] = (GenericMessagingTask<NodeIDType, ?>) givenList.get(i);
		}
		return array;
	}
	
	public AbstractContextServiceDB<NodeIDType> getContextServiceDB()
	{
		return contextserviceDB;
	}
	
	@Override
	public boolean handleJSONObject(JSONObject jsonObject)
	{
		//System.out.println("\n\n\n handleJSONObject contextService json "+jsonObject+"\n\n\n ");
		BasicContextServicePacket<NodeIDType> csPacket = null;
		//if(DEBUG) Reconfigurator.log.finest("Reconfigurator received " + jsonObject);
		try 
		{
			// try handling as reconfiguration packet through protocol task 
			if((csPacket = this.protocolTask.getContextServicePacket(jsonObject))!=null) 
			{
				this.protocolExecutor.handleEvent(csPacket);
			} /*else if(isExternalRequest(jsonObject)) 
			{
				assert(false);
			}*/
		} catch(JSONException je) 
		{
			je.printStackTrace();
		}
		return true; // neither reconfiguration packet nor app request
	}
	
	public long getNumMesgInSystem()
	{
		return this.numMessagesInSystem;
	}
	
	protected void sendReplyBackToUser(QueryInfo<NodeIDType> qinfo, LinkedList<String> resultList)
	{
		QueryMsgFromUserReply<NodeIDType> qmesgUR
			= new QueryMsgFromUserReply<NodeIDType>(this.getMyID(), qinfo.getQuery(), 
					resultList, qinfo.getUserReqID());
		try
		{
			System.out.println("sendReplyBackToUser "+qinfo.getUserIP()+" "+qinfo.getUserPort()+
					qmesgUR.toJSONObject());
			
			this.messenger.sendToAddress(new InetSocketAddress(InetAddress.getByName(qinfo.getUserIP()), qinfo.getUserPort())
								, qmesgUR.toJSONObject());
		} catch (UnknownHostException e)
		{
			e.printStackTrace();
		} catch (IOException e)
		{
			e.printStackTrace();
		} catch (JSONException e)
		{
			e.printStackTrace();
		}
	}
	
	protected void sendUpdateReplyBackToUser(String sourceIP, int sourcePort, long versioNum)
	{
		ValueUpdateFromGNSReply<NodeIDType> valUR
			= new ValueUpdateFromGNSReply<NodeIDType>(this.getMyID(), versioNum);
		
		try
		{
			System.out.println("sendUpdateReplyBackToUser "+sourceIP+" "+sourcePort+
					valUR.toJSONObject());
			
			this.messenger.sendToAddress(
					new InetSocketAddress(InetAddress.getByName(sourceIP), sourcePort)
								, valUR.toJSONObject());
		} catch (UnknownHostException e)
		{
			e.printStackTrace();
		} catch (IOException e)
		{
			e.printStackTrace();
		} catch (JSONException e)
		{
			e.printStackTrace();
		}
	}
	
	protected void sendRefreshReplyBackToUser(String sourceIP, int sourcePort, 
			String query, String groupGUID)
	{
		RefreshTrigger<NodeIDType> valUR 
			= new RefreshTrigger<NodeIDType>(this.getMyID(), query, groupGUID);
		
		try
		{
			System.out.println("sendRefreshReplyBackToUser "+sourceIP+" "+sourcePort+
					valUR.toJSONObject());
			
			this.messenger.sendToAddress(
					new InetSocketAddress(InetAddress.getByName(sourceIP), sourcePort)
								, valUR.toJSONObject());
		} catch (UnknownHostException e)
		{
			e.printStackTrace();
		} catch (IOException e)
		{
			e.printStackTrace();
		} catch (JSONException e)
		{
			e.printStackTrace();
		}
	}
	
	/**
	 * spawns the protocol associated 
	 * spawning starts the start[] method
	 * of the protocol task
	 */
	public void spawnTheTask()
	{
		this.protocolExecutor.spawn(this.protocolTask);
	}
	
	// public abstract methods
	public abstract NodeIDType getResponsibleNodeId(String AttrName);
	
	public abstract GenericMessagingTask<NodeIDType,?>[] handleMetadataMsgToValuenode(
			ProtocolEvent<ContextServicePacket.PacketType, String> event,
			ProtocolTask<NodeIDType, ContextServicePacket.PacketType, String>[] ptasks);
	
	public abstract GenericMessagingTask<NodeIDType,?>[] handleQueryMsgFromUser(
			ProtocolEvent<ContextServicePacket.PacketType, String> event,
			ProtocolTask<NodeIDType, ContextServicePacket.PacketType, String>[] ptasks);
	
	public abstract GenericMessagingTask<NodeIDType,?>[] handleQueryMsgToMetadataNode(
			ProtocolEvent<ContextServicePacket.PacketType, String> event,
			ProtocolTask<NodeIDType, ContextServicePacket.PacketType, String>[] ptasks);
	
	public abstract GenericMessagingTask<NodeIDType,?>[] handleQueryMsgToValuenode(
			ProtocolEvent<ContextServicePacket.PacketType, String> event,
			ProtocolTask<NodeIDType, ContextServicePacket.PacketType, String>[] ptasks);
	
	public abstract GenericMessagingTask<NodeIDType,?>[] handleQueryMsgToValuenodeReply(
			ProtocolEvent<ContextServicePacket.PacketType, String> event,
			ProtocolTask<NodeIDType, ContextServicePacket.PacketType, String>[] ptasks);
	
	public abstract GenericMessagingTask<NodeIDType,?>[] handleValueUpdateMsgToMetadataNode(
			ProtocolEvent<ContextServicePacket.PacketType, String> event,
			ProtocolTask<NodeIDType, ContextServicePacket.PacketType, String>[] ptasks);
	
	public abstract GenericMessagingTask<NodeIDType,?>[] handleValueUpdateMsgToValuenode(
			ProtocolEvent<ContextServicePacket.PacketType, String> event,
			ProtocolTask<NodeIDType, ContextServicePacket.PacketType, String>[] ptasks);
	
	public abstract GenericMessagingTask<NodeIDType,?>[] handleValueUpdateFromGNS(
			ProtocolEvent<ContextServicePacket.PacketType, String> event,
			ProtocolTask<NodeIDType, ContextServicePacket.PacketType, String>[] ptasks);
	
	public abstract GenericMessagingTask<NodeIDType,?>[] handleValueUpdateMsgToValuenodeReply(
			ProtocolEvent<ContextServicePacket.PacketType, String> event,
			ProtocolTask<NodeIDType, ContextServicePacket.PacketType, String>[] ptasks);
	
	public abstract void checkQueryCompletion(QueryInfo<NodeIDType> qinfo);
	
	public abstract GenericMessagingTask<NodeIDType, ?>[] initializeScheme();
	
	protected abstract void processReplyInternally
	(QueryMsgToValuenodeReply<NodeIDType> queryMsgToValnodeRep, QueryInfo<NodeIDType> queryInfo);
}