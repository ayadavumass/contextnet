package edu.umass.cs.contextservice.schemes.components;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import edu.umass.cs.contextservice.config.ContextServiceConfig;
import edu.umass.cs.contextservice.config.ContextServiceConfig.PrivacySchemes;
import edu.umass.cs.contextservice.database.AbstractDataStorageDB;
import edu.umass.cs.contextservice.database.DBConstants;
import edu.umass.cs.contextservice.database.recordformat.HashIndexGUIDRecord;
import edu.umass.cs.contextservice.logging.ContextServiceLogger;
import edu.umass.cs.contextservice.messages.QueryMesgToSubspaceRegion;
import edu.umass.cs.contextservice.messages.QueryMesgToSubspaceRegionReply;
import edu.umass.cs.contextservice.messages.QueryMsgFromUserReply;
import edu.umass.cs.contextservice.profilers.CNSProfiler;
import edu.umass.cs.contextservice.queryparsing.QueryInfo;
import edu.umass.cs.contextservice.queryparsing.QueryParser;
import edu.umass.cs.contextservice.regionmapper.AbstractRegionMappingPolicy;
import edu.umass.cs.contextservice.regionmapper.helper.AttributeValueRange;
import edu.umass.cs.contextservice.schemes.helperclasses.SearchReplyInfo;
import edu.umass.cs.contextservice.updates.UpdateInfo;
import edu.umass.cs.nio.JSONMessenger;

