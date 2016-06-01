package edu.umass.cs.contextservice.schemes;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.paukov.combinatorics.Factory;
import org.paukov.combinatorics.Generator;
import org.paukov.combinatorics.ICombinatoricsVector;

import com.google.common.hash.Hashing;

import edu.umass.cs.contextservice.attributeInfo.AttributeTypes;
import edu.umass.cs.contextservice.config.ContextServiceConfig;
import edu.umass.cs.contextservice.configurator.AbstractSubspaceConfigurator;
import edu.umass.cs.contextservice.configurator.BasicSubspaceConfigurator;
import edu.umass.cs.contextservice.configurator.CalculateOptimalNumAttrsInSubspace;
import edu.umass.cs.contextservice.configurator.ReplicatedSubspaceConfigurator;
import edu.umass.cs.contextservice.database.HyperspaceMySQLDB;
import edu.umass.cs.contextservice.gns.GNSCalls;
import edu.umass.cs.contextservice.hyperspace.storage.AttributePartitionInfo;
import edu.umass.cs.contextservice.hyperspace.storage.SubspaceInfo;
import edu.umass.cs.contextservice.logging.ContextServiceLogger;
import edu.umass.cs.contextservice.messages.ClientConfigReply;
import edu.umass.cs.contextservice.messages.ClientConfigRequest;
import edu.umass.cs.contextservice.messages.ContextServicePacket.PacketType;
import edu.umass.cs.contextservice.messages.GetMessage;
import edu.umass.cs.contextservice.messages.GetReplyMessage;
import edu.umass.cs.contextservice.messages.QueryMesgToSubspaceRegion;
import edu.umass.cs.contextservice.messages.QueryMesgToSubspaceRegionReply;
import edu.umass.cs.contextservice.messages.QueryMsgFromUser;
import edu.umass.cs.contextservice.messages.QueryTriggerMessage;
import edu.umass.cs.contextservice.messages.UpdateTriggerMessage;
import edu.umass.cs.contextservice.messages.UpdateTriggerReply;
import edu.umass.cs.contextservice.messages.ValueUpdateFromGNS;
import edu.umass.cs.contextservice.messages.ValueUpdateFromGNSReply;
import edu.umass.cs.contextservice.messages.ValueUpdateToSubspaceRegionMessage;
import edu.umass.cs.contextservice.messages.ValueUpdateToSubspaceRegionReplyMessage;
import edu.umass.cs.contextservice.queryparsing.QueryInfo;
import edu.umass.cs.contextservice.updates.GUIDUpdateInfo;
import edu.umass.cs.contextservice.updates.UpdateInfo;
import edu.umass.cs.nio.GenericMessagingTask;
import edu.umass.cs.nio.JSONMessenger;
import edu.umass.cs.nio.interfaces.NodeConfig;
import edu.umass.cs.protocoltask.ProtocolEvent;
import edu.umass.cs.protocoltask.ProtocolTask;

public class HyperspaceHashing<NodeIDType> extends AbstractScheme<NodeIDType>
{
	//TODO: make the trigger handling part in separate interfaces and classes.
	// also the privacy stuff. 
	// this files is getting very big.
	private final ExecutorService nodeES;
	
	private HashMap<String, GUIDUpdateInfo<NodeIDType>> guidUpdateInfoMap				= null;
	
	private final AbstractSubspaceConfigurator<NodeIDType> subspaceConfigurator;
	
	private final CalculateOptimalNumAttrsInSubspace optimalHCalculator;
	
	private final GUIDAttrValueProcessingInterface<NodeIDType> guidAttrValProcessing;
	private TriggerProcessingInterface<NodeIDType> triggerProcessing;
	
	//private PrivacyProcessing<NodeIDType> privacyProcesing;
	
	public static final Logger log 														= ContextServiceLogger.getLogger();
	
	public HyperspaceHashing(NodeConfig<NodeIDType> nc,
			JSONMessenger<NodeIDType> m) throws Exception
	{
		super(nc, m);
		
		nodeES = Executors.newFixedThreadPool(ContextServiceConfig.HYPERSPACE_THREAD_POOL_SIZE);
		
		guidUpdateInfoMap = new HashMap<String, GUIDUpdateInfo<NodeIDType>>();
		

		optimalHCalculator = new CalculateOptimalNumAttrsInSubspace(nc.getNodeIDs().size(),
					AttributeTypes.attributeMap.size());
		
		if( optimalHCalculator.getBasicOrReplicated() )
		{
			subspaceConfigurator 
			= new BasicSubspaceConfigurator<NodeIDType>(messenger.getNodeConfig(), optimalHCalculator.getOptimalH() );
		}
		else
		{
			subspaceConfigurator 
			= new ReplicatedSubspaceConfigurator<NodeIDType>(messenger.getNodeConfig(), optimalHCalculator.getOptimalH() );
		}
			
		ContextServiceLogger.getLogger().fine("configure subspace started");
		// configure subspaces
		subspaceConfigurator.configureSubspaceInfo();
		ContextServiceLogger.getLogger().fine("configure subspace completed");
		
		
		hyperspaceDB = new HyperspaceMySQLDB<NodeIDType>(this.getMyID(), 
					subspaceConfigurator.getSubspaceInfoMap());
		
		ContextServiceLogger.getLogger().fine("HyperspaceMySQLDB completed");
		
		
		guidAttrValProcessing = new GUIDAttrValueProcessing<NodeIDType>(
				this.getMyID(), subspaceConfigurator.getSubspaceInfoMap(), 
				hyperspaceDB, messenger , nodeES ,
				pendingQueryRequests );
		
//		if( ContextServiceConfig.PRIVACY_ENABLED )
//		{
//			privacyProcesing = new PrivacyProcessing<NodeIDType>( this.getMyID() , 
//					hyperspaceDB ,  messenger );
//		}
		
		if( ContextServiceConfig.TRIGGER_ENABLED )
		{
			triggerProcessing = new TriggerProcessing<NodeIDType>(this.getMyID(), 
				subspaceConfigurator.getSubspaceInfoMap(), hyperspaceDB, messenger);
		}
		//ContextServiceLogger.getLogger().fine("generateSubspacePartitions completed");
		//nodeES = Executors.newCachedThreadPool();
		//new Thread(new ProfilerStatClass()).start();
	}
	
