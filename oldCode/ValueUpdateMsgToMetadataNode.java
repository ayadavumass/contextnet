package edu.umass.cs.contextservice.messages;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * @author ayadav
 *
 * @param <Integer>
 */
public class ValueUpdateMsgToMetadataNode<Integer> extends BasicContextServicePacket<Integer>
{
	private enum Keys {VERSION_NUM , GUID, ATTR_NAME, OLD_VAL, NEW_VAL, REQUEST_ID};
	
	private final long versionNum;
	//GUID of the update
	private final String GUID;
	private final String attributeName;
	// old value of attribute, in the beginning
	// the old value and the new value is same.
	private final double oldValue;
	private final double newValue;
	
	//private final JSONObject allAttributes; // contains all context attributes for the group update trigger.
	
	// current attribute name, 
	// used in MercuryScheme, where update needs to be sent to all attribute hubs/attribute metadata nodes
	// this field denotes for which attribute update is meant for.
	// the value of this attribute is in allAttributes array.
	//private final String taggedAttrName;
	
	//private final Integer sourceID;
	
	private final long requestID;
	
	public ValueUpdateMsgToMetadataNode(Integer initiator, long versionNum, String GUID, String attrName, double oldValue,
			double newValue, long requestID)
	{
		super(initiator, ContextServicePacket.PacketType.VALUE_UPDATE_MSG_TO_METADATANODE);
		this.versionNum = versionNum;
		this.GUID = GUID;
		this.attributeName = attrName;
		this.oldValue = oldValue;
		this.newValue = newValue;
		//this.allAttributes = allAttributes;
		//this.taggedAttrName = currAttrName;
		//this.sourceID = sourceID;
		this.requestID = requestID;
	}
	
	public ValueUpdateMsgToMetadataNode(JSONObject json) throws JSONException
	{
		super(json);
		this.versionNum = json.getLong(Keys.VERSION_NUM.toString());
		this.GUID = json.getString(Keys.GUID.toString());
		this.attributeName = json.getString(Keys.ATTR_NAME.toString());
		this.oldValue = json.getDouble(Keys.OLD_VAL.toString());
		this.newValue = json.getDouble(Keys.NEW_VAL.toString());
		//this.allAttributes = json.getJSONObject(Keys.ALL_OTHER_ATTRs.toString());
		//this.taggedAttrName = json.getString(Keys.TAGGED_ATTR.toString());
		//this.sourceID= (Integer) json.get(Keys.SOURCE_ID.toString());
		this.requestID = json.getLong(Keys.REQUEST_ID.toString());
	}
	
	public JSONObject toJSONObjectImpl() throws JSONException
	{
		JSONObject json = super.toJSONObjectImpl();
		json.put(Keys.VERSION_NUM.toString(), this.versionNum);
		json.put(Keys.GUID.toString(), GUID);
		json.put(Keys.ATTR_NAME.toString(), attributeName);
		json.put(Keys.OLD_VAL.toString(), oldValue);
		json.put(Keys.NEW_VAL.toString(), newValue);
		//json.put(Keys.ALL_OTHER_ATTRs.toString(), this.allAttributes);
		//json.put(Keys.TAGGED_ATTR.toString(), this.taggedAttrName);
		//json.put(Keys.SOURCE_ID.toString(), this.sourceID);
		json.put(Keys.REQUEST_ID.toString(), this.requestID);
		return json;
	}
	
	public long getVersionNum()
	{
		return this.versionNum;
	}
	
	public String getGUID()
	{
		return this.GUID;
	}
	
	public String getAttrName()
	{
		return this.attributeName;
	}
	
	public double getOldValue()
	{
		return this.oldValue;
	}
	
	public double getNewValue()
	{
		return this.newValue;
	}
	
	/*public JSONObject getAllAttrs()
	{
		return this.allAttributes;
	}
	public String getTaggedAttribute()
	{
		return this.taggedAttrName;
	}*/
	/*public Integer getSourceID()
	{
		return this.sourceID;
	}*/
	
	public long getRequestID()
	{
		return this.requestID;
	}
	
	public static void main(String[] args)
	{
	}
}