public class GUIDAttrValueProcessing
								extends AbstractGUIDAttrValueProcessing 
{
	public GUIDAttrValueProcessing( Integer myID, 
			AbstractRegionMappingPolicy regionMappingPolicy, 
			AbstractDataStorageDB hyperspaceDB, 
		JSONMessenger<Integer> messenger , 
		ConcurrentHashMap<Long, QueryInfo> pendingQueryRequests, 
		CNSProfiler profStats )
	{
		super(myID, regionMappingPolicy, 
				hyperspaceDB, messenger , 
				pendingQueryRequests,  profStats);
	}
	
	public void processQueryMsgFromUser
		( QueryInfo queryInfo, boolean storeQueryForTrigger )
	{
		String query;
		String userIP;
		int userPort;
		String grpGUID;
		long expiryTime;
		
		query   = queryInfo.getQuery();
		userIP  = queryInfo.getUserIP();
		userPort   = queryInfo.getUserPort();
		grpGUID = queryInfo.getGroupGUID();
		expiryTime = queryInfo.getExpiryTime();
		
		
		if( grpGUID.length() <= 0 )
		{
			ContextServiceLogger.getLogger().fine
			("Query request failed at the recieving node ");
			return;
		}
	    
		synchronized(this.pendingQueryLock)
		{
			queryInfo.setQueryRequestID(queryIdCounter++);
		}
		pendingQueryRequests.put(queryInfo.getRequestId(), queryInfo);
		
		
		long start = System.currentTimeMillis();
		List<Integer> nodeList 
				= regionMappingPolicy.getNodeIDsForSearch
					(queryInfo.getSearchQueryAttrValMap());
		long end = System.currentTimeMillis();
		
		if(ContextServiceConfig.PROFILER_ENABLED)
		{
			queryInfo.getSearchStats().setNodeInfoAndTime
								(nodeList.size(), (end-start));
		}
		
		queryInfo.initializeSearchQueryReplyInfo(nodeList);
		
		for(int i=0; i< nodeList.size(); i++)
		{
			int nodeid = nodeList.get(i);
			
			QueryMesgToSubspaceRegion queryMesgToSubspaceRegion = 
					new QueryMesgToSubspaceRegion
	    			(myID, queryInfo.getRequestId(), query, grpGUID, 
	    					userIP, userPort, storeQueryForTrigger, 
	    					expiryTime, PrivacySchemes.NO_PRIVACY.ordinal());
			
			try
			{
				this.messenger.sendToID( nodeid, 
						queryMesgToSubspaceRegion.toJSONObject() );
			} catch (IOException e)
			{
				e.printStackTrace();
			} catch (JSONException e)
			{
				e.printStackTrace();
			}
			ContextServiceLogger.getLogger().info("Sending QueryMesgToSubspaceRegion mesg from " 
					+ myID +" to node "+nodeid);
		}
	}
	
	public int processQueryMesgToSubspaceRegion(QueryMesgToSubspaceRegion 
													queryMesgToSubspaceRegion, 
													JSONArray resultGUIDs)
	{
		String query 				 		= queryMesgToSubspaceRegion.getQuery();
		// we don't evaluate the query over full value space on all attributes here.
		// because in privacy some attributes may not be specified.
		// so a query should only be evaluated on attributes that are specified 
		// in the query. Attributes that are not spcfied are stored with Double.MIN
		// value, which is outside the Min max value corresponding to an attribute.
		HashMap<String, AttributeValueRange> searchAttrValRange	 = QueryParser.parseQuery(query);
		
		long start = System.currentTimeMillis();
		int resultSize = this.hyperspaceDB.processSearchQueryUsingAttrIndex
				(searchAttrValRange, resultGUIDs);
		long end = System.currentTimeMillis();
		
		if(ContextServiceConfig.PROFILER_ENABLED)
		{
			profStats.addSearchQueryProcessTime((end-start), resultSize);
		}
		
		return resultSize;
	}
	
	public void processQueryMesgToSubspaceRegionReply(
			QueryMesgToSubspaceRegionReply 
									queryMesgToSubspaceRegionReply)
	{
		Integer senderID = queryMesgToSubspaceRegionReply.getSender();
		long requestId = queryMesgToSubspaceRegionReply.getRequestId();
		
		QueryInfo queryInfo = pendingQueryRequests.get(requestId);
		
		boolean allRepRecvd = 
				queryInfo.addReplyFromANode( senderID, queryMesgToSubspaceRegionReply);
		
		if( allRepRecvd )
		{
			JSONArray concatResult 							 = new JSONArray();

			int totalNumReplies 							 = 0;
			
			HashMap<Integer, SearchReplyInfo> searchReplyMap 
											= queryInfo.getSearchReplyMap();
			
			if( ContextServiceConfig.sendFullRepliesToClient )
			{	
				if(!ContextServiceConfig.LIMITED_SEARCH_REPLY_ENABLE)
				{
					Iterator<Integer> nodeIdIter = searchReplyMap.keySet().iterator();
	
					while( nodeIdIter.hasNext() )
					{
						int nodeid = nodeIdIter.next();
						SearchReplyInfo replyInfo = searchReplyMap.get(nodeid);
						concatResult.put(replyInfo.replyArray);
						totalNumReplies = totalNumReplies + replyInfo.replyArray.length();
					}
				}
				else
				{
					JSONArray limitedArray = new JSONArray();
					Iterator<Integer> nodeIdIter = searchReplyMap.keySet().iterator();
					while( nodeIdIter.hasNext() )
					{
						int nodeid = nodeIdIter.next();
						SearchReplyInfo replyInfo = searchReplyMap.get(nodeid);
					
						for(int i=0; i<replyInfo.replyArray.length(); i++)
						{
							try {
								limitedArray.put(replyInfo.replyArray.getJSONObject(i));
							} catch (JSONException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							
							if(limitedArray.length() >= 
										ContextServiceConfig.LIMITED_SEARCH_REPLY_SIZE)
							{
								break;
							}
						}
						if(limitedArray.length() >= 
								ContextServiceConfig.LIMITED_SEARCH_REPLY_SIZE)
						{
							break;
						}
					}
					
					concatResult.put(limitedArray);
					totalNumReplies = totalNumReplies + limitedArray.length();
				}
			}
			else
			{
				Iterator<Integer> nodeIdIter = searchReplyMap.keySet().iterator();

				while( nodeIdIter.hasNext() )
				{
					int nodeid = nodeIdIter.next();
					SearchReplyInfo replyInfo = searchReplyMap.get(nodeid);
					int currRepSize = replyInfo.numReplies;
					totalNumReplies = totalNumReplies + currRepSize;
				}
			}
			
			QueryMsgFromUserReply queryMsgFromUserReply 
				= new QueryMsgFromUserReply( myID, 
						queryInfo.getQuery(), queryInfo.getGroupGUID(), concatResult, 
						queryInfo.getUserReqID(), totalNumReplies, 
						PrivacySchemes.NO_PRIVACY.ordinal() );
			
			try
			{
				this.messenger.sendToAddress(new InetSocketAddress(queryInfo.getUserIP(), 
						queryInfo.getUserPort()), queryMsgFromUserReply.toJSONObject());
			} catch (IOException e)
			{
				e.printStackTrace();
			} catch (JSONException e)
			{
				e.printStackTrace();
			}
			ContextServiceLogger.getLogger().info("Sending queryMsgFromUserReply mesg from " 
					+ myID +" to node "+new InetSocketAddress(queryInfo.getUserIP(), queryInfo.getUserPort()));

			QueryInfo qInfo = pendingQueryRequests.remove(requestId);
			
			if(ContextServiceConfig.PROFILER_ENABLED)
			{
				qInfo.getSearchStats().setQueryEndTime();
				profStats.addSearchStats(qInfo.getSearchStats());
			}
		}
	}
	
	
	/**
	 * This function processes a request serially.
	 * when one outstanding request completes.
	 */
	public void processUpdateFromGNS(UpdateInfo updateReq)
	{
		assert(updateReq != null);
		
		String GUID 	 		= updateReq.getValueUpdateFromGNS().getGUID();
		JSONObject attrValuePairs 
						 		= updateReq.getValueUpdateFromGNS().getAttrValuePairs();
		long requestID 	 		= updateReq.getRequestId();
		long updateStartTime	= updateReq.getValueUpdateFromGNS().getUpdateStartTime();
		JSONArray anonymizedIDToGuidMapping 
								= updateReq.getValueUpdateFromGNS().getAnonymizedIDToGuidMapping();
		
		// get the old value and process the update in primary subspace and other subspaces.
		Connection myConn = null;
		
		try
		{
			boolean firstTimeInsert = false;	
			
			myConn = this.hyperspaceDB.getDataSource().getConnection();
			
			long start = System.currentTimeMillis();
			HashIndexGUIDRecord oldGuidRecord 
						= this.hyperspaceDB.getGUIDStoredUsingHashIndex(GUID, myConn);
			long end = System.currentTimeMillis();
			
			if(ContextServiceConfig.PROFILER_ENABLED)
				updateReq.getUpdateStats().setHashIndexReadTime(end-start);
			
			int updateOrInsert 			= -1;
			
			if( oldGuidRecord.getAttrValJSON().length() == 0 )
			{
				firstTimeInsert = true;
				updateOrInsert = DBConstants.INSERT_REC;
			}
			else
			{
				if(ContextServiceConfig.PROFILER_ENABLED)
				{
					profStats.incrementIncomingUpdateRate();
				}
				firstTimeInsert = false;
				updateOrInsert = DBConstants.UPDATE_REC;
			}
			
			//FIXME: need to convert this from JSON to a meesage format.
			JSONObject jsonToWrite = getJSONToWriteInPrimarySubspace( oldGuidRecord, 
					attrValuePairs, anonymizedIDToGuidMapping );
			
			start = System.currentTimeMillis();
			this.hyperspaceDB.storeGUIDUsingHashIndex
								(GUID, jsonToWrite, updateOrInsert, myConn);
			
			end = System.currentTimeMillis();
			
			if(ContextServiceConfig.PROFILER_ENABLED)
				updateReq.getUpdateStats().setHashIndexWriteTime(end-start);
			
			// process update at secondary subspaces.
			updateGUIDInAttrIndexes( oldGuidRecord , 
					firstTimeInsert , attrValuePairs , GUID , 
					requestID, updateStartTime, jsonToWrite, updateReq  );
		}
		catch ( JSONException e )
		{
			e.printStackTrace();
		} catch (SQLException e) 
		{
			e.printStackTrace();
		}
		finally
		{
			if(myConn != null)
			{
				try 
				{
					myConn.close();
				} 
				catch (SQLException e) 
				{
					e.printStackTrace();
				}
			}
		}
	}
	
	
	private void updateGUIDInAttrIndexes( HashIndexGUIDRecord oldGuidRecord , 
			boolean firstTimeInsert , JSONObject updatedAttrValJSON , 
			String GUID , long requestID, long updateStartTime, 
			JSONObject primarySubspaceJSON, UpdateInfo updateReq )
					throws JSONException
	{
		guidValueProcessingOnUpdate
		( oldGuidRecord,  updatedAttrValJSON, GUID, requestID, firstTimeInsert, 
				updateStartTime, primarySubspaceJSON, updateReq );
	}
}