	//TODO not sure what is the overhead of synchronizig exectutor service
	// but as we are accessing same executor service from many threads, so may be good to synchronize
	@Override
	public GenericMessagingTask<NodeIDType, ?>[] handleQueryMsgFromUser(
			ProtocolEvent<PacketType, String> event,
			ProtocolTask<NodeIDType, PacketType, String>[] ptasks)
	{
		nodeES.execute(new HandleEventThread(event));
		return null;
	}
	
	@Override
	public GenericMessagingTask<NodeIDType, ?>[] handleValueUpdateFromGNS(
			ProtocolEvent<PacketType, String> event,
			ProtocolTask<NodeIDType, PacketType, String>[] ptasks) 
	{
		nodeES.execute(new HandleEventThread(event));
		return null;
	}
	
	@Override
	public GenericMessagingTask<NodeIDType, ?>[] handleQueryMesgToSubspaceRegion(
			ProtocolEvent<PacketType, String> event,
			ProtocolTask<NodeIDType, PacketType, String>[] ptasks) 
	{
		nodeES.execute(new HandleEventThread(event));
		return null;
	}
	
	@Override
	public GenericMessagingTask<NodeIDType, ?>[] handleQueryMesgToSubspaceRegionReply(
			ProtocolEvent<PacketType, String> event,
			ProtocolTask<NodeIDType, PacketType, String>[] ptasks)
	{
		nodeES.execute(new HandleEventThread(event));
		return null;
	}

	@Override
	public GenericMessagingTask<NodeIDType, ?>[] handleValueUpdateToSubspaceRegionMessage(
			ProtocolEvent<PacketType, String> event,
			ProtocolTask<NodeIDType, PacketType, String>[] ptasks)
	{
		nodeES.execute(new HandleEventThread(event));
		return null;
	}
	
	@Override
	public GenericMessagingTask<NodeIDType, ?>[] handleGetMessage(
			ProtocolEvent<PacketType, String> event,
			ProtocolTask<NodeIDType, PacketType, String>[] ptasks) 
	{
		nodeES.execute(new HandleEventThread(event));
		return null;
	}

	@Override
	public GenericMessagingTask<NodeIDType, ?>[] handleGetReplyMessage(
			ProtocolEvent<PacketType, String> event,
			ProtocolTask<NodeIDType, PacketType, String>[] ptasks) 
	{
		nodeES.execute(new HandleEventThread(event));
		return null;
	}
	
	@Override
	public GenericMessagingTask<NodeIDType, ?>[] handleValueUpdateToSubspaceRegionReplyMessage(
			ProtocolEvent<PacketType, String> event,
			ProtocolTask<NodeIDType, PacketType, String>[] ptasks) 
	{
		nodeES.execute(new HandleEventThread(event));
		return null;
	}
	
	@Override
	public GenericMessagingTask<NodeIDType, ?>[] handleQueryTriggerMessage(
			ProtocolEvent<PacketType, String> event,
			ProtocolTask<NodeIDType, PacketType, String>[] ptasks) 
	{
		nodeES.execute(new HandleEventThread(event));
		return null;
	}

	@Override
	public GenericMessagingTask<NodeIDType, ?>[] handleUpdateTriggerMessage(
			ProtocolEvent<PacketType, String> event,
			ProtocolTask<NodeIDType, PacketType, String>[] ptasks) 
	{
		nodeES.execute(new HandleEventThread(event));
		return null;
	}
	
	@Override
	public GenericMessagingTask<NodeIDType, ?>[] handleUpdateTriggerReply(
			ProtocolEvent<PacketType, String> event,
			ProtocolTask<NodeIDType, PacketType, String>[] ptasks) 
	{
		nodeES.execute(new HandleEventThread(event));
		return null;
	}
	
	@Override
	public GenericMessagingTask<NodeIDType, ?>[] handleClientConfigRequest(ProtocolEvent<PacketType, String> event,
			ProtocolTask<NodeIDType, PacketType, String>[] ptasks) 
	{
		nodeES.execute(new HandleEventThread(event));
		return null;
	}
	
	@Override
	public GenericMessagingTask<NodeIDType, ?>[] handleACLUpdateToSubspaceRegionMessage(
			ProtocolEvent<PacketType, String> event, 
			ProtocolTask<NodeIDType, PacketType, String>[] ptasks)
	{
		nodeES.execute(new HandleEventThread(event));
		return null;
	}

	@Override
	public GenericMessagingTask<NodeIDType, ?>[] handleACLUpdateToSubspaceRegionReplyMessage(
			ProtocolEvent<PacketType, String> event, 
			ProtocolTask<NodeIDType, PacketType, String>[] ptasks)
	{
		nodeES.execute(new HandleEventThread(event));
		return null;
	}
	
	@Override
	public NodeIDType getConsistentHashingNodeID( String stringToHash , 
			Vector<NodeIDType> listOfNodesToConsistentlyHash )
	{
		int numNodes = listOfNodesToConsistentlyHash.size();
		int mapIndex = Hashing.consistentHash(stringToHash.hashCode(), numNodes);
		return listOfNodesToConsistentlyHash.get(mapIndex);
	}
	
	private void processQueryMsgFromUser
		(QueryMsgFromUser<NodeIDType> queryMsgFromUser)
	{
		String query;
		String userIP;
		int userPort;
		
		query   = queryMsgFromUser.getQuery();
		userIP  = queryMsgFromUser.getSourceIP();
		userPort   = queryMsgFromUser.getSourcePort();
		
		ContextServiceLogger.getLogger().fine("QUERY RECVD: QUERY_MSG recvd query recvd "+query);
		
		// create the empty group in GNS
		String grpGUID = GNSCalls.createQueryGroup(query);
		
		if( grpGUID.length() <= 0 )
		{
			ContextServiceLogger.getLogger().fine
			("Query request failed at the recieving node "+queryMsgFromUser);
			return;
		}
		
		// check for triggers, if those are enabled then forward the query to the node
		// which consistently hashes the the query:userIp:userPort string
		if( ContextServiceConfig.TRIGGER_ENABLED && ContextServiceConfig.UniqueGroupGUIDEnabled )
		{
			String hashKey = query+":"+userIP+":"+userPort;
			NodeIDType respNodeId = this.getConsistentHashingNodeID(hashKey, this.allNodeIDs);
			
			// just forward the request to the node that has 
			// guid stored in primary subspace.
			if( this.getMyID() != respNodeId )
			{
				try
				{
					this.messenger.sendToID( respNodeId, queryMsgFromUser.toJSONObject() );
					return;
				} catch (IOException e)
				{
					e.printStackTrace();
				} catch (JSONException e)
				{
					e.printStackTrace();
				}
			}
		}
		
		QueryInfo<NodeIDType> currReq = 
				this.guidAttrValProcessing.processQueryMsgFromUser(queryMsgFromUser);
		
	    // trigger information like userIP, userPort 
	    // are stored for each attribute in the query one at a time
	    // We use value of one attribute and use the default value of other attributes 
	    // and do this for each attribute in turn.
	    //FIXME: check trigger with replication
	    if( ContextServiceConfig.TRIGGER_ENABLED )
	    {
	    	this.triggerProcessing.processTriggerOnQueryMsgFromUser(currReq);
	    }
	}
	
