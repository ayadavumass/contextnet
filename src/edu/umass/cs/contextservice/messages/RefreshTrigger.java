package edu.umass.cs.contextservice.messages;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class RefreshTrigger<NodeIDType> extends BasicContextServicePacket<NodeIDType>
{
	private enum Keys {TO_BE_REMOVED, TO_BE_ADDED, 
		VERSION_NUM, GUID, UPDATE_START_TIME};
	
	//private final String query;  // original query sent by the user.
	private final JSONArray toBeRemoved;
	private final JSONArray toBeAdded;
	private final long versionNum;
	private final String updateInGUID;
	//private final int addRemove;
	// indicates the update start time for this trigger, the udpate that caused this trigger. 
	private final long updStartTime;
	
	public RefreshTrigger(NodeIDType initiator, JSONArray toBeRemoved, 
			JSONArray toBeAdded, long versionNum,
			String GUID, long updStartTime)
	{
		super(initiator, ContextServicePacket.PacketType.REFRESH_TRIGGER);
		
		this.toBeRemoved = toBeRemoved;
		this.toBeAdded = toBeAdded;
		//this.query = query;
		this.versionNum = versionNum;
		this.updateInGUID = GUID;
		this.updStartTime = updStartTime;
	}
	
	public RefreshTrigger(JSONObject json) throws JSONException
	{
		super(json);
		this.toBeRemoved = json.getJSONArray(Keys.TO_BE_REMOVED.toString());
		this.toBeAdded = json.getJSONArray(Keys.TO_BE_ADDED.toString());
		//this.query = json.getString(Keys.QUERY.toString());
		this.versionNum = json.getLong(Keys.VERSION_NUM.toString());
		this.updateInGUID = json.getString(Keys.GUID.toString());
		this.updStartTime = json.getLong(Keys.UPDATE_START_TIME.toString());
	}
	
	public JSONObject toJSONObjectImpl() throws JSONException
	{
		JSONObject json = super.toJSONObjectImpl();
		json.put(Keys.TO_BE_REMOVED.toString(), this.toBeRemoved);
		json.put(Keys.TO_BE_ADDED.toString(), this.toBeAdded);
		json.put(Keys.VERSION_NUM.toString(), versionNum);
		json.put(Keys.GUID.toString(), updateInGUID);
		json.put(Keys.UPDATE_START_TIME.toString(), updStartTime);
		return json;
	}
	
	public JSONArray getToBeRemovedGroupGUIDs()
	{
		return this.toBeRemoved;
	}
	
	public JSONArray getToBeAddedGroupGUIDs()
	{
		return this.toBeAdded;
	}
	
	public long getVersionNum()
	{
		return this.versionNum;
	}
	
	public String getUpdateGUID()
	{
		return this.updateInGUID;
	}
	
	public long getUpdateStartTime()
	{
		return this.updStartTime;
	}
	
	public static void main(String[] args)
	{
	}
}