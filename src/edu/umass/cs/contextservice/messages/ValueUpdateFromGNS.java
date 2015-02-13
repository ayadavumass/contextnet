package edu.umass.cs.contextservice.messages;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Class defines the packet type of the GNS trigger
 * @author ayadav
 */

public class ValueUpdateFromGNS<NodeIDType> extends BasicContextServicePacket<NodeIDType>
{
	private enum Keys {VERSION_NUM, GUID, ATTRNAME, OLDVALUE, NEWVALUE, 
		ALL_OTHER_ATTRs, SOURCE_IP, SOURCE_PORT};
	
	private final long versionNum;
	private final String GUID;
	private final String attrName;
	private final String oldVal;
	private final String newVal;
	private final JSONObject allAttributes; // contains all context attributes for the group update trigger.
	private final String sourceIP;
	private final int sourcePort;
	
	public ValueUpdateFromGNS(NodeIDType initiator, long versionNum, String GUID, String attrName, 
			String oldVal, String newVal, JSONObject allAttributes, String sourceIP, int sourcePort)
	{
		super(initiator, ContextServicePacket.PacketType.VALUE_UPDATE_MSG_FROM_GNS);
		this.versionNum = versionNum;
		this.GUID = GUID;
		this.attrName = attrName;
		this.oldVal = oldVal;
		this.newVal = newVal;
		this.allAttributes = allAttributes;
		this.sourceIP = sourceIP;
		this.sourcePort = sourcePort;
	}
	
	public ValueUpdateFromGNS(JSONObject json) throws JSONException
	{
		//ValueUpdateFromGNS((NodeIDType)0, json.getString(Keys.GUID.toString()), 
		//		json.getDouble(Keys.OLDVALUE.toString()), json.getDouble(Keys.NEWVALUE.toString()));
		super(json);
		this.versionNum = json.getLong(Keys.VERSION_NUM.toString());
		this.GUID = json.getString(Keys.GUID.toString());
		this.attrName = json.getString(Keys.ATTRNAME.toString());
		this.oldVal = json.getString(Keys.OLDVALUE.toString());
		this.newVal = json.getString(Keys.NEWVALUE.toString());
		this.allAttributes = json.getJSONObject(Keys.ALL_OTHER_ATTRs.toString());
		this.sourceIP = json.getString(Keys.SOURCE_IP.toString());
		this.sourcePort = json.getInt(Keys.SOURCE_PORT.toString());
		//System.out.println("\n\n ValueUpdateFromGNS constructor");
	}
	
	public JSONObject toJSONObjectImpl() throws JSONException
	{
		JSONObject json = super.toJSONObjectImpl();
		json.put(Keys.VERSION_NUM.toString(), this.versionNum);
		json.put(Keys.GUID.toString(), this.GUID);
		json.put(Keys.ATTRNAME.toString(), attrName);
		json.put(Keys.OLDVALUE.toString(), this.oldVal);
		json.put(Keys.NEWVALUE.toString(), this.newVal);
		json.put(Keys.ALL_OTHER_ATTRs.toString(), this.allAttributes);
		json.put(Keys.SOURCE_IP.toString(), this.sourceIP);
		json.put(Keys.SOURCE_PORT.toString(), this.sourcePort);
		return json;
	}
	
	public long getVersionNum()
	{
		return this.versionNum;
	}
	
	public String getGUID()
	{
		return GUID;
	}
	
	public String getAttrName()
	{
		return attrName;
	}
	
	public String getOldVal()
	{
		return this.oldVal;
	}
	
	public String getNewVal()
	{
		return this.newVal;
	}
	
	public JSONObject getAllAttrs()
	{
		return this.allAttributes;
	}
	
	public String getSourceIP()
	{
		return this.sourceIP;
	}
	
	public int getSourcePort()
	{
		return this.sourcePort;
	}
	
	public static void main(String[] args)
	{
		/*int[] group = {3, 45, 6, 19};
		MetadataMsgToValuenode<Integer> se = 
		new MetadataMsgToValuenode<Integer>(4, "name1", 2, Util.arrayToIntSet(group), Util.arrayToIntSet(group));
		try
		{
			System.out.println(se);
			MetadataMsgToValuenode<Integer> se2 = new MetadataMsgToValuenode<Integer>(se.toJSONObject());
			System.out.println(se2);
			assert(se.toString().length()==se2.toString().length());
			assert(se.toString().indexOf("}") == se2.toString().indexOf("}"));
			assert(se.toString().equals(se2.toString())) : se.toString() + "!=" + se2.toString();
		} catch(JSONException je)
		{
			je.printStackTrace();
		}*/
	}
}