	private void processValueUpdateFromGNS( ValueUpdateFromGNS<NodeIDType> valueUpdateFromGNS )
	{
		String GUID 			  		= valueUpdateFromGNS.getGUID();
		NodeIDType respNodeId 	  		= this.getConsistentHashingNodeID
													(GUID, this.allNodeIDs);
		
		// just forward the request to the node that has 
		// guid stored in primary subspace.
		if( this.getMyID() != respNodeId )
		{
			ContextServiceLogger.getLogger().fine("not primary node case souceIp "
													+valueUpdateFromGNS.getSourceIP()
													+" sourcePort "+valueUpdateFromGNS.getSourcePort());
			try
			{
				this.messenger.sendToID( respNodeId, valueUpdateFromGNS.toJSONObject() );
			} catch (IOException e)
			{
				e.printStackTrace();
			} catch (JSONException e)
			{
				e.printStackTrace();
			}
		}
		else
		{
			ContextServiceLogger.getLogger().fine("primary node case souceIp "
								+valueUpdateFromGNS.getSourceIP()
								+" sourcePort "+valueUpdateFromGNS.getSourcePort());
			
			UpdateInfo<NodeIDType> updReq  	= null;
			long requestID 					= -1;
			// if no outstanding request then it is set to true
			boolean sendOutRequest 			= false;
			
			synchronized( this.pendingUpdateLock )
			{
				updReq = new UpdateInfo<NodeIDType>(valueUpdateFromGNS, updateIdCounter++, 
						this.subspaceConfigurator.getSubspaceInfoMap());
				pendingUpdateRequests.put(updReq.getRequestId(), updReq);
				requestID = updReq.getRequestId();
				
				GUIDUpdateInfo<NodeIDType> guidUpdateInfo = this.guidUpdateInfoMap.get(GUID);
				
				if(guidUpdateInfo == null)
				{
					guidUpdateInfo = new GUIDUpdateInfo<NodeIDType>(GUID);
					guidUpdateInfo.addUpdateReqNumToQueue(requestID);
					this.guidUpdateInfoMap.put(GUID, guidUpdateInfo);
					sendOutRequest = true;
				}
				else
				{
					guidUpdateInfo.addUpdateReqNumToQueue(requestID);
					// no need to send out request. it will be sent once the current
					// outstanding gets completed
				}
			}
			
			if( sendOutRequest )
			{
				processUpdateSerially(updReq);
			}
		}
	}
	
	private void setDefaultAttrValuesInJSON(JSONObject oldValueJSON)
	{
		try
		{
			// attributes which are not set should be set to default value
			// for attribute-space hashing
			HashMap<Integer, Vector<SubspaceInfo<NodeIDType>>> subspaceInfoMap = 
					this.subspaceConfigurator.getSubspaceInfoMap();					
			
			Iterator<Integer> subapceIdIter = subspaceInfoMap.keySet().iterator();
			while(subapceIdIter.hasNext())
			{
				int subspaceId = subapceIdIter.next();
				// at least one replica and all replica have same default value for each attribute.
				SubspaceInfo<NodeIDType> currSubspaceInfo 
											= subspaceInfoMap.get(subspaceId).get(0);
				HashMap<String, AttributePartitionInfo> attrSubspaceMap 
											= currSubspaceInfo.getAttributesOfSubspace();
				
				Iterator<String> attrIter = attrSubspaceMap.keySet().iterator();
				while(attrIter.hasNext())
				{
					String attrName = attrIter.next();
					AttributePartitionInfo attrPartInfo = attrSubspaceMap.get(attrName);
					if( !oldValueJSON.has(attrName) )
					{
						try
						{
							oldValueJSON.put(attrName, attrPartInfo.getDefaultValue());
						} catch (JSONException e)
						{
							e.printStackTrace();
						}
					}
				}
			}
		}
		catch(Error | Exception ex)
		{
			ex.printStackTrace();
		}
	}
	
	/**
	 * This function processes a request serially.
	 * when one outstanding request completes.
	 */
	private void processUpdateSerially(UpdateInfo<NodeIDType> updateReq)
	{
		assert(updateReq != null);
		try
		{
			ContextServiceLogger.getLogger().fine
					( "processUpdateSerially called "+updateReq.getRequestId() +
					" JSON"+updateReq.getValueUpdateFromGNS().toJSONObject().toString() );
		}
		catch(JSONException jso)
		{
			jso.printStackTrace();
		}
		
		String GUID 	 		= updateReq.getValueUpdateFromGNS().getGUID();
		JSONObject attrValuePairs 
						 		= updateReq.getValueUpdateFromGNS().getAttrValuePairs();
		long requestID 	 		= updateReq.getRequestId();
		long updateStartTime	= updateReq.getValueUpdateFromGNS().getUpdateStartTime();
		
		// this could be null, in no privacy case, and also in privacy case
		// , as anonymizedIDtoGuidMapping is not set in every message just in the beginning 
		// or on acl changes.
		JSONArray anonymizedIDToGuidMapping 
						= updateReq.getValueUpdateFromGNS().getAnonymizedIDToGuidMapping();
		
		
		// get the old value and process the update in primary subspace and other subspaces.
		String tableName = "primarySubspaceDataStorage";
		
		try
		{	
			boolean firstTimeInsert = false;
			
			long start 	 = System.currentTimeMillis();
			// FIXME: fetch only those attributes which are specified in the updated attrs.
			JSONObject oldValueJSON 	
						 = this.hyperspaceDB.getGUIDStoredInPrimarySubspace(GUID);
			
			long end 	 = System.currentTimeMillis();
			
			if(ContextServiceConfig.DEBUG_MODE)
			{
				System.out.println("getGUIDStoredInPrimarySubspace time "+(end-start)
							+" since upd start"+(end-updateStartTime));
			}
			
			int updateOrInsert 			= -1;
			
			if( oldValueJSON.length() == 0 )
			{
				firstTimeInsert = true;
				updateOrInsert = HyperspaceMySQLDB.INSERT_REC;
			}
			else
			{
				firstTimeInsert = false;
				updateOrInsert = HyperspaceMySQLDB.UPDATE_REC;
			}
			
			// default values are set for all attributes for hyperspace indexing.
			setDefaultAttrValuesInJSON(oldValueJSON);
			
			// ACL info doesn't need to be stored in primary subspace.
			// so just passing an empty JSONObject()
			// update for primary subspace
			boolean privacyInfoToBeUpdated = false;
			
			if( ContextServiceConfig.PRIVACY_ENABLED )
			{
				boolean alreadyStored = checkIfAnonymizedIDToGuidInfoAlreadyStored(oldValueJSON);
				
				if( !alreadyStored )
				{
					privacyInfoToBeUpdated = true;
				}
			}
			
			// privacy info needs to be updated in primary subspace.
			// for secondary subspace, the privacy info is sent in 
			// the message if the anonymized ID storage changes from one 
			// node to another.
			if(privacyInfoToBeUpdated)
			{
				this.hyperspaceDB.storeGUIDInPrimarySubspace
						( tableName, GUID, attrValuePairs, updateOrInsert, 
								oldValueJSON, anonymizedIDToGuidMapping);
			}
			else
			{
				// sending null means anonymizedIDToGuidMapping will not be inserted again.
				this.hyperspaceDB.storeGUIDInPrimarySubspace
				( tableName, GUID, attrValuePairs, updateOrInsert, 
						oldValueJSON, null);
			}
			if(ContextServiceConfig.DEBUG_MODE)
			{
				long now = System.currentTimeMillis();
				System.out.println("primary subspace update complete "+(now-updateStartTime));
			}
			// process update at secondary subspaces.
			//FIXME: anonymizedIDToGuidMapping can be null if not present in the message.
			// set it by reading from primarysubspace storage.
			updateGUIDInSecondarySubspaces( oldValueJSON , 
					firstTimeInsert , attrValuePairs , GUID , 
					requestID, anonymizedIDToGuidMapping, updateStartTime );
		}
		catch ( JSONException e )
		{
			e.printStackTrace();
		}
	}
	
	private boolean checkIfAnonymizedIDToGuidInfoAlreadyStored(JSONObject oldValJSON) 
					throws JSONException
	{
		boolean alreadyStored = false;
		if( oldValJSON.has(HyperspaceMySQLDB.anonymizedIDToGUIDMappingColName) )
		{
			String jsonArrayString = oldValJSON.getString
									(HyperspaceMySQLDB.anonymizedIDToGUIDMappingColName);
			
			if(jsonArrayString != null)
			{
				// We don't need to compare the actual value,
				// because this information change causes whole
				// anonymized ID to change, so anonymizedIDToGUIDMapping
				// either inserted or deleted, but never updated for 
				// an anonymized ID.
				if(jsonArrayString.length() > 0)
				{
					alreadyStored = true;
					return alreadyStored;
				}
			}
			return alreadyStored;
		}
		else
		{
			return alreadyStored;
		}
	}
	
	private void updateGUIDInSecondarySubspaces( JSONObject oldValueJSON , 
			boolean firstTimeInsert , JSONObject updatedAttrValJSON , 
			String GUID , long requestID, 
			JSONArray anonymizedIDToGuidMapping, long updateStartTime )
					throws JSONException
	{
		// process update at other subspaces.
		HashMap<Integer, Vector<SubspaceInfo<NodeIDType>>> subspaceInfoMap
									= this.subspaceConfigurator.getSubspaceInfoMap();
		
		Iterator<Integer> keyIter   = subspaceInfoMap.keySet().iterator();
		
		while( keyIter.hasNext() )
		{
			int subspaceId 			= keyIter.next();
			Vector<SubspaceInfo<NodeIDType>> replicasVect 
									= subspaceInfoMap.get(subspaceId);
			
			for( int i=0; i<replicasVect.size(); i++ )
			{
				SubspaceInfo<NodeIDType> currSubInfo 
							= replicasVect.get(i);
				int replicaNum = currSubInfo.getReplicaNum();
				
				HashMap<String, AttributePartitionInfo> attrsSubspaceInfo 
													= currSubInfo.getAttributesOfSubspace();
				
				//Vector<NodeIDType> subspaceNodes = currSubInfo.getNodesOfSubspace();
				
				this.guidAttrValProcessing.guidValueProcessingOnUpdate
					( attrsSubspaceInfo, oldValueJSON, subspaceId, replicaNum, 
							updatedAttrValJSON, GUID, requestID, firstTimeInsert, 
							anonymizedIDToGuidMapping, updateStartTime );
				
				
				// getting group GUIDs that are affected
				// FIXME: check how triggers can be affected by replica of subspaces
				// FIXME: check this for trigger thing too.
				// Doing this here was a bug, as we are also sending the message out
				// and sometimes replies were coming back quickly before initialization for all 
				// subspaces and the request completion code was assuming that the request was 
				// complete.
				// before recv replies from all subspaces.
				if( ContextServiceConfig.TRIGGER_ENABLED )
				{
					//FIXME: need to check if we need oldJSON here.
					this.triggerProcessing.triggerProcessingOnUpdate
						( updatedAttrValJSON, attrsSubspaceInfo, 
							subspaceId, replicaNum, oldValueJSON, requestID );
				}
				
//				if( ContextServiceConfig.PRIVACY_ENABLED )
//				{
//					NodeIDType hashedNodeIdInSubspace 
//						= this.getConsistentHashingNodeID(GUID, subspaceNodes);
//					
//					privacyProcesing.privacyProcessingOnUpdate
//						(hashedNodeIdInSubspace, GUID, subspaceId, requestID,
//							attrValuePairs);
//				}
			}
		}
	}
	
	private void processUpdateTriggerReply(
			UpdateTriggerReply<NodeIDType> updateTriggerReply)
	{
		long requestID              	= updateTriggerReply.getRequestId();
		UpdateInfo<NodeIDType> updInfo  = pendingUpdateRequests.get(requestID);
		boolean triggerCompl = updInfo.setUpdateTriggerReply(updateTriggerReply);
		
		if( triggerCompl )
		{
			try
			{
				this.triggerProcessing.sendOutAggregatedRefreshTrigger( updInfo);
			}
			catch(Exception ex)
			{
				ex.printStackTrace();
			}
			
			// removing here, because updInfo only gets removed 
			// when both trigger and update replies are recvd.
			boolean updateCompl = updInfo.checkAllUpdateReplyRecvd();
			UpdateInfo<NodeIDType> removedUpdate = null;
			
			if( updateCompl )
				removedUpdate = pendingUpdateRequests.remove(requestID);
			
			// starts the queues serialized updates for that guid
			// null is checked becuase it can also be remove on
			// update completion. So only one can start the new update
			if( removedUpdate != null )
			{
				startANewUpdate(removedUpdate, requestID);
			}
		}
	}
	
	private void processGetMessage(GetMessage<NodeIDType> getMessage)
	{
		String GUID 			  = getMessage.getGUIDsToGet();
		NodeIDType respNodeId 	  = this.getConsistentHashingNodeID(GUID, this.allNodeIDs);
		
		// just forward the request to the node that has 
		// guid stored in primary subspace.
		if( this.getMyID() != respNodeId )
		{
			try
			{
				this.messenger.sendToID( respNodeId, getMessage.toJSONObject() );
			} catch (IOException e)
			{
				e.printStackTrace();
			} catch (JSONException e)
			{
				e.printStackTrace();
			}
		}
		else
		{
			//String tableName = "primarySubspaceDataStorage";
			JSONObject valueJSON= this.hyperspaceDB.getGUIDStoredInPrimarySubspace(GUID);
			
			
			GetReplyMessage<NodeIDType> getReplyMessage = new GetReplyMessage<NodeIDType>(this.getMyID(),
					getMessage.getUserReqID(), GUID, valueJSON);
			
			try
			{
				this.messenger.sendToAddress( new InetSocketAddress(getMessage.getSourceIP(), getMessage.getSourcePort()), 
						getReplyMessage.toJSONObject() );
			} catch (IOException e)
			{
				e.printStackTrace();
			} catch (JSONException e)
			{
				e.printStackTrace();
			}
		}
	}
	
	
	private void processValueUpdateToSubspaceRegionMessageReply
		(ValueUpdateToSubspaceRegionReplyMessage<NodeIDType> 
		valueUpdateToSubspaceRegionReplyMessage)
	{
		long requestID  = valueUpdateToSubspaceRegionReplyMessage.getRequestID();
		int subspaceId  = valueUpdateToSubspaceRegionReplyMessage.getSubspaceNum();
		int numReply 	= valueUpdateToSubspaceRegionReplyMessage.getNumReply();
		int replicaNum  = valueUpdateToSubspaceRegionReplyMessage.getReplicaNum();
		
		UpdateInfo<NodeIDType> updInfo = pendingUpdateRequests.get(requestID);
		
		if( updInfo == null )
		{
			ContextServiceLogger.getLogger().severe( "updInfo null, update already removed from "
					+ " the pending queue before recv all replies requestID "+requestID
					+ "  valueUpdateToSubspaceRegionReplyMessage "
					+ valueUpdateToSubspaceRegionReplyMessage );
			assert(false);
		}
		boolean completion = updInfo.setUpdateReply(subspaceId, replicaNum, numReply);
		
		if( completion )
		{
			ValueUpdateFromGNSReply<NodeIDType> valueUpdateFromGNSReply = new ValueUpdateFromGNSReply<NodeIDType>
			(this.getMyID(), updInfo.getValueUpdateFromGNS().getVersionNum(), updInfo.getValueUpdateFromGNS().getUserRequestID());
			
			ContextServiceLogger.getLogger().fine("reply IP Port "+updInfo.getValueUpdateFromGNS().getSourceIP()
					+":"+updInfo.getValueUpdateFromGNS().getSourcePort()+ " ValueUpdateFromGNSReply for requestId "+requestID
					+" "+valueUpdateFromGNSReply+" ValueUpdateToSubspaceRegionReplyMessage "+valueUpdateToSubspaceRegionReplyMessage);
			try
			{
				this.messenger.sendToAddress( new InetSocketAddress(updInfo.getValueUpdateFromGNS().getSourceIP()
						, updInfo.getValueUpdateFromGNS().getSourcePort()), 
						valueUpdateFromGNSReply.toJSONObject() );
			} catch (IOException e)
			{
				e.printStackTrace();
			} catch (JSONException e)
			{
				e.printStackTrace();
			}
			
			UpdateInfo<NodeIDType> removedUpdate = null;
			if( ContextServiceConfig.TRIGGER_ENABLED )
			{
				boolean triggerCompl = updInfo.checkAllTriggerRepRecvd();
				
				if( triggerCompl )
					removedUpdate = pendingUpdateRequests.remove(requestID);
			}
			else
			{
				removedUpdate = pendingUpdateRequests.remove(requestID);
			}
			
			// starts the queues serialized updates for that guid
			if(removedUpdate != null)
			{
				startANewUpdate(removedUpdate, requestID);
			}
		}
	}
	
	private void startANewUpdate(UpdateInfo<NodeIDType> removedUpdate, long requestID)
	{
		boolean startANewUpdate = false;
		Long nextRequestID = null;
		synchronized( this.pendingUpdateLock )
		{
			// remove from guidUpdateInfo
			GUIDUpdateInfo<NodeIDType> guidUpdateInfo = 
					this.guidUpdateInfoMap.get(removedUpdate.getValueUpdateFromGNS().getGUID());
			
			assert(guidUpdateInfo!=null);
			Long currRequestID = guidUpdateInfo.removeFromQueue();
			// it must not be null
			assert(currRequestID != null);
			// it should be same as current requestID
			assert(requestID == currRequestID);
			
			// get the next requestID
			nextRequestID = guidUpdateInfo.getNextRequestID();
			if(nextRequestID == null)
			{
				// remove the guidUpdateInfo, there are no more updates for this GUID
				this.guidUpdateInfoMap.remove(removedUpdate.getValueUpdateFromGNS().getGUID());
			}
			else
			{
				// start a new update serially outside the lock
				startANewUpdate = true;
			}
		}
		
		if(startANewUpdate)
		{
			assert(nextRequestID != null);
			this.processUpdateSerially(pendingUpdateRequests.get(nextRequestID));
		}
	}
	
	private void processClientConfigRequest(ClientConfigRequest<NodeIDType> clientConfigRequest)
	{
		JSONArray nodeConfigArray 		= new JSONArray();
		JSONArray attributeArray  		= new JSONArray();
		// Each element is a JSONArray of attrbutes for a subspace
		JSONArray subspaceConfigArray   = new JSONArray();
		
		Iterator<NodeIDType> nodeIDIter = this.allNodeIDs.iterator();
		
		while( nodeIDIter.hasNext() )
		{
			NodeIDType nodeId = nodeIDIter.next();
			InetAddress nodeAddress = this.messenger.getNodeConfig().getNodeAddress(nodeId);
			int nodePort = this.messenger.getNodeConfig().getNodePort(nodeId);
			String ipPortString = nodeAddress.getHostAddress()+":"+nodePort;
			nodeConfigArray.put(ipPortString);
		}
		
		Iterator<String> attrIter = AttributeTypes.attributeMap.keySet().iterator();
		
		while(attrIter.hasNext())
		{
			String attrName = attrIter.next();
			attributeArray.put(attrName);
		}
		
		HashMap<Integer, Vector<SubspaceInfo<NodeIDType>>> subspaceInfoMap = 
				this.subspaceConfigurator.getSubspaceInfoMap();
		
		assert(subspaceInfoMap != null);
		
		Iterator<Integer> subspaceIter = subspaceInfoMap.keySet().iterator();
		
		while( subspaceIter.hasNext() )
		{
			// taking first replica, as we need distinct subspace attrs
			Vector<SubspaceInfo<NodeIDType>> subsapceInfoVect 
												= subspaceInfoMap.get( subspaceIter.next() );
			
			SubspaceInfo<NodeIDType> currSubspaceInfo = subsapceInfoVect.get(0);
			
			JSONArray attrsOfSubspace = new JSONArray();
			HashMap<String, AttributePartitionInfo> currSubInfoMap = 
								currSubspaceInfo.getAttributesOfSubspace();
			
			attrIter = currSubInfoMap.keySet().iterator();
			
			while( attrIter.hasNext() )
			{
				String attrName = attrIter.next();
				attrsOfSubspace.put(attrName);
			}
			subspaceConfigArray.put(attrsOfSubspace);
		}
		
		InetSocketAddress sourceSocketAddr = new InetSocketAddress(clientConfigRequest.getSourceIP(),
				clientConfigRequest.getSourcePort());
		ClientConfigReply<NodeIDType> configReply 
					= new ClientConfigReply<NodeIDType>( this.getMyID(), nodeConfigArray,
							attributeArray, subspaceConfigArray );
		try
		{
			this.messenger.sendToAddress( sourceSocketAddr, configReply.toJSONObject() );
		} catch (IOException e)
		{
			e.printStackTrace();
		} catch (JSONException e)
		{
			e.printStackTrace();
		}
	}
	
//	private void processACLUpdateToSubspaceRegionReplyMessage(
//			ACLUpdateToSubspaceRegionReplyMessage<NodeIDType> 
//						aclUpdateToSubspaceRegionReplyMessage )
//	{
//		long requestID  = aclUpdateToSubspaceRegionReplyMessage.getRequestID();
//		int subspaceId  = aclUpdateToSubspaceRegionReplyMessage.getSubspaceNum();
//		//int numReply 	= valueUpdateToSubspaceRegionReplyMessage.getNumReply();
//		int replicaNum  = aclUpdateToSubspaceRegionReplyMessage.getReplicaNum();
//		
//		UpdateInfo<NodeIDType> updInfo 
//						= pendingUpdateRequests.get(requestID);
//		
//		if( updInfo == null )
//		{
//			ContextServiceLogger.getLogger().severe( "updInfo null, update already removed from "
//					+ " the pending queue before recv all replies requestID "+requestID
//					+ "  valueUpdateToSubspaceRegionReplyMessage "
//					+ aclUpdateToSubspaceRegionReplyMessage );
//			assert(false);
//		}
//		boolean completion = updInfo.setUpdateReply(subspaceId, replicaNum, -1, 
//							UpdateInfo.PRIVACY_UPDATE_REPLY);
//		
//		if( completion )
//		{
//			ValueUpdateFromGNSReply<NodeIDType> valueUpdateFromGNSReply 
//				= new ValueUpdateFromGNSReply<NodeIDType>(this.getMyID(), 
//					updInfo.getValueUpdateFromGNS().getVersionNum(), 
//					updInfo.getValueUpdateFromGNS().getUserRequestID());
//			
//			ContextServiceLogger.getLogger().fine("reply IP Port "
//					+ updInfo.getValueUpdateFromGNS().getSourceIP()
//					+ ":"+updInfo.getValueUpdateFromGNS().getSourcePort()
//					+ " ValueUpdateFromGNSReply for requestId "+requestID
//					+ " "+valueUpdateFromGNSReply
//					+ " ValueUpdateToSubspaceRegionReplyMessage "
//					+ aclUpdateToSubspaceRegionReplyMessage);
//			try
//			{
//				this.messenger.sendToAddress( new InetSocketAddress(
//						updInfo.getValueUpdateFromGNS().getSourceIP()
//						, updInfo.getValueUpdateFromGNS().getSourcePort() ), 
//						valueUpdateFromGNSReply.toJSONObject() );
//			} catch (IOException e)
//			{
//				e.printStackTrace();
//			} catch (JSONException e)
//			{
//				e.printStackTrace();
//			}
//			
//			UpdateInfo<NodeIDType> removedUpdate = null;
//			if( ContextServiceConfig.TRIGGER_ENABLED )
//			{
//				boolean triggerCompl = updInfo.checkAllTriggerRepRecvd();
//				
//				if( triggerCompl )
//					removedUpdate = pendingUpdateRequests.remove(requestID);
//			}
//			else
//			{
//				removedUpdate = pendingUpdateRequests.remove(requestID);
//			}
//			
//			// starts the queues serialized updates for that guid
//			if( removedUpdate != null )
//			{
//				startANewUpdate(removedUpdate, requestID);
//			}
//		}
//	}
	
	/**
	 * returns the replica num for a subspace
	 * One nodeid should have just one replica num
	 * as it can belong to just one replica of that subspace
	 * @return
	 */
	private int getTheReplicaNumForASubspace( int subpsaceId )
	{
		Vector<SubspaceInfo<NodeIDType>> replicasVect 
				= subspaceConfigurator.getSubspaceInfoMap().get(subpsaceId);
		
		int replicaNum = -1;
		for( int i=0;i<replicasVect.size();i++ )
		{
			SubspaceInfo<NodeIDType> subInfo = replicasVect.get(i);
			if( subInfo.checkIfSubspaceHasMyID(this.getMyID()) )
			{
				replicaNum = subInfo.getReplicaNum();
				break;
			}
		}
		return replicaNum;
	}
	
	private class HandleEventThread implements Runnable
	{
		private final ProtocolEvent<PacketType, String> event;
		
		public HandleEventThread(ProtocolEvent<PacketType, String> event)
		{
			this.event = event;
		}
		
		@Override
		public void run()
		{
			// this try catch is very important.
			// otherwise exception from these methods are not at all printed by executor service
			// and debugging gets very time consuming
			try
			{
				switch(event.getType())
				{
					case  QUERY_MSG_FROM_USER:
					{
						//long t0 = System.currentTimeMillis();	
						@SuppressWarnings("unchecked")
						QueryMsgFromUser<NodeIDType> queryMsgFromUser 
												= (QueryMsgFromUser<NodeIDType>)event;
						
						processQueryMsgFromUser(queryMsgFromUser);
						
						break;
					}
					case QUERY_MESG_TO_SUBSPACE_REGION:
					{
						//long t0 = System.currentTimeMillis();
						
						@SuppressWarnings("unchecked")
						QueryMesgToSubspaceRegion<NodeIDType> queryMesgToSubspaceRegion = 
								(QueryMesgToSubspaceRegion<NodeIDType>) event;
						
						log.fine("CS"+getMyID()+" received " + event.getType() + ": " + event);
						
						guidAttrValProcessing.processQueryMesgToSubspaceRegion(queryMesgToSubspaceRegion);
						//processQueryMsgToMetadataNode(queryMsgToMetaNode);
						
						//DelayProfiler.updateDelay("handleQueryMsgToMetadataNode", t0);
						break;
					}
					case QUERY_MESG_TO_SUBSPACE_REGION_REPLY:
					{
						//long t0 = System.currentTimeMillis();
						@SuppressWarnings("unchecked")
						QueryMesgToSubspaceRegionReply<NodeIDType> queryMesgToSubspaceRegionReply = 
								(QueryMesgToSubspaceRegionReply<NodeIDType>)event;
						
						log.fine("CS"+getMyID()+" received " + event.getType() + ": " + queryMesgToSubspaceRegionReply);
						
						guidAttrValProcessing.processQueryMesgToSubspaceRegionReply(queryMesgToSubspaceRegionReply);
						
						//DelayProfiler.updateDelay("handleQueryMsgToValuenode", t0);
						break;
					}
					case VALUE_UPDATE_MSG_FROM_GNS:
					{
						//long t0 = System.currentTimeMillis();
						@SuppressWarnings("unchecked")
						ValueUpdateFromGNS<NodeIDType> valUpdMsgFromGNS = (ValueUpdateFromGNS<NodeIDType>)event;
						//log.info("CS"+getMyID()+" received " + event.getType() + ": " + valUpdMsgFromGNS);
						ContextServiceLogger.getLogger().fine("CS"+getMyID()+" received " + event.getType() + ": " + valUpdMsgFromGNS);
						
						processValueUpdateFromGNS(valUpdMsgFromGNS);
						
						//DelayProfiler.updateDelay("handleValueUpdateFromGNS", t0);
						break;
					}
					case VALUEUPDATE_TO_SUBSPACE_REGION_MESSAGE:
					{
						/* Actions:
						 * - send the update message to the responsible value node
						 */
						@SuppressWarnings("unchecked")
						ValueUpdateToSubspaceRegionMessage<NodeIDType> valueUpdateToSubspaceRegionMessage 
									= (ValueUpdateToSubspaceRegionMessage<NodeIDType>)event;
						
						ContextServiceLogger.getLogger().fine("CS"+getMyID()
							+" received " + event.getType() + ": " 
								+ valueUpdateToSubspaceRegionMessage);
						
						int subspaceId = valueUpdateToSubspaceRegionMessage.getSubspaceNum();
						int replicaNum = getTheReplicaNumForASubspace(subspaceId);
						
						guidAttrValProcessing.processValueUpdateToSubspaceRegionMessage
								( valueUpdateToSubspaceRegionMessage, replicaNum );
						break;
					}
					case GET_MESSAGE:
					
					{
						@SuppressWarnings("unchecked")
						GetMessage<NodeIDType> getMessage 
									= (GetMessage<NodeIDType>)event;
						//log.fine("CS"+getMyID()+" received " + event.getType() + ": " + valueUpdateToSubspaceRegionMessage);
						ContextServiceLogger.getLogger().fine("CS"+getMyID()+" received " + event.getType() + ": " 
										+ getMessage);
						
						processGetMessage(getMessage);
						break;
					}
					
					case VALUEUPDATE_TO_SUBSPACE_REGION_REPLY_MESSAGE:
					{
						@SuppressWarnings("unchecked")
						ValueUpdateToSubspaceRegionReplyMessage<NodeIDType> valueUpdateToSubspaceRegionReplyMessage 
									= (ValueUpdateToSubspaceRegionReplyMessage<NodeIDType>)event;
						//log.fine("CS"+getMyID()+" received " + event.getType() + ": " + valueUpdateToSubspaceRegionMessage);
						ContextServiceLogger.getLogger().fine("CS"+getMyID()+" received " + event.getType() + ": " 
								+ valueUpdateToSubspaceRegionReplyMessage);
						processValueUpdateToSubspaceRegionMessageReply(valueUpdateToSubspaceRegionReplyMessage);
						break;
					}
					
					case QUERY_TRIGGER_MESSAGE:
					{
						@SuppressWarnings("unchecked")
						QueryTriggerMessage<NodeIDType> queryTriggerMessage 
									= (QueryTriggerMessage<NodeIDType>)event;
						//log.fine("CS"+getMyID()+" received " + event.getType() + ": " + valueUpdateToSubspaceRegionMessage);
						ContextServiceLogger.getLogger().fine("CS"+getMyID()+" received " + event.getType() + ": " 
								+ queryTriggerMessage);
						triggerProcessing.processQueryTriggerMessage(queryTriggerMessage);
						break;
					}
					
					case UPDATE_TRIGGER_MESSAGE:
					{
						@SuppressWarnings("unchecked")
						UpdateTriggerMessage<NodeIDType> updateTriggerMessage 
									= (UpdateTriggerMessage<NodeIDType>)event;
						//log.fine("CS"+getMyID()+" received " + event.getType() + ": " + valueUpdateToSubspaceRegionMessage);
						ContextServiceLogger.getLogger().fine("CS"+getMyID()+" received " + event.getType() + ": " 
								+ updateTriggerMessage);
						triggerProcessing.processUpdateTriggerMessage(updateTriggerMessage);
						break;
					}
					
					case UPDATE_TRIGGER_REPLY_MESSAGE:
					{
						@SuppressWarnings("unchecked")
						UpdateTriggerReply<NodeIDType> updateTriggerReply 
									= (UpdateTriggerReply<NodeIDType>)event;
						//log.fine("CS"+getMyID()+" received " + event.getType() + ": " + valueUpdateToSubspaceRegionMessage);
						ContextServiceLogger.getLogger().fine("CS"+getMyID()+" received " + event.getType() + ": " 
								+ updateTriggerReply);
						processUpdateTriggerReply(updateTriggerReply);
						
						break;
					}
					
					case CONFIG_REQUEST:
					{
						@SuppressWarnings("unchecked")
						ClientConfigRequest<NodeIDType> configRequest 
									= (ClientConfigRequest<NodeIDType>)event;
						
						ContextServiceLogger.getLogger().fine("CS"+getMyID()+" received " + event.getType() + ": " 
								+ configRequest);
						processClientConfigRequest(configRequest);
						break;
					}
					
//					case ACLUPDATE_TO_SUBSPACE_REGION_MESSAGE:
//					{
//						@SuppressWarnings("unchecked")
//						ACLUpdateToSubspaceRegionMessage<NodeIDType> 
//							aclUpdateToSubspaceRegionMessage = 
//							(ACLUpdateToSubspaceRegionMessage<NodeIDType>)event;
//						
//						ContextServiceLogger.getLogger().fine("CS"+getMyID()+" received " 
//								+ event.getType() + ": " 
//								+ aclUpdateToSubspaceRegionMessage);
//						
//						int subspaceId = aclUpdateToSubspaceRegionMessage.getSubspaceNum();
//						
//						privacyProcesing.processACLUpdateToSubspaceRegionMessage
//									(aclUpdateToSubspaceRegionMessage, subspaceId);
//						break;
//					}
//					case ACLUPDATE_TO_SUBSPACE_REGION_REPLY_MESSAGE:
//					{
//						ACLUpdateToSubspaceRegionReplyMessage<NodeIDType> 
//								aclUpdateToSubspaceRegionReplyMessage = 
//									(ACLUpdateToSubspaceRegionReplyMessage<NodeIDType>) event;
//						
//						ContextServiceLogger.getLogger().fine("CS"+getMyID()+" received " 
//								+ event.getType() + ": " 
//								+ aclUpdateToSubspaceRegionReplyMessage);
//						
//						processACLUpdateToSubspaceRegionReplyMessage
//										(aclUpdateToSubspaceRegionReplyMessage);
//						break;
//					}
					default:
					{
						assert(false);
						break;
					}
				}
			}
			catch (Exception | Error ex)
			{
				ex.printStackTrace();
			}
		}
	}
	
	
	public static void main(String[] args)
	{
		double numPartitions = Math.ceil(Math.pow(16, 1.0/4));
		ContextServiceLogger.getLogger().fine("numPartitions "+numPartitions);
		
		double numAttr  = 5;
		//double numNodes = nodesOfSubspace.size();
		
		Integer[] partitionNumArray = new Integer[2];
		for( int j = 0; j<2; j++ )
		{
			partitionNumArray[j] = j;
			ContextServiceLogger.getLogger().fine("partitionNumArray[j] "+j+" "+partitionNumArray[j]);
		}
		
		// Create the initial vector of 2 elements (apple, orange)
		ICombinatoricsVector<Integer> originalVector = Factory.createVector(partitionNumArray);
		
	    //ICombinatoricsVector<Integer> originalVector = Factory.createVector(new String[] { "apple", "orange" });

		// Create the generator by calling the appropriate method in the Factory class. 
		// Set the second parameter as 3, since we will generate 3-elemets permutations
		Generator<Integer> gen = Factory.createPermutationWithRepetitionGenerator(originalVector, (int)numAttr);
		
		// Print the result
		for( ICombinatoricsVector<Integer> perm : gen )
		{
			ContextServiceLogger.getLogger().fine("perm.getVector() "+perm.getVector());
			ContextServiceLogger.getLogger().fine("hyperspaceDB."
					+ "insertIntoSubspacePartitionInfo complete");
		}
	}
	
//	private class ProfilerStatClass implements Runnable
//	{
//		private long localNumberOfQueryFromUser							= 0;
//		private long localNumberOfQueryFromUserDepart					= 0;
//		private long localNumberOfQuerySubspaceRegion					= 0;
//		private long localNumberOfQuerySubspaceRegionReply				= 0;
//		
//		@Override
//		public void run()
//		{
//			while(true)
//			{
//				try
//				{
//					Thread.sleep(10000);
//				} catch (InterruptedException e)
//				{
//					e.printStackTrace();
//				}
//				
//				long diff1 = numberOfQueryFromUser - localNumberOfQueryFromUser;
//				long diff2 = numberOfQueryFromUserDepart - localNumberOfQueryFromUserDepart;
//				long diff3 = numberOfQuerySubspaceRegion - localNumberOfQuerySubspaceRegion;
//				long diff4 = numberOfQuerySubspaceRegionReply - localNumberOfQuerySubspaceRegionReply;
//				
//				localNumberOfQueryFromUser							= numberOfQueryFromUser;
//				localNumberOfQueryFromUserDepart					= numberOfQueryFromUserDepart;
//				localNumberOfQuerySubspaceRegion					= numberOfQuerySubspaceRegion;
//				localNumberOfQuerySubspaceRegionReply				= numberOfQuerySubspaceRegionReply;
//				
//				//ContextServiceLogger.getLogger().fine("QueryFromUserRate "+diff1+" QueryFromUserDepart "+diff2+" QuerySubspaceRegion "+diff3+
//				//		" QuerySubspaceRegionReply "+diff4+
//				//		" DelayProfiler stats "+DelayProfiler.getStats());
//				
//				//ContextServiceLogger.getLogger().fine( "Pending query requests "+pendingQueryRequests.size() );
//				//ContextServiceLogger.getLogger().fine("DelayProfiler stats "+DelayProfiler.getStats());
//			}
//		}
//	}
}